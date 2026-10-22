# anyujia-legal 模块分析报告

> 分析日期：2026-10-21  
> 分析范围：`anyujia-legal`、相关 MySQL migration、部署 Dockerfile、模块 README 及当前工作区中的实现  
> 分析方法：目录/依赖审查、入口和关键链路代码审查、数据库模型审查、测试执行验证  
> 说明：当前工作区存在大量未提交改动，本文描述的是分析时工作区的实际状态，不等同于某个已提交版本。

## 1. 结论摘要

`anyujia-legal` 已经不是单纯的合同规则服务，而是一个面向租客的法律风险评估与法律问答模块，形成了以下闭环：

```text
租客上传合同/注册合同
        │
        ├─ 文件归档、格式校验、PDF/DOCX 解析、证据块持久化
        │
        └─ 创建幂等评估任务
                │
                ├─ 合同规则分析
                ├─ 聊天证据分析
                ├─ 房源发布信息一致性分析
                ├─ 法规检索：词法 + 意图 + 向量 + 重排
                ├─ 可选 AI 风险表述增强
                ├─ 证据、风险项、上下文指纹和阶段追踪持久化
                └─ 租客查询异步评估结果
```

总体判断：架构方向合理，已经具备租户隔离、幂等、证据溯源、降级、可恢复任务执行和可审计等关键基础；但当前仍更接近“功能完整的 MVP/增强版”，距离生产级法律智能服务还需要补齐数据库约束与级联清理、法规版本治理、外部调用隔离、运营监控和质量门禁。

建议优先级：

1. **P0：验证评估任务可靠执行模型**，当前已具备数据库任务状态、原子领取、租约、重试、超时接管和定时恢复机制；上线前仍需通过多实例和故障注入测试验证边界行为。
2. **P0：收紧法律问答和 Agent 的权限、范围及证据门禁**，尤其是 Agent 绑定校验、区域/时点传递、生成内容必须逐项落到证据。
3. **P1：完善数据库版本和一致性治理**，增加外键或应用级清理策略、必要索引、法规 chunk 的区域/状态/版本查询能力，并建立迁移回滚与升级检查。
4. **P1：完善外部依赖韧性**，为 Chat/House/File/AI/Milvus/Redis 分别设置超时、重试预算、熔断、指标和降级原因。
5. **P1：先修复测试运行环境，再建立冻结数据集质量门禁**；当前部分公共模块 class 仍有增量构建/Java 版本问题，不能据此宣称全量测试通过。

## 2. 模块结构与职责

### 2.1 Maven 结构

| 层次 | 目录 | 职责 |
| --- | --- | --- |
| 聚合模块 | `anyujia-legal/pom.xml` | 管理 API 与 service 子模块，统一 Java 17、Spring Boot 3.3.13、Spring Cloud 2023.0.6 等版本 |
| API 模块 | `anyujia-legal/anyujia-legal-api` | DTO、VO、Feign 客户端，供网关或其他服务调用 |
| 服务模块 | `anyujia-legal/anyujia-legal-service` | Controller、业务服务、实体/Mapper、文件解析、RAG、AI、外部服务适配和 CLI |
| 部署 | `deploy/dev/app/legal/Dockerfile` | 使用 Temurin 17 JRE，以非 root 用户运行打包后的 `anyujia-legal.jar` |
| 数据库 | `deploy/dev/app/mysql/migration/V8~V17__legal_*.sql` | 合同、评估、风险、证据、法规、追踪、上下文快照和 Agent 绑定表 |

服务代码按技术层分为 `controller`、`service`、`integration`、`mapper`、`domain`、`config`、`tools`。这种划分易于理解，但大量用例服务集中在单一 `service` 包，领域边界仍主要依赖类名和调用约定表达，后续扩展时可能出现服务类膨胀和跨域依赖增加。

分析时统计：service 主代码约 **93 个 Java 文件、5419 行**；测试约 **27 个 Java 文件、1592 行**。测试覆盖了控制器、核心规则、解析、检索、AI 适配、外部证据适配和 CLI，但尚未形成完整的端到端任务恢复、数据库迁移和多依赖故障演练覆盖。

## 3. 对外接口与权限边界

入口位于 `LegalController`，实现 `LegalFeignClient`，另有 `LegalAgentFeignClient`：

| 能力 | 入口 | 访问边界 |
| --- | --- | --- |
| 上传合同 | `POST /legal/contracts/upload` | 仅 `userFrom=app` 且 `userType=1` 的租客 |
| 注册合同 | `POST /legal/contracts` | 仅租客；注册时通过 Chat 服务校验会话成员关系 |
| 启动/查询评估 | `POST /legal/assessments`、`GET /legal/assessments/{jobId}` | 仅租客，并按租户查询 |
| 法规入库、Embedding 回填 | `/legal/knowledge/sources`、`/legal/knowledge/embeddings/backfill` | `userFrom=sys/service` 的内部调用 |
| 法规检索/问答 | `/legal/knowledge/search`、`/legal/knowledge/answer` | 内部调用，不面向租客直接开放 |
| Agent 问答 | `POST /legal/agent/answer` | 仅租客；基于绑定关系、合同和上下文生成回答 |
| Agent 绑定 | `POST /legal/agent/bind` | 仅内部调用 |

已有优点：

- Controller 统一检查租客和内部服务来源，避免直接把内部 RAG 调试接口暴露给客户端。
- 合同、任务、报告查询带有租户 ID；数据库中的合同内容也以租户和会话维度建立唯一约束。
- Agent 使用独立绑定表，不把 Agent 直接混入普通双边聊天参与者模型，符合 README 中“租客私有、房东不可查询”的设计目标。
- 上传链路包含文件大小、签名、SHA-256、解析字符数、页数和 block 数限制，合同内容被视为不可信输入。

需要重点关注：

- 权限主要依赖网关注入的 Header 和字符串/数值约定。服务内部虽然有校验，但仍建议将 `userFrom`、`userType` 封装为统一鉴权组件，并对 Feign 内部调用增加服务身份认证，而不是只依赖请求头值。
- `LegalAgentService` 的法规检索使用固定 `GLOBAL` 和 `LocalDate.now()`（见实现），而评估任务支持 `regionCode/asOfDate`。这会导致 Agent 问答与同一合同评估使用不同的法规范围和时点，存在法律依据不一致风险。
- 当前版本已为 Agent 请求增加可选的 `regionCode/asOfDate`，并在合同查询中同时校验租户归属；调用方仍应传递合同对应的区域和评估时点。
- Agent 绑定现在校验合同租户、合同会话和已有绑定的一致性；同一聊天会话若请求绑定到不同租户/合同会明确失败，不再静默返回。
- Agent 绑定数据需要验证租客、聊天会话、房东会话、合同和评估任务之间的完整关联；仅以 `chat_conversation_id` 唯一并不足以表达所有业务不变量。
- 需要明确“内部服务”调用的审计、调用方 allow-list、签名/令牌、来源 IP 或 mTLS 策略。`sys/service` 是粗粒度边界，不应成为所有内部能力的长期授权模型。

## 4. 核心业务链路

### 4.1 合同入库

1. 租客上传 PDF/DOCX，并携带 `conversationId`。
2. 文件归档到 File/OSS 适配层；验证文件签名和大小后计算 SHA-256。
3. `TikaLegalDocumentExtractor` 使用 Apache Tika 统一提取 PDF/DOCX 正文；`PdfLegalDocumentParser` 仅使用 PDFBox 补充加密检测、页数和页级定位，`DocxLegalDocumentParser` 仅使用 POI 补充段落/表格/标题等结构定位。`ParsedDocument.fullText` 以及 DOCX block 正文均以 Tika 输出为准，避免 PDFBox/POI 成为另一套正文事实源。
4. `LegalContractService` 以租户、会话和 SHA-256 做幂等去重，保存合同元数据和 `legal_document_block`。
5. 扫描件 PDF 当前返回 OCR-required 错误，未实现 OCR 兜底。

### 4.2 异步评估

1. `LegalAssessmentService.start` 校验合同归属，使用租户 + `requestId` 幂等创建 `legal_assessment_job`。
2. 事务提交后调用 `AssessmentTaskDispatcher`，由异步线程池触发执行；任务状态和租约持久化在 MySQL 中，支持跨实例原子领取。
3. `AssessmentWorker` 组装合同 block、最近聊天消息、房源发布快照和法规 passages。
4. 规则引擎识别合同风险，聊天/房源分析器补充外部事实，法规检索提供法律依据。
5. 可选 `RiskAnalysisEnhancer` 调用 DashScope 改写表述，但规则发现和证据先行。
6. 保存风险项、证据引用、上下文 snapshot 和 append-only trace，最后更新任务状态。
7. 通过 `GET /legal/assessments/{jobId}` 轮询结果，或通过 `GET /legal/assessments/{jobId}/stream` 使用 SSE 接收任务完成/失败事件；普通查询接口保留为兜底。

### 4.4 法律助手独立会话

产品交互要求为：租客在租客/房东聊天页面点击“法律助手”后，自动打开一个独立的法律助手窗口。该窗口不是复用原租客-房东会话，而是创建新的 Agent 类型会话，避免法律分析消息混入双方原始聊天记录。

当前链路如下：

```text
租客点击“法律助手”
        │
        ▼
POST /chat/agent/session/add
        │  landlordConversationId + contractId
        ▼
Chat 创建独立 conversation（conversation_type=AGENT）
        │
        ├─ tenantUserId = 当前租客
        ├─ landlordUserId = AGENT_USER_ID（-1）
        ├─ 创建租客成员和会话索引
        └─ 调用 /legal/agent/bind
                │
                └─ 绑定新 Agent 会话、房东原会话和合同
        ▼
返回新的 sessionId，前端打开独立窗口
```

