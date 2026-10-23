# Chat 分片灰度发布

灰度实例使用 `docker-compose-gray.yml` 覆盖配置。最终形态为**物理分片 + `SHARD_ONLY` + 最终一致**（热路径已放弃 Seata，见 [CHAT_PHYSICAL_SHARDING.md](CHAT_PHYSICAL_SHARDING.md)）：

```powershell
docker compose -p frameworkjava `
  -f docker-compose-mid.yml `
  -f docker-compose-physical-shards.yml `
  -f docker-compose-runtime-fix.yml `
  -f docker-compose-gray.yml `
  up -d frameworkjava-chat
```

灰度配置（物理分片 + 只写分片 + 最终一致）：

- `CHAT_MESSAGE_SHARDING_MODE=SHARD_ONLY`
- `CHAT_MESSAGE_SHARDING_READ_FALLBACK=true`（分片缺失回退旧表，观察期保留）
- `CHAT_MESSAGE_SHARDING_PHYSICAL_ENABLED=true`
- `SEATA_ENABLED=false`（客户端/TC 保留但关闭，`@GlobalTransactional` 已从消息热路径移除）
- 开启分片回填与校验任务（`CHAT_MESSAGE_SHARDING_BACKFILL_ENABLED` / `CHAT_MESSAGE_SHARDING_VERIFICATION_ENABLED`）

切换前置条件：

1. 已执行消息/outbox 相关迁移（主库 `chat_message`、`chat_message_outbox` 及索引）。
2. 4 个分片实例已启动、已建各自 8 张分片表（`mysql/shard/chat-message-shard.sql`）。
3. 先开启回填任务，确认旧表和分片表校验通过（`missing=0`；`SHARD_ONLY` 下分片行数可大于旧表，属预期）。
4. 灰度观察写入错误、分片缺失数、连接池饱和（`/actuator/chatDataSourcePools`）和 MySQL 延迟。
5. 观察稳定后再关闭 `read-fallback`，旧表转为可归档冷数据。

## 回退

灰度任何一步失败，只需把 override 降级即可立即回退：

- **回退到单库逻辑分片**：去掉 `CHAT_MESSAGE_SHARDING_PHYSICAL_ENABLED=true`（或设为 `false`）。
- **回退到双写**：`CHAT_MESSAGE_SHARDING_MODE=DUAL_WRITE`（旧表 + 分片表同时写；不再有跨库事务，依赖 `insertIgnore` 幂等 + 校验对账）。
- **回退到纯旧表**：`CHAT_MESSAGE_SHARDING_MODE=LEGACY_ONLY`。

## 迁移顺序（数据一致性）

1. **全量回填**：`ChatMessageShardBackfillService` 从主库旧表 `chat_message` 按主键游标 `insertIgnore` 到 4 个物理实例（幂等，可断点续跑）。
2. **增量双写**：应用层 `DUAL_WRITE` 同时写旧表 + 物理分片表，全量回填期间增量写入由 `insertIgnore` 兜底去重。
3. **校验通过**：`ChatMessageShardVerificationService` 按实例比对 `source=旧表` 与 `target=分片表`，`missing=0` 后切写路径。
4. **切 `SHARD_ONLY`**：写路径不再写旧表，消息只进分片（分片表为唯一事实来源）；主库副作用（会话元数据/outbox）最终一致，由异步更新 + 校验兜底对账。
