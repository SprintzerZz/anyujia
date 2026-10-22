# Chat load test

Run load generation from a different machine than the application server.

Required environment variables:

- `BASE_URL`: Gateway address, for example `http://127.0.0.1:18080`.
- `TOKEN`: a valid C-end access token stored in Redis.

Optional variables:

- `RATE`: target requests per second, default `20`.
- `DURATION`: steady-stage duration, default `2m`.
- `PRE_VUS`: preallocated virtual users, default `20`.
- `MAX_VUS`: maximum virtual users, default `100`.
- `TOKENS`: comma-separated C-end access tokens for multi-user tests.
- `SESSION_IDS`: comma-separated chat session IDs for multi-conversation throughput tests.
- `WS_SESSION_SECONDS`: seconds each WebSocket iteration keeps the connection
  open, default `max(WS_HEARTBEAT_SECONDS * 2, 60)`.

Start with 20 requests per second. Increase only after checking application,
database, Redis, and host resource usage from the previous run.

`chat-message-send.js` writes one unique message per iteration to a single
conversation. It is intentionally a hotspot test. Start at 5 requests per
second and record the message count before and after each run.

`chat-message-idempotency.js` sends the same client message ID concurrently.
Exactly one database row and one unread increment must result from each run.

`chat-websocket-smoke.js` opens one authenticated WebSocket through Gateway
and verifies the `heart_beat`/`pong` exchange without writing chat data.

`chat-websocket-scale.js` ramps WebSocket connections up to `WS_TARGET_VUS`
(default `10000`). Use `TOKENS` to spread connections across users. Run it from
a separate load generator host and watch JVM memory, file descriptors, gateway
connection limits, Redis, and RabbitMQ.

`chat-message-throughput.js` drives sustained write throughput across
`SESSION_IDS`. Start at `RATE=100`, then increase only after Prometheus shows
stable `chat_message_db_write_duration`, `chat_message_send_duration`, and low
`chat_message_push_failures_total`.

## Legal Agent load test

Run `legal-agent-load.js` from a separate load-generator host. It defaults to
500 enqueue requests per second and 200 end-to-end VUs, which is deliberately
higher than the old 20-concurrent/200-request sample. Supply `BASE_URL`,
`TOKEN` or `TOKENS`, `USER_ID`, and `CHAT_CONVERSATION_ID`; increase rates only
after checking MySQL, RabbitMQ, Redis, Agent Worker, RAG and model metrics.

`legal-agent-sse-scale.js` is the long-connection test. Set `TASK_IDS` to a
pool of pre-created pending task IDs, then start with `SSE_TARGET_VUS=1000` and
increase to 2000. This isolates SSE capacity from task creation and AI latency.
Do not claim a capacity result unless the run records HTTP status, SSE connect
latency, writer-pool queue/rejections, queue overflow, Redis Stream errors,
MySQL connection usage and container CPU/memory.

Use `SCENARIO=enqueue` or `SCENARIO=end_to_end` to run one stage independently;
the default runs both stages sequentially.

For actual SSE connection-capacity measurements, use
`legal-agent-sse-scale.mjs`. It uses Node fetch streaming and records the
latency when response headers arrive, instead of waiting for the SSE body to
finish like a normal k6 HTTP request. It keeps each connection open long enough
to observe heartbeats and writes a JSON result file.

## Safe single-host baseline runner

Use `chat-baseline-diagnose.ps1` for the evidence-gated database baseline. Its
default plan is `100, 200, 300, 400, 500 msg/s`; it stops after the first stage
that misses HTTP SLOs, loses a core container, fails evidence capture, or leaves
more than the permitted Outbox backlog after recovery.

Inspect the plan without contacting Docker:

```powershell
powershell -ExecutionPolicy Bypass -File deploy/perf/chat-baseline-diagnose.ps1 -PlanOnly
```

Run the bounded default plan after supplying the local development secrets:

```powershell
powershell -ExecutionPolicy Bypass -File deploy/perf/chat-baseline-diagnose.ps1 `
  -MysqlPassword '<app-password>' -MysqlAdminPassword '<root-password>' `
  -RedisPassword '<redis-password>'
```

The k6 container is limited to 512 MB, 2 CPUs, 256 VUs, and a 5-second request
timeout by default. A 1000 msg/s stage is never implicit and requires both an
explicit rate list and `-Allow1000`:

```powershell
powershell -ExecutionPolicy Bypass -File deploy/perf/chat-baseline-diagnose.ps1 `
  -Rates 100,200,300,400,500,1000 -Allow1000 `
  -MysqlPassword '<app-password>' -MysqlAdminPassword '<root-password>' `
  -RedisPassword '<redis-password>'
```

Every executed stage writes `stage-<rate>.json`; `diagnosis.json` keeps the
physical-sharding gate closed when evidence is incomplete or the environment
is unhealthy. Prefer a separate load-generator host for final capacity claims.
