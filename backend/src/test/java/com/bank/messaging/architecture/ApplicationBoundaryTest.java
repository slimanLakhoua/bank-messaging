package com.bank.messaging.architecture;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.tools.ToolProvider;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ApplicationBoundaryTest {
    @TempDir
    Path output;

    @Test
    void applicationCompilesWithOnlyTheJdkAndNoAdaptersOrFrameworks() throws Exception {
        var compiler = ToolProvider.getSystemJavaCompiler();
        assertThat(compiler).as("Exécuter les tests avec un JDK complet").isNotNull();
        List<Path> sources;
        try (var files = Files.walk(Path.of("src/main/java/com/bank/messaging"))) {
            sources = files.filter(path -> path.toString().contains("/application/"))
                    .filter(path -> path.toString().endsWith(".java")).toList();
        }
        assertThat(sources).isNotEmpty();
        var diagnostics = new StringWriter();
        try (var manager = compiler.getStandardFileManager(null, null, null)) {
            boolean compiled = compiler.getTask(diagnostics, manager, null,
                    List.of("--release", "21", "-proc:none", "-classpath", output.toString(),
                            "-sourcepath", output.toString(), "-d", output.toString()),
                    null, manager.getJavaFileObjectsFromPaths(sources)).call();
            assertThat(compiled).as(diagnostics.toString()).isTrue();
        }
    }
}
