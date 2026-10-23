# Chat module

## Realtime transports

The service keeps the existing Spring WebSocket endpoint on the HTTP service port. It now uses a
bounded per-connection outbound queue, so RabbitMQ consumers and request threads do not write to a
`WebSocketSession` concurrently.

The active test transport is Netty. It exposes the same `/chat/websocket` protocol on a separate
port. Spring WebSocket registration is disabled while `CHAT_NETTY_ENABLED=true`. Netty uses
Netty EventLoops for connection state and writes, a bounded worker pool for blocking chat business
operations, channel write-buffer watermarks for backpressure, and one shared hashed-wheel timer for
idle connections.

Relevant environment variables:

| Variable | Default | Purpose |
| --- | ---: | --- |
| `CHAT_NETTY_ENABLED` | `true` | Start the Netty WebSocket listener and disable Spring WebSocket registration. |
| `CHAT_NETTY_BIND_HOST` | `0.0.0.0` | Bind address; use `127.0.0.1` when testing from the same host. |
| `CHAT_NETTY_PORT` | `18084` | Netty listener port. |
| `CHAT_NETTY_MAX_QUEUED_MESSAGES` | `256` | Per-connection outbound message limit. |
| `CHAT_NETTY_MAX_QUEUED_BYTES` | `2097152` | Per-connection outbound byte limit. |
| `CHAT_NETTY_WRITE_LOW_WATER_MARK` | `32768` | Resume-write watermark. |
| `CHAT_NETTY_WRITE_HIGH_WATER_MARK` | `65536` | Pause-write watermark. |

Both WebSocket transports consume and validate the single-use `ticket` query parameter directly
against Redis. The ticket identity is the source of truth; client-supplied `userId` and `userFrom`
headers are ignored. The gateway only forwards the ticket and strips spoofable identity headers.
Do not expose the Netty listener directly to untrusted clients. Set the gateway's
`CHAT_WEBSOCKET_URI` to the internal listener, for example `ws://frameworkjava-chat:18084`. The
development Compose file already applies these settings.

Actuator health/info endpoints remain available for probes. Other actuator endpoints, including
Prometheus, datasource-pool inspection, and DLQ replay, require the `X-Actuator-Token` header set
to `CHAT_ACTUATOR_ACCESS_TOKEN`; if the token is unset they are denied by default.

When a connection exceeds its queue limit, the server closes it with code `4008`. The existing
client reconnect and `/chat/message/sync` flow recovers persisted messages after reconnect.
