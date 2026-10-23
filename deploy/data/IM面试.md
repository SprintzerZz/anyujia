# IM 模块面试拷打题与参考答案

> 范围：`anyujia-chat` 当前代码及 `deploy/data/im-resume2.0.md`、`deploy/data/v1.3-resume.md` 中的项目描述。按“简历出现概率 × 追问深度 × 答错风险”排序。答案中的“已验证”仅指现有单机 Docker 测试，不能扩展为生产集群承诺。

## P0：简历上最容易被深挖，必须讲清楚

### 1. 请从客户端发一条 WebSocket 消息开始，完整讲一次链路。

**参考答案：**客户端先取得一次性 WebSocket Ticket，Netty 握手时校验 Origin 并消费 Ticket，身份从服务端 Ticket 取得。文本帧进入连接级入站队列，再由独立业务线程执行 `ChatFrameProcessor`。它校验协议版本、消息类型和 DTO，通过 `ChatMessagePersistPublisher` 发布持久化事件，等待 RabbitMQ publisher confirm 后回 `chat_accepted`。`ChatMessagePersistConsumer` 批量消费事件，调用 `MessageServiceImpl.send()` 完成成员校验、幂等查询、限流、会话序号分配和分片落库，再经节点路由把 `chat_ack` 送回发送者。已落库消息异步生成 Outbox 事件，后续驱动接收方推送、未读数刷新和会话索引更新；断线客户端用 `/chat/message/sync` 补消息。

**追问重点：**`chat_accepted` 表示 MQ 发布被 broker 确认，不表示 MySQL 已提交；`chat_ack` 对应消费者完成落库后生成的确认。HTTP `/message/send` 是另一条同步调用 `send()` 的入口，不要混为一谈。

### 2. 你们的消息语义是 exactly once 吗？哪些阶段可能重复？

**参考答案：**不是端到端 exactly once。MQ 重投、客户端超时重发、Outbox 重试和 WebSocket 超时重推都可能导致重复处理或重复到达。服务端通过 `(sender_id, client_message_id)` 查询及数据库唯一约束约束重复落库；Outbox 用 `(message_id,event_type,recipient_id)` 唯一键约束重复事件；客户端要按 `messageId`/`clientMessageId` 去重。合理表述是“至少一次处理 + 分层幂等 + 断线同步”，而不是“网络只投递一次”。

**追问重点：**`conversationSeq` 在幂等查后、INSERT 前分配；并发重复请求可能先占用序号后在唯一键处失败，因此“所有情况下 seq 绝对无空洞”不是当前实现的理论保证。

### 3. 为什么用 Redis 分配会话序号？Redis 宕机会怎样？

**参考答案：**原方案依赖 `chat_conversation` 行锁，同一热点会话写入被串行化。现用 Redis Lua 的原子 `EXISTS/INCR`，热路径单次 Redis 往返；Key 不存在时读 DB 的 `next_message_seq` 再用 Lua 原子种子初始化。`MessageServiceImpl` 捕获 Redis 分配异常后回退数据库序号分配。热点会话 40 req/s 的历史对比中，P99 从 603.81ms 降至 59.8ms。

**追问重点：**Redis 故障切换、旧 Key 恢复和 DB 计数器更新不同步时，仍需专门验证序号不回退、不碰撞；不要把“有 DB 回退”直接说成“Redis 任意故障下严格连续”。

### 4. 32 分片和 1024 虚拟桶到底如何路由？扩容时真的可以无迁移吗？

**参考答案：**代码计算 `bucket = floorMod(conversationId, 1024)`、`shard = bucket % physicalShardCount`、`mysqlIndex = shard / shardsPerMysql`。当前配置可将 32 张逻辑分片表按每实例 8 张映射到 4 个 MySQL 实例；同一会话固定落在同一分片，按会话查询不需要跨实例聚合。迁移提供 `LEGACY_ONLY → DUAL_WRITE → SHARD_ONLY`、历史回填、校验和旧表读取 fallback。

**追问重点：**当前路由仍是取模。改变 `physicalShardCount` 会改变已有会话位置；“1024 虚拟桶”本身不保证无迁移扩容。生产扩容需要稳定的 bucket→shard 映射表、版本化路由、数据搬迁和切换校验。

### 5. 你为什么把 Outbox 放到异步线程？这还是标准事务 Outbox 吗？

