# IM 实时通信模块简历亮点（2.0）

> 本文基于 `anyujia-chat` 当前代码实现、`deploy/perf` 压测脚本及 `deploy/data/IM-moudule-analysis.md` 测试记录整理。以下内容优先使用已在 Docker 开发环境中验证的数据；未覆盖生产集群的部分已单独标注，避免将开发机结果表述为生产容量。

## 一、项目概述

负责租客—房东 IM 实时通信模块的架构设计、性能优化和可靠性建设，基于 Spring Boot 3、Netty、WebSocket、Redis、RabbitMQ、MySQL 分片和 Prometheus，完成从长连接接入、消息鉴权、顺序分配、幂等落库、异步推送到断线同步的完整链路建设。

核心消息链路为：

```text
Netty WebSocket
  → Ticket 鉴权 / Origin 校验 / 帧校验
  → ChatFrameProcessor
  → Redis 限流与 conversationSeq 分配
  → MySQL 分片持久化
  → 异步 Outbox
  → RabbitMQ 节点路由
  → 在线推送 / 未读数 / 会话索引
  → ACK、超时重推、DLQ 或断线 sync 补偿
```

## 二、可直接放入简历的项目描述

### 1. 设计 Netty 高并发 WebSocket 接入层，建立连接级背压和慢消费者治理机制

**S（场景）**：IM 服务需要长期维护大量 WebSocket 连接，网络写操作、慢客户端和业务线程阻塞可能导致 EventLoop 堵塞、连接内存持续增长，进而影响其他在线用户。

**T（任务）**：将实时连接管理与消息业务处理解耦，并为连接建立鉴权、队列上限、写缓冲水位线、空闲回收和优雅下线机制。

**A（行动）**：使用 Netty `NioEventLoopGroup` 处理网络事件，通过独立 `businessExecutor` 执行 Redis、MySQL 和 RabbitMQ 相关业务；在 `NettyChatConnection` 中实现连接级有界出站队列和字节数限制，配置 `WRITE_BUFFER_WATER_MARK` 高低水位线；当入站队列或出站队列超限时，以 `4008`/`1013` 关闭慢连接并依赖 `/chat/message/sync` 恢复消息；使用 `NettyIdleTimer` 定时回收空闲连接，并通过连接注册表和 Redis 在线状态完成跨实例路由。

**R（结果）**：Netty 直连 500、1000、2000 并发测试的握手和心跳成功率均为 **100%**，建连 P95 分别为 **1.02ms、1.08ms、1.99ms**，P99 分别为 **3.05ms、6.24ms、8.29ms**；650 VU 测试中 Ticket 请求 **2220 次全部成功**，握手和心跳成功率均为 **100%**，建连 P95 **17.38ms**、P99 **65.69ms**。

**简历精简版：**

> 基于 Netty 构建 IM WebSocket 长连接接入层，通过 EventLoop 与独立业务线程池隔离网络事件和阻塞业务，结合连接级有界队列、写缓冲高低水位线、空闲定时回收和 4008 慢消费者关闭策略治理背压；650 VU 实测握手/心跳成功率 100%，建连 P95 17.38ms、P99 65.69ms。

### 2. 使用 Redis 原子序列分配器解决热点会话行锁竞争，显著收敛尾延迟

**S（场景）**：原有 `chat_conversation.next_message_seq` 通过数据库行锁分配会话序号，同一热点会话的并发消息被串行化，导致高并发下 P99 延迟明显升高。

**T（任务）**：在保持会话序号唯一、单调和可恢复的前提下，降低序号分配对数据库事务和行锁的依赖。

**A（行动）**：实现 `RedisConversationSequenceAllocator`，使用 Redis Lua 脚本将“检查 Key、首次从 DB 种子初始化、INCR 分配”合并为原子操作；热路径保持单次 Redis 往返，Redis 异常时保留 DB 分配回退；消息写入前分配 `conversationSeq`，同步查询和序号校验均以该序号作为会话游标。

**R（结果）**：热点会话 40 req/s 压测中，P99 从 **603.81ms 降至 59.8ms，下降约 90%**；P95 从 **63.58ms 降至 32.92ms**，平均延迟从 **35.83ms 降至 20.79ms**，错误率从 **0.08% 降至 0.03%**。

**简历精简版：**

> 将热点会话的数据库行锁序号分配改造成 Redis Lua 原子序列分配器，并保留 DB 种子初始化与异常回退机制；40 req/s 热点压测下 P99 从 603.81ms 降至 59.8ms，尾延迟下降约 90%。

