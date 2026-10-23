# IM 会话模块 压测与可靠性报告

> 范围：`anyujia-chat` 租客-房东即时通信模块
> 环境：单机 Docker 开发环境（MySQL / Redis / RabbitMQ / Nacos + chat 服务，非集群）
> 压测工具：k6 v2.2.0
> 当前传输层：Netty；Spring WebSocket 注册已暂时封存

---

## 一、简历版总结

负责 IM 长连接（租客-房东会话）稳定性建设，当前以 Netty 作为 WebSocket 传输层，消息先落库、事务提交后经 RabbitMQ 直连交换机异步推送，Redis Hash 维护「用户→节点」在线路由实现跨实例投递。核心优化：以 Redis INCR 原子计数器替代 `SELECT ... FOR UPDATE` 行锁分配会话序号，将热点会话写入 **P99 从 603.81ms 降到 59.8ms（-90%）**、P95 从 63.58ms 降到 32.92ms、错误率从 0.08% 降到 0.03%；以 Redis Lua 令牌桶实现会话级分布式限流，以 Redis Hash 维护未读数并异步校准回 DB。可靠投递上实现客户端 ACK + 超时重推 + 死信补偿（RabbitMQ DLX/DLQ）。Netty 侧通过 EventLoop、独立业务线程池、连接级有界队列、写缓冲高低水位线和空闲连接定时回收治理长连接；本次 650 并发实测握手与心跳成功率均为 100%，建连 P95 为 17.38ms、P99 为 65.69ms。

---

## 二、四个优化点

### 1. Redis INCR 会话序号分配器（性能主杠杆）

**问题**：`chat_conversation.next_message_seq` 用 `SELECT ... FOR UPDATE` 行锁分配序号，把同一会话的所有写入串行化，尾部延迟（P99）飙到 603ms。

**方案**：改为 Redis `INCR` 原子计数器（Redisson `RAtomicLong`），首次使用从 DB 懒加载种子，每次写入同步回 DB 用于恢复。

```java
// RedisConversationSequenceAllocator
RAtomicLong counter = redissonClient.getAtomicLong("chat:seq:" + conversationId);
if (!counter.isExists()) {
    counter.compareAndSet(0L, conversationMapper.selectNextMessageSeq(conversationId));
}
return counter.incrementAndGet();
```

### 2. Redis Lua 令牌桶限流

**方案**：用单个 Lua 脚本原子完成令牌桶读改写（跨实例无竞态），替代本地固定窗口。

```lua
local bucket = redis.call('HMGET', KEYS[1], 'tokens', 'ts')
local tokens = tonumber(bucket[1]); local ts = tonumber(bucket[2])
if tokens == nil then tokens = capacity; ts = now end
tokens = math.min(capacity, tokens + (now - ts) / 1000.0 * refill_rate)
if tokens >= requested then tokens = tokens - requested; allowed = 1 end
redis.call('HSET', KEYS[1], 'tokens', tokens, 'ts', now)
```

### 3. Redis 未读数 + DB 校准

**方案**：未读数走 Redis Hash `HINCRBY`（热路径不再写 DB），脏集合 `chat:unread:dirty` 记录变更，`ChatUnreadCalibrator` 定时（5s）把未读数回写 MySQL。

### 4. 客户端 ACK + 超时重推 + 死信补偿

**方案**：客户端收到 `chat` 帧后回 `delivery_ack`；服务端在 Redis 记录待确认投递（带超时时间戳），`ChatDeliveryReaper` 定时扫描超时未确认的消息重推，超过 3 次重试后发布到 RabbitMQ 死信队列 `chat.message.push.dlq` 供补偿。

---

## 三、压测结果

### 热点会话写入（单会话，40 req/s，k6 `ramping-arrival-rate`）

| 指标 | 优化前 | 优化后 | 变化 |
|---|---|---|---|
| P99 | 603.81ms | **59.8ms** | **-90%** |
| P95 | 63.58ms | **32.92ms** | **-48%** |
| 平均 | 35.83ms | 20.79ms | -42% |
| 中位数 | 18.65ms | 17.52ms | — |
| 错误率 | 0.08% | 0.03%（2 条瞬时抖动） | — |
| 最大延迟 | 921ms | 1.52s（单点毛刺，P99 内为 59.8ms） | 尾部大幅收敛 |

### 热点限流保护（单会话 120 req/s，超出 50/s 限额）

限流器正确拦截：`chat_conversation_rate_limited_total` 累计 5030 条被拒，DB 写入受保护，推送失败为 0。

### Netty WebSocket 建连与心跳（650 并发 VU）

