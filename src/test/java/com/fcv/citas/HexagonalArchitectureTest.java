package com.fcv.citas;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/** domain/ y application/ no importan Spring, JPA, Jackson ni Servlet. */
class HexagonalArchitectureTest {

    private static final Pattern FORBIDDEN = Pattern.compile(
            "^import\\s+(static\\s+)?(org\\.springframework|jakarta\\.|javax\\.persistence|com\\.fasterxml"
                    + "|org\\.hibernate|com\\.fcv\\.citas\\.infrastructure)",
            Pattern.MULTILINE);

    @Test
    void coreLayersAreFrameworkFree() throws IOException {
        Path base = Path.of("src/main/java/com/fcv/citas");
        List<String> offenders;
        try (Stream<Path> files = Stream.concat(Files.walk(base.resolve("domain")),
                Files.walk(base.resolve("application")))) {
            offenders = files.filter(p -> p.toString().endsWith(".java"))
                    .filter(p -> {
                        try {
                            return FORBIDDEN.matcher(Files.readString(p)).find();
                        } catch (IOException e) {
                            throw new IllegalStateException(e);
                        }
                    })
                    .map(Path::toString)
                    .toList();
        }
        assertThat(offenders).isEmpty();
    }
}
