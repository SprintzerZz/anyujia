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

