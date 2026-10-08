package com.anyujia.anyujialegalservice.tools;

import com.anyujia.anyujialegalservice.service.LegalSourceChunker;
import java.io.StringReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import javax.swing.text.MutableAttributeSet;
import javax.swing.text.html.HTML;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.html.parser.ParserDelegator;

/** Rebuilds article chunks from authoritative pages while reusing unchanged embeddings. */
public final class LegalSourceRebuildCli {
    private static final long CIVIL_CODE_SOURCE_ID = 1791013630365000000L;
    private static final long RENTAL_REGULATION_SOURCE_ID = 1791013630365100000L;
    private static final String CIVIL_CODE_URL =
            "https://fjca.miit.gov.cn/zwgk/zcwj/wjfb/art/2020/"
                    + "art_9f0ef44677164c5eb3a5585bf82a07cb.html";
    private static final String RENTAL_REGULATION_URL =
            "https://www.mee.gov.cn/zcwj/gwywj/202507/t20250722_1123995.shtml";

    private LegalSourceRebuildCli() {
    }

    public static void main(String[] args) throws Exception {
        boolean apply = args.length == 1 && "--apply".equals(args[0]);
        String civilText = between(fetchText(CIVIL_CODE_URL), "绗洓鐧惧叚鍗佷笁鏉?, "绗洓缂?);
        String rentalPage = fetchText(RENTAL_REGULATION_URL);
        String rentalText = betweenInclusive(rentalPage, "绗竴鏉?, "鏈潯渚嬭嚜2025骞?鏈?5鏃ヨ捣鏂借銆?);
        LegalSourceChunker chunker = new LegalSourceChunker();
        List<LegalSourceChunker.LawChunk> civilChunks = chunker.split(civilText);
        List<LegalSourceChunker.LawChunk> rentalChunks = chunker.split(rentalText);
        validate(civilChunks, 526, "绗洓鐧惧叚鍗佷笁鏉?, "绗節鐧惧叓鍗佸叓鏉?);
        validate(rentalChunks, 50, "绗竴鏉?, "绗簲鍗佹潯");

        String jdbcUrl = "jdbc:mysql://" + LegalEmbeddingBackfillCli.env("MYSQL_HOST", "127.0.0.1")
                + ":" + LegalEmbeddingBackfillCli.env("MYSQL_PORT", "3306") + "/"
                + LegalEmbeddingBackfillCli.env("MYSQL_DATABASE", "frameworkjava_dev")
                + "?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai"
                + "&useSSL=false&allowPublicKeyRetrieval=true";
        try (Connection connection = DriverManager.getConnection(jdbcUrl,
                LegalEmbeddingBackfillCli.env("MYSQL_USERNAME", "anyujiadev"),
                LegalEmbeddingBackfillCli.env("MYSQL_PASSWORD", "change-me"))) {
            SourcePlan civil = plan(connection, CIVIL_CODE_SOURCE_ID, civilChunks);
            SourcePlan rental = plan(connection, RENTAL_REGULATION_SOURCE_ID, rentalChunks);
            printPlan("civil-code-contract", civil);
            printPlan("housing-rental-regulation", rental);
            if (!apply) {
                System.out.println("dry_run=true");
                return;
            }
            String apiKey = LegalEmbeddingBackfillCli.required("DASHSCOPE_API_KEY");
            String baseUrl = LegalEmbeddingBackfillCli.env(
                    "DASHSCOPE_BASE_URL", "https://dashscope.aliyuncs.com/compatible-mode/v1");
            String model = LegalEmbeddingBackfillCli.env("LEGAL_EMBEDDING_MODEL", "text-embedding-v4");
            int dimensions = Integer.parseInt(
                    LegalEmbeddingBackfillCli.env("LEGAL_EMBEDDING_DIMENSIONS", "1024"));
            prepareEmbeddings(civil, baseUrl, apiKey, model, dimensions);
            prepareEmbeddings(rental, baseUrl, apiKey, model, dimensions);
            replaceAll(connection, List.of(civil, rental));
            System.out.println("rebuild_complete=true");
        }
    }

    private static String fetchText(String url) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(30))
                .header("User-Agent", "Mozilla/5.0 legal-source-ingestion")
                .GET().build();
        HttpResponse<byte[]> response = HttpClient.newHttpClient()
                .send(request, HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() != 200) {
            throw new IllegalStateException("Authoritative source returned HTTP " + response.statusCode());
        }
        String html = new String(response.body(), StandardCharsets.UTF_8);
        StringBuilder text = new StringBuilder();
        new ParserDelegator().parse(new StringReader(html), new HTMLEditorKit.ParserCallback() {
            @Override
            public void handleText(char[] data, int position) {
                text.append(data);
            }

            @Override
            public void handleStartTag(HTML.Tag tag, MutableAttributeSet attributes, int position) {
                if (tag == HTML.Tag.P || tag == HTML.Tag.DIV || tag == HTML.Tag.BR
                        || tag == HTML.Tag.H1 || tag == HTML.Tag.H2 || tag == HTML.Tag.H3
                        || tag == HTML.Tag.LI) {
                    text.append('\n');
                }
            }

            @Override
            public void handleSimpleTag(HTML.Tag tag, MutableAttributeSet attributes, int position) {
                if (tag == HTML.Tag.BR || tag == HTML.Tag.HR) {
                    text.append('\n');
                }
            }
        }, true);
        return text.toString().replace('\u00a0', ' ').replaceAll("[ \\t]+", " ")
                .replaceAll("\\n{2,}", "\n").trim();
    }

    private static String between(String text, String startMarker, String endMarker) {
        int start = text.indexOf(startMarker);
        int end = start < 0 ? -1 : text.indexOf(endMarker, start);
        if (start < 0 || end < 0 || end <= start) {
            throw new IllegalStateException("Unable to locate law boundaries");
        }
        return text.substring(start, end).trim();
    }

    private static String betweenInclusive(String text, String startMarker, String endMarker) {
        int start = text.indexOf(startMarker);
        int end = start < 0 ? -1 : text.indexOf(endMarker, start);
        if (start < 0 || end < 0) {
            throw new IllegalStateException("Unable to locate regulation boundaries");
        }
        return text.substring(start, end + endMarker.length()).trim();
    }

    private static void validate(List<LegalSourceChunker.LawChunk> chunks, int expected,
                                 String first, String last) {
        long articleCount = chunks.stream().map(LegalSourceChunker.LawChunk::articleNumber)
                .filter(java.util.Objects::nonNull).distinct().count();
        if (articleCount != expected || !first.equals(chunks.get(0).articleLabel())
                || !last.equals(chunks.get(chunks.size() - 1).articleLabel())) {
            throw new IllegalStateException("Unexpected authoritative article sequence: count="
                    + articleCount + ", first=" + chunks.get(0).articleLabel()
                    + ", last=" + chunks.get(chunks.size() - 1).articleLabel());
        }
    }

    private static SourcePlan plan(Connection connection, long sourceId,
                                   List<LegalSourceChunker.LawChunk> chunks) throws Exception {
        Map<String, String> existing = new HashMap<>();
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT chunk_text, embedding_json FROM legal_source_chunk "
                        + "WHERE source_id = ? AND embedding_json IS NOT NULL")) {
            statement.setLong(1, sourceId);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    existing.putIfAbsent(result.getString(1), result.getString(2));
                }
            }
        }
        List<PreparedChunk> prepared = new ArrayList<>();
        for (int index = 0; index < chunks.size(); index++) {
            LegalSourceChunker.LawChunk chunk = chunks.get(index);
            prepared.add(new PreparedChunk(index, chunk.articleLabel(), chunk.text(),
                    existing.get(chunk.text())));
        }
        return new SourcePlan(sourceId, prepared);
    }

    private static void printPlan(String name, SourcePlan plan) {
        long reused = plan.chunks().stream().filter(chunk -> chunk.embeddingJson() != null).count();
        System.out.printf("source=%s chunks=%d reusable=%d new_embeddings=%d%n",
                name, plan.chunks().size(), reused, plan.chunks().size() - reused);
    }

    private static void prepareEmbeddings(SourcePlan plan, String baseUrl, String apiKey,
                                          String model, int dimensions) throws Exception {
        int batchSize = Math.max(1, Math.min(10, Integer.parseInt(
                LegalEmbeddingBackfillCli.env("LEGAL_EMBEDDING_BATCH_SIZE", "10"))));
        List<PreparedChunk> pending = plan.chunks().stream()
                .filter(chunk -> chunk.embeddingJson() == null).toList();
        int created = 0;
        for (int start = 0; start < pending.size(); start += batchSize) {
            List<PreparedChunk> batch = pending.subList(start, Math.min(start + batchSize, pending.size()));
            List<double[]> vectors = LegalEmbeddingBackfillCli.embedBatch(baseUrl, apiKey, model,
                    dimensions, batch.stream().map(PreparedChunk::text).toList());
            for (int index = 0; index < batch.size(); index++) {
                double[] vector = vectors.get(index);
                if (vector.length != dimensions) {
                    throw new IllegalStateException("Unexpected embedding dimensions: " + vector.length);
                }
                batch.get(index).setEmbeddingJson(new com.fasterxml.jackson.databind.ObjectMapper()
                        .writeValueAsString(vector));
                created++;
            }
            System.out.printf("source_id=%d new_embeddings_complete=%d remaining=%d%n",
                    plan.sourceId(), created, pending.size() - created);
        }
    }

    private static void replaceAll(Connection connection, List<SourcePlan> plans) throws Exception {
        connection.setAutoCommit(false);
        try (PreparedStatement delete = connection.prepareStatement(
                     "DELETE FROM legal_source_chunk WHERE source_id = ?");
             PreparedStatement insert = connection.prepareStatement(
                     "INSERT INTO legal_source_chunk "
                             + "(id, source_id, chunk_index, article_number, chunk_text, embedding_json) "
                             + "VALUES (?, ?, ?, ?, ?, ?)")) {
            for (SourcePlan plan : plans) {
                delete.setLong(1, plan.sourceId());
                delete.executeUpdate();
                for (PreparedChunk chunk : plan.chunks()) {
                    insert.setLong(1, ThreadLocalRandom.current().nextLong(1, Long.MAX_VALUE));
                    insert.setLong(2, plan.sourceId());
                    insert.setInt(3, chunk.index());
                    insert.setString(4, chunk.articleNumber());
                    insert.setString(5, chunk.text());
                    insert.setString(6, chunk.embeddingJson());
                    insert.addBatch();
                }
                insert.executeBatch();
            }
            connection.commit();
        } catch (Exception ex) {
            connection.rollback();
            throw ex;
        } finally {
            connection.setAutoCommit(true);
        }
    }

    private record SourcePlan(long sourceId, List<PreparedChunk> chunks) {
    }

    private static final class PreparedChunk {
        private final int index;
        private final String articleNumber;
        private final String text;
        private String embeddingJson;

        private PreparedChunk(int index, String articleNumber, String text, String embeddingJson) {
            this.index = index;
            this.articleNumber = articleNumber;
            this.text = text;
            this.embeddingJson = embeddingJson;
        }

        int index() { return index; }
        String articleNumber() { return articleNumber; }
        String text() { return text; }
        String embeddingJson() { return embeddingJson; }
        void setEmbeddingJson(String value) { this.embeddingJson = value; }
    }
}