本次测试只访问 Netty 端口，不经过 Spring WebSocket：HTTP Ticket 接口访问
`http://127.0.0.1:18083/chat/websocket/ticket`，WebSocket 直连
`ws://127.0.0.1:18085/chat/websocket`（宿主机 `18085` 映射到 Chat 容器内 Netty `18084`）。
测试使用 k6 以 30 秒爬坡达到 650 VU，保持 60 秒，每个连接保持 30 秒，每 10 秒发送一次
`heart_beat` 帧；Ticket 在 Redis 中一次性消费，连接身份来自 Ticket，不依赖客户端伪造身份头。

Netty 的具体实现链路为：`NioEventLoopGroup` 接受和处理网络事件，
`GatewayAuthenticationHandler` 异步消费 Redis Ticket 并写入连接用户 ID，
`WebSocketServerProtocolHandler` 完成协议升级，`RealtimeFrameHandler` 将文本帧放入单连接有界队列，
再交给独立业务线程池调用统一的 `ChatFrameProcessor`；写出侧由 Netty Channel 执行，并通过
写缓冲高低水位线、单连接消息/字节队列上限和空闲定时器实现背压及慢连接回收。

| 指标 | 结果 |
|---|---|
| 最大并发连接 | **650 VU** |
| WebSocket 会话数 | 2220 |
| 握手成功率 | **100%** |
| 心跳成功率 | **100%**（4440/4440 checks 全过） |
| 建连延迟 | avg 6.30ms，P95 17.38ms，P99 65.69ms |
| Ticket HTTP 请求失败率 | **0%**（2220 次请求） |
| Chat 容器状态 | 测试期间持续运行，无 Netty 线程池拒绝或处理异常 |

此前报告中的 `avg 2.97ms / P95 5.44ms / P99 14.23ms` 属于另一轮测试数据，不能直接标注为本次
Netty 650 并发结果；本节以上述本次直连 Netty 实测数据为准。

---

## 四、可靠性验证（在线双端）

用 `chat-delivery-ack.js` / `chat-delivery-retry.js` 让收件人（userId=2）保持 WebSocket 在线：

| 行为 | 验证结果 |
|---|---|
| 投递 | 收件人在线收到 `chat` 推送（`ws_msgs_received=1`） |
| ACK | 发 `delivery_ack` 后 `chat:delivery:due` 与 pending map 清空 |
| 超时重推 | 不 ACK 时重推 3 次，收件人共收到 4 条（初始 + 3 次重试） |
| 死信补偿 | 3 次重试后进入 DLQ，`chat.message.push.dlq` = 1 条，日志 `dead-lettered after 3 attempts` |

---

## 五、监控指标（Prometheus，`/actuator/prometheus`）

| 指标 | 类型 | 含义 |
|---|---|---|
| `chat_websocket_online_connections` | Gauge | 在线连接数 |
| `chat_message_send_duration` | Timer | 端到端发送延迟 |
| `chat_message_db_write_duration` | Timer | 落库 + 索引更新延迟 |
| `chat_message_sync_duration` | Timer | 增量同步延迟 |
| `chat_message_push_failures_total` | Counter | 在线推送失败数 |
| `chat_conversation_rate_limited_total` | Counter | 热点限流拦截数 |

---

## 六、复现方法

```bash
# 1. 起中间件 + chat 服务（端口重映射见 .agent/tmp/chat-local.override.yml）
docker compose -p frameworkjava -f docker-compose-mid.yml -f chat-local.override.yml up -d

# 2. 热点会话写入压测
k6 run chat-message-throughput-direct.js   # RATE=40 DURATION=2m

# 3. Netty WebSocket 建连与心跳压测
$env:TICKET_BASE_URL='http://127.0.0.1:18083'
$env:WS_URL='ws://127.0.0.1:18085'
$env:WS_TARGET_VUS='650'; $env:WS_RAMP_DURATION='30s'
$env:WS_HOLD_DURATION='60s'; $env:WS_SESSION_SECONDS='30'
$env:WS_HEARTBEAT_SECONDS='10'
k6 run chat-websocket-direct.js

# 4. 在线双端 ACK / 重推 / 死信验证
k6 run chat-delivery-ack.js
k6 run chat-delivery-retry.js
docker exec frameworkjava-rabbitmq rabbitmqctl list_queues name messages_ready
```

---

## 七、诚实的边界说明

- 数据来自**单机开发环境**，不是生产集群。「X 节点 / Y 万在线连接」的集群口径需在分库分表 + 多实例下另行压测。
- 监控侧目前只落地了 Prometheus 指标端点；**ACK 超时率、MQ 堆积量**指标与 **Grafana 面板 / 告警规则**未在仓库中，需外接 RabbitMQ Prometheus exporter + Alertmanager 补齐。
- 相关代码：`RedisConversationSequenceAllocator`、`RedisConversationRateLimiter`、`ChatUnreadService` + `ChatUnreadCalibrator`、`ChatDeliveryAckService` + `ChatDeliveryReaper`。
