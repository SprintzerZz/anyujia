package com.anyujia.anyujialegalservice.service;

import com.anyujia.anyujiachatapi.domain.vo.MessageVO;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Compresses chat evidence deterministically while retaining source message IDs.
 * It is deliberately rule-based: a failed/ambiguous extraction must not invent a fact.
 */
@Service
@RequiredArgsConstructor
public class LegalChatContextCompressor {
    private static final int DEFAULT_MAX_CHARS = 6_000;
    private static final int MAX_MESSAGE_CHARS = 800;
    private static final Pattern RISK_TERMS = Pattern.compile(
            "租金|押金|定金|维修|修缮|违约|解约|解除|转租|退租|续租|涨租|付款|逾期|损坏|赔偿|承担|负责|承诺|入住|退房",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern AMOUNT = Pattern.compile(
            "(?:人民币|RMB|￥)?\\s*\\d+(?:[.,]\\d+)?\\s*(?:元|块|万)?(?:/月|每月)?",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern DATE = Pattern.compile(
            "(?:\\d{4}[-年/]\\d{1,2}(?:[-月/]\\d{1,2}日?)?|\\d{1,2}月\\d{1,2}日|入住前|退租前|签约后\\d+日)");

    public String build(Long tenantId, List<MessageVO> messages, String query) {
        return build(tenantId, messages, query, DEFAULT_MAX_CHARS);
    }

    public String build(Long tenantId, List<MessageVO> messages, String query, int maxChars) {
        if (maxChars <= 0 || messages == null || messages.isEmpty()) {
            return "";
        }
        String normalizedQuery = query == null ? "" : query.toLowerCase(Locale.ROOT);
        List<MessageVO> ranked = messages.stream()
                .filter(message -> message != null && message.getContent() != null
                        && !message.getContent().isBlank())
                .sorted(Comparator.comparingInt((MessageVO message) -> score(message, normalizedQuery))
                        .reversed()
                        .thenComparing(MessageVO::getCreateTime, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
        boolean hasRiskMessage = ranked.stream().anyMatch(message -> score(message, normalizedQuery) > 0);
        List<MessageVO> relevant = hasRiskMessage
                ? ranked.stream().filter(message -> score(message, normalizedQuery) > 0).toList()
                : List.of();

        Set<MessageVO> selected = new LinkedHashSet<>();
        int used = 0;
        for (MessageVO message : relevant) {
            String rendered = render(tenantId, message);
            if (rendered.length() > maxChars - used) {
                continue;
            }
            selected.add(message);
            used += rendered.length() + 1;
            if (used >= maxChars) {
                break;
            }
        }
        if (selected.isEmpty()) {
            return fallback(tenantId, messages, maxChars);
        }

        return selected.stream()
                .sorted(Comparator.comparing(MessageVO::getCreateTime,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .map(message -> render(tenantId, message))
                .reduce((left, right) -> left + "\n" + right)
                .orElse("");
    }

    private int score(MessageVO message, String query) {
        String content = message.getContent().toLowerCase(Locale.ROOT);
        int score = 0;
        Matcher riskMatcher = RISK_TERMS.matcher(content);
        while (riskMatcher.find()) {
            score += 2;
        }
        if (!query.isBlank() && content.contains(query)) {
            score += 5;
        }
        if (AMOUNT.matcher(content).find()) {
            score += 3;
        }
        if (DATE.matcher(content).find()) {
            score += 2;
        }
        return score;
    }

    private String render(Long tenantId, MessageVO message) {
        String content = message.getContent();
        String bounded = content.length() <= MAX_MESSAGE_CHARS
                ? content : content.substring(0, MAX_MESSAGE_CHARS) + "[message_truncated]";
        List<String> facts = new ArrayList<>();
        collect(facts, AMOUNT, content, "amount");
        collect(facts, DATE, content, "date");
        if (facts.isEmpty()) {
            facts.add("risk_terms=" + riskTerms(content));
        }
        String sender = message.getFromId() != null && message.getFromId().equals(tenantId)
                ? "tenant" : "other";
        return "[chat_message id=" + safe(message.getMessageId())
                + " seq=" + safe(message.getConversationSeq())
                + " sender=" + sender + " facts=" + String.join(",", facts) + "]\n" + bounded;
    }

    private String fallback(Long tenantId, List<MessageVO> messages, int maxChars) {
        StringBuilder result = new StringBuilder();
        for (int i = Math.max(0, messages.size() - 5); i < messages.size(); i++) {
            String rendered = render(tenantId, messages.get(i));
            if (result.length() + rendered.length() + 1 > maxChars) {
                break;
            }
            if (!result.isEmpty()) {
                result.append('\n');
            }
            result.append(rendered);
        }
        return result.toString();
    }

    private void collect(List<String> facts, Pattern pattern, String content, String label) {
        Matcher matcher = pattern.matcher(content);
        while (matcher.find() && facts.size() < 8) {
            facts.add(label + "=" + matcher.group().trim());
        }
    }

    private String riskTerms(String content) {
        Set<String> terms = new LinkedHashSet<>();
        Matcher matcher = RISK_TERMS.matcher(content);
        while (matcher.find() && terms.size() < 8) {
            terms.add(matcher.group());
        }
        return String.join("|", terms);
    }

    private String safe(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