独立窗口中的每次 `/legal/agent/answer` 请求都使用新的 `chatConversationId`。Legal Agent 会读取：

- 合同解析正文和证据 block；
- 房东原会话中的聊天证据；
- 房源/商品发布信息；
- 根据 `regionCode/asOfDate` 检索的法规 RAG passages。

回答会写回 Agent 独立会话，并保留短期对话记忆；原租客-房东会话只作为证据来源，不会被 Legal Agent 的回答污染。合同风险评估完成后，也会将汇总结果通知到该独立 Agent 会话。

创建接口会校验绑定接口返回码；明确绑定失败时返回错误并触发本地事务回滚，避免正常失败路径出现“聊天窗口已创建但没有合同/证据绑定”的孤立 Agent 会话。由于 Chat 与 Legal 是跨服务调用，网络超时等不确定失败仍应通过幂等绑定和定期一致性扫描补偿处理。

这个顺序体现了较好的安全原则：LLM 是增强器，不是唯一事实来源；外部上下文缺失时，确定性规则仍可产出降级结果。

### 4.3 RAG 与法律问答

法规入库流程为：HTTPS 来源元数据 → 内容 hash 去重 → 文章感知切分 → MySQL 保存 chunk → 可选生成 embedding → 可选写入 Milvus。

检索流程为：

- 按区域、有效日期和 ACTIVE 状态过滤法规来源。
- 词法、法律意图、向量三个通道独立召回。
- 使用加权 RRF 融合候选。
- 使用确定性证据感知排序器，必要时再调用 BGE reranker。
- AI 回答只允许引用提供上下文中的数字索引；引用越界或非法时回退到抽取式回答。

当 Embedding、Milvus、Reranker、DashScope 或 Redis 不可用时，设计上能够退回词法/意图检索、抽取式回答或确定性规则。这是该模块目前最有价值的可靠性设计之一。

## 5. 数据模型与一致性

主要表及关系如下：

```text
legal_contract
  ├─ legal_document_block
  └─ legal_assessment_job
       ├─ legal_risk_item
       │    └─ legal_evidence_ref
       ├─ legal_trace_event
       └─ legal_assessment_context_snapshot

legal_source
  └─ legal_source_chunk

legal_agent_conversation
  └─ 绑定租客会话、房东会话、合同和可选评估任务
```

优点：

- 合同正文、解析 block、风险项、证据和法规 chunk 分开存储，支持定位和后续重建。
- `legal_assessment_context_snapshot` 存储合同、聊天、房源和检索结果 hash，不重复复制原始大文本，兼顾可复现性和存储成本。
- `legal_trace_event` 采用追加式阶段记录，能够记录外部调用、检索、AI 增强和持久化阶段。
- 合同、评估 job、风险 item 和法规 source 都有幂等或版本化设计意图。

风险与改进：

- migration 中主要使用逻辑关联，没有看到合同、job、risk、evidence、source、chunk、trace 之间的数据库外键。若应用层删除或重试逻辑不完整，容易产生孤儿记录；建议至少建立定期一致性检查和按合同/job 的可恢复清理任务。
- `legal_contract.extracted_text` 使用 `longtext`，同时还保存 block 文本，数据量可能快速膨胀。应明确正文保留策略、压缩/对象存储策略、加密和生命周期。
- `legal_source_chunk.embedding_json` 仍保留 MySQL JSON embedding，同时另有 Milvus，形成双份向量事实。应明确 MySQL 是回填/审计源还是在线查询源，并记录 embedding model、dimension、版本和生成时间，否则模型切换后可能混用不兼容向量。
- `legal_source` 具备 `effective_from/effective_to/status`，但法规更新、废止、同一标题多版本替换的运维流程尚未在模块内体现。需要版本发布、撤回、重建向量和旧版本可追溯策略。
- 关键关联字段缺少显式唯一约束时，应在服务层增加并发测试；尤其是 Agent 绑定、评估任务重试和法规入库并发场景。
- 需要为 `tenant_id`、`conversation_id`、`job_id`、`contract_id` 和法规筛选条件持续核对索引计划，避免大文本表和 trace 表增长后退化。

## 6. 可靠性、扩展性与性能判断

### 已有设计

- 上传和解析限制了 bytes、pages、characters、blocks，降低恶意大文件和内存压力。
- AI 有连接/读取超时、失败阈值和冷却时间；AI 失败不阻断确定性风险报告。
- RAG 每个通道和最终上下文都有候选预算，避免一次请求读取过多法规内容。
- 当前版本将 MySQL 向量回退读取的法规 chunk 数量改为可配置上限，默认 2000，避免语料增长后无界加载 embedding 到 JVM。
- 评估任务已增加 `lease_owner/lease_until/attempt_count/next_run_at`，支持跨实例原子领取和过期 `RUNNING` 任务接管；原轮询接口之外新增 SSE 流接口，SSE 使用独立执行器，避免占用评估工作线程。
- trace 已增加 `legal_trace_event_archive` 归档表和定时批量迁移任务，按保留天数批量迁移并删除旧事件，在线 trace 表不再无限增长。
- Redis 仅作为限定窗口的短期聊天记忆，具有 TTL、租户/会话隔离和不可用回退，不作为法律结论的事实源。
- 评估上下文和 trace 持久化了 hash、数量、阶段状态和降级信号，有利于问题复盘。
- Docker 运行时使用 JRE 17 和非 root 用户，部署基线较合理。

### 主要瓶颈/单点

1. **异步线程池是当前评估吞吐的潜在单点。** 当前已增加 PENDING 任务的定时重新分发、原子状态领取、可配置线程池容量、拒绝策略和优雅停机；数据库任务状态、跨实例租约、过期接管和重试已落地。后续重点是监控队列积压、租约异常和拒绝次数，并通过故障注入验证恢复效果。
2. **评估一次串联多个外部系统。** Chat、House、File/OSS 等 Feign 调用现在有默认连接/读取超时，AI/Milvus 已有独立超时和降级；仍需要进一步按依赖拆分重试预算、熔断和指标。
3. **法规向量检索存在内存回退成本。** 在 Milvus 不可用时，MySQL cosine 回退受 `LEGAL_RETRIEVAL_MAX_CHUNK_CANDIDATES` 限制；语料扩大后仍建议强制 Milvus 或改为分页/专用本地索引。
4. **trace 和 evidence 是只增不减的数据。** 长期运行后，查询、写入和备份成本会增长，需要归档分层、保留周期和压缩策略。
5. **状态通知已升级为 SSE，但仍需控制长连接资源。** 新增 `/legal/assessments/{jobId}/stream`，服务端定时检查任务状态并在完成或失败时结束连接；仍建议增加连接数、超时、断线重连和网关代理配置，并保留轮询接口作为降级路径。

### 建议的生产任务模型

当前已采用数据库持久化任务状态与租约模型，生命周期为：

```text
PENDING -> CLAIMED(lease_owner, lease_until) -> RUNNING
                              │
               timeout/restart ─┴─> RETRY_WAIT -> CLAIMED
                              │
                       max retry -> FAILED/DEAD_LETTER
```

领取使用带租户和状态条件的原子更新，任务执行以 job/requestId 幂等；`next_run_at`、`lease_until`、`attempt_count` 已用于重试调度和故障接管。每个阶段仍应继续补充错误类别、外部请求 ID 和耗时指标。若未来吞吐量超过数据库调度能力，再评估引入 MQ/outbox。

## 7. 安全与隐私

### 优点

- 合同、聊天消息、房源数据和法规证据按来源类别保存，结果可指向具体证据。
- 合同内容被视为不可信数据，README 明确禁止合同文本覆盖系统指令或授权工具。
- API key 通过环境变量注入，README 提醒不得提交到源代码。
- 只允许 HTTPS 法规来源，降低来源篡改和中间人风险。
- 租户隔离贯穿合同、任务、绑定和内存 key。

### 风险

- 合同正文、聊天消息、风险结论属于高敏感数据。当前配置中存在 `change-me` 默认密码，生产环境必须启动失败或至少强制外部覆盖，不能允许默认凭据上线。
- 日志和 trace 的 `details_json` 需要严格禁止写入合同原文、聊天原文、API key、模型 prompt 和完整 AI 响应；应采用 hash、长度、枚举状态和脱敏摘要。
- 上传原件归档、MySQL 正文、Redis 短期记忆、Milvus 向量和备份之间的加密、访问审计、删除请求和保留周期尚未形成完整策略。
- 法律回答需持续展示免责声明，并对超出“租赁法律信息”范围的个人化法律意见、诉讼策略、规避监管等问题做明确拒答或转人工。
- AI 生成回答目前有 citation allow-list，但“每个事实性结论都必须有足够证据”的门禁仍需要通过评测和运行时校验强化。README 已记录 answer scope compliance 94.59%，说明仍存在非目标问题或边界命中。

## 8. 测试与可验证性

### 已观察到的测试资产

测试覆盖了：

- Controller 权限与请求行为；
- PDF/DOCX 解析和 block 切分；
- 合同、评估、法规服务；
- 混合排序、向量缓存、RAG 检索；
- DashScope 客户端和降级实现；
- Chat/File 外部证据适配；
- 风险规则、房源风险和 trace；
- Embedding 回填 CLI。

### 本次执行结果

本次执行命令：

```text
mvn -f anyujia-legal/pom.xml clean -DskipTests
mvn -f anyujia-legal/pom.xml -pl anyujia-legal-service -am -Dtest=DocxLegalDocumentParserTest,PdfLegalDocumentParserTest,LegalDocumentParsingServiceTest,LegalControllerTest -Dsurefire.failIfNoSpecifiedTests=false test
```

