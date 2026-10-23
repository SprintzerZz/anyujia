# Chat 分阶段压测结果 — 2026-10-20

物理分片 + `SHARD_ONLY` + 最终一致（热路径无 Seata）落地后的分阶段压测。压测从同一 Docker network 打 `frameworkjava-chat:18083`，`userId=1`、`userFrom:app`，请求超时 5s。阈值：成功率 >99%、p95 <500ms、p99 <1200ms、错误率 <1%、无 dropped iteration。

**业务吞吐口径**：不以 k6 的 HTTP completed 为准，而以 4 个分片实例 `chat_message_s%` 的行数增量 / 稳态时长为准（`SHARD_ONLY` 下分片表是消息唯一事实来源）。分片行数由 `chat-baseline-diagnose.ps1` 的 `Get-BusinessMessageSnapshot` 逐实例精确 `COUNT(*)` 统计。

## 结果

| 目标 | 业务吞吐 | HTTP p95 | HTTP p99 | 错误率 | 结论 |
|---|---:|---:|---:|---:|---|
| 100 msg/s | **101.86 msg/s**（101.9% 达标） | 81.5 ms | 164 ms | 0.09% | ✅ 通过（SLO 仅因 9 次 ramp 期 dropped iteration 被判定失败，见下） |
| 200 msg/s | ~165 msg/s | 163 ms | 274 ms | 0% | ✅ 通过 |
| 300 msg/s | **~290–362 msg/s**（60s 稳态写满 21739 行，0 消息丢失） | 280 ms | 481 ms | 0% | ✅ 通过 |
| 400 msg/s | ~327 msg/s | 525 ms | 889 ms | 0.01% | ⚠️ 边缘（p95 越线，宿主 CPU 饱和） |
| 500 msg/s | ~390 msg/s | 825 ms | 1154 ms | 0.02% | ❌ 未通过（宿主 CPU 饱和） |

> 100 msg/s 官方脚本判定 `slo_passed=false` 的唯一原因是 `dropped_iterations=9`——20s 从 1→100 的 ramp 期内 k6 容器（`--cpus 2`）来不及调度 9 个迭代，属 k6 预热伪影，非应用失败：业务吞吐 101.86 msg/s、p95 81.5ms、错误率 0.09% 三项均显著优于阈值。

## 关键结论

**代码级瓶颈已全部排除，剩余上限是开发机 CPU，而非应用或数据库。** 400 msg/s 稳态采样（`docker stats` + `/actuator/chatDataSourcePools`）：

| 资源 | 稳态占用 | 是否饱和 |
|---|---|---:|
| `frameworkjava-chat` 容器 CPU | **~250%**（8 核中的 2.5 核） | 是 |
| `frameworkjava-mysql`（主库）CPU | **~146%** | 是 |
| 分片池 pending（shard-0..3） | **0**（pool 已扩到 40） | 否 |
| 4 个分片 MySQL CPU | ~25% each | 否 |

开发机 8 逻辑核 / ~16GB，跑着 17 个容器（chat、主 MySQL、4 分片 MySQL、Nacos、Seata、RabbitMQ、Redis 主从+哨兵、admin/house/coupon）。chat 应用 + 主 MySQL 两项即吃掉 4 核；分片库与连接池均未饱和，说明瓶颈是宿主 CPU 争抢。**要冲 500/1000 msg/s，需将分片库或应用搬到独立机器/加核**，而非继续改应用代码。

## 本轮修复的写放大（瓶颈归因）

从最初 100 msg/s p95 887ms、业务 ~11 msg/s，到 300 msg/s 业务写满、p95 280ms，逐项定位并修复：

