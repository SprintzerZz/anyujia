# Chat 消息物理分片设计

当前实现把 32 张分片表(`chat_message_s00`..`s31`)放在**同一个 MySQL 实例**里,是逻辑分片。当消息量达到亿级时,单实例成为存储和 IO 瓶颈,需要演进到**多 MySQL 实例的物理分片**。本文档给出路由设计、物理拓扑、跨库一致性与验证方式;对应可执行配置见 `docker-compose-mid.yml`(新增 `frameworkjava-seata` 与 4 个分片实例)与 `docker-compose-gray.yml`(物理灰度 override),分片初始化见 `mysql/shard/chat-message-shard.sql`,亿级数据模拟见 `deploy/perf/chat-shard-simulation.js`。

物理分片 Compose 使用 Docker named volumes，而不是 Windows bind mount；MySQL 初始化阶段需要修改数据目录内证书文件权限，Windows 挂载目录会导致实例启动失败。

## 路由模型(三级映射)

沿用 `ChatMessageShardRouter` 的公式,并在其上增加一层「物理实例」:

```
virtual_bucket = conversation_id % 1024     // 1024 个虚拟桶,便于后续 re-shard
physical_shard = virtual_bucket % 32        // 32 个物理分片
physical_mysql = physical_shard / 8         // 4 个 MySQL 实例,每实例 8 个分片
```

- `conversation_id` 一经确定,`virtual_bucket`/`physical_shard`/`physical_mysql` 全部确定,路由是纯函数。
- 一条会话的所有消息,`conversation_id` 相同,因此**永远落在同一个物理分片、同一个 MySQL 实例**,天然满足「一会话一库」的约束,避免跨库 JOIN。
- 实现上 `ChatMessageShardRouter` 新增 `physicalMysqlIndex(convId)` / `tableNameForShard(shard)` / `mysqlInstanceCount()`;消息表读写统一走 `ChatMessageShardStore`(每物理实例一个 `JdbcTemplate`),按实例+表名路由。

## 物理拓扑

| MySQL 实例 | 承载物理分片 | 表 |
|-----------|-------------|-----|
| shard-0 | 0–7 | `chat_message_s00`..`s07` |
| shard-1 | 8–15 | `chat_message_s08`..`s15` |
| shard-2 | 16–23 | `chat_message_s16`..`s23` |
| shard-3 | 24–31 | `chat_message_s24`..`s31` |

每个实例只物化自己的 8 张分片表。`chat_conversation_shard`(会话 → 虚拟桶/物理分片映射)作为路由元数据保留在**主库**,或同步到每个实例;`chat_message` 旧表在灰度期继续双写,验证通过后归档。

## 跨库一致性(最终一致)

消息写路径横跨主库(会话元数据/outbox)与分片库(`chat_message_sXX`)。单库 `@Transactional` 只能管一个数据源;最初引入 **Seata AT** 做跨库强一致,但实测每个全局事务(3 个分支 × 2PC 的分支注册/全局锁 RPC)约 3s,消息吞吐被压到 ~3 msg/s,无法支撑 500/1000 msg/s 目标。因此**消息热路径放弃 Seata,改为「分片独立提交 + 最终一致」**:

- **消息写分片库单库自提交**:`SHARD_ONLY` 下 `ChatMessageShardStore.insert` 只写一个物理实例的一张分片表,autocommit,无分布式事务;分片表即消息唯一事实来源(source of truth)。
- **主库副作用最终一致**:会话元数据(`advanceLatestMessage`)异步更新;outbox(push/unread/index 事件)最佳努力写入——失败不阻塞消息,由分片校验/回填兜底对账。
- **对账兜底**:`ChatMessageShardVerificationService` 按实例比对 `source=旧表` 与 `target=分片表`,保证 `missing=0`(旧表数据不丢);`SHARD_ONLY` 下分片行数可大于旧表(新消息只进分片),属预期。
- Seata 客户端/TC 仍在位但默认关闭(`SEATA_ENABLED=false`);`@GlobalTransactional` 已从 `send()/sendAgentMessage()/markVisited()/markRead()` 热路径移除,`DataSourceProxy` 在无 XID 时纯透传,保留回退强一致的能力。各库的 `undo_log` 表仍保留(重建 Seata 时免迁移)。

