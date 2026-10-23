# 法律 Agent 模块面试问答

面向 Java 后端实习面试，依据最终 HTML 简历、当前源码与既有测试报告整理。本次没有重新运行压测，也没有修改简历或业务代码。

## 阅读目录

| 文档 | 内容 | 题号 |
| --- | --- | --- |
| [01 架构与项目表达](01-架构与项目表达.md) | 一分钟介绍、独立会话、证据链、业务取舍 | Q01–Q08 |
| [02 文档解析与混合 RAG](02-文档解析与混合RAG.md) | Tika、切块、法规版本、BM25、RRF、Milvus、引用 | Q09–Q22 |
| [03 上下文、记忆与一致性](03-上下文记忆与一致性.md) | 压缩、短期/长期记忆、指纹、过期答案 | Q23–Q31 |
| [04 异步任务、Outbox 与 SSE](04-异步任务与SSE.md) | 租约、Checkpoint、Replay、事件重放、背压 | Q32–Q47 |
| [05 NLI、审核与模型服务](05-NLI与人工审核.md) | 支持度、冲突、降级、INT8、微批、故障接管 | Q48–Q59 |
| [06 指标、压测与面试追问](06-指标与面试追问.md) | 简历数字、测试口径、质量边界、复习清单 | Q60–Q73 |

共 73 题。每题先给可口述的参考回答，再列容易被追问的实现边界。Mermaid 图可在支持 Mermaid 的 Markdown 预览器中查看；正文也能独立阅读。

## 使用原则

- **当前实现**以源码为准；历史报告描述的是对应阶段，不能自动代表当前代码。
- **性能结果**注明入口、环境、样本量、并发和成功条件。
- **改进方向**用“计划/建议”表述，不能说成已经上线。
- 法律风险提示属于辅助信息；面试时不要把模型输出表述为法律意见或律师审核结论。

## 最终简历的重点与风险

| 简历表述 | 面试时应如何限定 |
| --- | --- |
| Tika 保留页码、标题 | PDF 页定位相对稳定；DOCX 页码受渲染影响，块、段落和标题更可靠 |
| BM25 + Milvus + RRF | 当前是 Java 自实现的 BM25 风格词法通道；Milvus 和本地模型是可选配置，不是所有部署都开启 |
| Recall@4 98.53%、citation validity 100% | 本地冻结集分别为 34 条检索、37 条回答；不是法律结论准确率 |
| 阶段级 replay | 当前 `LegalAgentTaskService.replay()` 仅允许 `CONTEXT_AND_EVIDENCE`；阶段表与接口为扩展打底 |
| Outbox + MQ + Redis Stream | 提交任务与 Outbox 同事务；完成任务与结果事件写入存在失败补偿边界，不能泛化为全链路事务一致 |
| 1000 SSE，P95 22.75ms | Gateway 到 Legal 的建连指标，不是 1000 个模型请求同时完成 |
| 双 NLI 副本 32.18 req/s | 64 VU、640 请求的模型接口测试，629 成功、11 拒绝；不是 Agent 端到端吞吐 |
| 故障接管成功率 100% | 停止一个 NLI 副本后的 32 VU、320 请求场景；不能外推所有故障 |
| OpenTelemetry | 代码接入 API 埋点；Collector、Trace UI 和生产采样配置尚需独立部署 |

## 主要证据入口

- 最终简历：`D:/Project/html-resume/resume-standalone-balanced (1).html`
- [法律模块分析报告](../legal-module-analysis.md)
- [法律 Agent 简历素材](../legal-resume.md)
- [RAG 评估集](../../eval/legal-rag/README.md)
- [NLI 评估集](../../eval/legal-entailment/README.md)
- [Agent 任务服务](../../../anyujia-legal/anyujia-legal-service/src/main/java/com/anyujia/anyujialegalservice/service/LegalAgentTaskService.java)
- [混合排序器](../../../anyujia-legal/anyujia-legal-service/src/main/java/com/anyujia/anyujialegalservice/service/HybridLegalRanker.java)
- [SSE 控制器](../../../anyujia-legal/anyujia-legal-service/src/main/java/com/anyujia/anyujialegalservice/controller/LegalController.java)
- [本地模型服务](../../embedding/app.py)
