package com.anyujia.anyujialegalservice.service;

import com.anyujia.anyujiahouseapi.domain.vo.HousePublishVO;
import com.anyujia.anyujialegalservice.domain.model.RiskFinding;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class RentalProductRiskAnalyzer {
    private static final String LEGAL_BASIS = "房源发布信息与租赁合同一致性核对";
    private static final Pattern DEPOSIT = Pattern.compile("押金|押一|押二|押三|押四|保证金");
    private static final Pattern PAYMENT_CYCLE = Pattern.compile("月付|季付|半年付|年付|付一|付二|付三|付款周期|支付周期");
    private static final Pattern PROPERTY_FEE = Pattern.compile("物业费|管理费");

    public List<RiskFinding> analyze(String contractText, HousePublishVO product) {
        if (product == null) {
            return List.of();
        }
        String contract = contractText == null ? "" : contractText.replaceAll("\\s+", "");
        List<RiskFinding> findings = new ArrayList<>();
        if (positive(product.getDepositMonths()) && !DEPOSIT.matcher(contract).find()) {
            findings.add(productFinding(product, "PRODUCT_DEPOSIT_NOT_IN_CONTRACT", "MEDIUM",
                    "房源发布页承诺的押金未在合同中明确",
                    "房源发布信息包含押金月数，但合同正文未识别到押金或保证金约定。",
                    "签约前要求把押金金额、退还时间、扣减条件和举证责任写入合同。",
                    84));
        }
        if (positive(product.getPaymentCycleMonths()) && product.getPaymentCycleMonths() > 1
                && !PAYMENT_CYCLE.matcher(contract).find()) {
            findings.add(productFinding(product, "PRODUCT_PAYMENT_CYCLE_NOT_IN_CONTRACT", "MEDIUM",
                    "房源发布页的付款周期未在合同中明确",
                    "房源发布信息包含非月付的付款周期，但合同正文未识别到对应支付周期约定。",
                    "要求合同明确租金支付周期、每期金额、付款日和逾期后果。",
                    80));
        }
        if (product.getPropertyFee() != null && product.getPropertyFee().compareTo(BigDecimal.ZERO) > 0
                && !PROPERTY_FEE.matcher(contract).find()) {
            findings.add(productFinding(product, "PRODUCT_PROPERTY_FEE_NOT_IN_CONTRACT", "MEDIUM",
                    "房源发布页的物业费未在合同中明确",
                    "房源发布信息包含物业费或管理类费用，但合同正文未识别到对应收费约定。",
                    "要求列明物业费金额、支付方、支付周期、是否包含在租金内以及调整规则。",
                    82));
        }
        if (hasMaterialRequirements(product.getRentalRequirements())
                && !contractMentionsRequirements(contract, product.getRentalRequirements())) {
            findings.add(productFinding(product, "PRODUCT_REQUIREMENTS_NOT_IN_CONTRACT", "LOW",
                    "房源发布页的租住要求未在合同中明确",
                    "房源发布信息包含宠物、吸烟、入住人数或最短租期等租住要求，但合同正文未识别到对应约定。",
                    "如该要求会影响入住、续租、退租或押金扣减，应写入合同或补充协议。",
                    72));
        }
        return findings;
    }

    private boolean positive(Integer value) {
        return value != null && value > 0;
    }

    private boolean hasMaterialRequirements(String requirements) {
        if (requirements == null || requirements.isBlank()) {
            return false;
        }
        String normalized = requirements.toLowerCase(Locale.ROOT);
        return normalized.contains("宠物") || normalized.contains("吸烟") || normalized.contains("人数")
                || normalized.contains("最短") || normalized.contains("短租") || normalized.contains("转租");
    }

    private boolean contractMentionsRequirements(String contract, String requirements) {
        String normalized = requirements == null ? "" : requirements.toLowerCase(Locale.ROOT);
        return (normalized.contains("宠物") && contract.contains("宠物"))
                || (normalized.contains("吸烟") && contract.contains("吸烟"))
                || (normalized.contains("人数") && contract.contains("人数"))
                || ((normalized.contains("最短") || normalized.contains("短租")) && contract.contains("租期"))
                || (normalized.contains("转租") && contract.contains("转租"));
    }

    private RiskFinding productFinding(HousePublishVO product, String code, String level, String title,
                                       String conclusion, String suggestion, int confidence) {
        RiskFinding finding = new RiskFinding(code, level, title, conclusion,
                evidence(product), LEGAL_BASIS, suggestion, confidence, false);
        finding.setEvidenceType("PRODUCT");
        finding.setEvidenceSourceId(String.valueOf(product.getPublishId()));
        finding.setEvidenceLocatorPrecision("PRODUCT_SNAPSHOT");
        return finding;
    }

    private String evidence(HousePublishVO product) {
        StringBuilder result = new StringBuilder();
        append(result, "publishId", product.getPublishId());
        append(result, "title", product.getTitle());
        append(result, "monthlyRent", product.getMonthlyRent());
        append(result, "depositMonths", product.getDepositMonths());
        append(result, "paymentCycleMonths", product.getPaymentCycleMonths());
        append(result, "propertyFee", product.getPropertyFee());
        append(result, "rentalRequirements", product.getRentalRequirements());
        return result.substring(0, Math.min(result.length(), 1000));
    }

    private void append(StringBuilder result, String key, Object value) {
        if (value != null) {
            result.append(key).append('=').append(value).append("; ");
        }
    }
}
