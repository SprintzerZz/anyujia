import http from 'k6/http';
import { check, fail } from 'k6';

const baseUrl = (__ENV.BASE_URL || 'http://127.0.0.1:18080').replace(/\/$/, '');
const tokens = (__ENV.TOKENS || __ENV.TOKEN || '').split(',').map((token) => token.trim()).filter(Boolean);
const sessionIds = (__ENV.SESSION_IDS || __ENV.SESSION_ID || '').split(',').map((id) => id.trim()).filter(Boolean);
const targetRate = Number(__ENV.RATE || 1000);
const steadyDuration = __ENV.DURATION || '10m';

export const options = {
  scenarios: {
    chat_message_throughput: {
      executor: 'ramping-arrival-rate',
      startRate: 10,
      timeUnit: '1s',
      preAllocatedVUs: Number(__ENV.PRE_VUS || 500),
      maxVUs: Number(__ENV.MAX_VUS || 5000),
      stages: [
        { target: targetRate, duration: __ENV.RAMP_DURATION || '2m' },
        { target: targetRate, duration: steadyDuration },
        { target: 0, duration: '1m' },
      ],
      gracefulStop: '30s',
    },
  },
  thresholds: {
    checks: ['rate>0.99'],
    http_req_failed: ['rate<0.01'],
    http_req_duration: ['p(95)<500', 'p(99)<1200'],
  },
};

function pick(values, label) {
  if (!values.length) {
    fail(`${label} environment variable is required`);
  }
  return values[(__VU + __ITER) % values.length];
}

function isSuccessful(response) {
  if (response.status !== 200) {
    return false;
  }
  try {
    return response.json('code') === 200000 && Boolean(response.json('data.messageId'));
  } catch (_) {
    return false;
  }
}

export default function () {
  const token = pick(tokens, 'TOKENS or TOKEN');
  const sessionId = pick(sessionIds, 'SESSION_IDS or SESSION_ID');
  const clientMessageId = `k6-throughput-${__VU}-${__ITER}-${Date.now()}`;
  const response = http.post(
    `${baseUrl}/chat/message/send`,
    JSON.stringify({
      sessionId,
      clientMessageId,
      type: 0,
      content: `k6 throughput ${clientMessageId}`,
    }),
    {
      headers: {
        Authorization: `Bearer ${token}`,
        'Content-Type': 'application/json',
      },
      tags: { api: 'chat-message-throughput' },
    },
  );
  check(response, {
    'HTTP 200': (res) => res.status === 200,
    'message persisted': (res) => isSuccessful(res),
  });
}