结果：clean 构建和主代码编译成功，PDF/DOCX 解析器定向测试 **2/2 通过**。Chat 模块主代码编译成功；新增的独立 Agent 会话测试在 Surefire/JUnit Jupiter 测试引擎发现阶段受到当前工作区测试运行环境问题影响，未进入用例执行。同一轮 Legal 指定测试中，`LegalControllerTest` 和 `LegalDocumentParsingServiceTest` 的 **4 个用例**也因公共模块 class 编译环境问题失败，并非 Tika 解析器断言失败。

主要错误为公共模块 `ResultCode/ApiResult` 报 `Flexible Constructor Bodies` 仅支持 source level 25 以上，随后产生 `NoClassDefFoundError`；依赖这些接口的测试因此无法创建 mock。建议统一 Maven 编译链的 Java source/target 配置，再重新执行全量测试。

### 应补充的测试

- 多实例并发启动同一个 `requestId`，确保只有一个有效执行者。
- 进程在 `RUNNING` 各阶段重启后的恢复和幂等。
- Chat/House/Redis/Milvus/DashScope 超时、5xx、返回非法数据时的降级。
- 租户 A 读取租户 B 的 contract/job/agent binding 的负向测试。
- Agent 绑定被替换、合同被替换、房东会话不匹配、聊天会话类型不匹配的授权测试。
- 法规 effective date、region、STALE/ACTIVE、旧版本和向量模型版本混用测试。
- 超大 PDF、恶意压缩包、DOCX 外部关系、扫描件、乱码和超长表格测试。
- trace/evidence 中不得出现原文和 secret 的日志审计测试。
- 数据库迁移在空库、已有历史数据和重复执行场景下的集成测试。

## 9. 质量指标解读

README 中记录的本地冻结集结果为：

| 类型 | 样本 | 指标 |
| --- | ---: | --- |
| Retrieval | 34 | Recall@4 98.53%、HitRate@4 100%、MRR 0.8578、nDCG@4 0.8809、P95 2401.13ms |
| Answer | 37 | citation validity 100%、citation coverage 93.24%、keyword coverage 89.19%、scope compliance 94.59%、P95 706.69ms |

这些数字只说明当前本地冻结数据集上的能力，README 已正确说明它们来自 dirty worktree，不能直接当作生产 SLO。尤其需要注意：

- Retrieval P95 约 2.4 秒，若该链路进入租客同步请求或高并发评估，会明显影响体验和线程池占用。
- Citation validity 100% 不代表 citation coverage 100%；仍有部分回答没有覆盖所有应引用的事实。
- Scope compliance 94.59% 表明应优先建设领域分类、租赁法意图 allow-list、负例集和生成前拒答，而不是只继续优化模型。
- 质量报告必须绑定数据集 hash、评估器 hash、模型/embedding/reranker 版本和配置快照，避免不同版本结果不可比较。

## 10. 分阶段改进路线

### P0：上线前必须完成

- 补充多实例、进程重启、租约过期、最大重试和线程池拒绝场景测试，验证当前数据库持久化任务与租约模型；达到吞吐上限后再评估 MQ/outbox。
- 完善 Agent 绑定的租户、会话类型、合同和评估 job 关联校验。
- 让 Agent 问答显式接收并传递 `regionCode/asOfDate`，与评估使用同一法规时点策略。
- 修复干净构建和测试发现问题；CI 必须使用 clean workspace，禁止只依赖历史 `target` 产物。
- 禁止默认数据库/Redis/Nacos 密码进入生产；为 secret、合同正文、聊天内容和 trace 加密/脱敏/审计策略。
- 为外部调用设置连接、读取、总耗时和重试上限，避免级联阻塞。

### P1：稳定性和可运营性

- 增加任务、阶段、外部依赖、降级、法规版本和 AI 质量指标；接入健康检查和告警。
- 增加数据库一致性扫描、孤儿记录清理、trace/evidence 归档和数据保留策略。
- 为法规 source/chunk 增加 embedding model/version、内容版本和向量同步状态。
- 通过批量加载、分页、Milvus 优先查询和受限缓存，避免 MySQL embedding 全量进入 JVM。
- 增加 API 限流、上传并发限制、RAG 查询成本预算以及轮询退避。
- 建立 source-verified 冻结集的发布门禁：citation validity、coverage、scope compliance、P95 latency 均设阈值。

### P2：演进方向

- 将 `service` 包按领域拆成 `contract`、`assessment`、`knowledge`、`agent`、`evidence`、`integration` 等概念边界，降低跨域修改冲突。
- 将规则发现、证据定位、法规检索、回答生成抽象为可观测的 pipeline stage，支持逐阶段重跑。
- 为 OCR、法规版本发布、人工复核和律师转接预留异步工作流。
- 完善 SSE 的连接上限、心跳、断线重连、网关超时和多实例路由；保留普通查询作为兜底。

## 11. P0 并发链路整改方案（参考 Chat 模块）

### 11.0 本轮已落地的第一阶段改造

本轮先处理已经被压测证实的 SSE 线程池单点：

- 删除原来“每条 SSE 连接占用一个长循环执行线程”的实现；
- 改为共享 `legalSseScheduler`，连接任务只执行短时状态读取和事件写出；
- 增加 `legalSseConnectionLimiter`，默认最大 2000 条连接，避免实例被无限连接拖垮；
- `legal.async.*`、`legal.sse.*` 全部支持环境变量配置；
- 保留任务查询接口和 SSE 120 秒超时，连接结束、异常和超时都会释放连接配额；
- 评估线程池增加优雅停机、拒绝策略和启动日志，避免容量被隐式使用。

本轮验证：Legal 聚合构建成功，定向测试 **10/10 通过**，覆盖评估服务、上下文快照、trace、PDF/DOCX（Apache Tika）解析和文档解析服务。该改造解决的是 SSE 连接与业务线程耦合问题；事件总线、Outbox 和完整 Agent 异步问答仍按后续 P0 阶段实施。

### 11.0.1 本轮第二阶段已落地：Agent 问答持久化异步任务

在保留原有 `POST /legal/agent/answer` 兼容性的前提下，新增异步任务入口：

- `POST /legal/agent/tasks`：只完成租户鉴权、`requestId` 幂等落库和任务入队；
- `GET /legal/agent/tasks/{taskId}`：查询 `PENDING/RUNNING/COMPLETED/FAILED` 状态和最终回答；
- 新增 `legal_agent_task` 表，保存租户、Agent 会话、问题、法规时点、租约、尝试次数、结果和错误信息；
- 使用独立 `legalAgentExecutor`，默认 `4/16/200`（核心线程/最大线程/队列）并发配置；
- Worker 通过条件更新领取任务，使用 `lease_owner + lease_until` 防止重复执行；
- 问答结果以 JSON 持久化，服务重启后仍可查询，不再依赖同步 HTTP 请求线程保持结果；
- 请求按 `tenant_id + request_id` 幂等，重复提交返回原任务，不重复执行。

本阶段验证：新增 V28 数据库迁移已在本地开发库执行，Legal 聚合构建成功，定向测试 **12/12 通过**。当前阶段仍是“数据库持久化 + 本地异步 Worker”，尚未接入 RabbitMQ Outbox、事件广播和 Agent 问答 SSE；下一阶段将补齐可靠投递、跨实例消费和结果事件路由。

### 11.0.2 本轮第三阶段已落地：RabbitMQ + Outbox 可靠投递

为避免本地 `@Async` 线程池成为唯一调度入口，本阶段新增可靠投递链路：

- 新增 `legal_agent_task_outbox` 表，任务创建和 Outbox 记录在同一个事务中写入；
- 新增 Legal Agent RabbitMQ exchange、durable queue、DLX 和 DLQ；
- Outbox Publisher 按批量扫描、原子 claim、RabbitMQ 发布、成功确认、指数退避和最大重试处理；
- Outbox 支持处理中超时 reclaim，避免实例宕机后永久卡在 `PROCESSING`；
- RabbitMQ Consumer 收到任务后通过 `taskId` 幂等执行，任务状态使用条件更新，重复消息不会重复生成最终结果；
- Agent 任务增加过期租约恢复，实例异常退出后由定时任务重新置回 `PENDING`；
- 增加独立消费者并发度、Outbox 批量、轮询周期、重试次数和处理超时配置；
- RabbitMQ 不可用时，任务仍保留在 MySQL Outbox 中，恢复后由 Publisher 补发。

本阶段验证：V29 迁移已在本地开发库执行；RabbitMQ 容器连接、交换机/队列声明和服务启动验证通过；Legal 聚合定向测试 **12/12 通过**。当前已具备“数据库任务 + Outbox + RabbitMQ Consumer”的可靠执行基础，但 Agent 结果事件尚未广播到 SSE，下一阶段继续实现事件表/Redis Streams 和断线重放。

### 11.1 当前链路为什么不合格

本次压测暴露的问题不是单纯的 RAG 算法慢，而是 Legal Agent 仍然把多个重操作放在同步 HTTP/SSE 请求链路中：

```text
HTTP 请求
  └─ MySQL 读取合同/上下文
  └─ Chat/House 远程调用
  └─ RAG 召回与重排
  └─ AI 生成
  └─ Redis/Chat 回写
  └─ HTTP 返回
```

在 100 并发、1000 请求的本地压测中，当前内部 RAG 检索 P95 约 **4.86 秒**，答案链路 P95 约 **1.68 秒**；200 并发时两条链路 P95 分别约 **2.67 秒**和 **2.77 秒**。SSE 当前使用固定最大 8 个业务线程，100 个连接只成功建立 8 个，其余连接超时。这种模型会把数据库、远程依赖、RAG 和长连接共同挤压到同一个服务实例，不能作为生产级 Agent 并发链路。

