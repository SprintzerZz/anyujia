package com.anyujia.anyujialegalservice.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.anyujia.anyujiachatapi.domain.vo.MessageVO;
import java.util.List;
import org.junit.jupiter.api.Test;

class ConversationRiskAnalyzerTest {
    private final ConversationRiskAnalyzer analyzer = new ConversationRiskAnalyzer();

    @Test
    void onlyUsesLandlordMessagesAndCrossChecksContractCharges() {
        MessageVO tenant = message("m1", 10L, "押金不退也可以");
        MessageVO landlord = message("m2", 20L, "我可以随时直接进入房屋，还要另外交服务费");

        var findings = analyzer.analyze(10L, "租金每月三千元。", List.of(tenant, landlord));

        assertThat(findings).extracting(finding -> finding.getRiskCode())
                .containsExactlyInAnyOrder("CHAT_UNANNOUNCED_ENTRY", "CHAT_EXTRA_CHARGE_NOT_IN_CONTRACT");
        assertThat(findings).allMatch(finding -> "m2".equals(finding.getEvidenceSourceId()));
    }

    @Test
    void doesNotFlagExtraChargeWhenContractAlreadyDisclosesIt() {
        var findings = analyzer.analyze(10L, "每月服务费100元。",
                List.of(message("m2", 20L, "另外交服务费")));

        assertThat(findings).isEmpty();
    }

    private MessageVO message(String id, Long fromId, String content) {
        MessageVO result = new MessageVO();
        result.setMessageId(id);
        result.setFromId(fromId);
        result.setContent(content);
        return result;
    }
}