**参考答案：**`SHARD_ONLY` 中消息先持久化到分片，Outbox 再通过有界线程池异步写主库，避免第二次数据库写入占用发送临界路径。队列满时 `CallerRunsPolicy` 将压力反馈给调用方；Outbox 多目标事件批量插入，定时 Worker 用 Claim Token 领取、派发、重试。这是以分片消息表为事实来源、靠定时对账修复消息已落库但 Outbox 未写窗口的最终一致方案。

**追问重点：**消息分片写与 Outbox 主库写并非同一事务，因此不能称为“原子事务 Outbox”或“落库后事件绝不丢”。Worker 在落库与入队之间死亡，必须依赖对账补偿；补偿延迟应单独监控。

### 6. Outbox Worker 如何避免多实例重复消费？

**参考答案：**定时任务批量把到期 `pending` 事件更新为 `processing`，写入本轮 `claim_token`；之后仅查询本 token 的行。处理成功时以 `id + status + claim_token` 条件更新为已发送；失败则按退避时间重新置为待处理，超次置终态。对超时 `processing` 事件另有重新 Claim 逻辑。索引化拆分两种 Claim 条件，避免 `OR` 扫描导致锁争用。

**追问重点：**Claim Token 能减少并发 Worker 重复领取，不等于外部副作用 exactly once。派发成功后、标记成功前宕机会再派发，接收侧仍需幂等。

### 7. “小 ID 晚提交”为什么会让对账游标漏消息？你们修到什么程度？

**参考答案：**事务 A 先取得小 ID 但不提交，事务 B 取得大 ID 并先提交。对账扫描看见 B 后把游标推进到 B，A 随后提交；若以后只查 `id > cursor`，A 永远被跳过。当前代码每轮从 `max(0, cursor - reconcileOverlapMessages)` 重新扫描，通过 Outbox 唯一键让重复补偿不产生重复事件。真实事务 A/B 测试中，A/B 各补齐 4 条 Outbox 事件，重复副作用为 0。

**追问重点：**配置名虽叫 `overlap-messages:100`，实现是消息 ID **数值减 100**，并非回看“最近 100 行”。Snowflake ID 不连续，延迟提交跨度超过数值窗口仍可漏扫。要严格补齐，需按时间窗口回查、按行数取前 N 行、记录未决事务，或定期全量/分段反查；这是当前残余风险。

### 8. 10,752 条一致性测试到底证明了什么？

**参考答案：**在单机 Docker 环境、31 个会话、目标 100 msg/s 的一轮测试中，发送成功 10,752，数据库落库 10,752，同步接口分页回补 10,752，Outbox 关联消息 10,752；重复 `clientMessageId`、重复 `conversationSeq` 和本轮统计的序号空洞均为 0。HTTP P95 349.78ms、P99 858.17ms、失败率 0、dropped iteration 0。

**追问重点：**这是“发送—落库—HTTP 同步回补”的闭环，未逐条采集接收端实时 WebSocket 推送帧；也不是 Netty WebSocket 的 10,752 条逐帧发送吞吐。数据来自特定测试窗口，不能外推所有故障下均无空洞。

### 9. 300/500 msg/s 压测该怎么讲？为什么 500 不算达标？

**参考答案：**分阶段压测以分片表的行数增量/稳态时间作为业务吞吐，避免仅用 k6 completed 请求数。300 目标档记录稳态写入 21,739 行、约 290–362 msg/s、HTTP P95 280ms、0 消息丢失；400 目标档业务约 327 msg/s、P95 525ms；500 目标档约 390 msg/s、P95 825ms，未达到目标速率和延迟阈值。400 档观测到 chat 约 250% CPU、主库约 146% CPU，分片池 pending 为 0，因此这轮瓶颈更接近宿主 CPU/主库资源争抢。

**追问重点：**测试在 8 逻辑核、约 17 容器的开发机上完成，且基线调过开发环境 MySQL fsync 参数；不能把 300 msg/s 或 2000 连接写成生产 SLO 或水平扩容上限。

### 10. Netty 相比 Spring WebSocket 具体解决了什么？怎么保证 EventLoop 不被业务阻塞？

