package com.anyujia.anyujialegalservice.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.anyujia.anyujiahouseapi.domain.vo.HousePublishVO;
import com.anyujia.anyujialegalservice.domain.model.RiskFinding;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class RentalProductRiskAnalyzerTest {
    private final RentalProductRiskAnalyzer analyzer = new RentalProductRiskAnalyzer();

    @Test
    void flagsStructuredProductPromisesMissingFromContract() {
        HousePublishVO product = product();
        product.setDepositMonths(2);
        product.setPaymentCycleMonths(3);
        product.setPropertyFee(new BigDecimal("300.00"));
        product.setRentalRequirements("禁止养宠物，最多入住2人");

        List<RiskFinding> findings = analyzer.analyze("租金每月5000元。租期一年。", product);

        assertThat(findings).extracting(RiskFinding::getRiskCode)
                .containsExactlyInAnyOrder("PRODUCT_DEPOSIT_NOT_IN_CONTRACT",
                        "PRODUCT_PAYMENT_CYCLE_NOT_IN_CONTRACT",
                        "PRODUCT_PROPERTY_FEE_NOT_IN_CONTRACT",
                        "PRODUCT_REQUIREMENTS_NOT_IN_CONTRACT");
        assertThat(findings).allSatisfy(finding -> {
            assertThat(finding.getEvidenceType()).isEqualTo("PRODUCT");
            assertThat(finding.getEvidenceSourceId()).isEqualTo("700");
            assertThat(finding.getEvidenceLocatorPrecision()).isEqualTo("PRODUCT_SNAPSHOT");
            assertThat(finding.getEvidenceQuote()).contains("publishId=700");
        });
    }

    @Test
    void doesNotFlagProductTermsAlreadyDisclosedInContract() {
        HousePublishVO product = product();
        product.setDepositMonths(1);
        product.setPaymentCycleMonths(3);
        product.setPropertyFee(new BigDecimal("200.00"));
        product.setRentalRequirements("禁止养宠物");

        List<RiskFinding> findings = analyzer.analyze(
                "押金一个月，租金季付，物业费每月200元，禁止养宠物。", product);

        assertThat(findings).isEmpty();
    }

    private HousePublishVO product() {
        HousePublishVO result = new HousePublishVO();
        result.setPublishId(700L);
        result.setTitle("近地铁整租两居室");
        result.setMonthlyRent(new BigDecimal("5000.00"));
        return result;
    }
}
