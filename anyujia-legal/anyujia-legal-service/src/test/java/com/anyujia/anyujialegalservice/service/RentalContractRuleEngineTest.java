package com.anyujia.anyujialegalservice.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.anyujia.anyujialegalservice.domain.model.RiskFinding;
import java.util.List;
import org.junit.jupiter.api.Test;

class RentalContractRuleEngineTest {
    private final RentalContractRuleEngine engine = new RentalContractRuleEngine();

    @Test
    void emitsEvidenceBackedCoreRentalRisks() {
        String contract = "承租人支付押金5000元。甲方有权随时进入房屋检查。"
                + "出租人有权单方解除本合同，承租人不得提出异议。";

        List<RiskFinding> findings = engine.analyze(contract);

        assertThat(findings).extracting(RiskFinding::getRiskCode)
                .containsExactlyInAnyOrder("DEPOSIT_RETURN_UNCLEAR", "LANDLORD_ENTRY_WITHOUT_CONSENT",
                        "UNILATERAL_TERMINATION", "LESSOR_AUTHORITY_MISSING");
        assertThat(findings).filteredOn(item -> item.getEvidenceQuote() != null)
                .allSatisfy(item -> assertThat(item.getEvidenceQuote()).isNotBlank());
        assertThat(findings).allSatisfy(item -> {
            assertThat(item.getLegalBasis()).isNotBlank();
            assertThat(item.getSuggestion()).isNotBlank();
            assertThat(item.getConfidence()).isBetween(0, 100);
        });
    }

    @Test
    void doesNotFlagDepositWhenConcreteReturnDeadlineExists() {
        List<RiskFinding> findings = engine.analyze("押金为5000元，退房验收后五个工作日内返还押金。不动产权证已核验。");

        assertThat(findings).extracting(RiskFinding::getRiskCode)
                .doesNotContain("DEPOSIT_RETURN_UNCLEAR", "LESSOR_AUTHORITY_MISSING");
    }

    @Test
    void doesNotTreatTenantsTerminationRightAsLandlordsRight() {
        List<RiskFinding> findings = engine.analyze(
                "出租人有下列情形之一的，承租人有权单方解除合同：迟延交付房屋。"
                        + "不动产权证已经核验。");

        assertThat(findings).extracting(RiskFinding::getRiskCode)
                .doesNotContain("UNILATERAL_TERMINATION");
    }

    @Test
    void doesNotFlagLandlordsTerminationRightWhenTiedToListedTenantBreaches() {
        List<RiskFinding> findings = engine.analyze(
                "承租人有下列情形之一的，出租人有权单方解除本合同，收回房屋："
                        + "不按照约定支付租金，经催告后仍未支付。不动产权证已经核验。");

        assertThat(findings).extracting(RiskFinding::getRiskCode)
                .doesNotContain("UNILATERAL_TERMINATION");
    }
}