**参考答案：**Netty 侧以 `NioEventLoopGroup` 管理网络读写、连接状态和写缓冲；Ticket 的 Redis 校验及文本帧业务处理交给独立有界 `businessExecutor`。单连接的入站帧通过队列串行派发，避免同一连接的业务帧同时执行；出站使用连接级消息数/字节数上限与 Channel 写水位线，慢消费者达到上限则关闭。Spring WebSocket 接入代码仍保留，但当前直连测试指定 Netty 端口。

**追问重点：**单连接串行不等于同一会话跨连接、跨节点严格有序；多发送者并发、RabbitMQ 批量/并行消费仍需独立验证顺序。

### 11. 650 VU 与 2000 并发的建连结果为何差别很大？能比较吗？

**参考答案：**650 VU 那轮包含 HTTP Ticket 申请、一次性消费、30 秒爬坡和 60 秒保持，记录 2220 个 WebSocket 会话，建连 P95 17.38ms、P99 65.69ms；另一轮 500/1000/2000 是从 Docker 网络直连 Netty 的握手/心跳测试，2000 档 P95 1.99ms、P99 8.29ms。环境、脚本、网络路径、爬坡和建连延迟口径可能不同，因此分别陈述，不能推导“并发越高反而越快”。

**追问重点：**WebSocket 建连压测测的是握手与心跳，不是完整消息发送→落库→接收端 ACK 的吞吐。

### 12. 客户端 `chat_accepted` 后 MQ/Worker 出故障，消息还会不会丢？

**参考答案：**`chat_accepted` 在 `ChatMessagePersistPublisher` 等到 RabbitMQ publisher confirm 后发出。此时 broker 已确认接收持久化事件，消费者可能尚未落库；正常 broker 持久化及消费者重投机制下可恢复处理，但这还需要 RabbitMQ 队列耐久性、确认策略和实际 HA 拓扑共同保证。客户端应在 `chat_ack` 超时后用相同 `clientMessageId` 重试，并在重连后查同步/发送结果。不能把 `chat_accepted` 当成最终持久化成功。

**追问重点：**MQ confirm、consumer ACK、DB commit、WebSocket `chat_ack` 是四个不同确认点。

### 13. 杀 Worker 或 RabbitMQ 停机测试具体得到什么结论？

**参考答案：**持续写入时强杀 Chat Worker，恢复后对固定 `clientMessageId` 重试，30/30 落库，重复消息、重复序号、序号空洞和缺失 Outbox 都为 0。RabbitMQ 停机期间通过 HTTP 发送 20 条消息，20/20 落库；恢复后 Outbox pending 为 0，数据库未读数增量为 20。它们证明短时故障和当前负载下的恢复行为。

**追问重点：**HTTP 发送与 WebSocket 先入 MQ 的路径不同。不能用“MQ 停机期间 HTTP 仍可落库”证明“WebSocket `chat_accepted` 仍正常”。也没有覆盖长时间 MQ 不可用、网络分区或三节点 HA。

### 14. ACK、delivery_ack、已读是同一个概念吗？

**参考答案：**不是。`chat_ack` 是发送方得到的持久化消息确认；`delivery_ack` 是接收端收到在线推送后回给服务端的投递确认，用于停止超时重推；`batch_read`/已读游标是用户确实读过会话消息后的业务状态。服务端推送帧成功写入 Channel 也不等于客户端业务处理成功。

**追问重点：**代码里的 Redis pending 键按 `userId + messageId` 记录，没有设备维度；未来多端登录时一端 ACK 可能清掉另一端的待确认状态，必须重构为设备级投递账本。

## P1：架构、故障与数据一致性追问

### 15. 慢消费者怎么处理？队列满和 Channel 不可写时分别发生什么？

**参考答案：**Netty 设置 `WRITE_BUFFER_WATER_MARK`，连接对象还限制最大排队消息数和字节数；`channelWritabilityChanged` 变为可写时继续 drain。入站队列超限或业务线程池拒绝时关闭连接并返回 1013；出站慢消费者超过连接上限时用 4008 关闭。客户端重连后按会话游标 sync。

**追问重点：**要说清楚队列限制的是每连接内存和背压，不保证“任何慢客户端都不丢实时帧”；关闭后要靠持久化消息同步恢复。

### 16. 消息序号唯一、连续和发送顺序分别靠什么？