### 3. 构建“消息事实落库 + 异步派生事件”的最终一致性架构，支撑 MySQL 物理分片演进

**S（场景）**：消息持久化、实时推送、未读数和会话索引分别依赖 MySQL、RabbitMQ 和 Redis，如果全部放在同步发送链路中，会放大数据库写入和跨组件故障影响；如果缺少补偿，又可能出现消息已落库但推送事件缺失。

**T（任务）**：降低消息发送临界路径写放大，同时保证消息事实可恢复、派生事件可重试、缺失事件可对账补偿。

**A（行动）**：以 MySQL 分片表作为消息事实来源，使用 1024 虚拟桶将同一会话稳定路由到 32 个逻辑分片；在 `SHARD_ONLY` 模式下将 Seata 全局事务移出热路径；通过有界 `enqueueExecutor` 异步写入 Outbox，并使用批量插入、Claim Token、状态机、退避重试和终态失败处理派生事件；增加分片回填、校验、旧表 fallback 与灰度模式 `LEGACY_ONLY/DUAL_WRITE/SHARD_ONLY`。

**R（结果）**：分阶段压测中，300 msg/s 场景稳态写入 **21739 行且 0 消息丢失**，业务吞吐约 **290–362 msg/s**，HTTP P95 **280ms**；分片连接池从 10 扩展至 40 后，300/400 msg/s 场景分片池 pending 降为 **0**。单条消息端到端 send 的观测耗时由约 **166ms 降至 14ms**，分片 INSERT 约 **8ms**。

**简历精简版：**

> 以 MySQL 分片表作为消息事实来源，基于 1024 虚拟桶实现 32 逻辑分片和灰度迁移；将 Outbox 事件写入移出发送临界路径，通过有界异步队列、Claim Token、批量派发、退避重试和对账补偿实现最终一致性；300 msg/s 稳态写入 21739 条消息且 0 丢失。

### 4. 实现 Outbox 重试、死信和对账补偿，修复“小 ID 晚提交”造成的游标盲区

**S（场景）**：消息落库与 Outbox 写入不在同一个物理事务中，单调 `id > last_message_id` 对账游标可能先扫描到大 ID 事务，随后小 ID 事务迟到提交，导致该消息永久错过补偿。

**T（任务）**：验证并修复迟到提交、事件缺失和重复补偿场景，保证补偿任务可重复执行且不产生重复副作用。

**A（行动）**：在 `ChatMessageOutboxService` 中增加固定数量的重叠扫描窗口，默认回看 100 条消息；对账按物理分片维护游标，并对每轮回看数据执行幂等 Outbox 插入；通过 `(message_id,event_type,recipient_id)` 唯一键、`INSERT IGNORE`/幂等更新、Claim Token 和状态条件更新避免并发重复处理；对失败事件采用退避重试，超过最大次数进入终态并支持 DLQ Replay。

**R（结果）**：事务 A/B 测试中，事务 B 先提交较大 ID，事务 A 后提交较小 ID；修复后 A、B 两条消息均生成 **4 条 Outbox 事件**，重复副作用为 **0**，游标盲区未复现。故障场景中，删除事件后通过重叠窗口约一个调度周期恢复为 4 条事件；同一消息重复补偿两次，Outbox 数量仍为 **4 条**。

**简历精简版：**

> 针对 Outbox 单调游标在“小 ID 晚提交”场景下的漏扫问题，引入 100 条重叠扫描窗口，并结合 Outbox 唯一键、幂等补偿和 Claim Token 保证重复扫描无副作用；事务 A/B 实测迟到提交消息均补齐 4 条事件，重复副作用为 0。

### 5. 建立客户端幂等、ACK、超时重推和 DLQ 补偿闭环，提升消息投递可靠性

**S（场景）**：客户端网络抖动、服务重启或 RabbitMQ 暂时不可用时，发送请求可能超时；简单重试会带来重复落库或重复推送，单纯依赖在线连接又无法覆盖离线用户。

**T（任务）**：定义消息发送、持久化、投递和已读语义，保证客户端可以安全重试，服务端能够恢复未确认投递。

