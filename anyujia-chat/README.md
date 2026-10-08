# Chat module

## Realtime transports

The service keeps the existing Spring WebSocket endpoint on the HTTP service port. It now uses a
bounded per-connection outbound queue, so RabbitMQ consumers and request threads do not write to a
`WebSocketSession` concurrently.

An opt-in Netty transport exposes the same `/chat/websocket` protocol on a separate port. It uses
Netty EventLoops for connection state and writes, a bounded worker pool for blocking chat business
operations, channel write-buffer watermarks for backpressure, and one shared hashed-wheel timer for
idle connections.

Relevant environment variables:

| Variable | Default | Purpose |
| --- | ---: | --- |
| `CHAT_NETTY_ENABLED` | `false` | Start the Netty WebSocket listener. |
| `CHAT_NETTY_BIND_HOST` | `127.0.0.1` | Bind address; set `0.0.0.0` only on a trusted internal network. |
| `CHAT_NETTY_PORT` | `18084` | Netty listener port. |
| `CHAT_NETTY_MAX_QUEUED_MESSAGES` | `256` | Per-connection outbound message limit. |
| `CHAT_NETTY_MAX_QUEUED_BYTES` | `2097152` | Per-connection outbound byte limit. |
| `CHAT_NETTY_WRITE_LOW_WATER_MARK` | `32768` | Resume-write watermark. |
| `CHAT_NETTY_WRITE_HIGH_WATER_MARK` | `65536` | Pause-write watermark. |

The Netty listener trusts the `userId` and `userFrom` headers inserted by the gateway after its
single-use WebSocket ticket check. Do not expose this listener directly to untrusted clients. Set
the gateway's `CHAT_WEBSOCKET_URI` to the internal listener, for example
`ws://frameworkjava-chat:18084`. The development Compose file already applies these settings.

When a connection exceeds its queue limit, the server closes it with code `4008`. The existing
client reconnect and `/chat/message/sync` flow recovers persisted messages after reconnect.
