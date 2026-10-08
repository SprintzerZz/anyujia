# Anyujia chat web

## Start

The development server proxies HTTP and WebSocket requests to Gateway, so the
browser does not require a temporary CORS exception.

PowerShell:

```powershell
cd D:\Project\frame\anyujia-frameworkjava\anyujia-chat\anyujia-chat-web
$env:CHAT_GATEWAY_TARGET = "http://127.0.0.1:18080"
npm install
npm run dev
```

Open `http://127.0.0.1:5173/`.

## Landlord publication test

1. Apply `deploy/dev/app/mysql/migration/V10__house_publish_details.sql` once.
2. Start Gateway and `anyujia-house-service` in addition to the Vite development server.
3. Open `http://127.0.0.1:5173/#publish` or click **发布房源** in the header.
4. Paste an authenticated, approved landlord token into **房东访问令牌**.
5. Complete the required location, rent, and area fields. The card on the right updates immediately.
6. Click **保存草稿** to create status `0`, or **提交审核** to create status `1` with audit status `0`.
7. Click the refresh icon under **我的房源** and verify the new record and state badges appear.

The Vite server proxies `/house/**` to the same Gateway target used by chat. A submitted
publication remains unavailable to tenant chat until a separate moderation flow changes
`audit_status` to `2`; the landlord publication API never self-approves it.

## Two-user test

1. Open the page in a normal browser window and an incognito window.
2. Use the tenant token and user ID `1` in the first window.
3. Use the landlord token and user ID `2` in the second window.
4. Use session ID `1` and sequence `0` in both windows.
5. Connect both windows, then send messages in both directions.

Each message displays its `conversationSeq`. The sequence must increase within
the conversation, regardless of which user sends the message.

## Reconnect test

1. Connect one window and send a message.
2. Click **模拟网络中断**.
3. Verify the event log changes through `等待重连`, `正在连接`, `同步消息`, and
   `连接正常`.
4. Send a message from the other window while the first window is reconnecting.
5. Verify the first window receives the missing message after synchronization
   and does not render it twice.

## Cursor recovery test

1. Disconnect the first window.
2. Send several messages from the second window.
3. Reconnect the first window without changing its saved sequence.
4. Verify only messages after the saved cursor are appended.
5. Click the reset icon next to **连接配置**, reconnect, and verify the full
   conversation is synchronized from sequence `0`.

## Idempotency test

The client keeps unacknowledged messages in memory and resends them with the
same `clientMessageId` after reconnect. After a reconnect test, verify MySQL:

```sql
SELECT client_message_id, COUNT(*) AS row_count
FROM chat_message
GROUP BY client_message_id
HAVING COUNT(*) > 1;
```

The query must return no rows.

## Sequence verification

```sql
SELECT conversation_id,
       COUNT(*) AS message_count,
       COUNT(DISTINCT conversation_seq) AS distinct_sequence_count,
       MIN(conversation_seq) AS min_sequence,
       MAX(conversation_seq) AS max_sequence
FROM chat_message
GROUP BY conversation_id;
```

`message_count` and `distinct_sequence_count` must be equal. Sequence gaps are
allowed; duplicate or decreasing sequence values are not.

## Connection recovery behavior

- The client sends an application heartbeat every 30 seconds and closes the
  socket when a Pong is not received within 15 seconds.
- Retryable failures use exponential backoff with jitter, capped at 30 seconds.
- Browser `online` events bypass the remaining backoff delay; `offline` events
  close an open socket so recovery starts immediately.
- Authentication, authorization, invalid-request failures, and close codes
  `1000`, `4003`, and `4004` stop automatic reconnect.
- Unacknowledged outgoing messages are stored in IndexedDB and resent with the
  same `clientMessageId` after reconnect or page reload.
- Reconnect first synchronizes messages after the last conversation sequence,
  then drains live messages received during synchronization and resends the
  persisted outbox.

Run the client unit tests and production build with:

```powershell
npm test
npm run build
```
