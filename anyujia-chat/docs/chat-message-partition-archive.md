# Chat Message Partition And Archive Plan

## Goal

Keep the online chat tables fast at million-to-billion message scale while preserving reliable reconnect sync for recent messages.

## Current Hot Tables

- `chat_message`: append-only message detail table.
- `chat_conversation`: one row per tenant-landlord-house publication conversation.
- `chat_user_conversation`: per-user conversation list index.

`chat_user_conversation` should remain unpartitioned because it is a small, hot index table. The large growth surface is `chat_message`.

## Recommended Rollout

1. Keep recent messages in `chat_message`.
2. Move cold messages to `chat_message_archive` by `created_at` in monthly batches.
3. Add physical partitioning only after archive traffic and table size justify it. For most MySQL deployments, archiving first is lower risk than altering the primary write table.
4. Keep `/chat/message/sync` on the hot table for online reconnect. Add an explicit history endpoint later if product needs cold-history browsing.

## Archive Table

Use the same columns and indexes needed by conversation history:

```sql
CREATE TABLE chat_message_archive LIKE chat_message;
ALTER TABLE chat_message_archive
  ADD KEY idx_chat_archive_created_at (created_at),
  ADD KEY idx_chat_archive_conversation_seq (conversation_id, conversation_seq);
```

## Batch Archive Job

Run during low traffic. Move messages older than the retention window, for example 180 days:

```sql
INSERT IGNORE INTO chat_message_archive
SELECT *
  FROM chat_message
 WHERE created_at < DATE_SUB(NOW(), INTERVAL 180 DAY)
 ORDER BY id
 LIMIT 5000;

DELETE m
  FROM chat_message m
  JOIN chat_message_archive a ON a.id = m.id
 WHERE m.created_at < DATE_SUB(NOW(), INTERVAL 180 DAY)
 LIMIT 5000;
```

Repeat until the affected row count is 0. Keep each batch small to avoid long locks and replication lag.

## Future Sharding Rule

When one MySQL primary can no longer handle writes, shard by `conversation_id`:

- route key: `conversation_id % shard_count`
- keep `chat_conversation` and `chat_user_conversation` in the primary metadata database
- store `chat_message` rows in shard databases
- continue using `conversation_seq` as the per-conversation ordering key

## Hot Conversation Strategy

The current service uses a row lock on `chat_conversation.next_message_seq` to preserve strict per-conversation order. This is correct for one-to-one tenant-landlord chats. If product introduces group chats or very hot conversations, use one of these:

- keep strict ordering and reject excess traffic with the conversation rate limiter
- move sequence allocation to a Redis `INCR` backed allocator and reconcile persisted gaps
- enqueue messages per conversation and persist them serially from a worker

For this project, the rate limiter is the safest first step because it protects the database without changing ordering semantics.
