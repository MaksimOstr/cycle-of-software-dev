package com.cycleofsoftwaredev.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Protects the module boundaries of the modular monolith (Laboratory Work 2, section 2.4): a module may use
 * only the {@code api} package of another module, and the domain model does not depend on frameworks or
 * on infrastructure (Dependency Inversion principle).
 */
class ModuleBoundariesTest {

    private static final Path SOURCES = Path.of("src/main/java/com/cycleofsoftwaredev");
    private static final List<String> MODULES = List.of("identity", "catalog", "inventory", "cart", "ordering",
            "payment", "delivery", "reviews", "notification", "administration");
    private static final Pattern PROJECT_IMPORT =
            Pattern.compile("^import\\s+(?:static\\s+)?com\\.cycleofsoftwaredev\\.(\\w+)\\.(\\w+)", Pattern.MULTILINE);

    @Test
    void modulesUseOnlyThePublicApiOfOtherModules() {
        List<String> violations = new ArrayList<>();
        for (String module : MODULES) {
            for (Path file : javaFiles(SOURCES.resolve(module))) {
                Matcher matcher = PROJECT_IMPORT.matcher(read(file));
                while (matcher.find()) {
                    String targetModule = matcher.group(1);
                    String targetPackage = matcher.group(2);
                    if (MODULES.contains(targetModule) && !targetModule.equals(module) && !targetPackage.equals("api")) {
                        violations.add(file + " uses " + targetModule + "." + targetPackage);
                    }
                }
            }
        }
        assertThat(violations).isEmpty();
    }

    @Test
    void sharedKernelDoesNotDependOnModules() {
        List<String> violations = new ArrayList<>();
        for (Path file : javaFiles(SOURCES.resolve("shared"))) {
            Matcher matcher = PROJECT_IMPORT.matcher(read(file));
            while (matcher.find()) {
                if (!matcher.group(1).equals("shared")) {
                    violations.add(file + " uses " + matcher.group(1));
                }
            }
        }
        assertThat(violations).isEmpty();
    }

    @Test
    void domainModelDoesNotDependOnSpringOrInfrastructure() {
        List<String> violations = new ArrayList<>();
        for (String module : MODULES) {
            for (Path file : javaFiles(SOURCES.resolve(module).resolve("domain"))) {
                String source = read(file);
                if (source.contains("import org.springframework") || source.contains(".infrastructure.")) {
                    violations.add(file.toString());
                }
            }
        }
        assertThat(violations).isEmpty();
    }

    private static List<Path> javaFiles(Path directory) {
        if (!Files.isDirectory(directory)) {
            return List.of();
        }
        try (Stream<Path> files = Files.walk(directory)) {
            return files.filter(file -> file.toString().endsWith(".java")).toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String read(Path file) {
        try {
            return Files.readString(file);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
