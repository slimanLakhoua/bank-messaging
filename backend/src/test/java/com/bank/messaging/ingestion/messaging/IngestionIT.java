package com.bank.messaging.ingestion.messaging;

import com.bank.messaging.ingestion.support.CrashAfterSqlCommit;
import com.bank.messaging.ingestion.application.StoreMessageUseCase;
import com.bank.messaging.ingestion.application.ReceivedTextMessage;

import jakarta.jms.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jms.listener.DefaultMessageListenerContainer;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.nio.file.Path;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static com.bank.messaging.support.IntegrationInfrastructure.await;
import static com.bank.messaging.support.IntegrationInfrastructure.compose;
import static com.bank.messaging.support.IntegrationInfrastructure.mqsc;

@SpringBootTest(properties = {"bank.ingestion.auto-startup=false", "bank.ingestion.retry-delay-ms=200", "spring.datasource.hikari.connection-timeout=500", "bank.ingestion.max-payload-bytes=1024"})
class IngestionIT {
    static final String INPUT = "DEV.BANK.T" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    static final String BACKOUT = INPUT + ".BO";
    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("bank.ingestion.queue", () -> INPUT);
        registry.add("bank.ingestion.backout-queue", () -> BACKOUT);
    }
    @Autowired ApplicationContext application;
    @Autowired JdbcTemplate jdbc;
    @Autowired ConnectionFactory connectionFactory;
    @Autowired org.springframework.core.env.Environment environment;
    @Autowired StoreMessageUseCase storageService;
    @Autowired io.micrometer.core.instrument.MeterRegistry metrics;
    DefaultMessageListenerContainer container;

    @BeforeEach
    void prepare() throws Exception {
        mqsc("DEFINE QLOCAL('" + INPUT + "') BOTHRESH(0) REPLACE\nDEFINE QLOCAL('" + BACKOUT + "') REPLACE\n");
        container = application.getBean("ingestionContainer", DefaultMessageListenerContainer.class);
        if (!container.isActive()) container.initialize();
    }

    @AfterEach
    void cleanup() throws Exception {
        if (container != null) container.shutdown();
        if (connectionFactory instanceof org.springframework.jms.connection.CachingConnectionFactory cached) cached.resetConnection();
        mqsc("CLEAR QLOCAL('" + INPUT + "')\nCLEAR QLOCAL('" + BACKOUT + "')\n");
        jdbc.update("delete from messaging.received_message where source_queue = ?", INPUT);
    }

    @AfterAll
    static void deleteQueues() throws Exception {
        mqsc("DELETE QLOCAL('" + INPUT + "')\nDELETE QLOCAL('" + BACKOUT + "')\n");
    }

    @Test
    void storesTextAndMetadataBeforeAcknowledging() throws Exception {
        String id = sendText("Paiement synthétique €", "test-correlation");
        container.start();
        await(() -> rows(id) == 1);
        container.stop();
        assertThat(jdbc.queryForObject("select payload from messaging.received_message where jms_message_id = ? and source_queue = ?", String.class, id, INPUT))
                .isEqualTo("Paiement synthétique €");
        assertThat(jdbc.queryForObject("select payload_size from messaging.received_message where jms_message_id = ? and source_queue = ?", Integer.class, id, INPUT))
                .isEqualTo("Paiement synthétique €".getBytes(java.nio.charset.StandardCharsets.UTF_8).length);
        assertThat(jdbc.queryForObject("select correlation_id from messaging.received_message where jms_message_id = ? and source_queue = ?", String.class, id, INPUT))
                .isEqualTo("test-correlation");
        assertThat(browseCount(INPUT)).isZero();
    }

    @Test
    void unsupportedMessageIsMovedToBackoutWithBodyAndIdentity() throws Exception {
        String id;
        try (var context = connectionFactory.createContext()) {
            var message = context.createBytesMessage();
            message.writeBytes(new byte[]{1, 2, 3});
            context.createProducer().setDeliveryMode(DeliveryMode.PERSISTENT).send(context.createQueue(INPUT), message);
            id = message.getJMSMessageID();
        }
        container.start();
        await(() -> browseCount(BACKOUT) == 1);
        container.stop();
        try (var context = connectionFactory.createContext(); var consumer = context.createConsumer(context.createQueue(BACKOUT))) {
            var message = (BytesMessage) consumer.receive(3000);
            assertThat(message.getStringProperty("OriginalMessageId")).isEqualTo(id);
            assertThat(message.getIntProperty("FailedDeliveryCount")).isGreaterThanOrEqualTo(3);
            byte[] body = new byte[3];
            message.readBytes(body);
            assertThat(body).containsExactly(1, 2, 3);
        }
        assertThat(rows(id)).isZero();
        assertThat(browseCount(INPUT)).isZero();
    }

    @Test
    void malformedUtf8IsQuarantinedWithoutLosingItsRawBytes() throws Exception {
        var properties = new java.util.Hashtable<String, Object>();
        String connectionName = environment.getProperty("ibm.mq.conn-name");
        int separator = connectionName.indexOf('(');
        properties.put(com.ibm.mq.constants.MQConstants.HOST_NAME_PROPERTY, connectionName.substring(0, separator));
        properties.put(com.ibm.mq.constants.MQConstants.PORT_PROPERTY, Integer.parseInt(connectionName.substring(separator + 1, connectionName.length() - 1)));
        properties.put(com.ibm.mq.constants.MQConstants.CHANNEL_PROPERTY, environment.getProperty("ibm.mq.channel"));
        properties.put(com.ibm.mq.constants.MQConstants.USER_ID_PROPERTY, environment.getProperty("ibm.mq.user"));
        properties.put(com.ibm.mq.constants.MQConstants.PASSWORD_PROPERTY, environment.getProperty("ibm.mq.password"));
        var manager = new com.ibm.mq.MQQueueManager("QM1", properties);
        byte[] bytes = {(byte) 0xc3, 0x28}; // Invalid UTF-8, MQSTR body deliberately produced outside JMS.
        try {
            var queue = manager.accessQueue(INPUT, com.ibm.mq.constants.MQConstants.MQOO_OUTPUT);
            try {
                var message = new com.ibm.mq.MQMessage();
                message.format = com.ibm.mq.constants.MQConstants.MQFMT_STRING;
                message.characterSet = 1208;
                message.persistence = com.ibm.mq.constants.MQConstants.MQPER_PERSISTENT;
                message.write(bytes);
                queue.put(message);
            } finally { queue.close(); }
            container.start();
            await(() -> browseCount(BACKOUT) == 1);
            container.stop();
            try (var context = connectionFactory.createContext(); var browser = context.createBrowser(context.createQueue(BACKOUT))) {
                var quarantined = (Message) browser.getEnumeration().nextElement();
                assertThat(quarantined.getStringProperty("FailureReason")).isEqualTo("MESSAGE_DECODING_FAILED");
                assertThat(quarantined.getIntProperty("FailedDeliveryCount")).isGreaterThanOrEqualTo(3);
            }
            assertThat(browseCount(INPUT)).isZero();
            assertThat(jdbc.queryForObject("select count(*) from messaging.received_message where source_queue = ?", Integer.class, INPUT)).isZero();
            var output = manager.accessQueue(BACKOUT, com.ibm.mq.constants.MQConstants.MQOO_INPUT_AS_Q_DEF);
            try {
                var received = new com.ibm.mq.MQMessage();
                output.get(received);
                byte[] raw = new byte[received.getDataLength()];
                received.readFully(raw);
                // A JMS RFH2 header may be added; the original undecodable body must be intact.
                assertThat(java.util.Arrays.copyOfRange(raw, raw.length - bytes.length, raw.length)).containsExactly(bytes);
            } finally { output.close(); }
        } finally { manager.disconnect(); }
    }

    @Test
    void transientExtractionFailureIsNotQuarantinedEvenAfterManyDeliveries() throws Exception {
        var failure = new JMSException("Connection interrupted");
        failure.setLinkedException(new java.io.IOException("Connection interrupted"));
        try (var connection = connectionFactory.createConnection();
             var session = connection.createSession(true, Session.SESSION_TRANSACTED)) {
            var original = session.createTextMessage("valid body");
            var message = (TextMessage) java.lang.reflect.Proxy.newProxyInstance(TextMessage.class.getClassLoader(),
                    new Class<?>[]{TextMessage.class}, (proxy, method, args) -> {
                        if (method.getName().equals("getText")) throw failure;
                        if (method.getName().equals("getJMSMessageID")) return "ID:transient";
                        if (method.getName().equals("getIntProperty") && "JMSXDeliveryCount".equals(args[0])) return 10;
                        try { return method.invoke(original, args); }
                        catch (java.lang.reflect.InvocationTargetException ex) { throw ex.getCause(); }
                    });
            var ingestor = application.getBean(JmsMessageListener.class);
            assertThat(Assertions.assertThrows(JMSException.class, () -> ingestor.onMessage(message, session))).isSameAs(failure);
            session.rollback();
        }
        assertThat(browseCount(BACKOUT)).isZero();
        assertThat(rows("ID:transient")).isZero();
    }

    @Test
    void sameIdentityIsInsertedOnceUnderConcurrentDelivery() throws Exception {
        String id = "ID:" + UUID.randomUUID();
        try (var pool = java.util.concurrent.Executors.newFixedThreadPool(8)) {
            var start = new java.util.concurrent.CountDownLatch(1);
            var futures = new java.util.ArrayList<java.util.concurrent.Future<Boolean>>();
            for (int i = 0; i < 8; i++) futures.add(pool.submit(() -> {
                start.await();
                return storageService.store(new ReceivedTextMessage(
                        INPUT, id, null, "original"));
            }));
            start.countDown();
            int inserted = 0;
            for (var future : futures) if (future.get(15, java.util.concurrent.TimeUnit.SECONDS)) inserted++;
            assertThat(inserted).isEqualTo(1);
        }
        assertThat(storageService.store(new ReceivedTextMessage(
                INPUT, id, null, "changed"))).isFalse();
        assertThat(rows(id)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select payload from messaging.received_message where source_queue = ? and jms_message_id = ?", String.class, INPUT, id)).isEqualTo("original");
    }

    @Test
    void identicalBodiesWithDifferentTransportIdsAreDistinct() throws Exception {
        String first = sendText("same body", null);
        String second = sendText("same body", null);
        container.start();
        await(() -> rows(first) == 1 && rows(second) == 1);
        assertThat(first).isNotEqualTo(second);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.NullSource
    @org.junit.jupiter.params.provider.ValueSource(strings = {"invalid\u0000text"})
    void invalidTextIsQuarantined(String text) throws Exception {
        String id = sendText(text, null);
        container.start();
        await(() -> browseCount(BACKOUT) == 1);
        container.stop();
        assertThat(rows(id)).isZero();
        assertThat(browseCount(INPUT)).isZero();
    }

    @Test
    void oversizeTextIsQuarantined() throws Exception {
        String id = sendText("x".repeat(1025), null);
        container.start();
        await(() -> browseCount(BACKOUT) == 1);
        container.stop();
        assertThat(rows(id)).isZero();
        assertThat(browseCount(INPUT)).isZero();
    }

    @Test
    void failedBackoutSendRollsBackTheOriginalReceive() throws Exception {
        mqsc("ALTER QLOCAL('" + BACKOUT + "') PUT(DISABLED)\n");
        double failures = metrics.counter("bank.ingestion.failures").count();
        String id = sendText("invalid\u0000text", null);
        try {
            container.start();
            await(() -> metrics.counter("bank.ingestion.failures").count() >= failures + 4);
            container.stop();
            assertThat(browseCount(INPUT)).isEqualTo(1);
            assertThat(browseCount(BACKOUT)).isZero();
            assertThat(rows(id)).isZero();
        } finally {
            mqsc("ALTER QLOCAL('" + BACKOUT + "') PUT(ENABLED)\n");
        }
        container.start();
        await(() -> browseCount(BACKOUT) == 1);
        container.stop();
        assertThat(browseCount(INPUT)).isZero();
    }

    @Test
    void databaseOutageDoesNotQuarantineValidMessagesAndRecovers() throws Exception {
        String id = sendText("survives database outage", null);
        long recoveryStart;
        double failures = metrics.counter("bank.ingestion.failures").count();
        try {
            compose("stop", "postgres");
            container.start();
            await(() -> metrics.counter("bank.ingestion.failures").count() >= failures + 4);
            container.stop();
            assertThat(browseCount(INPUT)).isEqualTo(1);
            assertThat(browseCount(BACKOUT)).isZero();
            // Resume while SQL is still down: recovery must happen without another listener restart.
            container.start();
        } finally {
            recoveryStart = System.nanoTime();
            compose("up", "-d", "--wait", "--wait-timeout", "120", "postgres");
        }
        await(() -> rows(id) == 1);
        recordRecovery("sql-restart", recoveryStart, id);
        container.stop();
        assertThat(browseCount(INPUT)).isZero();
        assertThat(browseCount(BACKOUT)).isZero();
    }

    @Test
    void reconnectsAfterQueueManagerRestart() throws Exception {
        container.start();
        String first = sendText("before restart", null);
        await(() -> rows(first) == 1);
        long recoveryStart;
        try {
            compose("stop", "mq");
        } finally {
            recoveryStart = System.nanoTime();
            compose("up", "-d", "--wait", "--wait-timeout", "120", "mq");
        }
        String second = sendText("after restart", null);
        await(() -> rows(second) == 1);
        recordRecovery("mq-restart", recoveryStart, second);
        container.stop();
        assertThat(rows(first)).isEqualTo(1);
        assertThat(browseCount(INPUT)).isZero();
    }

    @Test
    void processCrashAfterSqlCommitRedeliversWithoutDuplicateStorage() throws Exception {
        String id = sendText("survives actual process crash", null);
        String javaExecutable = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        String classpath = System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"));
        var process = new ProcessBuilder(javaExecutable, "-cp", classpath, CrashAfterSqlCommit.class.getName(),
                "--bank.ingestion.queue=" + INPUT, "--bank.ingestion.backout-queue=" + BACKOUT,
                "--bank.ingestion.auto-startup=true")
                .redirectErrorStream(true).redirectOutput(Path.of("target", "crash-process.log").toFile()).start();
        try {
            assertThat(process.waitFor(40, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            assertThat(process.exitValue()).as("See target/crash-process.log").isEqualTo(23);
        } finally {
            if (process.isAlive()) { process.destroyForcibly(); process.waitFor(); }
        }
        long recoveryStart = System.nanoTime();
        assertThat(rows(id)).isEqualTo(1);
        await(() -> browseCount(INPUT) == 1);
        double duplicates = metrics.counter("bank.ingestion.duplicates").count();
        container.start();
        await(() -> metrics.counter("bank.ingestion.duplicates").count() > duplicates);
        recordRecovery("crash-redelivery", recoveryStart, id);
        container.stop();
        assertThat(rows(id)).isEqualTo(1);
        assertThat(browseCount(INPUT)).isZero();
    }

    @Test
    void poisonDoesNotBlockFollowingValidMessageAndDurationsAreMeasured() throws Exception {
        sendText("invalid\u0000text", null);
        String valid = sendText("valid after poison", null);
        container.start();
        await(() -> rows(valid) == 1 && browseCount(BACKOUT) == 1);
        container.stop();
        assertThat(browseCount(INPUT)).isZero();
        assertThat(metrics.find("bank.ingestion.duration").timer()).isNotNull();
        assertThat(metrics.find("bank.ingestion.duration").timer().count()).isPositive();
        assertThat(metrics.find("bank.storage.duration").timer()).isNotNull();
        assertThat(metrics.find("bank.storage.duration").timer().count()).isPositive();
    }

    void recordRecovery(String scenario, long start, String id) throws Exception {
        var result = new org.json.JSONObject().put("scenario", scenario)
                .put("observedRecoveryMs", (System.nanoTime() - start) / 1e6)
                .put("storedRows", rows(id)).put("backoutDepth", browseCount(BACKOUT))
                .put("timestamp", java.time.Instant.now().toString());
        java.nio.file.Files.writeString(Path.of("target", "resilience.jsonl"), result + "\n",
                java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
    }

    String sendText(String body, String correlation) throws Exception {
        try (var context = connectionFactory.createContext()) {
            var message = context.createTextMessage(body);
            message.setJMSCorrelationID(correlation);
            context.createProducer().setDeliveryMode(DeliveryMode.PERSISTENT).send(context.createQueue(INPUT), message);
            return message.getJMSMessageID();
        }
    }
    int rows(String id) {
        return jdbc.queryForObject("select count(*) from messaging.received_message where source_queue = ? and jms_message_id = ?", Integer.class, INPUT, id);
    }
    int browseCount(String queue) {
        try (var context = connectionFactory.createContext(); var browser = context.createBrowser(context.createQueue(queue))) {
            int count = 0;
            var messages = browser.getEnumeration();
            while (messages.hasMoreElements()) { messages.nextElement(); count++; }
            return count;
        } catch (JMSException ex) { throw new java.lang.IllegalStateException(ex); }
    }
}