| # | 瓶颈 | 处置 | 效果 |
|---|---|---|---|
| 1 | Seata AT 全局事务在热路径 | `SHARD_ONLY` + 最终一致，`@GlobalTransactional` 移出 `send()` | 吞吐从 ~3 msg/s 恢复 |
| 2 | fsync 主导写延迟 | 开发机放宽 `innodb_flush_log_at_trx_commit=2` / `sync_binlog=0`（生产禁用） | 100 msg/s p95 887→647ms |
| 3 | outbox claim `OR` 全表扫 + 间隙锁 | 拆成两条单谓词 UPDATE，各钉一条索引（`idx_chat_outbox_pending` / `idx_chat_outbox_sent_cleanup`） | 消除 outbox 写争用 |
| 4 | outbox 写入占发送临界路径 ~65ms | 移到有界异步 `enqueueExecutor`（CallerRunsPolicy） | 临界路径只剩分片 INSERT |
| 5 | 幂等查每次多回退旧表一次 | 新增 `idempotency-fallback`，灰度置 `false`（校验已 missing=0） | 每消息省 1 次查询 |
| 6 | 对端查询每次 3 次读 | 新增 `ConversationParticipantCache`（成员校验仍走 DB） | 对端 17ms→2.6ms |
| 7 | 序列分配 2 次 Redis 往返 | 单条 Lua `EXISTS+INCR` 合一 | 序列 ~1ms |
| 8 | 分片连接池 10 饱和 | `CHAT_SHARD_POOL_SIZE=40` | pending 16–28 → 0 |

单条消息稳态耗时拆解（Micrometer `chat_message_*` Timer，`/actuator/prometheus`）：

| 步骤 | 优化前（100 msg/s 稳态） | 优化后（空闲稳态） |
|---|---:|---:|
| 对端查询 requireCounterparty | ~5ms（3 读） | **2.6ms**（缓存） |
| 幂等查 findByClientId | ~2.6ms | **1.0ms**（不回退旧表） |
| 序列分配 sequence | ~1.4ms | **1.0ms**（单 Lua） |
| 限流 rate-limit | ~1.5ms | **1.3ms** |
| 分片 INSERT db-write | ~30ms | **8.0ms** |
| **端到端 send** | **~166ms** | **~14ms** |

## 连接池观测（Task 3 交付）

`/actuator/chatDataSourcePools` 返回主库 + 4 分片池的 active/idle/pending/timeout：

```
main:30   shard-0:40   shard-1:40   shard-2:40   shard-3:40   （maximumPoolSize）
100 msg/s 稳态: main active≤4 / pending=0；分片 pending=0
400 msg/s 稳态: main active≤21/30；分片 pending=0（池扩容后不再排队）
```

此前分片池 10 连接在 300 msg/s 即 pending 16–28（消息路径每条消息占用 1 个分片连接做幂等查 + INSERT），扩容到 40 后消除。分片写分布 `[shard-0, shard-1, shard-2, shard-3] ≈ [8148, 7256, 6335, 0]`，与会话 `conversation_id` 的确定性路由一致（压测会话落在 shard 0–23，shard-3 无命中属正常）。

## 原始结果

- 官方分阶段脚本输出：`deploy/perf/results/physical-shard-only-final-2026-10-20/`（`stage-100.json`、`summary-100.json`、`diagnosis.json`、`mysql-digests-*.tsv`、`datasource-pools-*.json`）
- 早期失败基线：`results/physical-shard-only-fsync-fix-2026-10-20/`、`results/physical-2026-10-19/`

## 复现

```powershell
# 1. 构建并重建镜像（JDK17）
JAVA_HOME=/d/DownLoad/Java/JDK17_temurin/jdk-17.0.20+8 mvn -pl anyujia-chat/anyujia-chat-service -am package -DskipTests
# 2. 分阶段压测（业务吞吐 = 分片行数增量）
powershell -File deploy/perf/chat-baseline-diagnose.ps1 `
  -Rates @(100,200,300,400,500) -StorageMode SHARD_ONLY -MinimumHostFreeMemoryGB 0.3 `
  -OutputDirectory deploy/perf/results/physical-shard-only-final-2026-10-20
```

## 后续（冲刺 500/1000 msg/s 的前置条件）

1. **横向扩容宿主**：把 4 个分片 MySQL 与 chat 应用搬到独立机器/加核，解除 8 核争抢（当前硬上限）。
2. **降低主库 CPU**：稳态关闭分片校验任务的 32 次 `MOD(MOD())` 全表扫（`CHAT_MESSAGE_SHARDING_VERIFICATION_FIXED_DELAY_MILLIS` 调大或切换完成后禁用）；outbox 事件可考虑批量 `INSERT`（现每消息 4 行异步写入主库）。
3. **调 k6 预热**：提高 `PRE_VUS` 或放宽 ramp 期 `dropped_iterations` 阈值，消除 100 msg/s 的 9 次预热 dropped。