### 11.2 Chat 模块可复用的核心思想

Chat 模块的高并发链路不是让每个连接独占业务线程，而是将“连接承载、业务处理、可靠投递、持久化和节点路由”拆开：

| Chat 设计 | Legal Agent 对应改造 |
| --- | --- |
| Netty EventLoop 承载大量 WebSocket 连接 | SSE 使用异步非阻塞连接管理器，连接不占用评估/Agent 业务线程 |
| 独立 `businessExecutor` 处理业务帧 | 独立 Agent 请求执行池，按阶段设置并发预算 |
| 连接级有界队列、写缓冲高低水位 | SSE 事件按租户/任务设置有界缓冲，慢客户端丢弃中间进度、保留最终状态 |
| Redis Hash 维护用户到节点的路由 | Redis 维护 `jobId -> instanceId/connection set`，多实例下由事件订阅将结果路由到本地连接 |
| MySQL 事务提交后写 RabbitMQ/outbox | Agent 任务创建先落库，事务提交后写任务 outbox，由消费者异步执行 |
| 客户端 ACK、超时重推、DLQ | Agent 结果事件使用 `eventId/sequence`，支持断线重放、幂等消费和失败补偿 |
| Redis INCR 分配会话序号 | Agent 对话和评估事件使用 Redis/DB 原子序号，避免热点行锁 |
| 物理分片和连接池治理 | 合同/任务/trace 按租户或 job 分区，数据库连接池、RAG 并发池和外部调用池分别限流 |

这里不建议直接把法律助手改成租客/房东 WebSocket。Chat 的 Netty 适合高频双向消息；Legal Agent 的主要问题是长耗时任务和外部依赖阻塞，应复用 Chat 的“事件驱动、异步投递、连接与业务解耦”思想，保留 HTTP 创建任务和 SSE 订阅结果的产品形态。

### 11.3 目标链路

```text
租客请求 Agent 问答
        │
        ▼
Legal API（只做鉴权、幂等、快速入队）
        │  PENDING + request_id + context_snapshot
        ▼
legal_agent_task_outbox
        │  事务提交后发布 RabbitMQ legal.agent.task
        ▼
Agent Worker 集群
        │
        ├─ 合同/聊天/房源上下文并行读取
        ├─ RAG 检索池（词法、向量、重排分阶段限流）
        ├─ AI 生成池（独立超时、熔断、降级）
        ├─ 结果和 trace 幂等落库
        └─ 发布 legal.agent.event(jobId, eventId, sequence)
                │
                ├─ Redis Pub/Sub 或 Redis Streams：多实例事件路由
                ├─ SSE Connection Manager：仅负责连接和写出
                └─ 断线后按 sequence 从 MySQL/Redis 重放
```

问答接口改为：

1. `POST /legal/agent/tasks`：校验租户、Agent 绑定和幂等键，目标是 P95 小于 100 ms，只返回 `taskId`。
2. `GET /legal/agent/tasks/{taskId}`：查询最终状态，作为无 SSE、断线或网关不支持流式时的兜底接口。
3. `GET /legal/agent/tasks/{taskId}/events`：SSE 订阅，发送 `accepted/running/stage/result/failed` 事件；SSE 不执行 RAG、不轮询数据库、不占用 Agent Worker 线程。
4. Agent Worker 完成后通过事件总线通知 SSE；事件消费失败不影响任务结果，连接恢复后按 `Last-Event-ID` 重放。

合同解析和风险评估也采用同一模型：上传接口只负责文件归档、SHA-256 幂等和任务创建，Apache Tika 解析、block 切分、规则计算和 RAG 均由 Worker 执行。这样合同解析吞吐可以通过增加 Worker 横向扩展，不会阻塞 API 和 SSE。

### 11.4 必须落地的组件和数据结构

#### A. 任务 Outbox

新增 `legal_agent_task_outbox`，至少包含：

- `id、task_id、event_type、payload、status、retry_count、next_retry_at、claim_token、created_at、updated_at`；
- 唯一键 `uk_task_event(task_id,event_type)`，保证入队幂等；
- `status=0/1/2/3` 表示待发送、处理中、已完成、终态失败；
- 使用与 Chat outbox 相同的“原子 claim、超时 reclaim、指数退避、终态补偿、定期清理”模型。

#### B. Agent 任务表

现有 `legal_assessment_job` 保留并补充通用字段：

- `task_type、request_id、trace_id、priority、stage、progress、lease_owner、lease_until、attempt_count、next_run_at`；
- 唯一键按 `tenant_id + request_id` 幂等；
- 任务结果只允许一个终态写入，重试必须使用条件更新；
- 每次阶段切换记录 `stage_started_at/stage_finished_at/error_code`，避免只看到总耗时而无法定位瓶颈。

#### C. RAG 分阶段资源池

禁止所有 Agent 请求共用一个无限增长的线程池，建议拆成：

```text
agent-api-pool       只处理鉴权、入队和查询
agent-worker-pool    合同/上下文组装和任务编排
rag-retrieve-pool    MySQL/Milvus 召回
rerank-pool          BGE 或其他重排模型
ai-generate-pool     DashScope/模型生成
event-publish-pool   结果事件发布
```

每个池都必须有最大并发、队列上限、拒绝策略和指标。队列满时返回可识别的 `BUSY/RETRY_LATER`，不能让 Tomcat 请求线程无限等待。RAG 默认先走 Milvus/专用索引；MySQL embedding 回退只允许小批量、分页和受控并发。

#### D. SSE Connection Manager

将当前 Controller 中“每个连接启动一个循环任务并每 500 ms 查一次 MySQL”的实现替换为：

- 连接建立时只注册 `jobId、tenantId、instanceId、lastEventId`；
- 任务状态变化时主动发布事件，不做固定频率轮询；
- 每个实例维护有界连接表和每租户连接上限；
- 心跳由单一调度器批量发送，不为每条连接创建线程；
- 客户端慢写时触发背压，丢弃中间 `progress`，必须保留 `result/failed`；
- 实例重启或连接迁移时根据 `Last-Event-ID` 从事件表/Redis Streams 补发；
- 保留 `/legal/assessments/{jobId}` 查询接口作为最终一致性兜底。

目标是让 SSE 连接数主要受网络、文件描述符和事件缓冲限制，而不是受 8 个 Java 业务线程限制。

### 11.5 超时、重试和降级预算

每个外部依赖都要有明确预算，不能使用统一的长超时：

| 阶段 | 建议超时 | 失败处理 |
| --- | ---: | --- |
| Chat/House/File 上下文 | 300–800 ms | 使用最近快照；标记 `context_degraded` |
| 词法 RAG | 200–500 ms | 返回词法结果或无证据 |
| Milvus 向量召回 | 300–800 ms | 回退词法，不阻塞整条任务 |
| Reranker | 500–1000 ms | 使用融合排序结果 |
| AI 生成 | 3–8 s | 规则/抽取式回答，记录 `ai_degraded` |
| SSE 写出 | 单连接非阻塞 | 慢连接断开，结果可通过查询接口获取 |

重试只允许用于连接超时、临时网络错误和明确的 5xx；参数错误、权限失败、合同不存在和证据缺失不得重试。所有重试必须带 requestId、traceId 和 attemptCount，避免外部服务被放大流量冲击。

### 11.6 目标验收指标

整改完成后不再以 20 并发/200 请求作为结论，至少执行以下分层压测：

| 场景 | 压测规模 | P0 验收目标 |
| --- | ---: | ---: |
| Agent 入队接口 | 500 并发、5000 请求 | P95 < 200 ms，错误率 < 0.1% |
| RAG 检索 Worker | 100 并发任务、5000 查询 | P95 < 800 ms，P99 < 1500 ms |
| Agent 端到端问答 | 100 并发、2000 任务 | 入队 P95 < 200 ms；完成时间 P95 < 8 s；无重复终态 |
| SSE 长连接 | 1000/2000 同时连接 | 建连成功率 ≥ 99.9%，连接不占用 Worker 线程 |
| 评估任务 | 1000 个任务、多实例 | 吞吐可线性扩展；重复执行率为 0 |
| Worker 故障恢复 | 执行中杀死 1 个实例 | 租约接管延迟 P95 < 30 s，最终成功/失败状态可查询 |
| RabbitMQ/Redis 短暂故障 | 持续写入并恢复 | Outbox 不丢任务、不重复产生最终结果，积压可清零 |

压测报告必须同时记录 CPU、堆内存、GC、Tomcat/Netty 线程、各资源池队列、MySQL 连接池、慢 SQL、Redis、RabbitMQ ready/unacked、Milvus 和 AI 调用耗时。只记录接口 P95 而不记录队列和依赖指标，不能证明链路具备并发能力。

### 11.7 实施顺序

1. **P0-1：先拆同步链路。** Agent 问答和合同解析统一返回 taskId，API 与 Worker 解耦，保留现有同步接口作为兼容层。
2. **P0-2：补齐 Agent outbox + RabbitMQ 消费者。** 复用 Chat 的 claim/retry/reconcile 思路，先保证任务不丢和结果幂等。
3. **P0-3：重构 SSE。** 连接注册、事件发布、断线重放和慢客户端背压独立实现，移除每连接 500 ms 数据库轮询。
4. **P0-4：拆分 RAG/AI 资源池。** 增加并发预算、超时、熔断、降级和队列指标。
5. **P0-5：多实例故障演练。** 验证实例宕机、RabbitMQ 暂停、Redis 重启、Milvus 不可用、AI 超时和数据库连接池耗尽。
6. **P0-6：使用真实合同、真实 Agent 会话和冻结法规集重新压测。** 当前临时种子数据只能用于链路诊断，不能作为最终性能或简历数据。

