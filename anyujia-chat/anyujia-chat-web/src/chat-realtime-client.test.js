import assert from 'node:assert/strict';
import test from 'node:test';
import {
  ChatRealtimeClient,
  ChatRequestError,
} from '../../../deploy/client/chat-realtime-client.js';

globalThis.WebSocket = { CONNECTING: 0, OPEN: 1 };

function createClient(options = {}) {
  return new ChatRealtimeClient({
    baseUrl: 'http://localhost',
    sessionId: '1',
    tokenProvider: async () => 'token',
    ...options,
  });
}

test('restores persisted outbox before connecting', async () => {
  const requests = [{
    sessionId: '1',
    clientMessageId: 'pending-1',
    type: 0,
    content: 'hello',
  }];
  const client = createClient({ outboxStore: { load: async () => requests } });
  let connected = false;
  client.connect = async () => { connected = true; };

  client.start('7');
  await new Promise((resolve) => setImmediate(resolve));

  assert.equal(connected, true);
  assert.deepEqual(client.outbox.get('pending-1'), requests[0]);
  assert.equal(client.lastConversationSeq, '7');
  client.stop();
});

test('persists outgoing messages and deletes them after acknowledgement', async () => {
  const writes = [];
  const deletes = [];
  const client = createClient({
    outboxStore: {
      load: async () => [],
      put: async (request) => writes.push(request.clientMessageId),
      delete: async (clientMessageId) => deletes.push(clientMessageId),
    },
  });

  client.send('hello', 0, 'message-1');
  client.socket = {};
  client.onFrame(client.socket, JSON.stringify({
    type: 'chat_ack',
    data: { clientMessageId: 'message-1', conversationSeq: '1' },
  }));
  await client.outboxWrite;

  assert.deepEqual(writes, ['message-1']);
  assert.deepEqual(deletes, ['message-1']);
  assert.equal(client.outbox.has('message-1'), false);
});

test('retries transient request failures but stops on authentication failures', () => {
  const client = createClient();

  assert.equal(client.shouldRetryRequest(new TypeError('network failed')), true);
  assert.equal(client.shouldRetryRequest(new ChatRequestError('busy', 503, 500000)), true);
  assert.equal(client.shouldRetryRequest(new ChatRequestError('expired', 401, 401003)), false);
  assert.equal(client.shouldRetryRequest(new ChatRequestError('invalid', 200, 400000)), false);
  assert.equal(client.shouldRetryRequest(new ChatRequestError('server', 200, 500000)), true);
});

test('classifies terminal and retryable WebSocket close codes', () => {
  const client = createClient();

  assert.equal(client.shouldRetryClose(1000), false);
  assert.equal(client.shouldRetryClose(4003), false);
  assert.equal(client.shouldRetryClose(4004), false);
  assert.equal(client.shouldRetryClose(1006), true);
  assert.equal(client.shouldRetryClose(1012), true);
  assert.equal(client.shouldRetryClose(4000), true);
});
