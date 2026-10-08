# Chat realtime browser client

`chat-realtime-client.js` is a framework-neutral browser client for the chat
WebSocket protocol. It provides:

- a fresh one-time ticket for every connection attempt;
- heartbeat every 45 seconds and a 75-second pong timeout;
- exponential backoff with equal jitter, capped at 30 seconds;
- reconnect synchronization by `conversationSeq`;
- an in-memory outbox that resends unacknowledged messages with the original
  `clientMessageId`.

Example:

```js
import { ChatRealtimeClient } from './chat-realtime-client.js';

const client = new ChatRealtimeClient({
  baseUrl: 'https://api.example.com',
  sessionId: '1',
  tokenProvider: async () => localStorage.getItem('accessToken'),
  onMessage: (message) => console.log(message),
  onStateChange: (state, error) => console.log(state, error),
});

client.start(localStorage.getItem('chat:1:lastSeq') || '0');
```

Persist the latest delivered `conversationSeq` in the application state. The
sample outbox is memory-only; a mobile client that must survive process restarts
should persist pending messages locally until `chat_ack` is received.
