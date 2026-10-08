package com.anyujia.anyujialegalservice.service;

import com.anyujia.anyujiachatapi.domain.vo.MessageVO;
import com.anyujia.anyujialegalservice.domain.model.RiskFinding;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class ConversationRiskAnalyzer {
    private static final Pattern DEPOSIT = Pattern.compile("(不退|没收|扣除).{0,6}押金|押金.{0,6}(不退|没收)");
    private static final Pattern ENTRY = Pattern.compile("(随时|直接|无需通知).{0,10}(进房|进入|开门|看房)");
    private static final Pattern EXTRA_CHARGE = Pattern.compile("(额外|另外|再).{0,8}(费用|服务费|管理费|押金)");

    public List<RiskFinding> analyze(Long tenantId, String contractText, List<MessageVO> messages) {
        List<RiskFinding> result = new ArrayList<>();
        addFirst(result, tenantId, messages, message -> DEPOSIT.matcher(text(message)).find(),
                finding("CHAT_DEPOSIT_FORFEITURE", "HIGH", "房东在聊天中主张不退或没收押金",
                        "该聊天表述可能扩大房东扣除押金的范围，应与合同退还条件及实际损失证据交叉核对。",
                        "要求将押金扣除条件、举证责任和退还期限写入合同或补充协议。", 92));
        addFirst(result, tenantId, messages, message -> ENTRY.matcher(text(message)).find(),
                finding("CHAT_UNANNOUNCED_ENTRY", "HIGH", "房东在聊天中主张可未经通知进入房屋",
                        "该表述可能影响承租人对租赁房屋的正常占有和隐私权益。",
                        "明确除紧急情形外的提前通知期限、进入理由和双方确认方式。", 91));
        boolean contractMentionsExtraCharge = contractText != null
                && Pattern.compile("服务费|管理费|额外费用").matcher(contractText).find();
        if (!contractMentionsExtraCharge) {
            addFirst(result, tenantId, messages, message -> EXTRA_CHARGE.matcher(text(message)).find(),
                    finding("CHAT_EXTRA_CHARGE_NOT_IN_CONTRACT", "MEDIUM", "聊天中出现合同未明确的额外收费",
                            "房东提出的额外收费在合同正文中未检出对应约定，存在费用范围不清的风险。",
                            "签约前要求列明收费名称、金额、周期、触发条件及退款规则。", 88));
        }
        return result;
    }

    private void addFirst(List<RiskFinding> target, Long tenantId, List<MessageVO> messages,
                          Predicate<MessageVO> predicate, RiskFinding template) {
        messages.stream().filter(message -> message.getFromId() != null && !message.getFromId().equals(tenantId))
                .filter(predicate).findFirst().ifPresent(message -> {
                    template.setEvidenceQuote(bounded(text(message), 500));
                    template.setEvidenceType("CHAT");
                    template.setEvidenceSourceId(message.getMessageId());
                    template.setEvidenceLocatorPrecision("MESSAGE_EXACT");
                    target.add(template);
                });
    }

    private RiskFinding finding(String code, String level, String title, String conclusion,
                                String suggestion, int confidence) {
        return new RiskFinding(code, level, title, conclusion, "", "待结合法规库检索核对",
                suggestion, confidence, false);
    }

    private String text(MessageVO message) {
        return message.getContent() == null ? "" : message.getContent();
    }

    private String bounded(String value, int length) {
        return value.substring(0, Math.min(value.length(), length));
    }
}
