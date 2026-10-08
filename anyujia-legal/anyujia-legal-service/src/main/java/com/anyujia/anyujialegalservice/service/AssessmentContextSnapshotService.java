package com.anyujia.anyujialegalservice.service;

import com.anyujia.anyujiachatapi.domain.vo.MessageVO;
import com.anyujia.anyujialegalservice.domain.entity.LegalAssessmentContextSnapshot;
import com.anyujia.anyujialegalservice.domain.entity.LegalAssessmentJob;
import com.anyujia.anyujialegalservice.domain.entity.LegalContract;
import com.anyujia.anyujialegalservice.domain.entity.LegalDocumentBlock;
import com.anyujia.anyujialegalservice.domain.model.LawPassage;
import com.anyujia.anyujialegalservice.mapper.LegalAssessmentContextSnapshotMapper;
import com.anyujia.anyujiahouseapi.domain.vo.HousePublishVO;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class AssessmentContextSnapshotService {
    private static final int CHAT_MESSAGE_LIMIT = 100;

    private final LegalAssessmentContextSnapshotMapper mapper;

    public LegalAssessmentContextSnapshot createSnapshot(LegalAssessmentJob job,
                                                         LegalContract contract,
                                                         List<LegalDocumentBlock> blocks,
                                                         List<MessageVO> messages,
                                                         HousePublishVO product,
                                                         Map<String, List<LawPassage>> passagesByRisk) {
        LegalAssessmentContextSnapshot snapshot = new LegalAssessmentContextSnapshot();
        snapshot.setJobId(job.getId());
        snapshot.setContractId(contract.getId());
        snapshot.setConversationId(contract.getConversationId());
        snapshot.setTenantId(job.getTenantId());
        snapshot.setRegionCode(job.getRegionCode());
        snapshot.setAsOfDate(job.getAsOfDate());
        snapshot.setContractBlockCount(blocks.size());
        snapshot.setContractHash(hash(contractMaterial(contract, blocks)));
        snapshot.setChatMessageCount(messages.size());
        snapshot.setChatMessageLimit(CHAT_MESSAGE_LIMIT);
        snapshot.setChatHash(hash(chatMaterial(messages)));
        snapshot.setHousePublishId(product == null ? null : product.getPublishId());
        snapshot.setProductHash(product == null ? null : hash(productMaterial(product)));
        snapshot.setRetrievalQueryCount(passagesByRisk.size());
        snapshot.setLawPassageCount(passageCount(passagesByRisk));
        snapshot.setRetrievalHash(hash(retrievalMaterial(passagesByRisk)));
        snapshot.setContextHash(hash(String.join("|",
                nullToEmpty(snapshot.getContractHash()),
                nullToEmpty(snapshot.getChatHash()),
                nullToEmpty(snapshot.getProductHash()),
                nullToEmpty(snapshot.getRetrievalHash()))));
        snapshot.setCreatedAt(LocalDateTime.now());
        try {
            mapper.insert(snapshot);
        } catch (Exception ex) {
            log.warn("Legal context snapshot skipped, jobId={}: {}", job.getId(), ex.getMessage());
        }
        return snapshot;
    }

    private String contractMaterial(LegalContract contract, List<LegalDocumentBlock> blocks) {
        String blockIndex = blocks.stream()
                .map(block -> block.getBlockIndex() + ":" + block.getPageNumber() + ":"
                        + block.getParagraphIndex() + ":" + block.getLocatorPrecision())
                .collect(Collectors.joining("\n"));
        return contract.getId() + "\n" + nullToEmpty(contract.getSha256()) + "\n"
                + nullToEmpty(contract.getDocumentType()) + "\n" + blockIndex;
    }

    private String chatMaterial(List<MessageVO> messages) {
        return messages.stream()
                .map(message -> nullToEmpty(message.getMessageId()) + ":"
                        + nullToEmpty(message.getFromId()) + ":"
                        + hash(nullToEmpty(message.getContent())))
                .collect(Collectors.joining("\n"));
    }

    private String productMaterial(HousePublishVO product) {
        return product.getPublishId() + "\n"
                + nullToEmpty(product.getTitle()) + "\n"
                + nullToEmpty(product.getMonthlyRent()) + "\n"
                + nullToEmpty(product.getDepositMonths()) + "\n"
                + nullToEmpty(product.getPaymentCycleMonths()) + "\n"
                + nullToEmpty(product.getPropertyFee()) + "\n"
                + nullToEmpty(product.getRentalRequirements()) + "\n"
                + hash(nullToEmpty(product.getDescription()) + "\n" + nullToEmpty(product.getFacilities()));
    }

    private String retrievalMaterial(Map<String, List<LawPassage>> passagesByRisk) {
        return passagesByRisk.entrySet().stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue().stream()
                        .map(passage -> passage.sourceId() + ":"
                                + nullToEmpty(passage.articleNumber()) + ":"
                                + hash(nullToEmpty(passage.text())))
                        .collect(Collectors.joining(",")))
                .collect(Collectors.joining("\n"));
    }

    private int passageCount(Map<String, List<LawPassage>> passagesByRisk) {
        return passagesByRisk.values().stream().mapToInt(List::size).sum();
    }

    private String hash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }

    private String nullToEmpty(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
