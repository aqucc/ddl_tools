package io.github.aqucc.ddltools.json;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import io.github.aqucc.ddltools.model.DatabaseMetadata;

/**
 * メタ情報 ⇔ JSONファイルの入出力。
 */
public class MetadataJsonMapper {

    private final ObjectMapper mapper;

    public MetadataJsonMapper() {
        mapper = new ObjectMapper();
        mapper.enable(SerializationFeature.INDENT_OUTPUT);
        // 将来のフォーマット拡張で未知フィールドがあっても読めるようにする
        mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    }

    public String toJson(DatabaseMetadata metadata) {
        try {
            return mapper.writeValueAsString(metadata);
        } catch (IOException e) {
            throw new UncheckedIOException("failed to serialize metadata", e);
        }
    }

    public DatabaseMetadata fromJson(String json) {
        try {
            DatabaseMetadata metadata = mapper.readValue(json, DatabaseMetadata.class);
            checkFormatVersion(metadata);
            return metadata;
        } catch (IOException e) {
            throw new UncheckedIOException("failed to deserialize metadata", e);
        }
    }

    public void writeToFile(DatabaseMetadata metadata, Path file) throws IOException {
        if (file.getParent() != null) {
            Files.createDirectories(file.getParent());
        }
        byte[] bytes = toJson(metadata).getBytes(StandardCharsets.UTF_8);
        Files.write(file, bytes);
    }

    public DatabaseMetadata readFromFile(Path file) throws IOException {
        byte[] bytes = Files.readAllBytes(file);
        return fromJson(new String(bytes, StandardCharsets.UTF_8));
    }

    private void checkFormatVersion(DatabaseMetadata metadata) {
        String version = metadata.getFormatVersion();
        if (version == null) {
            throw new IllegalArgumentException("metadata JSON has no formatVersion");
        }
        // メジャーバージョンが異なる場合のみ拒否する
        String currentMajor = DatabaseMetadata.CURRENT_FORMAT_VERSION.split("\\.")[0];
        String major = version.split("\\.")[0];
        if (!currentMajor.equals(major)) {
            throw new IllegalArgumentException(
                    "unsupported metadata formatVersion: " + version
                            + " (supported: " + DatabaseMetadata.CURRENT_FORMAT_VERSION + ")");
        }
    }
}
