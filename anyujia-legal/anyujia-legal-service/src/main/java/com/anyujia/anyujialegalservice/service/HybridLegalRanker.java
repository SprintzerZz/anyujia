package com.anyujia.anyujialegalservice.service;

import com.anyujia.anyujialegalservice.config.LegalAiProperties;
import com.anyujia.anyujialegalservice.domain.entity.LegalSource;
import com.anyujia.anyujialegalservice.domain.entity.LegalSourceChunk;
import com.anyujia.anyujialegalservice.domain.model.LawPassage;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.ToDoubleFunction;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class HybridLegalRanker {
    private static final Map<String, List<String>> LEGAL_INTENTS = Map.ofEntries(
            Map.entry("deposit", List.of("押金", "保证金", "退还", "返还时间", "扣除", "扣减", "没收")),
            Map.entry("termination", List.of("解除", "解约", "终止", "违约", "催告", "合理时间", "腾退")),
            Map.entry("indefinite", List.of("不定期租赁", "没写租期", "没有约定期限", "租多久", "随时解除", "合理期限", "通知")),
            Map.entry("entry", List.of("房东", "出租人", "开门", "进来", "进入房屋", "擅自进入", "检查房屋", "承租人同意", "隐私")),
            Map.entry("repair", List.of("维修", "修缮", "故障", "坏了", "自己修", "报销", "费用由出租人负担", "减少租金")),
            Map.entry("rent", List.of("租金", "房租", "晚交", "迟延支付", "支付期限", "逾期不支付", "滞纳金")),
            Map.entry("authority", List.of("出租权", "房产证", "不动产权属证书", "合法出租权利", "所有权", "授权", "权属")),
            Map.entry("sublet", List.of("转租", "出租给朋友", "第三人", "出租人同意", "未经同意", "解除合同")),
            Map.entry("wear", List.of("正常居住", "自然磨损", "正常损耗", "按照约定的方法", "使用方法", "赔偿责任")),
            Map.entry("ownership-change", List.of("卖房", "卖了", "把房子卖了", "新房主", "所有权变动",
                    "不影响租赁合同效力", "买卖不破租赁", "立即搬走")),
            Map.entry("safety", List.of("消防", "燃气", "安全", "健康", "质量不合格", "危及", "随时解除合同")),
            Map.entry("filing", List.of("备案", "登记备案", "合同无效", "不影响合同的效力", "承租人可以办理"))
    );

    private final LegalAiProperties properties;
    private final LegalReranker reranker;

    public HybridLegalRanker(LegalAiProperties properties, LegalReranker reranker) {
        this.properties = properties;
        this.reranker = reranker;
    }

    public List<LawPassage> rank(String query, Map<Long, LegalSource> sources,
                                 List<LegalSourceChunk> chunks, Map<Long, Double> vectorScores, int limit) {
        if (chunks.isEmpty()) {
            return List.of();
        }
        Set<String> terms = terms(query);
        Map<String, Long> documentFrequency = terms.stream().collect(Collectors.toMap(
                term -> term, term -> chunks.stream()
                        .filter(chunk -> normalized(chunk.getChunkText()).contains(term)).count()));
        Map<Long, Candidate> candidates = new LinkedHashMap<>();
        for (LegalSourceChunk chunk : chunks) {
            LegalSource source = sources.get(chunk.getSourceId());
            if (source == null) {
                continue;
            }
            double lexical = lexicalScore(chunk.getChunkText(), terms, documentFrequency, chunks.size());
            double vector = vectorScores.getOrDefault(chunk.getId(), 0D);
            double intent = legalIntentScore(query, source, chunk);
            candidates.put(chunk.getId(), new Candidate(source, chunk, lexical, vector, intent));
        }

        Map<Long, Double> fused = new LinkedHashMap<>();
        LegalAiProperties.Retrieval config = properties.getRetrieval();
        addRrf(fused, rank(candidates.values(), Candidate::lexicalScore), config.getLexicalWeight());
        if (!vectorScores.isEmpty()) {
            addRrf(fused, rank(candidates.values(), Candidate::vectorScore), config.getVectorWeight());
        }
        addRrf(fused, rank(candidates.values(), Candidate::intentScore), config.getIntentWeight());

        int rerankLimit = Math.min(config.getRerankCandidateLimit(), fused.size());
        int finalLimit = Math.max(1, Math.min(limit, 10));
        List<LawPassage> deterministic = fused.entrySet().stream()
                .sorted(Map.Entry.<Long, Double>comparingByValue().reversed())
                .limit(rerankLimit)
                .map(entry -> rerank(candidates.get(entry.getKey()), entry.getValue(), query, terms))
                .sorted(Comparator.comparingDouble(LawPassage::score).reversed())
                .toList();
        if (reranker != null && reranker.isAvailable() && deterministic.size() > 1) {
            int topN = Math.min(properties.getLocalReranker().getTopN(), deterministic.size());
            return reranker.rerank(query, deterministic.subList(0, topN))
                    .stream().limit(finalLimit).toList();
        }
        return deterministic.stream().limit(finalLimit).toList();
    }

    private List<Candidate> rank(java.util.Collection<Candidate> candidates,
                                 ToDoubleFunction<Candidate> scorer) {
        return candidates.stream().filter(candidate -> scorer.applyAsDouble(candidate) > 0D)
                .sorted(Comparator.comparingDouble(scorer).reversed())
                .limit(properties.getRetrieval().getChannelCandidateLimit()).toList();
    }

    private void addRrf(Map<Long, Double> fused, List<Candidate> ranked, double weight) {
        if (weight <= 0D) {
            return;
        }
        for (int index = 0; index < ranked.size(); index++) {
            long chunkId = ranked.get(index).chunk().getId();
            double contribution = weight / (properties.getRetrieval().getRrfK() + index + 1D);
            fused.merge(chunkId, contribution, Double::sum);
        }
    }

    private LawPassage rerank(Candidate candidate, double fusedScore, String query, Set<String> terms) {
        String text = normalized(candidate.chunk().getChunkText());
        long matchedTerms = terms.stream().filter(text::contains).count();
        double coverage = terms.isEmpty() ? 0D : (double) matchedTerms / terms.size();
        String normalizedQuery = normalized(query);
        double exactPhrase = !normalizedQuery.isBlank() && text.contains(normalizedQuery) ? 0.25D : 0D;
        double articleEvidence = candidate.chunk().getArticleNumber() == null
                || candidate.chunk().getArticleNumber().isBlank() ? 0D : 0.1D;
        double authority = isAuthoritative(candidate.source()) ? 0.15D : 0D;
        double intentEvidence = Math.min(candidate.intentScore() / 3D, 1D);
        double statuteEvidence = Math.min(candidate.intentScore() / 5D, 2.2D);
        double rerank = coverage + exactPhrase + articleEvidence + authority + intentEvidence + statuteEvidence;
        double domainPenalty = domainPenalty(normalizedQuery, text);
        double finalScore = fusedScore + properties.getRetrieval().getRerankWeight() * rerank - domainPenalty;
        return new LawPassage(candidate.chunk().getId(), candidate.source().getId(),
                candidate.source().getTitle(), candidate.chunk().getArticleNumber(),
                candidate.chunk().getChunkText(), candidate.source().getSourceUrl(), finalScore);
    }

    private boolean isAuthoritative(LegalSource source) {
        String url = source.getSourceUrl() == null ? "" : source.getSourceUrl().toLowerCase(Locale.ROOT);
        return (source.getIssuer() != null && !source.getIssuer().isBlank())
                || url.contains(".gov.cn") || url.contains("gov.cn/");
    }

    private double legalIntentScore(String query, LegalSource source, LegalSourceChunk chunk) {
        String normalizedQuery = normalized(query);
        String normalizedText = normalized(chunk.getChunkText());
        double score = 0D;
        for (List<String> vocabulary : LEGAL_INTENTS.values()) {
            boolean requested = vocabulary.stream().map(this::normalized).anyMatch(normalizedQuery::contains);
            if (requested) {
                score += vocabulary.stream().map(this::normalized)
                        .filter(normalizedText::contains).count();
            }
        }
        return score + rentalArticleScore(normalizedQuery, source, chunk, normalizedText);
    }

    private double rentalArticleScore(String query, LegalSource source, LegalSourceChunk chunk, String text) {
        String article = normalized(chunk.getArticleNumber());
        String title = normalized(source.getTitle());
        double score = chapterPreference(query, text, title);
        score += boost(query, article, "第七百零三条", 4D,
                List.of("租房合同", "租赁合同", "核心义务", "本质"),
                List.of("出租人", "承租人", "支付租金"));
        score += boost(query, article, "第七百零五条", 5D,
                List.of("25年", "二十五年", "20年", "二十年", "超过", "租多久"),
                List.of("二十年", "超过部分", "无效"));
        score += boost(query, article, "第七百零六条", 4D,
                List.of("登记备案", "备案", "没有办理", "没备案", "合同效力", "无效"),
                List.of("不影响", "效力"));
        score += boost(query, article, "第七百零八条", 4D,
                List.of("交房", "交付", "迟迟不交", "收了钱"),
                List.of("交付", "符合约定用途"));
        score += boost(query, article, "第七百零九条", 5D,
                List.of("只能居住", "工作室", "用途", "使用方式", "接待客户"),
                List.of("约定的方法", "租赁物性质", "使用"));
        score += boost(query, article, "第七百一十条", 4D,
                List.of("自然磨损", "正常损耗", "正常居住", "墙面", "地板"),
                List.of("正常损耗", "不承担赔偿"));
        score += boost(query, article, "第七百一十一条", 4D,
                List.of("拆墙", "擅自改造", "损坏", "赔偿", "解除"),
                List.of("受到损失", "解除合同", "赔偿损失"));
        score += boost(query, article, "第七百一十二条", 4D,
                List.of("谁负责维修", "默认", "设施", "自然老化", "维修义务"),
                List.of("出租人", "维修义务"));
        score += boost(query, article, "第七百一十三条", 5D,
                List.of("一直不修", "自己修", "报销", "维修费用", "水管"),
                List.of("自行维修", "维修费用", "减少租金"));
        score += boost(query, article, "第七百一十四条", 4D,
                List.of("保管", "门窗", "家具", "保管坏"),
                List.of("妥善保管", "赔偿责任"));
        score += boost(query, article, "第七百一十五条", 4D,
                List.of("装修", "加装", "固定设施", "增设"),
                List.of("同意", "改善", "增设他物"));
        score += boost(query, article, "第七百一十六条", 5D,
                List.of("转租", "房东同意", "未经", "卧室", "朋友"),
                List.of("同意", "转租", "继续有效", "解除合同"));
        score += boost(query, article, "第七百一十八条", 5D,
                List.of("半年", "六个月", "没反对", "知道我转租"),
                List.of("六个月", "未提出异议", "视为同意"));
        score += boost(query, article, "第七百二十一条", 5D,
                List.of("什么时候交", "付款时间", "支付时间", "没有写清", "房租什么时候"),
                List.of("支付期限", "租赁期限", "届满"));
        score += boost(query, article, "第七百二十二条", 5D,
                List.of("晚交", "逾期", "马上解除", "迟延支付"),
                List.of("合理期限", "不支付", "解除合同"));
        score += boost(query, article, "第七百二十三条", 4D,
                List.of("不是房东", "第三人", "主张", "权利", "少交租"),
                List.of("第三人", "减少租金", "不支付租金"));
        score += boost(query, article, "第七百二十四条", 4D,
                List.of("不能住", "不能实现", "不是我的原因", "解除租赁"),
                List.of("不可归责于承租人", "解除合同"));
        score += boost(query, article, "第七百二十五条", 6D,
                List.of("房东把房子卖", "新房主", "立即搬走", "买卖不破", "所有权变动"),
                List.of("所有权变动", "不影响", "租赁合同"));
        score += boost(query, article, "第七百二十六条", 4D,
                List.of("优先购买", "卖房", "同等条件"),
                List.of("优先购买", "合理期限", "通知"));
        score += boost(query, article, "第七百二十七条", 5D,
                List.of("拍卖", "五日前"),
                List.of("拍卖", "五日前", "通知"));
        score += boost(query, article, "第七百二十八条", 5D,
                List.of("没通知", "未通知", "侵害", "赔偿", "优先购买权"),
                List.of("赔偿责任", "买卖合同"));
        score += boost(query, article, "第七百二十九条", 5D,
                List.of("暴雨", "毁损", "灭失", "减租", "不能正常住"),
                List.of("减少租金", "不支付租金", "解除合同"));
        score += boost(query, article, "第七百三十条", 5D,
                List.of("没写租多久", "不定期", "随时解除", "合理期限通知"),
                List.of("不定期租赁", "随时解除", "合理期限"));
        score += boost(query, article, "第七百三十二条", 4D,
                List.of("去世", "家人", "一起住", "继续租住"),
                List.of("共同居住", "继续租赁"));
        score += boost(query, article, "第七百三十三条", 4D,
                List.of("退房", "返还", "租期结束", "什么状态"),
                List.of("返还", "使用后的状态"));
        score += boost(query, article, "第七百三十四条", 6D,
                List.of("继续住", "没反对", "租期到了", "续租", "优先承租"),
                List.of("继续使用", "没有提出异议", "不定期"));
        score += boost(query, article, "第九条", 5D,
                List.of("开门", "进来", "检查房屋", "随时", "房产证", "出租权"),
                List.of("不得擅自进入", "身份证明", "合法出租权利"));
        score += boost(query, article, "第十条", 5D,
                List.of("押金", "扣押金", "扣减", "退押金"),
                List.of("扣减押金", "无正当理由"));
        score += boost(query, article, "第十二条", 5D,
                List.of("当天", "立刻搬走", "腾退", "解除租赁合同"),
                List.of("合理时间", "腾退"));
        score += boost(query, article, "第三十五条", 5D,
                List.of("拒绝退押金", "协商不成", "途径", "调解", "仲裁", "诉讼"),
                List.of("调解", "仲裁", "诉讼"));
        score += boost(query, article, "第九条", 7D,
                List.of("开门", "进来", "进入房屋", "擅自进入", "检查房屋", "随时", "房产证", "出租权", "权属证明", "合法出租权利"),
                List.of("不得擅自进入", "身份证明", "合法出租权利", "不动产权属"));
        score += boost(query, article, "第十二条", 6D,
                List.of("维修", "修缮", "故障", "坏了", "一直不修", "自己修", "报销", "维修费用", "水管", "设施"),
                List.of("出租人", "维修义务", "自行维修", "维修费用", "减少租金"));
        score += boost(query, article, "第十三条", 6D,
                List.of("维修", "修缮", "故障", "坏了", "一直不修", "自己修", "报销", "维修费用", "水管", "设施"),
                List.of("出租人", "维修义务", "自行维修", "维修费用", "减少租金"));
        return score;
    }

    private double boost(String query, String article, String expectedArticle, double weight,
                         List<String> queryTerms, List<String> evidenceTerms) {
        boolean queryMatches = queryTerms.stream().map(this::normalized).anyMatch(query::contains);
        if (!queryMatches || !article.contains(normalized(expectedArticle))) {
            return 0D;
        }
        return weight + evidenceTerms.size() * 0.15D;
    }

    private double chapterPreference(String query, String text, String title) {
        boolean rentalQuery = List.of("租房", "出租", "承租", "房东", "房租", "押金", "租期", "转租", "退房")
                .stream().map(this::normalized).anyMatch(query::contains);
        if (!rentalQuery) {
            return 0D;
        }
        double score = 0D;
        if (text.contains(normalized("第十四章  租赁合同"))) {
            score += 0.8D;
        }
        if (title.contains(normalized("住房租赁条例"))) {
            score += 0.5D;
        }
        return score;
    }

    private double domainPenalty(String query, String text) {
        boolean financingQuery = query.contains(normalized("融资租赁"));
        if (!financingQuery && text.contains(normalized("融资租赁合同"))) {
            return 0.35D;
        }
        boolean rentalQuery = List.of("租房", "房东", "房租", "押金", "出租屋", "退房")
                .stream().map(this::normalized).anyMatch(query::contains);
        if (rentalQuery && !text.contains(normalized("租赁合同")) && !text.contains(normalized("住房租赁"))) {
            return 0.2D;
        }
        boolean entryQuery = List.of("开门", "进来", "进入", "擅自进入", "检查房屋", "随时")
                .stream().map(this::normalized).anyMatch(query::contains);
        if (entryQuery && List.of("优先购买", "拍卖", "出卖租赁房屋", "所有权变动")
                .stream().map(this::normalized).anyMatch(text::contains)) {
            return 0.9D;
        }
        boolean authorityQuery = List.of("房产证", "出租权", "权属证明", "合法出租权利", "有权出租")
                .stream().map(this::normalized).anyMatch(query::contains);
        if (authorityQuery && !List.of("身份证明", "不动产权属", "合法出租权利", "核验")
                .stream().map(this::normalized).anyMatch(text::contains)) {
            return 0.45D;
        }
        return 0D;
    }

    private Set<String> terms(String query) {
        String normalized = normalized(query);
        Set<String> result = new LinkedHashSet<>();
        for (String word : normalized.split("[^a-z0-9\\u4e00-\\u9fa5]+")) {
            if (word.length() >= 2) {
                result.add(word);
            }
        }
        for (int i = 0; i + 2 <= normalized.length(); i++) {
            String gram = normalized.substring(i, i + 2);
            if (gram.matches("[\\u4e00-\\u9fa5]{2}")) {
                result.add(gram);
            }
        }
        return result;
    }

    private double lexicalScore(String text, Set<String> terms, Map<String, Long> documentFrequency,
                                int documentCount) {
        String normalized = normalized(text);
        return terms.stream().filter(normalized::contains).mapToDouble(term -> {
            double lengthWeight = term.length() > 2 ? 1.5D : 1D;
            double inverseFrequency = Math.log((documentCount + 1D)
                    / (documentFrequency.getOrDefault(term, 0L) + 1D)) + 1D;
            return lengthWeight * inverseFrequency;
        }).sum();
    }

    private String normalized(String text) {
        return text == null ? "" : text.toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
    }

    private record Candidate(LegalSource source, LegalSourceChunk chunk, double lexicalScore,
                             double vectorScore, double intentScore) {
    }
}