**参考答案：**Redis 原子分配和数据库 `(conversation_id,conversation_seq)` 唯一约束负责减少/阻断重复序号；同一会话消息路由同一分片，便于按 `conversationSeq` 查询。连续性是特定测试观察结果，不是分配器保证：占号后事务失败、幂等并发冲突都可能产生空洞。跨连接的“客户端发送顺序 = 落库序号顺序”也不由 Redis INCR 自动保证，取决于 MQ 消费并行度与同会话串行化策略。

**追问重点：**如果业务需要严格顺序，应按会话固定分区/单消费者或引入会话串行执行，再用故障注入验证；不要把“序号唯一”讲成“消息绝对按发送顺序落库”。

### 17. RabbitMQ 重投时如何防止重复落库？

**参考答案：**`ChatMessagePersistConsumer` 消费持久化事件时调用 `MessageServiceImpl.send()`；它先查同发送者的 `clientMessageId`，命中即返回已有消息，并且插入遇到唯一键冲突时再次查询已落库记录。这样可以覆盖“DB 已提交但 broker 尚未 ACK，消息重投”的典型窗口。客户端超时重试必须保留同一个 `clientMessageId`。

**追问重点：**幂等键作用域、唯一索引所在表与跨会话复用 ID 的语义要说清楚；重新生成 ID 的客户端重试不受该机制保护。

### 18. MQ 故障时 Outbox 为什么不会立即丢事件？积压如何追赶？

**参考答案：**消息和 Outbox 行分别持久化后，Worker 轮询 pending 事件；派发失败保留行、累加重试次数并按退避延后，超限置终态，后续补偿/运维路径可恢复。若 Worker 在消息落库后、Outbox 写入前死亡，对账从消息事实表补写事件。短时 MQ 停机 20 条测试中，恢复后 Outbox pending 清零。

**追问重点：**“pending=0”还应同时检查 `processing`、`terminal`、MQ 积压和接收端到达，否则不能单凭一个计数断言所有推送都成功。

### 19. 为什么 Outbox 重复扫描不会重复未读数？

**参考答案：**Outbox 对同一 `(message_id,event_type,recipient_id)` 用唯一键防止重复事件行；`unread_refresh` 派发时按分片数据库未读事实计算绝对值，再调用 `ChatUnreadService.setCount()` 写 Redis，避免同一事件重放导致 `HINCRBY` 重复加一。会话索引也以覆盖更新为主。接收端实时推送本身可能重复，仍需要客户端按消息 ID 去重。

**追问重点：**“Outbox 行数不变”只说明事件插入幂等，不等于在线推送 exactly once。

### 20. 为什么分片模式移除 Seata 全局事务？付出了什么代价？

**参考答案：**跨分片库和主库的全局事务会把多个资源的提交协调放进每条消息热路径，增加延迟及锁竞争。`SHARD_ONLY` 让分片消息写入成为主要持久化动作，Outbox/未读/索引改为异步最终一致，减少同步写放大。代价是跨库派生状态有可见延迟和故障窗口，必须靠幂等、定时对账、失败告警和同步接口补偿。

**追问重点：**这是可用性/性能与强原子性之间的选择；若产品要求“ACK 前所有下游状态强一致”，当前方案不满足。

### 21. 分片迁移的三个模式怎么切换？怎么防止回填和在线写入互相覆盖？

**参考答案：**`LEGACY_ONLY` 只读写旧表；`DUAL_WRITE` 为迁移期写旧表和分片表；`SHARD_ONLY` 以分片为事实来源。回填历史消息时保留消息 ID、会话序号和幂等键，分批记录进度；切换前做计数、缺失和字段校验，关闭旧表读取 fallback 前确认旧数据已齐。在线增量写与回填并发必须依靠唯一键和幂等插入，切换时再验证读路径及回退策略。

**追问重点：**当前物理 `DUAL_WRITE` 先旧表后分片写，跨实例强原子性需要具体确认，不能笼统宣称双写 100% 原子成功。

### 22. 在线用户跨节点推送是怎样路由的？Presence 有什么竞态？

**参考答案：**本地 `ChatConnectionRegistry` 管理用户的连接集合，`ChatPresenceService` 以 Redis Hash 记录用户所在节点及该节点连接 ID/心跳。推送方查询目标用户的在线节点集合，按 `nodeId` 经 RabbitMQ 发送事件；目标节点消费者把帧发送给本地连接。上下线更新中使用带连接 ID 前缀的条件写/删，减少旧连接断开时误删新连接的可能；心跳和 TTL 清理陈旧记录。

