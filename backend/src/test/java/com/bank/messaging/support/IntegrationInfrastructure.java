package com.bank.messaging.support;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;

/** Infrastructure commands shared by integration tests and the opt-in benchmark. */
public final class IntegrationInfrastructure {
    private IntegrationInfrastructure() {
    }

    public static void compose(String... args) throws Exception {
        var command = new ArrayList<>(List.of(
                "docker", "compose", "--project-directory", projectDirectory()));
        command.addAll(List.of(args));
        var log = Files.createTempFile(Path.of("target"), "compose-test-", ".log");
        var process = new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(log.toFile()).start();
        if (!process.waitFor(150, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            throw new AssertionError("Compose timeout: " + log);
        }
        assertThat(process.exitValue()).as(Files.readString(log)).isZero();
    }

    public static void await(BooleanSupplier condition) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() > deadline) {
                throw new AssertionError("Condition not reached within 30 seconds");
            }
            Thread.sleep(100);
        }
    }

    public static void mqsc(String commands) throws Exception {
        var log = Files.createTempFile(Path.of("target"), "mqsc-test-", ".log");
        var process = new ProcessBuilder(
                "docker", "compose", "--project-directory", projectDirectory(),
                "exec", "-T", "mq", "runmqsc", "QM1")
                .redirectErrorStream(true)
                .redirectOutput(log.toFile())
                .start();
        try (var input = process.getOutputStream()) {
            input.write(commands.getBytes(StandardCharsets.UTF_8));
        }
        if (!process.waitFor(30, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            throw new AssertionError("MQSC timeout: " + log);
        }
        assertThat(process.exitValue()).as(Files.readString(log)).isZero();
    }

    private static String projectDirectory() {
        return Path.of("..").toAbsolutePath().normalize().toString();
    }
}
