# 法律 Agent 合同测试集

本目录包含仅用于自动化测试的合成租赁合同，不对应任何真实当事人、房源或法律文件。文本中的高风险条款是故意构造的测试噪点，不能直接作为法律结论。

## 覆盖场景

| 文件 | 主要场景 | 期望识别的风险 |
| --- | --- | --- |
| `contract-01-normal.txt` | 基本正常合同 | 正常条款、低风险基线 |
| `contract-02-damage-triple-compensation.txt` | 损坏物品按原价三倍赔偿 | 赔偿标准过高、缺少实际损失和责任认定 |
| `contract-03-furniture-depreciation-no-limit.txt` | 家具折旧费、全新价扣款 | 折旧规则不透明、押金扣除依据不足 |
| `contract-04-landlord-unilateral-rights.txt` | 房东随时入户、单方涨租、没收押金 | 单方变更、隐私/居住安宁和押金风险 |
| `contract-05-conflict-and-noise.txt` | 合同与聊天事实冲突、无关文本噪点 | 证据冲突、无关内容过滤、上下文压缩 |
| `contract-06-security-deposit-and-cleaning-fee.txt` | 不可退清洁费、押金无限期扣留 | 费用透明度、退还期限和格式条款风险 |

## 使用建议

- 先直接注册/解析合同，验证 Apache Tika 的正文、block、页码/定位和 SHA-256 幂等。
- 再将对应合同与聊天样本、房源快照绑定，验证合同条款与聊天承诺冲突的识别能力。
- 评估时要求 Agent 输出：风险等级、原文证据定位、风险理由、待确认事实和免责声明。
- 不应要求模型直接断言条款“必然无效”；应使用“存在较高风险/可能需要调整/需结合地区和实际履行判断”等审慎表述。

对应清单：`manifest.v1.jsonl`。