**追问重点：**在线路由是瞬时状态，查询在线后目标连接可能已断开；消息事实仍靠落库和后续 sync 恢复。

### 23. 离线消息是专门存一份吗？用户上线后怎样补？

**参考答案：**没有独立离线消息表。消息无论接收方在线与否都先写 MySQL 分片；在线时尝试实时推送，离线或断线时客户端持有每会话最后确认的 `conversationSeq`，调用 `/chat/message/sync` 分页取得之后的消息并去重。未读数由 Redis 缓存、dirty set 和定时校准维护，帮助客户端发现待处理会话。

**追问重点：**客户端是否持久化游标、是否在重连后主动 sync 属客户端协议责任；当前代码没有 APNs/短信/系统级离线通知。

### 24. Redis 限流为何用 Lua？Redis 不可用时是 fail open 还是 fail closed？

**参考答案：**令牌桶要原子执行取值、按时间补令牌、扣减和 TTL 更新；Lua 避免多实例并发时“都看到同一余额”的竞态。`RedisConversationRateLimiter` 捕获 Redis 异常后放行，即 fail open，避免缓存故障阻断全部发送；代价是 Redis 故障期间失去热点保护，需要入口层兜底限流、线程池/队列容量和告警。单会话 120 req/s、阈值 50/s 的历史测试累计拦截 5030 次。

### 25. 未读数怎么保证可靠？dirty set 有什么风险？

**参考答案：**热路径优先更新 Redis Hash，dirty set 标记需刷新的用户会话；`ChatUnreadCalibrator` 默认 5 秒扫描脏项，将绝对计数写回两张 MySQL 表。Outbox 的 `unread_refresh` 使用数据库重算的绝对值覆盖 Redis，以降低重放时重复累加。若 Redis 更新成功但 dirty 标记失败，或校准写库与清理 dirty 并发发生，可能出现短时偏差；应定期按消息和已读游标反查数据库并做独立对账。

**追问重点：**“每 5 秒校准”不等于任意故障下 5 秒内严格一致。

## P2：压测方法、演进设计和压力追问

### 26. 你如何设计真正的端到端消息推送吞吐测试？

**参考答案：**发送端使用唯一 `run_id/clientMessageId/clientSeq`、开放模型固定速率发送，并分别记录 `sent/accepted/acked/error/timeout`；接收端保持 Netty 连接，记录每条实时推送的 `messageId/conversationSeq/到达时间`，主动 `delivery_ack`；断线后用 sync 补齐并统计重复。测试结束按会话、分片查 MySQL，验证已 `chat_ack` 消息全部落库、幂等键和序号无重复、`推送∪同步` 覆盖全部落库消息，并报告 P50/P95/P99、实际业务写入速率、推送到达率、MQ/Outbox 积压和故障恢复耗时。

**追问重点：**k6 的 HTTP completed、MQ 投递数和 DB 行增量不能互相替代；现有 10K 测试尚未形成实时接收端逐条账本。

### 27. P95/P99 为什么变大？你如何证明瓶颈在 CPU 而不是分片连接池？

**参考答案：**分阶段监控应用、主库和分片数据库 CPU，同时读取 `/actuator/chatDataSourcePools` 的 active/idle/pending/timeout、MySQL 慢查询与消息各阶段 Micrometer Timer。400 目标档 chat CPU 约 250%、主库约 146%，分片池扩容后 pending 为 0，分片库 CPU 较低，说明当轮最明显的约束是宿主资源和主库开销。下一步应在独立宿主拆分应用和数据库、固定资源配额后重跑同一脚本，才能验证真实服务瓶颈。

**追问重点：**现有观测支持“本轮更可能受 CPU 争抢影响”，不能证明代码级瓶颈已全部不存在。

### 28. Publisher Confirm、Consumer ACK 和 DLQ 分别处理什么问题？

**参考答案：**Publisher Confirm 让生产者知道 broker 是否确认接收持久化事件；Consumer ACK 控制消息何时从队列移除，进程异常可触发重投；DLQ 收容达到重试上限的失败事件，避免无限阻塞正常队列。三者分别覆盖不同阶段，不直接证明数据库已提交或客户端已收到帧。该项目还要叠加 DB 幂等、Outbox 补偿和客户端 sync。

### 29. Ticket、身份头、Origin 和超大帧怎么防御？

