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
