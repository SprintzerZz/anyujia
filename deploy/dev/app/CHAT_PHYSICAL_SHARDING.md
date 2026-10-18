# Chat 消息物理分片设计

当前实现把 32 张分片表(`chat_message_s00`..`s31`)放在**同一个 MySQL 实例**里,是逻辑分片。当消息量达到亿级时,单实例成为存储和 IO 瓶颈,需要演进到**多 MySQL 实例的物理分片**。本文档给出路由设计、物理拓扑和验证方式;对应可执行配置见 `docker-compose-physical-shards.yml`,亿级数据模拟见 `deploy/perf/chat-shard-simulation.js`。

## 路由模型(三级映射)

沿用 `ChatMessageShardRouter` 的公式,并在其上增加一层「物理实例」:

```
virtual_bucket = conversation_id % 1024     // 1024 个虚拟桶,便于后续 re-shard
physical_shard = virtual_bucket % 32        // 32 个物理分片
physical_mysql = physical_shard / 8         // 4 个 MySQL 实例,每实例 8 个分片
```

- `conversation_id` 一经确定,`virtual_bucket`/`physical_shard`/`physical_mysql` 全部确定,路由是纯函数。
- 一条会话的所有消息,`conversation_id` 相同,因此**永远落在同一个物理分片、同一个 MySQL 实例**,天然满足「一会话一库」的约束,避免跨库 JOIN 和分布式事务。

## 物理拓扑

| MySQL 实例 | 承载物理分片 | 表 |
|-----------|-------------|-----|
| shard-0 | 0–7 | `chat_message_s00`..`s07` |
| shard-1 | 8–15 | `chat_message_s08`..`s15` |
| shard-2 | 16–23 | `chat_message_s16`..`s23` |
| shard-3 | 24–31 | `chat_message_s24`..`s31` |

每个实例只物化自己的 8 张分片表。`chat_conversation_shard`(会话 → 虚拟桶/物理分片映射)作为路由元数据保留在**主库**,或同步到每个实例;`chat_message` 旧表在灰度期继续双写,验证通过后归档。

## 演进步骤(从单库 32 表到多库)

1. **逻辑分片(现状)**:单库 32 表,`chat_message_shard_backfill_progress` 回填旧数据。
2. **物理分片准备**:按 `docker-compose-physical-shards.yml` 拉起 4 个实例;用 `physical_mysql` 公式把每个分片的建表语句落到对应实例。
3. **双写**:写路径同时写旧表 + 对应物理实例的分片表(灰度开关,复用现有 `DUAL_WRITE` 机制)。
4. **回填**:按分片把 `chat_message` 历史数据并行回填到 4 个实例。
5. **校验**:`ChatMessageShardVerificationService` 或对每个实例单独跑校验,确认 `missing=0` 且抽样无分叉。
6. **切换**:逐步关闭旧表读兜底、关闭旧表双写,读路径改为按 `physical_mysql` 路由到对应实例。

## 亿级数据模拟与验证

`deploy/perf/chat-shard-simulation.js` 用确定性 PRNG 生成会话/消息基数(默认 100M 消息、10M 会话),按上述公式路由,并输出每实例的消息量、会话量、存储与峰值 RPS,同时校验「一会话一库」零违规:

```bash
node deploy/perf/chat-shard-simulation.js 100000000 10000000
# MYSQL_INSTANCES=8 node deploy/perf/chat-shard-simulation.js 100000000 10000000
```

输出(4 实例)每条约 2500 万消息、12.5 GiB、峰值 2500 msg/s,四实例各占约 25%,路由零违规。非目标:不在开发机真实落盘 1 亿行;模拟用生成基数 + 外推存储/负载作为证据。
