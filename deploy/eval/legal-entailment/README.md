# 法律结论 Entailment 人工标注集

该数据集用于评估法律 Agent 的“结论是否被法规证据支持”，不是法律意见，也不替代律师审核。

每条样本包含：法规证据 `premise`、Agent 结论 `hypothesis`、人工标签 `label`：

- `ENTAILMENT`：证据足以支持结论；
- `CONTRADICTION`：结论与证据明确冲突；
- `UNKNOWN`：证据不足以推出结论。

使用：

```powershell
python evaluate.py --dataset manifest.v1.jsonl
```

若环境安装了 `sentence-transformers`，可使用语义向量评估：

```powershell
python evaluate.py --dataset manifest.v1.jsonl --model sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2
```

未安装模型时自动运行可审计的字符 n-gram 基线；基线结果只能用于回归和数据质量检查，不能宣称达到生产级法律语义理解。