## 存储模式与灰度开关

`chat.message-sharding.mode` 三态,叠加 `chat.message-sharding.physical-enabled`(默认 `false`):

| 模式 | physical-enabled=false | physical-enabled=true |
|------|------------------------|------------------------|
| `LEGACY_ONLY` | 只写旧表 `chat_message` | 同左(不回分片) |
| `DUAL_WRITE` | 旧表 + 单库分片表 | 旧表(主库) + 物理分片实例 |
| `SHARD_ONLY` | 只写单库分片表 | 只写物理分片实例 |

`physical-enabled=false` 时行为与现状完全一致(单库逻辑分片),保证向后兼容与可回退。`read-fallback=true` 时读路径分片缺失回退旧表。

## 演进步骤(从单库 32 表到多库)

1. **逻辑分片(现状)**:单库 32 表,`chat_message_shard_backfill_progress` 回填旧数据。
2. **物理分片准备**:在 `docker-compose-mid.yml` 拉起 `frameworkjava-seata` 与 4 个分片实例;对主库执行 `seata-server.sql`(建 `frameworkjava_seata` 库),对 4 个分片实例执行 `seata-undo-log.sql`(建 `undo_log`),并确保每个实例已建自己负责的 8 张表(`mysql/shard/chat-message-shard.sql`)。
3. **双写**:`docker-compose-gray.yml` 设 `CHAT_MESSAGE_SHARDING_MODE=DUAL_WRITE` + `CHAT_MESSAGE_SHARDING_PHYSICAL_ENABLED=true` + `SEATA_ENABLED=true`;写路径同时写旧表(主库) + 对应物理实例的分片表。
4. **回填**:开启 `CHAT_MESSAGE_SHARDING_BACKFILL_ENABLED=true`;`ChatMessageShardBackfillService` 从主库旧表按游标读,`ChatMessageShardStore.insertIgnore` 按分片幂等写 4 实例(游标仍在主库 `chat_message_shard_backfill_progress`)。
5. **校验**:`ChatMessageShardVerificationService` 物理模式下按实例比对 `source=旧表` 与 `target=各实例分片表` 的行数,输出 `missing=0`(跨库无法 JOIN,故用行数差 + 确定性路由近似缺失数)。
6. **切换**:逐步关闭旧表读兜底、关闭旧表双写(`SHARD_ONLY`),读路径改为按 `physical_mysql` 路由到对应实例。
7. **回退**:任一环节异常,把 `physical-enabled=false` 或 `mode=LEGACY_ONLY` 即可回到单库旧表路径(旧表双写期数据完整,可直接回退)。

## 亿级数据模拟与验证

`deploy/perf/chat-shard-simulation.js` 用确定性 PRNG 生成会话/消息基数(默认 100M 消息、10M 会话),按上述公式路由,并输出每实例的消息量、会话量、存储与峰值 RPS,同时校验「一会话一库」零违规:

```bash
node deploy/perf/chat-shard-simulation.js 100000000 10000000
# MYSQL_INSTANCES=8 node deploy/perf/chat-shard-simulation.js 100000000 10000000
```

输出(4 实例)每条约 2500 万消息、12.5 GiB、峰值 2500 msg/s,四实例各占约 25%,路由零违规。非目标:不在开发机真实落盘 1 亿行;模拟用生成基数 + 外推存储/负载作为证据。

## 连接池观测