本方案的核心判断是：Legal Agent 应复用 Chat 的“事件驱动 + 有界资源池 + 持久化 Outbox + 跨实例路由 + ACK/重放/补偿”架构能力，但不应照搬 Chat 的 WebSocket 业务模型。对于法律分析，任务执行和证据结果可靠性优先于高频双向消息；HTTP 负责快速入队，Worker 负责分析，SSE 只负责事件传输，查询接口负责最终兜底。

### 11.0.3 本轮第四阶段已落地：持久化 Agent 事件流与 SSE 断线重放

本阶段解决了 SSE 连接依赖实例内存和固定轮询的问题，补齐了跨实例通知、断线恢复和最终结果可靠传输能力：

1. **事件持久化。** 新增 `legal_agent_task_event` 表（V30），按 `tenant_id + task_id + id` 建立顺序查询索引。任务完成或失败时写入 `result/failed` 事件，MySQL 事件表作为断线重放的事实源。
2. **Redis Stream 跨实例通知。** 事件落库后尽力写入 Redis Stream `legal:agent:events`，Redis 只承担跨实例唤醒/通知，不作为唯一数据源；Redis 短暂不可用时不影响任务最终状态和事件重放。
3. **SSE 断线重放。** 新增 `GET /legal/agent/tasks/{taskId}/events`，支持 `Last-Event-ID`，按照事件 ID 增量补发，发送 `result/failed` 后自动完成连接；保留原任务查询接口作为最终一致性兜底。
4. **资源治理。** 复用共享 SSE 调度器、连接信号量和心跳机制，避免每个连接创建独立线程；任务不存在或无权限时在建立连接前拒绝，避免无效连接占用配额。
5. **异常边界修复。** 任务终态更新和事件发布解耦：事件发布失败只记录日志，不会把已经完成的任务反向标记为失败，后续可接入事件补偿机制。

本阶段验证结果：法律模块定向测试 12 个全部通过，包含 Controller、任务链路、Tika 文档解析、PDF/DOCX 解析和 trace 相关测试。当前 SSE 仍以共享调度器进行短周期增量查询，Redis Stream 已完成跨实例通知基础能力；下一阶段可进一步改为 Redis Stream 订阅驱动，减少空闲连接的固定查询开销，并补充事件补偿、慢连接背压和连接级指标。

### 11.0.4 本轮第五阶段已落地：Redis Stream 唤醒、事件补偿与 SSE 背压

本阶段将上一阶段的固定周期查询改为事件驱动：

1. **Redis Stream 订阅唤醒。** 每个 SSE 连接建立时只注册任务级监听器，事件到达后由共享 `StreamMessageListenerContainer` 唤醒对应连接；连接不再每 500ms 查询数据库，心跳单独按 15 秒发送。
2. **可靠补偿。** 事件表增加 `redis_published` 标记（V31）。Redis 写入成功后更新标记，失败事件由定时补偿任务批量重发；MySQL 仍是唯一重放事实源，重复通知不会导致重复终态。
3. **慢连接保护。** 事件通知只触发一次有界增量刷新，每次最多发送 50 条事件；发送期间的重复唤醒合并为一次刷新，终态事件始终优先通过查询重放保证送达，避免 Redis listener 被慢客户端长期阻塞。
4. **可观测性。** 增加事件发布成功、发布失败、补偿次数和 Stream 唤醒计数，便于结合 Prometheus 观察 Redis 故障、通知积压和跨实例 SSE 活跃度。

本阶段的边界：`SseEmitter.send` 仍是同步写出，慢客户端会占用一个共享调度器执行槽，但不会占用 Agent Worker；下一阶段可引入每连接有界事件队列和专用写出执行器，进一步隔离慢客户端，并补充真实多实例 SSE、Redis 故障恢复和 1000+ 长连接压测。

### 11.0.5 本轮第六阶段已落地：SSE 有界队列与独立写出资源池

本阶段进一步隔离慢客户端对 Redis 订阅线程和共享调度器的影响：

1. **每连接有界唤醒队列。** 每条 Agent SSE 连接维护容量为 100 的事件唤醒队列；队列满时合并重复唤醒，仅保留最新通知，事件内容仍从 MySQL 事件表按游标重放，不会因队列满丢失 `result/failed`。
2. **独立 SSE 写出执行器。** 新增 `legalSseWriterExecutor`，默认 8/32 线程、2000 队列容量。Redis Stream listener 只负责入队和调度，实际 `SseEmitter.send` 在独立写出池执行，避免慢连接阻塞跨实例通知。
3. **写出背压与拒绝。** 写出任务使用 `AbortPolicy`，资源池耗尽时主动结束受影响连接并保留查询接口兜底，避免 Tomcat、Redis listener 或 Agent Worker 被无限反压。
4. **新增性能指标。** 增加 SSE 唤醒队列溢出、写出任务拒绝和写出耗时指标，可用于压测时识别慢客户端、写出池不足和网络抖动。

本阶段验证结果：法律模块定向测试 12 个全部通过，且 Legal 模块打包成功。下一阶段应在真实多实例环境执行 1000/2000 SSE 长连接、慢客户端、Redis 短暂不可用和 writer pool 饱和压测，校准 writer pool 大小、单连接队列容量及连接超时。

### 11.0.6 本轮第七阶段已落地：高并发压测脚本与验收口径

本阶段补齐可重复的高并发验证工具：

1. `legal-agent-load.js` 默认覆盖 500 入队请求/秒和 200 个端到端 VU，采集入队 P95/P99、端到端 P95/P99、SSE 失败和入队失败，不再使用 20 并发/200 请求作为容量结论。
2. `legal-agent-sse-scale.js` 要求使用预创建的待处理任务 ID，默认支持 1000 长连接并可提升到 2000，避免把 AI 生成耗时混入 SSE 连接容量测试。
3. 压测 README 明确要求同时采集 writer pool 队列与拒绝、SSE 唤醒队列溢出、Redis Stream、MySQL 连接池、RabbitMQ、Agent Worker、RAG 和模型依赖指标。

脚本已完成 k6 配置化，但本轮未伪造性能数字；只有在提供真实租户 Token、待处理任务池和独立压测机后，才将实测 P95/P99、吞吐、错误率和故障恢复时间写入报告或简历。

补充说明：普通 k6 HTTP 请求需要等待 SSE 响应结束后才能得到结果，不能准确代表长连接已建连。因此新增 `legal-agent-sse-scale.mjs`，使用 Node 原生流式 `fetch` 在收到响应头时记录建连延迟，并持续读取心跳；SSE 长连接数据应以该脚本输出为准。

### 11.0.7 本轮第八阶段已完成：本地真实租户压测结果

本轮使用本地开发环境构造了专用压测身份：租户 ID `900001`，登录态按项目 `login:token:<token>` Redis 格式写入，Token 未写入代码或报告；同时在数据库预创建 1000 个 `PENDING` Agent 任务作为 SSE 长连接池。压测完成后已删除全部测试任务、Outbox、事件和临时 Token。

#### SSE 长连接结果

使用 Node 原生流式 `fetch`，直连本地 Legal 服务 `18086`，目标 1000 条连接，单连接保持约 45 秒：

| 指标 | 实测结果 |
| --- | ---: |
| 目标连接数 | 1000 |
| HTTP 200 建连数 | 1000 |
| 建连失败数 | 0 |
| 建连成功率 | 100% |
| 建连 P95 | 15.685 s |
| 建连 P99 | 15.734 s |
| 连接保持时长 | 约 62.8 s |

结果说明：成功率达到预期，但 P95/P99 约 15.7 秒，说明当前 Servlet SSE 客户端通常要等首个心跳或数据刷新后才观测到响应头，不能作为低延迟建连指标。下一阶段应在连接建立后立即发送一次 `connected`/`ready` 事件，再单独发送心跳，重新测量真实建连延迟。

#### Agent 入队结果

使用 k6 目标 500 req/s、持续 30 秒，Legal 服务直连：

| 指标 | 实测结果 |
| --- | ---: |
| 目标请求量 | 15000 |
| 实际完成请求 | 10946 |
| 实际吞吐 | 约 324.2 req/s |
| dropped iterations | 4055 |
| HTTP 错误率 | 0% |
| 入队 P95 | 6.804 s |
| 入队 P99 | 约 7.46 s |

本次未达标：2,000 VU 上限不足以承载约 4.58 秒平均响应时间，k6 在目标速率下发生 VU 不足；同时 P95 明显超过 200ms。该结果只能证明当前本地环境在该配置下的瓶颈，不能写成产品容量或简历性能结论。原始数据保存在 `deploy/perf/results/legal-agent-sse-scale-2026-10-22.json` 和 `deploy/perf/results/legal-agent-enqueue-2026-10-22.json`。

#### 链路边界

本轮 Gateway 验证因本地 Nacos gRPC 9848 端口不可用未完成，性能数据是直连 Legal 服务，虽然 Redis 中写入了格式正确的开发 Token，但没有测量 Gateway 鉴权和路由耗时。完整链路压测前必须先恢复 Nacos 服务发现，再通过 Gateway 重跑同样场景。

### 11.0.8 本轮第九阶段：SSE 首包确认优化

针对上一轮 SSE 建连 P95/P99 约 15.7 秒的问题，连接建立后立即发送 `connected: ok` 事件并刷新响应头，事件重放、心跳和终态结果仍由后续异步流程处理。这样客户端可以区分“HTTP 请求已到达”和“应用层 SSE 已真正建立”，避免把首个 15 秒心跳等待误计为建连延迟。

本阶段代码已完成，需使用同一批次的 1000 长连接脚本复测，验收目标为建连成功率不低于 99.9%、P95 < 1 秒、P99 < 3 秒；复测结果将在本节继续追加。

