# Legal Agent 上下文压缩离线评估集

版本：`legal-context-v1`

该评估集不依赖 Docker、Redis、MySQL、RAG 服务或在线模型，用于验证上下文压缩与证据保留的基础正确性。

覆盖 7 类场景：

- 押金金额和退还期限；
- 维修责任与例外；
- 提前解约和违约金；
- 聊天与合同事实冲突；
- 无关聊天消息过滤；
- Agent 多轮历史引用；
- 上下文版本变化与重新确认。

数据约束：

- 原始聊天、合同块和 Agent 历史必须保留在 case 中，不允许只保留压缩结果。
- `expected_evidence_ids` 是必须进入压缩上下文的来源 ID。
- `forbidden_evidence_ids` 不应在命中风险消息的场景中被选入。
- `requires_conflict_flag` 要求结果标记事实冲突，不能静默选择一个版本。
- `expected_context_status` 用于校验上下文版本检测结果。

运行校验：

```powershell
python deploy/eval/legal-context/evaluate.py --dataset deploy/eval/legal-context/cases.v1.jsonl
```

如果已有离线压缩器输出，可额外传入预测文件：

```powershell
python deploy/eval/legal-context/evaluate.py `
  --dataset deploy/eval/legal-context/cases.v1.jsonl `
  --predictions deploy/eval/legal-context/predictions.jsonl `
  --output deploy/eval/legal-context/report.json
```

预测文件必须为每个 case 恰好提供一行，包含 `case_id`、`selected_evidence_ids`、`facts`、`context_status`、`semantic_conflict` 和 `history_reference`。`semantic_conflict` 指合同与聊天的事实冲突，不等同于任务分析期间的 `context_conflict`（上下文变更）。缺失字段不能通过评估。
