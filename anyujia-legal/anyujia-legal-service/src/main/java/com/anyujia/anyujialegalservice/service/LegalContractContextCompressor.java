package com.anyujia.anyujialegalservice.service;

import com.anyujia.anyujialegalservice.domain.entity.LegalDocumentBlock;
import com.anyujia.anyujialegalservice.mapper.LegalDocumentBlockMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Builds a bounded contract context without cutting the whole document at an arbitrary
 * character offset. The original blocks remain stored in legal_document_block and every
 * selected fragment carries its block/page locator for evidence tracing.
 */
@Service
@RequiredArgsConstructor
public class LegalContractContextCompressor {
    private static final int DEFAULT_MAX_CHARS = 20_000;
    private static final Pattern RISK_TERMS = Pattern.compile(
            "押金|定金|租金|维修|修缮|违约|解约|解除|转租|退租|续租|涨租|付款|逾期|损坏|押一付三|责任|赔偿|提前",
            Pattern.CASE_INSENSITIVE);

    private final LegalDocumentBlockMapper blockMapper;

    public String build(Long contractId, String extractedText, String query) {
        return build(contractId, extractedText, query, DEFAULT_MAX_CHARS);
    }

    public String build(Long contractId, String extractedText, String query, int maxChars) {
        if (maxChars <= 0) {
            return "";
        }
        List<LegalDocumentBlock> blocks = blocks(contractId);
        if (blocks.isEmpty()) {
            return fallback(extractedText, maxChars);
        }

        String normalizedQuery = query == null ? "" : query.toLowerCase(Locale.ROOT);
        Set<LegalDocumentBlock> selected = new HashSet<>();
        List<LegalDocumentBlock> ranked = blocks.stream()
                .sorted(Comparator.comparingInt((LegalDocumentBlock block) -> score(block, normalizedQuery))
                        .reversed()
                        .thenComparing(LegalDocumentBlock::getBlockIndex,
                                Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
        int used = 0;
        for (LegalDocumentBlock block : ranked) {
            String fragment = render(block);
            if (fragment.length() > maxChars - used) {
                continue;
            }
            selected.add(block);
            used += fragment.length() + 1;
            if (used >= maxChars) {
                break;
            }
        }

        // If query terms did not match, preserve the beginning of the contract by block,
        // rather than returning an arbitrary substring from the middle of a clause.
        if (selected.isEmpty()) {
            for (LegalDocumentBlock block : blocks) {
                String fragment = render(block);
                if (fragment.length() > maxChars - used) {
                    break;
                }
                selected.add(block);
                used += fragment.length() + 1;
            }
        }

        return selected.stream()
                .sorted(Comparator.comparing(LegalDocumentBlock::getBlockIndex,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .map(this::render)
                .reduce((left, right) -> left + "\n" + right)
                .orElseGet(() -> fallback(extractedText, maxChars));
    }

    private List<LegalDocumentBlock> blocks(Long contractId) {
        if (contractId == null) {
            return List.of();
        }
        return blockMapper.selectList(new LambdaQueryWrapper<LegalDocumentBlock>()
                .eq(LegalDocumentBlock::getContractId, contractId)
                .orderByAsc(LegalDocumentBlock::getBlockIndex));
    }

    private int score(LegalDocumentBlock block, String query) {
        String value = (safe(block.getHeading()) + " " + safe(block.getBlockText()))
                .toLowerCase(Locale.ROOT);
        int score = 0;
        if (!query.isBlank() && value.contains(query)) {
            score += 10;
        }
        var matcher = RISK_TERMS.matcher(value);
        while (matcher.find()) {
            score++;
        }
        if (block.getHeading() != null && !block.getHeading().isBlank()) {
            score += 2;
        }
        return score;
    }

    private String render(LegalDocumentBlock block) {
        String locator = "[contract_block id=" + safe(block.getId())
                + " index=" + safe(block.getBlockIndex())
                + " page=" + safe(block.getPageNumber())
                + " heading=" + safe(block.getHeading()) + "]";
        return locator + "\n" + safe(block.getBlockText());
    }

    private String fallback(String value, int maxChars) {
        String text = safe(value);
        return text.length() <= maxChars ? text : text.substring(0, maxChars) + "\n[contract_context_truncated]";
    }

    private String safe(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
