import http from 'k6/http';
import { check, fail } from 'k6';

const baseUrl = (__ENV.BASE_URL || 'http://127.0.0.1:18080').replace(/\/$/, '');
const token = __ENV.TOKEN;
const sessionId = __ENV.SESSION_ID || '1';
const clientMessageId = __ENV.CLIENT_MESSAGE_ID;

export const options = {
  scenarios: {
    same_message_concurrency: {
      executor: 'shared-iterations',
      vus: Number(__ENV.VUS || 50),
      iterations: Number(__ENV.ITERATIONS || 100),
      maxDuration: '30s',
    },
  },
  thresholds: {
    checks: ['rate==1'],
    http_req_failed: ['rate==0'],
    http_req_duration: ['p(95)<1000', 'p(99)<2000'],
  },
};

const params = {
  headers: {
    Authorization: `Bearer ${token}`,
    'Content-Type': 'application/json',
  },
  tags: { api: 'chat-message-idempotency' },
};

export function setup() {
  if (!token) {
    fail('TOKEN environment variable is required');
  }
  if (!clientMessageId) {
    fail('CLIENT_MESSAGE_ID environment variable is required');
  }
}

export default function () {
  const response = http.post(
    `${baseUrl}/chat/message/send`,
    JSON.stringify({
      sessionId,
      clientMessageId,
      type: 0,
      content: `k6 idempotency test ${clientMessageId}`,
    }),
    params,
  );

  check(response, {
    'HTTP 200': (res) => res.status === 200,
    'same request accepted': (res) => {
      try {
        return res.json('code') === 200000 && Boolean(res.json('data.messageId'));
      } catch (_) {
        return false;
      }
    },
  });
}
