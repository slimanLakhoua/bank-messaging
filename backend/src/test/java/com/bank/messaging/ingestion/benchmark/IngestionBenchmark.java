package com.bank.messaging.ingestion.benchmark;

import com.bank.messaging.ingestion.application.StoreMessageUseCase;
import com.bank.messaging.ingestion.application.ReceivedTextMessage;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.jms.*;
import org.json.JSONObject;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import java.net.URI;
import java.net.http.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jms.connection.CachingConnectionFactory;
import org.springframework.jms.listener.DefaultMessageListenerContainer;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.beans.factory.annotation.Qualifier;

import java.lang.management.ManagementFactory;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.Queue;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;
import static com.bank.messaging.support.IntegrationInfrastructure.mqsc;

/** Opt-in benchmark. All fault/measurement hooks live in test code. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "bank.ingestion.auto-startup=false")
class IngestionBenchmark {
    static final String INPUT = "DEV.BANK.B" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    static final String BACKOUT = INPUT + ".BO";
    static final ConcurrentMap<String, Long> SENT = new ConcurrentHashMap<>();
    static final ConcurrentMap<String, Long> LATENCIES = new ConcurrentHashMap<>();
    static final int COUNT = Integer.getInteger("bench.messages", 10000);
    static final int BYTES = Integer.getInteger("bench.bytes", 1024);
    static final int CONCURRENCY = Integer.getInteger("bench.concurrency", 1);
    static final int WARMUP = Integer.getInteger("bench.warmup", 500);
    static final int BATCH = 100;

    @DynamicPropertySource static void settings(DynamicPropertyRegistry registry) {
        registry.add("bank.ingestion.queue", () -> INPUT);
        registry.add("bank.ingestion.backout-queue", () -> BACKOUT);
        registry.add("bank.ingestion.concurrency", () -> CONCURRENCY);
    }
    @TestConfiguration(proxyBeanMethods = false)
    static class Measurements {
        @Bean @Primary StoreMessageUseCase measuredStorageService(
                @Qualifier("storeMessageUseCase") StoreMessageUseCase delegate) {
            return new StoreMessageUseCase() {
                @Override public boolean store(ReceivedTextMessage message) {
                    boolean inserted = delegate.store(message);
                    Long start = SENT.get(message.correlationId());
                    if (start != null) {
                        LATENCIES.putIfAbsent(message.correlationId(), System.nanoTime() - start);
                    }
                    return inserted;
                }
            };
        }
    }
    @LocalServerPort int port;
    static final int HTTP_CONCURRENCY = Integer.getInteger("bench.httpConcurrency", 2);
    final Queue<Long> httpLatencies = new ConcurrentLinkedQueue<>();
    final AtomicInteger httpErrors = new AtomicInteger();
    final AtomicBoolean querying = new AtomicBoolean();

    @Autowired ConnectionFactory factory;
    @Autowired DefaultMessageListenerContainer container;
    @Autowired JdbcTemplate jdbc;
    @Autowired MeterRegistry metrics;

    @Test void measurePersistentIngestion() throws Exception {
        assertThat(COUNT).isBetween(1, 50000);
        assertThat(BYTES).isBetween(1, 1048576);
        assertThat(CONCURRENCY).isBetween(1, 4);
        assertThat(WARMUP).isBetween(0, 5000);
        assertThat(HTTP_CONCURRENCY).isBetween(0, 8);
        mqsc("DEFINE QLOCAL('" + INPUT + "') MAXDEPTH(50000) BOTHRESH(0) REPLACE\nDEFINE QLOCAL('" + BACKOUT + "') REPLACE\n");
        try {
            container.start();
            send(WARMUP, "warmup", "x".repeat(BYTES));
            awaitCount(WARMUP);
            SENT.clear(); LATENCIES.clear();
            double receivedBefore = counter("received");
            double duplicatesBefore = counter("duplicates");
            double backoutBefore = counter("backout.attempts");
            double errorsBefore = counter("failures");
            var os = (com.sun.management.OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
            long cpuBefore = os.getProcessCpuTime();
            long start = System.nanoTime();
            long peakHeap = 0, maxOutstanding = 0;
            var samples = new ArrayList<JSONObject>();
            long producerEnd;
            try (var executor = Executors.newFixedThreadPool(1 + HTTP_CONCURRENCY);
                 var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()) {
                querying.set(true);
                var requests = new ArrayList<Future<?>>();
                for (int i = 0; i < HTTP_CONCURRENCY; i++) {
                    requests.add(executor.submit(() -> queryWhileIngesting(client)));
                }
                try {
                var producer = executor.submit(() -> {
                    try { send(COUNT, "measured", "x".repeat(BYTES)); }
                    catch (Exception failure) { throw new CompletionException(failure); }
                    return System.nanoTime();
                });
                long deadline = start + Duration.ofMinutes(5).toNanos();
                while (!producer.isDone() || LATENCIES.size() < COUNT) {
                    if (System.nanoTime() > deadline) { producer.cancel(true); throw new AssertionError("Benchmark timed out"); }
                    if (producer.isDone()) producer.get(); // Propagate producer failures immediately.
                    long heap = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed();
                    int outstanding = SENT.size() - LATENCIES.size();
                    peakHeap = Math.max(peakHeap, heap); maxOutstanding = Math.max(maxOutstanding, outstanding);
                    samples.add(new JSONObject().put("elapsedMs", (System.nanoTime() - start) / 1e6)
                            .put("submitted", SENT.size()).put("committed", LATENCIES.size()).put("heapBytes", heap));
                    Thread.sleep(100);
                }
                producerEnd = producer.get();
                } finally {
                    querying.set(false);
                    for (var request : requests) request.get(20, TimeUnit.SECONDS);
                }
            }
            long end = System.nanoTime();
            assertThat(httpErrors.get()).isZero();
            if (HTTP_CONCURRENCY > 0) assertThat(httpLatencies).isNotEmpty();
            long cpuAfter = os.getProcessCpuTime();
            container.shutdown();
            if (factory instanceof CachingConnectionFactory cached) cached.resetConnection();
            long stored = jdbc.queryForObject("SELECT count(*) FROM messaging.received_message WHERE source_queue = ?", Long.class, INPUT) - WARMUP;
            assertThat(stored).isEqualTo(COUNT);
            assertThat(LATENCIES).hasSize(COUNT);
            assertThat(counter("duplicates") - duplicatesBefore).isZero();
            assertThat(counter("backout.attempts") - backoutBefore).isZero();
            assertThat(counter("failures") - errorsBefore).isZero();
            int inputDepth = depth(INPUT), backoutDepth = depth(BACKOUT);
            assertThat(inputDepth).isZero(); assertThat(backoutDepth).isZero();
            var latencies = LATENCIES.values().stream().mapToLong(Long::longValue).sorted().toArray();
            var httpTimes = httpLatencies.stream().mapToLong(Long::longValue).sorted().toArray();
            var report = new JSONObject().put("timestamp", Instant.now().toString()).put("messages", COUNT)
                    .put("payloadBytes", BYTES).put("warmupMessages", WARMUP).put("concurrency", CONCURRENCY)
                    .put("httpConcurrency", HTTP_CONCURRENCY).put("httpRequests", httpTimes.length)
                    .put("httpErrors", httpErrors.get())
                    .put("httpP95Ms", httpTimes.length == 0 ? JSONObject.NULL : percentile(httpTimes, .95) / 1e6)
                    .put("httpP99Ms", httpTimes.length == 0 ? JSONObject.NULL : percentile(httpTimes, .99) / 1e6)
                    .put("sqlPool", 5).put("producerThreads", 1).put("persistent", true).put("producerCommitBatch", BATCH)
                    .put("durationSeconds", (end - start) / 1e9).put("sendSeconds", (producerEnd - start) / 1e9)
                    .put("messagesPerSecond", COUNT * 1e9 / (end - start)).put("latencyP95Ms", percentile(latencies, .95) / 1e6)
                    .put("latencyP99Ms", percentile(latencies, .99) / 1e6).put("maxOutstandingSampled", maxOutstanding)
                    .put("peakHeapBytesSampled", peakHeap).put("processCpuCoreEquivalent", (cpuAfter - cpuBefore) / (double) (end - start))
                    .put("stored", stored).put("receivedAttempts", counter("received") - receivedBefore)
                    .put("duplicates", counter("duplicates") - duplicatesBefore).put("backout", backoutDepth)
                    .put("inputDepthFinal", inputDepth).put("errors", counter("failures") - errorsBefore)
                    .put("java", System.getProperty("java.runtime.version")).put("os", System.getProperty("os.name"))
                    .put("samples", samples);
            Path output = Path.of("target", "benchmark-" + COUNT + "-" + BYTES + "-c" + CONCURRENCY + ".json");
            Files.writeString(output, report.toString(2));
            System.out.println("BENCHMARK " + output + " " + report.getDouble("messagesPerSecond") + " msg/s");
        } finally {
            container.shutdown();
            if (factory instanceof CachingConnectionFactory cached) cached.resetConnection();
            jdbc.update("DELETE FROM messaging.received_message WHERE source_queue = ?", INPUT);
            mqsc("DELETE QLOCAL('" + INPUT + "')\nDELETE QLOCAL('" + BACKOUT + "')\n");
            SENT.clear(); LATENCIES.clear();
        }
    }
    void queryWhileIngesting(HttpClient client) {
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port
                + "/api/messages?size=50&sourceQueue=" + INPUT)).timeout(Duration.ofSeconds(15)).GET().build();
        while (querying.get()) {
            long start = System.nanoTime();
            try {
                var response = client.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() != 200 || response.body().contains("\"payload\":")) {
                    httpErrors.incrementAndGet();
                }
                httpLatencies.add(System.nanoTime() - start);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception failure) {
                httpErrors.incrementAndGet();
            }
            try {
                Thread.sleep(50);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    void send(int count, String prefix, String body) throws Exception {
        try (var context = factory.createContext(JMSContext.SESSION_TRANSACTED)) {
            var producer = context.createProducer().setDeliveryMode(DeliveryMode.PERSISTENT);
            var queue = context.createQueue(INPUT);
            for (int i = 0; i < count; i++) {
                String correlation = prefix + ":" + i;
                var message = context.createTextMessage(body);
                message.setJMSCorrelationID(correlation);
                SENT.put(correlation, System.nanoTime());
                producer.send(queue, message);
                if ((i + 1) % BATCH == 0) context.commit();
            }
            context.commit();
        }
    }
    void awaitCount(int count) throws Exception {
        long deadline = System.nanoTime() + Duration.ofMinutes(2).toNanos();
        while (LATENCIES.size() < count) {
            if (System.nanoTime() > deadline) throw new AssertionError("Warmup timed out");
            Thread.sleep(50);
        }
    }
    double counter(String suffix) { return metrics.counter("bank.ingestion." + suffix).count(); }
    static long percentile(long[] sorted, double p) { return sorted[Math.max(0, (int) Math.ceil(p * sorted.length) - 1)]; }
    int depth(String queue) throws Exception {
        try (var context = factory.createContext(); var browser = context.createBrowser(context.createQueue(queue))) {
            var messages = browser.getEnumeration(); int count = 0;
            while (messages.hasMoreElements()) { messages.nextElement(); count++; }
            return count;
        }
    }
}