**A（行动）**：以 `(sender_id, client_message_id)` 建立发送幂等边界，重试请求复用同一个 `clientMessageId`；消息落库后通过 ACK 返回持久化确认；服务端通过 Redis 记录待确认投递，`ChatDeliveryReaper` 扫描超时消息并重推，最多重试 3 次后投递到 RabbitMQ DLQ；断线客户端以 `conversationSeq` 调用 `/chat/message/sync`，将实时推送和离线补偿统一为可去重消息集合。

**R（结果）**：Worker 强制终止并使用固定 ID 重试的场景中，30 条消息最终全部落库，重复消息、重复序号、序号空洞和缺失 Outbox 均为 **0**；不 ACK 场景验证为初始推送 1 次加 3 次重试，共 4 次，随后进入 DLQ；RabbitMQ 短暂重启期间持续发送 20 条消息，恢复后 Outbox pending 为 **0**，未读增量与消息数一致。

**简历精简版：**

> 基于 `clientMessageId` 实现客户端重试幂等，结合持久化 ACK、Redis 待确认投递、超时重推和 RabbitMQ DLQ 建立消息可靠投递闭环；Worker 重启后固定 ID 重试 30 条消息全部落库，重复消息/序号/空洞均为 0。

### 6. 使用 Redis Lua 令牌桶和未读数校准机制保护热点会话

**S（场景）**：单一热点会话的高频发送可能占满数据库连接和业务线程；未读数若每条消息同步写库，会增加主库写放大并拖慢消息发送。

**T（任务）**：在多实例场景下提供会话级分布式限流，并将未读数更新从消息热路径中拆出，同时保留数据库可恢复性。

**A（行动）**：使用 Redis Lua 脚本原子完成令牌桶读取、补充、扣减和 TTL 更新，避免跨实例竞态；以 Redis Hash 保存用户—会话未读数，以 dirty set 记录变更，由 `ChatUnreadCalibrator` 每 5 秒将脏数据刷新到 `chat_user_conversation` 和会话成员表；Redis 异常时限流和未读缓存按代码策略降级，不阻塞消息主流程。

**R（结果）**：单会话 120 req/s、限额 50/s 的压测中，正确拦截 **5030 条**超限请求，数据库写入受到保护，推送失败为 **0**；MQ 停机期间发送 20 条消息，恢复后未读数从 1361 增加到 1381，增量与消息数完全一致。

**简历精简版：**

> 使用 Redis Lua 令牌桶实现跨实例会话级限流，结合 Redis Hash 未读缓存、dirty set 和 5 秒周期 DB 校准降低写放大；120 req/s 热点测试拦截 5030 条超限请求，推送失败为 0。

### 7. 以“发送—落库—同步”不变量验证消息一致性，补齐可量化质量门禁

**S（场景）**：仅观察 HTTP 成功率和延迟，无法证明消息是否全部落库、是否能被离线用户同步到达，也无法发现重复消息、重复序号和序号空洞。

**T（任务）**：建立独立于业务接口返回值的消息账本和数据库对账口径，将一致性从功能描述转化为可执行验证项。

**A（行动）**：为每轮测试生成唯一 `run_id` 和 `clientMessageId`，分别统计发送成功集合、分片表落库集合、Outbox 关联集合和 `/chat/message/sync` 回补集合；按会话校验 `conversationSeq` 连续性、重复性和空洞；将实时推送与同步补偿作为两类到达来源分别统计，避免把同步结果误报为实时推送结果。

**R（结果）**：100 条 Netty 闭环测试中，发送、落库、同步回补均为 **100**，重复 `clientMessageId`、重复 `conversationSeq`、序号空洞和缺失 Outbox 均为 **0**；10K 级多会话测试实际发送 **10752** 条，落库和同步回补均为 **10752**，上述一致性指标全部为 **0**。该测试明确验证的是“发送—落库—同步”闭环，未夸大为实时 WebSocket 逐条推送证明。

**简历精简版：**

> 建立发送账本、分片数据库和同步接口三方对账机制，校验落库数、同步到达数、幂等键、会话序号重复与空洞；Netty 10K 级测试中 10752 条消息实现发送=落库=同步回补，重复消息/序号/空洞均为 0。

## 三、综合性能结果与容量边界

