# Hot-listing chat load test — 2026-10-29

The push latencies below used the last retry frame and therefore overstate
first delivery. See the [corrected optimization report](chat-hot-listing-optimization-2026-10-29.md)
for valid-ACK, first-frame measurements.

Scenario: 100 distinct tenants, 100 one-to-one conversations with landlord 2,
all for listing `900000000000000001`. Each tenant sent one text and one 197 KB
PNG. The landlord had one active WebSocket. The test also checked all 100 inbox
rows, unread increments, latest-message IDs, and descending inbox order.

| Measure | 100 concurrent image uploads | 8 concurrent image uploads |
| --- | ---: | ---: |
| Text messages sent | 100/100 | 100/100 |
| Image uploads and messages sent | 71/100 | 100/100 |
| Delivered WebSocket pushes | 171/171 successful sends | 200/200 |
| Inbox unread/latest/order | 100/100 | 100/100 |
| Image upload P50 / P95 | 11.47 s / 15.52 s | 0.79 s / 1.29 s |
| Send API P50 / P95 | 0.50 s / 2.09 s | 0.12 s / 0.51 s |
| Send start to push P50 / P95 | 12.23 s / 17.31 s | 7.58 s / 11.88 s |
| Text send start to push P50 / P95 | — | 11.66 s / 11.89 s |
| Image send start to push P50 / P95 | — | 1.84 s / 7.05 s |

The 29 failed images in the burst run exhausted bounded upload retries after
HTTP 429 responses. The controlled run had no upload retries or failures. The
delivery P95 remained about 12 seconds in that run; this is an observed latency
problem and needs separate outbox/push-path diagnosis before claiming real-time
capacity at this load.

The initial burst runner did not send delivery acknowledgements; the controlled
runner did. Treat the runs as two stress scenarios rather than a controlled
before/after comparison. Both use unique message IDs and verify each push by
message ID and conversation ID.

The test exposed a conversation-index cache race: a new conversation could be
cached before its asynchronous MySQL index update, then remain absent from the
landlord inbox. `ChatConversationIndexCache.markFlushed` now invalidates a list
that is missing the calibrated conversation or holds an older sequence. A
single-tenant smoke run passed after the fix.

Evidence: [burst result](results/chat-hot-listing-100-2026-10-29.json),
[controlled result](results/chat-hot-listing-100-controlled-2026-10-29.json),
[smoke result](results/chat-hot-listing-smoke-fixed-2026-10-29.json).

Limits: one local chat node and one local load generator, direct service headers
instead of Gateway tokens, local S3-compatible image storage, no cross-node
failover. This is a functional concurrency test, not a production capacity
measurement.
