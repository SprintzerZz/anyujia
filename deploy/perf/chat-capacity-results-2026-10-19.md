# Chat capacity baseline — 2026-10-19

测试在目标 Docker 主机上执行，JDK17 构建使用 `maven:3.9-eclipse-temurin-17`。压测请求从同一 Docker network 进入 `frameworkjava-chat:18083`，使用 32 个隔离会话、`USER_ID=1`，请求超时 5 秒；脚本阈值为成功率 >99%、p95 <500ms、p99 <1200ms、错误率 <1%。

## 结果

| 目标 | 结果 | 结论 |
|---|---:|---|
| 500 msg/s | 442 completed requests，约 37 req/s；HTTP 错误 52.03%；p95 5.00s；p99 5.01s；2763 dropped iterations | 未通过 |
| 1000 msg/s | 893 completed requests，约 75.2 req/s；HTTP 错误 9.63%；p95 5.00s；p99 5.00s；8200 dropped iterations | 未通过 |

两次测试均因阈值失败提前终止，结果仍保留为真实失败基线，不代表已经达到目标容量。

## 现场诊断

- 四个物理 MySQL 分片实例已启动并完成初始化，每个实例拥有 8 张 `chat_message_s%` 表。
- 当前应用仍连接 `frameworkjava-mysql` 单一数据源，尚未接入四个物理分片的数据源路由，因此没有切换到 `SHARD_ONLY`。
- 500 测试后主库 `Com_commit=696`、`Threads_running=2`；应用 Hikari 连接获取最大等待约 10.92 秒，连接池最大连接数为 10。
- 该结果证明当前单库路径无法满足 500/1000 msg/s；必须先完成应用侧物理分片路由、灰度写入/读取验证，再重新压测。

## 原始结果

- [k6-500.json](../../.tmp-chat-e2e/results-20261019/k6-500.json)
- [k6-1000.json](../../.tmp-chat-e2e/results-20261019/k6-1000.json)

## 当前部署文件

- [docker-compose-physical-shards.yml](../dev/app/docker-compose-physical-shards.yml)
- [chat-message-shard.sql](../dev/app/mysql/shard/chat-message-shard.sql)
- [CHAT_PHYSICAL_SHARDING.md](../dev/app/CHAT_PHYSICAL_SHARDING.md)