主库 + 4 个分片库的 HikariCP 通过 `ChatDataSourcePoolMetrics` 注册 Micrometer gauge(`hikaricp_connections_active/_idle/_pending/_total/_max`、`hikaricp_connection_timeout_millis`,`pool` 标签区分 `main`/`shard-0..3`);actuator 端点 `GET /actuator/chatDataSourcePools` 返回各池 JSON。压测期间据此判断是否饱和(pending 堆积 / connection timeout)。

## 压测环境的持久化调优(仅开发机)

Docker Desktop for Windows 上 MySQL 默认 `innodb_flush_log_at_trx_commit=1` + 主库 `sync_binlog=1`,每次提交触发 1~2 次磁盘 fsync,单条 INSERT/UPDATE 实测 50~118ms(读 1~2ms),消息发送延迟被 fsync 主导,100 msg/s 即触发 p95 超阈值、k6 丢迭代。压测前在 Compose 里对 5 个 MySQL 实例做了**开发机专用**的持久化放宽:

- 主库 `frameworkjava-mysql`:`--innodb-flush-log-at-trx-commit=2` + `--sync-binlog=0`
- 4 个分片实例:`--innodb-flush-log-at-trx-commit=2`

这是压测/开发环境的吞吐调优,换取吞吐、牺牲断电/OS 崩溃时的少量已提交事务(MySQL 进程崩溃不丢)。**生产环境不得使用**,生产保持 `=1`/`sync_binlog=1` 以保证崩溃一致性。压测报告中的吞吐数字应注明在此持久化配置下测得。

## 发送热路径的写放大治理

持久化放宽后,单条消息的平均耗时仍有约 166ms。用 `chat_message_*` 系列 Micrometer Timer 拆解后,耗时分布为:分片 INSERT ~30ms(维护 6 个二级索引)、outbox INSERT ~65ms、会话元数据 UPDATE ~19ms(已异步)、前置读 ~5ms + Redis ~3ms。针对 outbox 做了两处改造:

1. **outbox 写入移出请求线程**。`ChatMessageOutboxService` 新增 `enqueueExecutor`(`chat.outbox.enqueue-threads`,默认 4;队列 `chat.outbox.enqueue-queue-capacity`,默认 20000),`enqueueMessageEventsAsync` / `enqueueDerivedEventsAsync` 提交后立即返回,`MessageServiceImpl.enqueueOutboxBestEffort` 改调异步版本。队列满时使用 `CallerRunsPolicy`,让背压回到发送线程而不是丢事件或无限堆积。消息在调用前已提交到分片表,outbox 只是 push/未读/会话索引的派生副作用,延后写入不改变「分片表为唯一事实来源」的语义。
2. **claim 查询按索引拆分**。原 `publishPending` 用 `WHERE (status=0 AND next_retry_at<=now) OR (status=1 AND updated_at<=timeout)` 认领,`OR` 让优化器无法用单条索引,每次轮询都要扫全表并持大量行/间隙锁,阻塞并发的 outbox INSERT。现拆成两条单谓词 UPDATE:待发送走 `idx_chat_outbox_pending (status, next_retry_at, id)`,`ORDER BY next_retry_at, id` 与索引同序;超时回收走 `idx_chat_outbox_sent_cleanup (status, updated_at, id)`,并用 `FORCE INDEX` 钉住索引(否则优化器会退化成仅按 `status` 的扫描)。回收只在待发送行不足 `batch-size` 时才执行,天然避开热路径。

发送线程只剩「分片 INSERT」一次同步写;开发机单条稳态耗时由 ~166ms 降到 ~19ms(前置 5 步 + 分片 INSERT 8ms)。

另:派生事件由 8 个 dispatch 线程并行投递,同一会话的相邻事件存在乱序落库的可能。`ChatUserConversationMapper.touchMessage/touchMessageForUsers` 增加 `last_message_seq < #{messageSeq}` 单调守卫,保证 `chat_user_conversation` 的最近消息只会前进不会回退(Redis 缓存层由下一条消息自愈)。