复测结果（2026-10-22，本地直连 Legal 服务）：

| 指标 | 优化前 | 增加 `connected` 事件后 |
| --- | ---: | ---: |
| 目标连接数 | 1000 | 1000 |
| 建连成功率 | 100% | 100% |
| 建连 P95 | 15.685 s | 26.37 ms |
| 建连 P99 | 15.734 s | 167.07 ms |

本轮已达到建连 P95/P99 验收目标，证明上一轮瓶颈主要是首个 SSE 数据帧发送时机，而不是连接数信号量或 writer pool 容量。原始复测数据：`deploy/perf/results/legal-agent-sse-scale-ready-2026-10-22.json`。Gateway 仍需在 Nacos gRPC 9848 恢复后补测。

### 11.0.9 本轮第十阶段：Gateway 鉴权与 SSE 完整链路冒烟

本阶段补齐并验证了本地完整链路：`租户 Token -> Gateway AuthFilter -> Nacos 服务发现 -> Legal Agent -> SSE`。

#### 环境修复

- Nacos 启动失败根因是 Compose 默认账号 `anyujiadev` 与本地 MySQL 运行账号不一致，已按仓库运行时修复配置切换为 `appdev`。
- Nacos 重建时因 Compose 项目名变化接入了错误网络，已将其接入实际运行的 `frameworkjava_frameworkjava_network`。
- 已确认 Nacos HTTP 8848 与 gRPC 9848 均可用，Legal 服务成功注册为 `anyujia-legal`，Gateway 成功注册为 `anyujia-gateway`。
- 本地 Nacos 数据库原先没有 `frameworkjava-dev` 命名空间和 Gateway 配置，本阶段补充了仅用于开发验证的命名空间及 `/legal/** -> lb://anyujia-legal` 路由。

#### 完整链路结果

使用临时真实格式开发 Token 写入 Redis `login:token:<token>`，Token 仅用于本次验证，完成后已删除；租户身份为 `userId=900001,userType=1,userFrom=app`。

| 验证项 | 实测结果 |
| --- | ---: |
| Gateway 端口 | 18080，启动成功 |
| Nacos gRPC | 9848 连接成功 |
| Gateway 鉴权 | 通过 |
| Legal 任务提交 | HTTP 200，任务 ID `2113179781457973249` |
| Gateway 转发到 Legal | 通过 |
| SSE 响应类型 | `text/event-stream` |
| SSE 首事件 | `event:connected / data:ok` |
| 任务终态事件 | 收到 `event:failed`，原因为测试会话不存在，链路本身正常返回 |

本阶段证明此前的 Gateway 失败来自本地基础设施配置，而不是 Agent SSE 实现。测试任务、Outbox、事件和 Token 均已清理；临时 Gateway/Legal 容器已移除，Nacos 保留为已修复状态。下一阶段应在已准备的真实租户测试数据和有效会话上下文下，通过 Gateway 重跑高并发入队、SSE 长连接及任务恢复压测，并记录 Gateway 额外耗时。

### 11.0.10 本轮第十一阶段：Gateway 高并发 SSE 与入队复测

本阶段使用有效聊天会话 `910000000000000031` 作为上下文，临时租户 Token 仅写入本地 Redis，未进入代码或报告；压测结束后已删除 47,865 个测试任务、Outbox、事件和 Token，并移除临时 Gateway/Legal 容器。

#### Gateway SSE 长连接

通过 `18080` Gateway，1000 个任务以 10 秒斜坡建立连接并保持 15 秒：

| 指标 | 实测结果 |
| --- | ---: |
| 目标连接数 | 1000 |
| HTTP 200 | 1000 |
| 失败数 | 0 |
| 成功率 | 100% |
| 建连 P95 | 22.75 ms |
| 建连 P99 | 43.30 ms |
| 收到心跳的连接数 | 1000 |

原始结果：`deploy/perf/results/legal-agent-sse-gateway-ready-2026-10-22.json`。该结果覆盖 Gateway 鉴权、路由、Nacos 服务发现、Legal SSE writer 和心跳链路。

#### Gateway 入队吞吐

以 500 req/s 目标速率持续 2 分钟，最大 VU 2000：

| 指标 | 实测结果 |
| --- | ---: |
| 完成请求 | 46865 |
| 实际吞吐 | 379.11 req/s |
| HTTP 错误率 | 0% |
| dropped iterations | 13136 |
| 入队 P95 | 6.626 s |
| 入队 P99 | 约 7.39 s |

本轮仍未达到 200ms 入队延迟目标，但所有已完成请求均通过 Gateway 鉴权并返回任务 ID；主要瓶颈是本地环境在 2000 VU 上限下的请求排队、数据库写入和异步任务竞争，不能将该结果写成产品容量承诺。原始结果：`deploy/perf/results/legal-agent-enqueue-gateway-2026-10-22.json`。下一步应拆分 Gateway 鉴权、任务数据库写入和 Outbox 发布耗时，并针对数据库连接池与入队事务做专项优化。

### 11.0.11 本轮第十二阶段：入队数据库争用优化

本阶段针对入队 P95 做了两项处理：

- 将 Legal 数据库 Hikari 连接池参数改为可配置：`LEGAL_DB_MAX_POOL_SIZE`、`LEGAL_DB_MIN_IDLE`、连接超时和校验超时；默认最大连接数保持 10，避免在共享本地 MySQL 上盲目扩大连接池。
- 优化幂等写入路径：新任务直接依赖 `(tenant_id, request_id)` 唯一索引插入，不再先执行一次查询；只有发生重复键时才回查既有任务，减少正常请求的一次数据库往返。

#### 连接池实验结论

将最大连接数临时扩大到 30 后，在 500 req/s 目标、2000 VU 上限下仅完成 4028 个请求，HTTP 错误率 4.71%，入队 P95 24.42 秒，并产生 10973 个 dropped iterations。该实验说明当前本地 MySQL/容器资源已出现数据库争用，扩大连接池反而放大排队和失败，因此默认值已恢复为 10，仅保留运行时可调能力。该失败实验未作为容量结论使用。

#### 代码验证

- Legal 模块 Maven 构建成功。
- 12 个法律模块定向测试全部通过，失败数 0。
- 测试任务、Outbox、事件、Token 和临时容器均已清理。

下一步应在 Gateway 容器稳定在线的前提下，使用本次幂等写入优化版本重新跑 500 req/s 基线，并增加 Controller、Service、数据库插入和 Outbox 插入的分段 Timer，确认 P95 改善来自查询减少，而不是测试环境波动。

### 11.0.12 本轮第十三阶段：入队链路分段耗时观测

本阶段完成入队链路埋点，为下一轮压测提供可拆分的耗时证据：

- Legal Controller：`legal.agent.submit{stage=controller}`。
- 任务表插入：`legal.agent.submit{stage=task_insert}`。
- Outbox 插入：`legal.agent.submit{stage=outbox_insert}`。
- Legal Service 总体处理：`legal.agent.submit{stage=service}`。
- Gateway Redis Token 查询：`gateway.auth.redis`。
- Gateway 完整认证及转发前处理：`gateway.auth.total`。

埋点使用固定 stage/name，不引入用户、Token、任务 ID 等高基数标签；Gateway 的 MeterRegistry 在单元测试环境缺失时安全降级，不影响认证逻辑。

#### 验证结果

- Legal 模块构建成功。
- Gateway 模块构建成功。
- Gateway `AuthFilterTest`：1 个测试通过。
- Legal 定向测试：12 个测试通过，失败数 0。

下一步重新启动稳定的 Gateway/Legal 压测环境，执行 500 req/s 入队基线，并从 Actuator/Prometheus 读取上述 Timer 的 P95/P99，完成“Gateway 鉴权 vs 任务插入 vs Outbox 插入”的瓶颈归因。

### 11.0.13 本轮第十四阶段：分段耗时实测与瓶颈归因

本阶段使用刚构建的 Gateway/Legal JAR 启动独立压测容器，开启 Actuator `metrics`，通过有效租户 Token 和有效聊天会话执行 500 req/s、持续 30 秒的入队压测。

#### 端到端结果

| 指标 | 实测结果 |
| --- | ---: |
| HTTP 请求数 | 5411 |
| HTTP 错误率 | 6.65% |
| 实际完成吞吐 | 149.76 req/s |
| dropped iterations | 9591 |
| 入队 P95 | 14.59 s |
| 入队 P99 | 未达到稳定采样条件，需先解决本地资源争用 |

本轮未达 500 req/s，结果不能作为产品容量结论；但分段指标已经足以确认瓶颈位置。

#### Actuator 分段指标

| 指标 | 样本数 | 平均耗时 | 最大耗时 |
| --- | ---: | ---: | ---: |
| Gateway Redis Token 查询 | 5411 | 9.54 ms | 340.39 ms |
| Gateway 鉴权处理 | 5411 | 9.82 ms | 340.73 ms |
| Legal task insert | 5051 | 12.95 ms | 765.65 ms |
| Legal outbox insert | 5051 | 9.58 ms | 534.04 ms |
| Legal service | 5051 | 24.15 ms | 1.07 s |
| Legal controller | 5411 | 1.087 s | 3.73 s |

结论：Gateway 鉴权和两次数据库插入均不是本轮秒级 P95 的主要原因，主要等待集中在 Legal Controller 请求处理外围和本地容器/数据库资源排队；之前把下游等待混入 Gateway `auth.total` 的问题已修正。Timer 当前通过 Actuator 暴露 count、total、max，P95/P99 继续由 k6 端到端结果采集。

原始结果：`deploy/perf/results/legal-agent-enqueue-instrumented-final-2026-10-22.json`。下一步应采集 JVM 线程池、Tomcat 工作线程、Hikari 活跃连接/等待线程和 MySQL 慢查询，继续确认 Controller 外围排队的具体来源。

