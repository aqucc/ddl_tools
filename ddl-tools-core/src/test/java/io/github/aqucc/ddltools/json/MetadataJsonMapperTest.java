package io.github.aqucc.ddltools.json;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.aqucc.ddltools.model.DatabaseMetadata;
import io.github.aqucc.ddltools.model.Dialect;
import io.github.aqucc.ddltools.parse.DdlParser;
import io.github.aqucc.ddltools.parse.Fixtures;
import io.github.aqucc.ddltools.parse.ParseOptions;

class MetadataJsonMapperTest {

    private final MetadataJsonMapper mapper = new MetadataJsonMapper();

    @Test
    void roundTripKeepsAllInformation() {
        DatabaseMetadata original = new DdlParser(new ParseOptions(Dialect.ORACLE))
                .parse(Fixtures.read("oracle_sample.sql")).getMetadata();
        String json1 = mapper.toJson(original);
        DatabaseMetadata restored = mapper.fromJson(json1);
        String json2 = mapper.toJson(restored);
        assertThat(json2).isEqualTo(json1);
        assertThat(restored.getDialect()).isEqualTo(Dialect.ORACLE);
        assertThat(restored.findSchema("HR").getTables()).hasSize(2);
    }

    @Test
    void fileWriteAndRead(@TempDir Path dir) throws IOException {
        DatabaseMetadata original = new DdlParser(new ParseOptions(Dialect.POSTGRESQL))
                .parse(Fixtures.read("postgres_sample.sql")).getMetadata();
        Path file = dir.resolve("out/metadata.json");
        mapper.writeToFile(original, file);
        DatabaseMetadata restored = mapper.readFromFile(file);
        assertThat(restored.findSchema("sales").getTables()).hasSize(2);
    }

    @Test
    void rejectsIncompatibleFormatVersion() {
        assertThatThrownBy(() -> mapper.fromJson("{\"formatVersion\":\"9.0\"}"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("formatVersion");
    }
}
