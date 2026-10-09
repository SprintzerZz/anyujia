package com.anyujia.anyujialegalservice.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Standalone semantic-search smoke test for the legal RAG store. */
public final class LegalEmbeddingSearchCli {
    private static final ObjectMapper JSON = new ObjectMapper();

    private LegalEmbeddingSearchCli() {
    }

    public static void main(String[] args) throws Exception {
        String[] queries = args.length == 0 ? new String[]{
                "房东不退押金怎么办？",
                "出租房屋损坏后由谁承担维修责任？",
                "租客提前解除租赁合同需要承担什么责任？"
        } : args;
        String apiKey = LegalEmbeddingBackfillCli.required("DASHSCOPE_API_KEY");
        String baseUrl = LegalEmbeddingBackfillCli.env(
                "DASHSCOPE_BASE_URL", "https://dashscope.aliyuncs.com/compatible-mode/v1");
        String model = LegalEmbeddingBackfillCli.env("LEGAL_EMBEDDING_MODEL", "text-embedding-v4");
        int dimensions = Integer.parseInt(
                LegalEmbeddingBackfillCli.env("LEGAL_EMBEDDING_DIMENSIONS", "1024"));
        String jdbcUrl = "jdbc:mysql://" + LegalEmbeddingBackfillCli.env("MYSQL_HOST", "127.0.0.1")
                + ":" + LegalEmbeddingBackfillCli.env("MYSQL_PORT", "3306") + "/"
                + LegalEmbeddingBackfillCli.env("MYSQL_DATABASE", "frameworkjava_dev")
                + "?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai"
                + "&useSSL=false&allowPublicKeyRetrieval=true";

        try (Connection connection = DriverManager.getConnection(jdbcUrl,
                LegalEmbeddingBackfillCli.env("MYSQL_USERNAME", "anyujiadev"),
                LegalEmbeddingBackfillCli.env("MYSQL_PASSWORD", "change-me"))) {
            List<Candidate> candidates = loadCandidates(connection);
            for (String query : queries) {
                double[] queryVector = LegalEmbeddingBackfillCli.embed(
                        baseUrl, apiKey, model, dimensions, query);
                List<Hit> hits = candidates.stream()
                        .map(candidate -> new Hit(candidate, cosine(queryVector, candidate.vector())))
                        .sorted(Comparator.comparingDouble(Hit::score).reversed())
                        .limit(5)
                        .toList();
                System.out.println("QUERY: " + query);
                for (Hit hit : hits) {
                    String text = hit.candidate().text().replaceAll("\\s+", " ");
                    String preview = text.substring(0, Math.min(text.length(), 100));
                    System.out.printf("%.6f | %s | %s | %s%n", hit.score(),
                            hit.candidate().title(), hit.candidate().articleNumber(), preview);
                }
            }
        }
    }

    private static List<Candidate> loadCandidates(Connection connection) throws Exception {
        List<Candidate> candidates = new ArrayList<>();
        String sql = "SELECT s.title, c.article_number, c.chunk_text, c.embedding_json "
                + "FROM legal_source_chunk c JOIN legal_source s ON s.id = c.source_id "
                + "WHERE c.embedding_json IS NOT NULL AND s.status = 1";
        try (PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet result = statement.executeQuery()) {
            while (result.next()) {
                candidates.add(new Candidate(result.getString(1), result.getString(2),
                        result.getString(3), JSON.readValue(result.getString(4), double[].class)));
            }
        }
        return candidates;
    }

    private static double cosine(double[] left, double[] right) {
        if (left.length != right.length) {
            return -1d;
        }
        double dot = 0d;
        double leftNorm = 0d;
        double rightNorm = 0d;
        for (int index = 0; index < left.length; index++) {
            dot += left[index] * right[index];
            leftNorm += left[index] * left[index];
            rightNorm += right[index] * right[index];
        }
        return leftNorm == 0d || rightNorm == 0d
                ? -1d : dot / (Math.sqrt(leftNorm) * Math.sqrt(rightNorm));
    }

    private record Candidate(String title, String articleNumber, String text, double[] vector) {
    }

    private record Hit(Candidate candidate, double score) {
    }
}
