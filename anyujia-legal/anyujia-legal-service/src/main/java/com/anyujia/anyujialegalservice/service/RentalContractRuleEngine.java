package com.anyujia.anyujialegalservice.service;

import com.anyujia.anyujialegalservice.domain.model.RiskFinding;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class RentalContractRuleEngine {
    private static final String RENTAL_REGULATION_ARTICLE_10 = "《住房租赁条例》第十条";
    private static final String RENTAL_REGULATION_ARTICLE_9 = "《住房租赁条例》第九条";
    private static final String CIVIL_CODE = "《中华人民共和国民法典》合同编";

    private static final Pattern DEPOSIT = Pattern.compile("[^。；;]{0,80}押金[^。；;]{0,120}[。；;]?");
    private static final Pattern DEPOSIT_DEADLINE = Pattern.compile("押金[^。；;]{0,100}(\\d+|三|五|七|十|十五|三十)个?(工作)?日内[^。；;]{0,40}(返还|退还)|(?:返还|退还)[^。；;]{0,60}押金[^。；;]{0,60}(\\d+|三|五|七|十|十五|三十)个?(工作)?日内");
    private static final Pattern ENTRY = Pattern.compile("[^。；;]{0,60}(出租人|甲方)[^。；;]{0,50}(随时进入|有权进入|无需.{0,8}同意)[^。；;]{0,80}[。；;]?");
    private static final Pattern UNILATERAL_TERMINATION = Pattern.compile(
            "[^。；;]{0,40}(出租人|甲方)[^。；;，,]{0,20}(?:有权)?(?:随时解除|单方解除|无条件解除)[^。；;]{0,100}[。；;]?");
    private static final Pattern AUTHORITY = Pattern.compile("不动产权证|房屋所有权证|产权证明|授权委托|合法出租权|转租授权");

    public List<RiskFinding> analyze(String text) {
        String normalized = text == null ? "" : text.replaceAll("\\s+", " ").trim();
        List<RiskFinding> risks = new ArrayList<>();

        Matcher deposit = DEPOSIT.matcher(normalized);
        if (deposit.find() && !DEPOSIT_DEADLINE.matcher(normalized).find()) {
            risks.add(finding("DEPOSIT_RETURN_UNCLEAR", "HIGH", "押金返还期限不明确",
                    "合同提到押金，但没有识别到明确的返还期限。",
                    deposit.group(), RENTAL_REGULATION_ARTICLE_10,
                    "约定退房验收及费用结清后多少个工作日内返还，并列明可扣减的具体情形。", 92));
        }

        Matcher entry = ENTRY.matcher(normalized);
        if (entry.find()) {
            risks.add(finding("LANDLORD_ENTRY_WITHOUT_CONSENT", "HIGH", "出租人进入房屋的权限过宽",
                    "条款可能允许出租人在未获得承租人同意时进入租赁住房。",
                    entry.group(), RENTAL_REGULATION_ARTICLE_9,
                    "限定为紧急情况或提前通知并取得承租人同意后方可进入。", 95));
        }

        Matcher termination = UNILATERAL_TERMINATION.matcher(normalized);
        String terminationEvidence = null;
        while (termination.find()) {
            String candidate = termination.group();
            if (!candidate.contains("承租人有下列情形之一")
                    && !candidate.contains("乙方有下列情形之一")) {
                terminationEvidence = candidate;
                break;
            }
        }
        if (terminationEvidence != null) {
            risks.add(finding("UNILATERAL_TERMINATION", "HIGH", "出租人单方解除权过宽",
                    "合同可能赋予出租人缺少明确条件的单方解除权。",
                    terminationEvidence, CIVIL_CODE,
                    "将解除条件限定为明确的重大违约，并设置通知期和合理腾退时间。", 90));
        }

        if (!AUTHORITY.matcher(normalized).find()) {
            risks.add(finding("LESSOR_AUTHORITY_MISSING", "MEDIUM", "未识别到出租权证明约定",
                    "合同中未识别到产权证明、授权委托或合法转租授权的核验安排。",
                    null, RENTAL_REGULATION_ARTICLE_9,
                    "签约前核验出租人身份证明、不动产权属证明；代理或转租时同时核验授权文件。", 82));
        }
        return risks;
    }

    private RiskFinding finding(String code, String level, String title, String conclusion,
                                String evidence, String legalBasis, String suggestion, int confidence) {
        return new RiskFinding(code, level, title, conclusion, evidence, legalBasis, suggestion,
                confidence, false);
    }
}