**参考答案：**WebSocket 身份来自服务端签发/存于 Redis 的短时一次性 Ticket，Netty 握手时消费；白名单校验 Origin，配置 WebSocket 帧及 HTTP 聚合大小上限，并对帧版本、类型、DTO 做校验。客户端传来的用户身份头不能作为最终身份；握手失败返回对应 HTTP 状态。还应验证 Ticket 重放、过期、畸形 JSON、超大帧和单连接高频发送。

**追问重点：**Origin 是浏览器侧辅助检查，不能单独充当认证；非浏览器客户端可以自行设置 Origin。

### 30. 如果新增小程序端和电脑端同时登录，当前架构够吗？

**参考答案：**当前注册表允许同一用户有多个本地连接，Presence 支持用户对应多个节点，因此“同时在线”有一定基础，但投递待确认键按 `userId + messageId`，不是设备级；一个设备 ACK 可能把另一个设备的 pending 删除。改造需要把 Ticket、Presence、连接注册表和推送目标扩成 `userId/deviceId/connectionId/nodeId`，以 `messageId + recipientId + deviceId` 记录每设备 delivery ACK/重推，以设备级游标同步，消息正文仍按会话只存一份；已读游标通常保持用户级共享，规则需由产品确定。

**追问重点：**多端方案是后续演进建议，当前没有设备级投递状态，面试不能讲成已经实现。

### 31. 如果面试官质疑“Outbox 死信与投递 DLQ 混用”，如何解释？

**参考答案：**两类失败应分开讲：Outbox Worker 失败按 `retry_count/next_retry_at/status` 在 Outbox 表重试，终态后可由对账/运维路径恢复；在线投递 `delivery_ack` 超时由 `ChatDeliveryReaper` 重推，重试上限后送 RabbitMQ 推送 DLQ。另有持久化消费失败的 MQ DLQ。它们的触发条件和数据源不同，不能统一说“所有 Outbox 失败都进 RabbitMQ DLQ”。

### 32. 若重做一次架构，你会先改哪里？

**参考答案：**第一优先级是明确并落实协议状态机：`accepted/persisted/delivered/read`，为每一步建立可对账 ID 和监控；第二是修复当前对账重叠窗口按 Snowflake ID 数值减 100 的盲区，用真实时间窗口/按行回看及周期反查；第三是同会话跨消费者顺序控制和 Redis 回退一致性测试；第四是设备级 ACK 与同步游标，为小程序/桌面多端做准备。性能侧先拆宿主资源复测，再决定主库 Outbox 批量写、队列拓扑和分片扩容。

## 一分钟项目自述

“我负责租客和房东的 IM 模块。WebSocket 接入使用 Netty，网络线程和业务线程分离，连接通过一次性 Ticket 鉴权，慢连接由有界队列和写水位线控制。消息经 RabbitMQ 持久化队列进入业务服务，按客户端消息 ID 幂等、Redis 会话序号分配后写入 MySQL 分片；落库后通过异步 Outbox 推送和更新未读，断线后按会话序号同步。热点会话历史压测 P99 从 603.81ms 降到 59.8ms；在单机 Docker 的 31 会话测试中，10,752 条消息的发送、落库和同步回补数量一致，重复消息和重复序号为 0。我的主要工作也包括 Worker/MQ 故障测试和 Outbox 迟到提交问题定位。目前这套数据代表开发环境验证，实时推送逐条到达和生产集群容量还需独立验证。”

## 核对依据

- 简历口径：`deploy/data/im-resume2.0.md`、`deploy/data/v1.3-resume.md`。
- 性能报告：`deploy/perf/chat-load-test-report.md`、`deploy/perf/chat-capacity-results-2026-10-20.md`。
- 一致性与故障记录：`deploy/data/IM-moudule-analysis.md`；脚本 `deploy/perf/chat-fault-consistency.ps1`、`deploy/perf/chat-transaction-ab-late-commit.ps1`。
- 主链路：`ChatFrameProcessor`、`ChatMessagePersistPublisher`、`ChatMessagePersistConsumer`、`MessageServiceImpl`、`ChatMessageOutboxService`。
- 基础设施：`NettyChatServer`、`NettyChatConnection`、`ChatMessageShardRouter`、`RedisConversationSequenceAllocator`、`ChatDeliveryAckService`、`ChatDeliveryReaper`、`ChatPresenceService`。