### 11.0.14 本轮第十五阶段：运行时资源观测与数据库快照

本阶段补齐 Legal 异步线程池的运行时指标，并检查数据库当前资源状态，为下一轮压测期间采样做准备。

#### 新增运行时指标

Legal 的 assessment、agent、sse-writer 三个线程池均暴露以下低基数 Gauge：

- `legal.executor.active{executor=...}`：当前活跃线程数。
- `legal.executor.pool.size{executor=...}`：当前线程池线程数。
- `legal.executor.queue.size{executor=...}`：当前排队任务数。
- `legal.executor.queue.remaining{executor=...}`：队列剩余容量。

同时保留 Spring Boot Actuator 的 Tomcat、Hikari 指标入口，压测时可读取请求线程、数据库连接池活跃连接及等待线程，和上一阶段的入队分段 Timer 对齐。

#### MySQL 当前快照

本次采样发生在压测容器已停止后的空闲状态，仅用于确认环境基线，不能作为高并发容量结论：

| 指标 | 当前值 |
| --- | ---: |
| Threads_connected | 40 |
| Threads_running | 2 |
| Threads_created | 71 |
| Threads_cached | 25 |
| Slow_queries | 3 |
| Queries | 2,031,633 |
| max_connections | 2,000 |
| slow_query_log | OFF |
| long_query_time | 10 s |

空闲快照显示当前没有明显的 MySQL 执行堆积，但由于慢查询日志关闭且采样不在压力窗口内，不能据此排除压测期间的连接等待或锁等待。下一轮应在压力运行期间同步读取 `hikaricp.*`、`tomcat.*`、`legal.executor.*` 和 MySQL `Threads_running`，并使用临时、隔离的开发环境慢查询配置或 Performance Schema 采样，避免修改生产数据库全局配置。

#### 本阶段验证

- Legal 模块构建成功。
- Legal 定向测试 5 个通过，失败数 0。
- 线程池指标代码已随 Legal JAR 编译进入运行版本。
- 本阶段未将空闲数据库快照包装为压测结果；下一步是“资源指标与 500 req/s 入队压测同窗采集”，定位 Controller 外围排队究竟来自 Tomcat、Hikari 还是数据库执行。

### 11.0.15 本轮第十六阶段：资源指标与 500 req/s 同窗压测

本阶段使用最新 Legal/Gateway JAR、有效租户 Token 和有效聊天会话，通过 Gateway 执行 `SCENARIO=enqueue`，目标 500 req/s，持续 30 秒；同时每 2 秒采集 Legal 线程池、Hikari 和 MySQL 状态。

#### 压测结果

| 指标 | 实测结果 |
| --- | ---: |
| 入队请求样本 | 4,139 |
| HTTP 200 | 3,712 |
| HTTP 500 | 412 |
| 无状态响应/连接异常 | 15 |
| dropped iterations | 10,862 |
| 入队平均耗时 | 14.75 s |
| 入队 P50 | 14.80 s |
| 入队 P95 | 21.65 s |
| 入队 P99 | 24.17 s |
| 最大耗时 | 27.69 s |

该结果仍未达到 500 req/s 的稳定有效吞吐，不能作为产品容量结论；但本轮资源采样已经足够定位主要瓶颈。

#### 资源证据与瓶颈归因

- Hikari `connections.active` 达到连接池上限 10。
- Hikari `connections.pending` 峰值 171，说明请求大量等待数据库连接。
- 压测结束后 `hikaricp.connections.timeout` 累计为 421。
- MySQL `Threads_running` 压测窗口内约为 3–14，未出现等同于请求并发量的执行线程堆积；当前 `Slow_queries` 仍为 3，但慢查询日志处于关闭状态，不能用它证明没有锁等待。
- Legal `assessment`、`agent`、`sse-writer` 线程池队列采样保持 0，说明本轮瓶颈不是 Agent 异步线程池或 SSE 写线程池。
- 分段 Timer：Controller 约 1.47 s/次，task insert 约 24.57 ms/次，Outbox insert 约 15.13 ms/次，Service 约 40.46 ms/次。Controller 与业务分段的差值与 Hikari 等待及请求外围排队一致。

结论：当前 P0 主瓶颈已从“可能的 Controller/容器排队”收敛为“同步入队链路受 Hikari 连接池耗尽影响”。下一阶段应优先做数据库写入背压/连接获取策略和任务入队链路解耦验证，再比较连接池 10、15、20 的有效吞吐、超时率和 P95；不能仅扩大业务线程池。

压测摘要：`deploy/perf/results/legal-agent-resource-sampled-2026-10-22-summary.json`。逐请求原始采样已清理，避免将约 20 MB 的临时明细纳入仓库。

### 11.0.16 本轮第十七阶段：Hikari 连接池 10/15/20 A/B/C 对比

本阶段固定 Gateway 内网链路、有效租户 Token、聊天会话、500 req/s、30 秒入队场景，仅改变 Legal `LEGAL_DB_MAX_POOL_SIZE`，对比数据库连接池容量对有效吞吐和延迟的影响。

#### 对比结果

| 连接池上限 | 实际请求 | HTTP 200 | HTTP 失败 | 有效请求吞吐 | 平均耗时 | P95 | dropped | 连接超时 |
| ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| 10 | 3,822 | 3,296 | 526 | 91.69 req/s | 16.69 s | 21.79 s | 11,179 | 533 |
| 15 | 5,981 | 5,470 | 511 | 161.54 req/s | 9.77 s | 13.64 s | 9,019 | 1,027 |
| 20 | 6,247 | 6,087 | 160 | 162.21 req/s | 9.05 s | 12.07 s | 8,753 | 162 |

#### 结论

- 从 10 提升到 20，HTTP 失败数从 526 降至 160，P95 从 21.79 秒降至 12.07 秒，连接池容量确实是当前瓶颈的一部分。
- 15 档出现较高的连接超时累计值，说明仅观察连接池大小还不够，还需要结合数据库执行、锁等待和容器 CPU 判断；不能简单按“连接池越大越好”配置生产值。
- 20 档已经是本轮最优，但实际完成吞吐约 162 req/s，远低于目标 500 req/s；因此不能宣称 Legal 入队支持 500 req/s。
- 20 档分段 Timer 中 Controller 平均约 1.03 s，而 task insert、Outbox insert、Service 平均约 24.07 ms、19.67 ms、45.95 ms，剩余耗时仍主要来自同步请求排队和数据库连接/事务竞争。

本阶段建议：开发环境可暂以连接池 20 作为后续验证参数，但生产不能直接照搬；下一阶段应增加有界入队背压或将“请求接收”和“数据库任务落库”解耦，避免 Tomcat 请求线程无限等待 Hikari。只有在背压后重新达到稳定吞吐并保持错误率、P95 可控，才进入数据库锁/索引级优化。

对比原始摘要：

- `deploy/perf/results/legal-agent-pool10-2026-10-22.json`
- `deploy/perf/results/legal-agent-pool15-2026-10-22.json`
- `deploy/perf/results/legal-agent-pool20-2026-10-22.json`

### 11.0.17 本轮第十八阶段：入队有界背压验证

针对上一阶段 Hikari 连接池耗尽问题，在 Legal 入队入口增加可配置的 Semaphore admission control：默认最多 10 个请求同时进入同步任务表/Outbox 写入，超出上限立即返回“法律助手入队繁忙，请稍后重试”，并通过 `legal.agent.submit.rejected` 记录拒绝数。该阈值通过 `LEGAL_AGENT_SUBMIT_MAX_IN_FLIGHT` 配置，不改变 Agent 和 SSE 线程池。

#### 背压压测结果

本轮仍使用 500 req/s、30 秒、Gateway 内网链路和连接池 10：

| 指标 | 实测结果 |
| --- | ---: |
| HTTP 请求 | 4,813 |
| HTTP 200 | 1,766 |
| 背压拒绝 | 3,047 |
| 实际完成吞吐 | 111.48 req/s |
| 平均端到端耗时 | 13.26 s |
| P95 端到端耗时 | 19.64 s |
| Hikari pending（结束快照） | 0 |
| Hikari connection timeout | 0 |
| `legal.agent.submit.rejected` | 3,047 |

#### 结果解释

- 背压已经阻止 Hikari 等待继续放大：本轮连接获取超时为 0，说明数据库保护目标达成。
- Legal Controller 分段平均约 211ms，task insert、Outbox insert、Service 平均约 44.32ms、23.51ms、68.83ms；端到端 P95 仍为 19.64 秒，主要差值来自 Gateway/压测客户端在高并发下的排队，不能继续归因于 Hikari。
- 当前拒绝使用统一 `500000` 服务繁忙码，客户端需要按“稍后重试”处理；后续可补充专用 503/限流响应码及 Retry-After。
- 该方案优先保护实例稳定性，但在单机 500 req/s 目标下有效吞吐下降，不能作为容量提升方案；它是防止雪崩的 P0 安全阀。

下一步对同一版本做 Legal 直连与 Gateway 链路对照压测，分别采集 Legal Controller、Gateway 转发、客户端连接建立耗时，确认剩余长尾是否来自 Gateway 连接池/事件循环排队；之后再决定是否需要 Gateway 限流、连接池配置或协议层改造。

背压压测摘要：`deploy/perf/results/legal-agent-backpressure-2026-10-22.json`。

### 11.0.18 本轮第十九阶段：Legal 直连与 Gateway 链路对照

为拆分上一阶段剩余长尾，本阶段保持 Legal 连接池 10、入队背压 10、500 req/s、30 秒场景不变，分别执行 Gateway 内网访问和直接访问 Legal 容器的对照测试。

#### 对照结果