| 能力 | 已验证结果 | 简历表述建议 |
|---|---:|---|
| Netty 长连接 | 650 VU，握手/心跳 100%，P95 17.38ms，P99 65.69ms | 可写“650 并发连接稳定性验证” |
| Netty 直连扩展 | 500/1000/2000 并发握手和心跳均 100% | 可写“完成 2000 并发连接压测” |
| 热点消息写入 | P99 603.81ms → 59.8ms，约下降 90% | 可写性能优化结果 |
| 多会话吞吐 | 300 msg/s 稳态写入 21739 条，0 丢失 | 可写“300 msg/s 一致性与吞吐验证” |
| 400/500 msg/s | 400 约 327 msg/s，500 约 390 msg/s；P95 分别 525/825ms | 应注明受 8 核开发机 CPU 争抢影响 |
| 10K 一致性 | 10752 发送=落库=同步，重复/空洞 0 | 可写可靠性和对账能力 |

当前测试环境为单机 Docker 开发环境，运行约 17 个容器。400 msg/s 稳态时 chat 容器约 250% CPU、主 MySQL 约 146% CPU，分片连接池 pending 为 0，说明本轮 500 msg/s 上限主要受宿主机 CPU 争抢影响，不能直接等同于生产集群容量。

## 四、推荐简历最终版本

### 项目亮点版

> 负责租客—房东 IM 实时通信模块建设，基于 Netty、Redis、RabbitMQ、MySQL 分片和 WebSocket 完成长连接接入、消息幂等、顺序控制、可靠投递及断线同步闭环。使用 Netty EventLoop + 独立业务线程池隔离网络事件和阻塞业务，结合连接级有界队列、写缓冲高低水位线和慢消费者回收策略，650 VU 实测握手/心跳成功率 100%，建连 P95 17.38ms。将数据库行锁序号分配改造成 Redis Lua 原子序列分配器，热点会话 P99 从 603.81ms 降至 59.8ms，下降约 90%。引入 32 逻辑分片、Outbox、Claim Token、退避重试、DLQ 和重叠窗口对账机制，修复“小 ID 晚提交”造成的游标盲区；10K 级测试中 10752 条消息实现发送=落库=同步回补，重复消息、重复序号和序号空洞均为 0。

### 更短的一句话版本

> 负责 Netty IM 实时通信模块性能与可靠性建设，通过 Redis 原子序列、MySQL 分片、Outbox 对账补偿和客户端幂等重试，将热点会话 P99 降低约 90%，并在 10K 级测试中实现发送=落库=同步回补、重复和序号空洞均为 0。

## 五、面试时需要主动说明的边界

1. 650 VU 的 2220 次 WebSocket 会话测试证明了 Netty 建连、Ticket 鉴权和心跳稳定性；10K 消息测试验证的是同步回补闭环，不应表述为 10K 条实时 WebSocket 推送帧全部被逐条观测。
2. 300 msg/s 是本轮分片写入和一致性验证中较稳定的业务吞吐；400/500 msg/s 已受到开发机 CPU 争抢影响，不能直接宣称为生产容量。
3. RabbitMQ 故障测试覆盖短暂重启和恢复后的 Outbox 清空，尚未等价覆盖三节点 RabbitMQ 集群长时间不可用、网络分区和大规模积压。
4. 以上结果来自单机 Docker 环境；生产环境仍需在独立应用节点、独立 MySQL 分片和 MQ 高可用拓扑下重新建立容量基线。

## 六、主要代码与测试依据

- Netty 接入：`anyujia-chat/anyujia-chat-service/src/main/java/com/anyujia/anyujiachatservice/realtime/netty/NettyChatServer.java`
- 连接队列与背压：`.../realtime/netty/NettyChatConnection.java`
- 帧处理与消息入口：`.../realtime/ChatFrameProcessor.java`
- 消息发送、序号、分片和同步：`.../service/impl/MessageServiceImpl.java`
- Outbox、重试、对账：`.../realtime/ChatMessageOutboxService.java`
- Redis 序号：`.../service/sequence/RedisConversationSequenceAllocator.java`
- Redis 限流：`.../service/ratelimit/RedisConversationRateLimiter.java`
- 未读校准：`.../service/unread/ChatUnreadService.java`、`ChatUnreadCalibrator.java`
- Netty 连接测试：`deploy/perf/chat-websocket-direct.js`、`chat-websocket-scale.js`
- 消息吞吐测试：`deploy/perf/chat-message-throughput-direct.js`、`chat-baseline-diagnose.ps1`
- 故障与迟到提交测试：`deploy/perf/chat-fault-consistency.ps1`、`chat-transaction-ab-late-commit.ps1`
- 详细分析与原始结果：`deploy/data/IM-moudule-analysis.md`、`deploy/perf/chat-capacity-results-2026-10-20.md`
