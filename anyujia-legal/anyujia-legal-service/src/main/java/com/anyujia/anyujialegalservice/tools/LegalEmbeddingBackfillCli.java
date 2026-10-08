package com.anyujia.anyujialegalservice.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Standalone, idempotent operations tool for backfilling legal chunk embeddings. */
public final class LegalEmbeddingBackfillCli {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(20))
            .build();

    private LegalEmbeddingBackfillCli() {
    }

    public static void main(String[] args) throws Exception {
        String apiKey = required("DASHSCOPE_API_KEY");
        String baseUrl = env("DASHSCOPE_BASE_URL", "https://dashscope.aliyuncs.com/compatible-mode/v1");
        String model = env("LEGAL_EMBEDDING_MODEL", "text-embedding-v4");
        int dimensions = Integer.parseInt(env("LEGAL_EMBEDDING_DIMENSIONS", "1024"));
        String jdbcUrl = "jdbc:mysql://" + env("MYSQL_HOST", "127.0.0.1") + ":"
                + env("MYSQL_PORT", "3306") + "/" + env("MYSQL_DATABASE", "frameworkjava_dev")
                + "?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai"
                + "&useSSL=false&allowPublicKeyRetrieval=true";

        try (Connection connection = DriverManager.getConnection(
                jdbcUrl, env("MYSQL_USERNAME", "anyujiadev"), env("MYSQL_PASSWORD", "change-me"))) {
            List<Chunk> chunks = loadPending(connection);
            System.out.printf("pending=%d model=%s dimensions=%d%n", chunks.size(), model, dimensions);
            int completed = 0;
            for (Chunk chunk : chunks) {
                double[] vector = embed(baseUrl, apiKey, model, dimensions, chunk.text());
                if (vector.length != dimensions) {
                    throw new IllegalStateException("Unexpected embedding dimensions: " + vector.length);
                }
                update(connection, chunk.id(), JSON.writeValueAsString(vector));
                completed++;
                if (completed % 25 == 0 || completed == chunks.size()) {
                    System.out.printf("embedded=%d remaining=%d%n", completed, chunks.size() - completed);
                }
            }
            System.out.printf("complete=%d database_remaining=%d%n", completed, countPending(connection));
        }
    }

    private static List<Chunk> loadPending(Connection connection) throws Exception {
        List<Chunk> chunks = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT id, chunk_text FROM legal_source_chunk "
                        + "WHERE embedding_json IS NULL ORDER BY id")) {
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    chunks.add(new Chunk(result.getLong(1), result.getString(2)));
                }
            }
        }
        return chunks;
    }

    static double[] embed(String baseUrl, String apiKey, String model,
                          int dimensions, String text) throws Exception {
        return embedBatch(baseUrl, apiKey, model, dimensions, List.of(text)).get(0);
    }

    static List<double[]> embedBatch(String baseUrl, String apiKey, String model,
                                     int dimensions, List<String> texts) throws Exception {
        if (texts == null || texts.isEmpty()) {
            return List.of();
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", model);
        payload.put("input", texts);
        payload.put("dimensions", dimensions);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/embeddings"))
                .timeout(Duration.ofSeconds(60))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(payload)))
                .build();
        HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("Embedding request failed with HTTP " + response.statusCode());
        }
        JsonNode data = JSON.readTree(response.body()).path("data");
        if (!data.isArray() || data.size() != texts.size()) {
            throw new IllegalStateException("Embedding response count did not match request");
        }
        double[][] ordered = new double[texts.size()][];
        for (JsonNode item : data) {
            int itemIndex = item.path("index").asInt(-1);
            JsonNode values = item.path("embedding");
            if (itemIndex < 0 || itemIndex >= ordered.length || !values.isArray() || values.isEmpty()) {
                throw new IllegalStateException("Embedding response contained an invalid item");
            }
            double[] vector = new double[values.size()];
            for (int valueIndex = 0; valueIndex < values.size(); valueIndex++) {
                vector[valueIndex] = values.get(valueIndex).asDouble();
            }
            ordered[itemIndex] = vector;
        }
        List<double[]> result = new ArrayList<>(ordered.length);
        for (double[] vector : ordered) {
            if (vector == null) {
                throw new IllegalStateException("Embedding response omitted an input index");
            }
            result.add(vector);
        }
        return List.copyOf(result);
    }

    private static void update(Connection connection, long id, String embeddingJson) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE legal_source_chunk SET embedding_json = ? "
                        + "WHERE id = ? AND embedding_json IS NULL")) {
            statement.setString(1, embeddingJson);
            statement.setLong(2, id);
            statement.executeUpdate();
        }
    }

    private static long countPending(Connection connection) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT COUNT(*) FROM legal_source_chunk WHERE embedding_json IS NULL")) {
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getLong(1);
            }
        }
    }

    static String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " is required");
        }
        return value;
    }

    static String env(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }

    private record Chunk(long id, String text) {
    }
}

