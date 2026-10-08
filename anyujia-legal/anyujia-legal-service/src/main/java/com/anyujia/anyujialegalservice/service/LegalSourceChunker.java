package com.anyujia.anyujialegalservice.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class LegalSourceChunker {
    private static final String LEGAL_NUMBER = "[零一二三四五六七八九十百千万0-9]+";
    private static final Pattern PART_PATTERN = hierarchyPattern("编");
    private static final Pattern CHAPTER_PATTERN = hierarchyPattern("章");
    private static final Pattern SECTION_PATTERN = hierarchyPattern("节");
    private static final Pattern ARTICLE_PATTERN = Pattern.compile(
            "(?m)^\\h*(第(" + LEGAL_NUMBER + ")条)(?=\\h|$)");
    private static final int MAX_CHARS = 1800;
    private static final int OVERLAP_CHARS = 150;

    static {
        if (OVERLAP_CHARS >= MAX_CHARS) {
            throw new IllegalStateException("Legal chunk overlap must be smaller than maximum size");
        }
    }

    public List<LawChunk> split(String fullText) {
        String text = normalize(fullText);
        if (text.isEmpty()) {
            return List.of();
        }
        List<ArticleSection> articles = parseArticles(text);
        if (articles.isEmpty()) {
            List<String> pieces = splitLongText(text, MAX_CHARS);
            List<LawChunk> fallback = new ArrayList<>(pieces.size());
            for (int index = 0; index < pieces.size(); index++) {
                fallback.add(new LawChunk(null, null, null, null, null, index, pieces.get(index)));
            }
            return List.copyOf(fallback);
        }

        List<LawChunk> result = new ArrayList<>();
        for (ArticleSection article : articles) {
            String prefix = buildContextPrefix(article.context(), article.label());
            int bodyLimit = Math.max(1, MAX_CHARS - prefix.length() - (prefix.isEmpty() ? 0 : 1));
            List<String> pieces = article.body().isEmpty()
                    ? List.of("") : splitLongText(article.body(), bodyLimit);
            for (int index = 0; index < pieces.size(); index++) {
                String embeddingText = buildEmbeddingText(article.context(), article.label(), pieces.get(index));
                result.add(new LawChunk(article.number(), article.label(), article.context().part(),
                        article.context().chapter(), article.context().section(), index, embeddingText));
            }
        }
        return List.copyOf(result);
    }

    private static Pattern hierarchyPattern(String unit) {
        return Pattern.compile("(?m)^\\h*(第" + LEGAL_NUMBER + unit
                + "(?:\\h+[^\\n]*)?)\\h*$");
    }

    private String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\r\n", "\n")
                .replace('\r', '\n')
                .replaceAll("(?m)\\h+$", "")
                .replaceAll("\\n(?:\\h*\\n){2,}", "\n\n")
                .strip();
    }

    private List<ArticleSection> parseArticles(String text) {
        List<ArticleMarker> articles = articleMarkers(text);
        if (articles.isEmpty()) {
            return List.of();
        }
        List<HierarchyMarker> hierarchy = hierarchyMarkers(text);
        List<ArticleSection> result = new ArrayList<>(articles.size());
        LegalContext context = new LegalContext(null, null, null);
        int hierarchyIndex = 0;
        for (int index = 0; index < articles.size(); index++) {
            ArticleMarker article = articles.get(index);
            while (hierarchyIndex < hierarchy.size()
                    && hierarchy.get(hierarchyIndex).start() < article.start()) {
                context = apply(context, hierarchy.get(hierarchyIndex));
                hierarchyIndex++;
            }
            int end = index + 1 < articles.size() ? articles.get(index + 1).start() : text.length();
            if (hierarchyIndex < hierarchy.size() && hierarchy.get(hierarchyIndex).start() < end) {
                end = hierarchy.get(hierarchyIndex).start();
            }
            String body = normalize(text.substring(article.end(), Math.max(article.end(), end)));
            result.add(new ArticleSection(article.number(), article.label(), context, body));
        }
        return List.copyOf(result);
    }

    private List<ArticleMarker> articleMarkers(String text) {
        Matcher matcher = ARTICLE_PATTERN.matcher(text);
        List<ArticleMarker> result = new ArrayList<>();
        while (matcher.find()) {
            result.add(new ArticleMarker(matcher.start(), matcher.end(), matcher.group(1).strip(),
                    articleNumber(matcher.group(2))));
        }
        return result;
    }

    private List<HierarchyMarker> hierarchyMarkers(String text) {
        List<HierarchyMarker> result = new ArrayList<>();
        addHierarchyMarkers(result, text, PART_PATTERN, HierarchyType.PART);
        addHierarchyMarkers(result, text, CHAPTER_PATTERN, HierarchyType.CHAPTER);
        addHierarchyMarkers(result, text, SECTION_PATTERN, HierarchyType.SECTION);
        result.sort(Comparator.comparingInt(HierarchyMarker::start));
        return result;
    }

    private void addHierarchyMarkers(List<HierarchyMarker> target, String text,
                                     Pattern pattern, HierarchyType type) {
        Matcher matcher = pattern.matcher(text);
        while (matcher.find()) {
            target.add(new HierarchyMarker(matcher.start(), matcher.group(1).strip(), type));
        }
    }

    private LegalContext apply(LegalContext current, HierarchyMarker marker) {
        return switch (marker.type()) {
            case PART -> new LegalContext(marker.label(), null, null);
            case CHAPTER -> new LegalContext(current.part(), marker.label(), null);
            case SECTION -> new LegalContext(current.part(), current.chapter(), marker.label());
        };
    }

    private List<String> splitLongText(String text, int maxChars) {
        String normalized = normalize(text);
        if (normalized.isEmpty()) {
            return List.of();
        }
        if (normalized.length() <= maxChars) {
            return List.of(normalized);
        }
        List<String> chunks = new ArrayList<>();
        int start = 0;
        while (start < normalized.length()) {
            int limit = Math.min(start + maxChars, normalized.length());
            if (limit == normalized.length()) {
                addIfPresent(chunks, normalized.substring(start));
                break;
            }
            int end = preferredBoundary(normalized, start, limit);
            if (end <= start) {
                end = limit;
            }
            String chunk = normalized.substring(start, end).strip();
            addIfPresent(chunks, chunk);
            String overlap = tailOverlap(chunk);
            int next = overlap.isEmpty() ? end
                    : normalized.lastIndexOf(overlap, Math.max(start, end - overlap.length()));
            if (next <= start || next >= end) {
                next = end;
            }
            start = next;
        }
        return List.copyOf(chunks);
    }

    private int preferredBoundary(String text, int start, int limit) {
        int minimumUsefulBoundary = start + Math.max(1, (limit - start) / 2);
        int boundary = lastBoundary(text, "\n\n", minimumUsefulBoundary, limit);
        if (boundary >= 0) {
            return boundary;
        }
        boundary = lastBoundary(text, "\n", minimumUsefulBoundary, limit);
        if (boundary >= 0) {
            return boundary;
        }
        for (int index = limit - 1; index >= minimumUsefulBoundary; index--) {
            if (isSentenceEnd(text.charAt(index))) {
                return index + 1;
            }
        }
        return limit;
    }

    private int lastBoundary(String text, String delimiter, int minimum, int limit) {
        int index = text.lastIndexOf(delimiter, limit - 1);
        return index >= minimum ? index + delimiter.length() : -1;
    }

    private String tailOverlap(String previousChunk) {
        if (previousChunk.length() < 2) {
            return "";
        }
        int floor = Math.max(0, previousChunk.length() - OVERLAP_CHARS);
        int paragraph = previousChunk.lastIndexOf("\n\n");
        if (paragraph >= floor && paragraph + 2 < previousChunk.length()) {
            return previousChunk.substring(paragraph + 2);
        }
        int line = previousChunk.lastIndexOf('\n');
        if (line >= floor && line + 1 < previousChunk.length()) {
            return previousChunk.substring(line + 1);
        }
        for (int index = floor; index < previousChunk.length() - 1; index++) {
            if (isSentenceEnd(previousChunk.charAt(index))) {
                String candidate = previousChunk.substring(index + 1).strip();
                if (!candidate.isEmpty() && candidate.length() <= OVERLAP_CHARS) {
                    return candidate;
                }
            }
        }
        int length = Math.min(OVERLAP_CHARS, previousChunk.length() - 1);
        return previousChunk.substring(previousChunk.length() - length);
    }

    private boolean isSentenceEnd(char value) {
        return value == '。' || value == '；' || value == '！' || value == '？'
                || value == '.' || value == ';' || value == '!' || value == '?';
    }

    private void addIfPresent(List<String> chunks, String value) {
        String normalized = value.strip();
        if (!normalized.isEmpty()) {
            chunks.add(normalized);
        }
    }

    private String buildEmbeddingText(LegalContext context, String articleLabel, String body) {
        List<String> headers = contextHeaders(context, articleLabel);
        String normalizedBody = normalize(body);
        for (String header : headers) {
            if (normalizedBody.equals(header)) {
                normalizedBody = "";
                break;
            }
            if (normalizedBody.startsWith(header + "\n") || normalizedBody.startsWith(header + " ")
                    || normalizedBody.startsWith(header + "\u3000")) {
                normalizedBody = normalizedBody.substring(header.length()).strip();
            }
        }
        String prefix = String.join("\n", headers);
        if (prefix.isEmpty()) {
            return normalizedBody;
        }
        return normalizedBody.isEmpty() ? prefix : prefix + "\n" + normalizedBody;
    }

    private String buildContextPrefix(LegalContext context, String articleLabel) {
        return String.join("\n", contextHeaders(context, articleLabel));
    }

    private List<String> contextHeaders(LegalContext context, String articleLabel) {
        Set<String> headers = new LinkedHashSet<>();
        addHeader(headers, context.part());
        addHeader(headers, context.chapter());
        addHeader(headers, context.section());
        addHeader(headers, articleLabel);
        return List.copyOf(headers);
    }

    private void addHeader(Set<String> headers, String value) {
        if (value != null && !value.isBlank()) {
            headers.add(value.strip());
        }
    }

    private Integer articleNumber(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        if (value.chars().allMatch(Character::isDigit)) {
            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException ex) {
                return null;
            }
        }
        int total = 0;
        int section = 0;
        int digit = 0;
        for (char character : value.toCharArray()) {
            int number = chineseDigit(character);
            if (number >= 0) {
                digit = number;
                continue;
            }
            int unit = chineseUnit(character);
            if (unit < 0) {
                return null;
            }
            if (unit == 10000) {
                section = (section + digit) * unit;
                total += section;
                section = 0;
            } else {
                section += (digit == 0 ? 1 : digit) * unit;
            }
            digit = 0;
        }
        return total + section + digit;
    }

    private int chineseDigit(char value) {
        return switch (value) {
            case '零' -> 0;
            case '一' -> 1;
            case '二' -> 2;
            case '三' -> 3;
            case '四' -> 4;
            case '五' -> 5;
            case '六' -> 6;
            case '七' -> 7;
            case '八' -> 8;
            case '九' -> 9;
            default -> -1;
        };
    }

    private int chineseUnit(char value) {
        return switch (value) {
            case '十' -> 10;
            case '百' -> 100;
            case '千' -> 1000;
            case '万' -> 10000;
            default -> -1;
        };
    }

    public record LawChunk(Integer articleNumber, String articleLabel, String part, String chapter,
                           String section, int chunkIndex, String text) {
    }

    private record LegalContext(String part, String chapter, String section) {
    }

    private record ArticleSection(Integer number, String label, LegalContext context, String body) {
    }

    private record ArticleMarker(int start, int end, String label, Integer number) {
    }

    private record HierarchyMarker(int start, String label, HierarchyType type) {
    }

    private enum HierarchyType {
        PART, CHAPTER, SECTION
    }
}
