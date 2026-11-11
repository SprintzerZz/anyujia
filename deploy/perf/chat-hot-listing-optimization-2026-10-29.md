# Hot-listing delivery optimization — 2026-10-29

The load generator uses 100 distinct tenants in 100 one-to-one conversations
with one landlord, one text and one 197 KB image per tenant. Text sends use
100-way concurrency; image uploads use eight-way concurrency. One landlord
WebSocket receives and acknowledges all messages. The test checks every inbox
row's unread increment, latest-message ID, and sort order.

The earlier 11–16 second figures were **not first-delivery latency**. The
runner overwrote the first push timestamp with later retry frames, and its
`delivery_ack` lacked the required `version` and `requestId`. The server did
not accept those acknowledgements. The runner now records the first frame,
counts duplicates, and sends valid ACKs. In the corrected runs, Redis pending
delivery count returned to zero.

| Corrected run | Outbox batch / workers / scheduler threads | Sent and received | First-push P50 | First-push P95 | Duplicate frames |
| --- | --- | ---: | ---: | ---: | ---: |
| Original tuning | 100 / 4 / 1 | 200/200 | 3.44 s | 5.13 s | 0 |
| Optimized tuning, run 1 | 250 / 8 / 4 | 200/200 | 0.50 s | 1.25 s | 0 |
| Optimized tuning, final run | 250 / 8 / 4 | 200/200 | 0.78 s | 2.57 s | 0 |

The final run also had 100/100 image uploads, 100/100 inbox checks, image
upload P95 1.31 s, and send API P95 0.55 s. Runs used the same application
code; the comparison changed only the three tuning values. These are local
single-node samples, so they show an improvement in this environment, not a
production capacity guarantee.

Phase metrics located the wait before RabbitMQ consumption. In one diagnostic
run, RabbitMQ queue delay peaked at 0.41 s while sampled Outbox rows spent
1–2 seconds between enqueue and completion. The single scheduled thread had
also handled reconciliation and cleanup. The optimized configuration uses a
four-thread scheduler, a 250-row Outbox batch, and eight dispatch workers.
RabbitMQ remains the transport; adding Kafka, NATS, or another broker would
not have corrected the invalid ACKs or scheduler contention.

A separate failure occurred when Docker's clock moved backward by 149–456 ms:
MyBatis Snowflake ID generation rejected four sends in one diagnostic run.
`ChatIdWorker` now retries that specific failure for at most two seconds and is
used for chat messages, conversations, sharded messages, and Outbox rows. Unit
tests cover recovery and unrelated errors. The final 100-tenant run had no
send failures; the test did not intentionally force a live clock rollback.

Raw results: [original tuning](results/chat-hot-listing-100-original-tuning-2026-10-29.json),
[optimized run 1](results/chat-hot-listing-100-valid-ack-2026-10-29.json),
[final run](results/chat-hot-listing-100-final-2026-10-29.json).

Limits: local Docker stack, direct chat-service identity headers, one chat
node, one local load generator, and local S3-compatible image storage. Cross-node
delivery and Gateway authentication were not part of this test.