| 链路 | 实际请求 | HTTP 200 | 拒绝/失败 | 实际吞吐 | 平均耗时 | P95 |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| Gateway → Legal | 4,813 | 1,766 | 3,047 | 111.48 req/s | 13.26 s | 19.64 s |
| 直连 Legal | 10,268 | 4,469 | 5,799 | 242.74 req/s | 5.42 s | 10.40 s |

直连 Legal 的成功请求 P95 约 7.83 秒，Controller Timer 平均约 83.89ms；task insert、Outbox insert、Service 平均约 20.66ms、9.33ms、31.01ms。两条链路的 Legal 业务分段量级接近，但 Gateway 链路的端到端 P95 和有效吞吐明显更差。

#### 结论

- 背压保护仍然生效，直连场景 Hikari pending 结束值为 0，连接获取超时未增加。
- 同一 Legal 实例直连吞吐约为 Gateway 链路的 2.18 倍，P95 降低约 47%，因此剩余长尾主要来自 Gateway 转发层、连接建立/事件循环排队或单机网络资源争用。
- 本结果不是 Gateway 生产容量结论，因为压测发生在同一开发主机；但已经证明继续扩大 Legal 线程池或 Hikari 不能解决当前 Gateway 链路长尾。

下一步应对 Gateway 单独做转发链路观测：采集 Gateway `gateway.auth.redis`、`gateway.auth.total`、HTTP client pending/active、事件循环线程和容器 CPU/连接数，并用固定并发、较低目标速率做阶梯压测，确定 Gateway 的饱和点后再调整连接池、限流和超时参数。

直连压测摘要：`deploy/perf/results/legal-agent-direct-backpressure-2026-10-22.json`。

### 11.0.19 本轮第二十阶段：Gateway 转发层观测补齐

针对直连与 Gateway 对照中发现的转发长尾，本阶段在 Gateway 增加低基数转发观测：

- `gateway.forward.active`：当前等待下游响应的 Gateway 请求数。
- `gateway.forward{route=...}`：从 Gateway 开始转发到下游响应完成的耗时 Timer，发布 P95/P99。
- 开启 Spring Cloud Gateway metrics 和 HTTP Client metrics 配置开关，分别由 `GATEWAY_METRICS_ENABLED`、`GATEWAY_HTTPCLIENT_METRICS_ENABLED` 控制。

指标只使用 route id 标签，不记录 Token、租户、任务 ID 或 URL 参数，避免高基数导致指标系统膨胀。

#### 本阶段验证

- Gateway 构建成功。
- `AuthFilterTest`：1 个测试通过。
- 最新 Gateway 容器健康检查 HTTP 200。
- Actuator `gateway.forward.active` 已成功注册并返回当前值 0。

下一步使用该版本重新跑 Gateway → Legal 的阶梯压测，并同步采集 `gateway.auth.redis`、`gateway.auth.total`、`gateway.forward`、`gateway.forward.active`、HTTP Client metrics、CPU 和连接数，确定 Gateway 的饱和点和长尾来源。

### 11.0.20 本轮第二十一阶段：Gateway 转发阶梯压测

使用新增 `gateway.forward` 观测的 Gateway JAR，固定 Legal 连接池 10、入队背压 10、有效 Token 和 Gateway 内网链路，执行 100/200/300 req/s、每档 20 秒的阶梯压测。

#### 阶梯结果

| 目标速率 | 实际请求吞吐 | HTTP 200 | HTTP 失败 | 端到端平均 | 端到端 P95 | 端到端 P99 | Gateway forward 平均 |
| ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| 100 req/s | 91.02 req/s | 1,511 | 489 | 274.94 ms | 943.73 ms | 1.23 s | 约 190 ms |
| 200 req/s | 183.63 req/s | 2,255 | 1,746 | 193.94 ms | 787.52 ms | 1.18 s | 约 156 ms |
| 300 req/s | 288.84 req/s | 3,442 | 2,384 | 569.20 ms | 2.49 s | 3.71 s | 约 535 ms |

累计 Gateway `forward{route=legal-agent}` Timer：P95 2.08 s、P99 3.62 s、最大 4.47 s；Gateway `auth.redis` 和 `auth.total` 平均维持在约 5–10ms，未随阶梯明显增长。

#### 结论

- Redis 鉴权不是本轮瓶颈，认证耗时稳定且量级较低。
- 300 req/s 档开始出现明显的 Gateway 下游转发长尾，`gateway.forward` 平均耗时从约 156–190ms 上升到约 535ms，P95 达到秒级。
- 由于 Legal 背压主动拒绝部分请求，HTTP 失败率不能直接等同于 Gateway 单独失败率；但转发 Timer 已证明 Gateway 等待下游响应的时间在高档位显著上升。
- 当前开发机可观测到 Gateway 的饱和拐点约在 200–300 req/s 区间，不能将其作为生产容量承诺。

下一步继续采集 Gateway HTTP Client 的连接池/事件循环和容器 CPU、文件描述符，验证 300 req/s 档是连接池上限、事件循环阻塞还是宿主机资源争用；确认后再调整 Gateway 连接池与限流参数。

阶梯压测摘要：

- `deploy/perf/results/legal-agent-gateway-step-100-2026-10-22.json`
- `deploy/perf/results/legal-agent-gateway-step-200-2026-10-22.json`
- `deploy/perf/results/legal-agent-gateway-step-300-2026-10-22.json`

### 11.0.21 本轮第二十二阶段：Gateway 300 req/s 资源窗口采样

本阶段在最新观测版 Gateway/Legal 容器上，以 300 req/s、持续 20 秒执行入队压测，并每 2 秒采集 Gateway `forward.active`、JVM 线程、进程 CPU 和容器 CPU/内存。

#### 压测与资源结果

| 指标 | 实测结果 |
| --- | ---: |
| 实际 HTTP 请求 | 4,135 |
| HTTP 200 | 2,211 |
| HTTP 失败 | 1,924 |
| dropped iterations | 1,866 |
| 端到端平均耗时 | 5.69 s |
| 端到端 P95/P99 | 9.05 s / 10.11 s |
| Gateway forward P95/P99 | 8.59 s / 10.20 s |
| `gateway.forward.active` 峰值 | 1,193 |
| Gateway 容器 CPU 峰值 | 364.50% |
| Gateway 容器内存 | 536 MiB → 约 780 MiB |
| JVM live threads | 100–102 |

#### 结论

- Gateway 转发活跃请求在压力初期快速升至千级，且容器 CPU 达到约 3.6 个核，内存同步增长；这是当前长尾的直接资源证据。
- JVM 线程数保持约 100，没有出现线程数量失控；问题更接近 Reactor Netty 事件循环/HTTP Client 连接与下游等待叠加，而不是传统线程池耗尽。
- 当前版本仅成功暴露自定义 `gateway.forward`，Spring Cloud Gateway HTTP Client metrics 未出现在 Actuator 指标列表中，不能声称已拿到连接池 active/pending 证据。
- 本轮结果进一步证明 300 req/s 已超过当前单机 Gateway 稳态能力；下一步应显式配置并观测 Reactor Netty ConnectionProvider，或先增加 Gateway 并发/连接上限和限流保护，再做相同阶梯复测。

资源窗口压测摘要：`deploy/perf/results/legal-agent-gateway-resource-300-2026-10-23.json`。

## 12. 参考文件

- `anyujia-legal/README.md`
- `anyujia-legal/anyujia-legal-api/src/main/java/com/anyujia/anyujialegalapi/feign/LegalFeignClient.java`
- `anyujia-legal/anyujia-legal-api/src/main/java/com/anyujia/anyujialegalapi/feign/LegalAgentFeignClient.java`
- `anyujia-legal/anyujia-legal-service/src/main/java/com/anyujia/anyujialegalservice/controller/LegalController.java`
- `anyujia-chat/anyujia-chat-api/src/main/java/com/anyujia/anyujiachatapi/feign/ChatFeignClient.java`
- `anyujia-chat/anyujia-chat-service/src/main/java/com/anyujia/anyujiachatservice/controller/ChatController.java`
- `anyujia-chat/anyujia-chat-service/src/main/java/com/anyujia/anyujiachatservice/service/impl/ConversationServiceImpl.java`
- `anyujia-legal/anyujia-legal-service/src/main/java/com/anyujia/anyujialegalservice/service/TikaLegalDocumentExtractor.java`
- `anyujia-legal/anyujia-legal-service/src/main/java/com/anyujia/anyujialegalservice/service/PdfLegalDocumentParser.java`
- `anyujia-legal/anyujia-legal-service/src/main/java/com/anyujia/anyujialegalservice/service/DocxLegalDocumentParser.java`
- `anyujia-legal/anyujia-legal-service/src/main/java/com/anyujia/anyujialegalservice/service/AssessmentWorker.java`
- `anyujia-legal/anyujia-legal-service/src/main/java/com/anyujia/anyujialegalservice/service/LegalKnowledgeRetriever.java`
- `anyujia-legal/anyujia-legal-service/src/main/java/com/anyujia/anyujialegalservice/service/LegalAgentService.java`
- `anyujia-legal/anyujia-legal-service/src/main/resources/bootstrap.yml`
- `deploy/dev/app/mysql/migration/V8__legal_agent_mvp.sql`
- `deploy/dev/app/mysql/migration/V9__legal_evidence_and_rag.sql`
- `deploy/dev/app/mysql/migration/V11__legal_assessment_trace.sql`
- `deploy/dev/app/mysql/migration/V13__legal_context_snapshot.sql`
- `deploy/dev/app/mysql/migration/V17__legal_agent_conversation.sql`
- `deploy/dev/app/mysql/migration/V27__legal_assessment_lease_and_trace_archive.sql`

