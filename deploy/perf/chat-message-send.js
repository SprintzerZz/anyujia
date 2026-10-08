import http from 'k6/http';
import { check, fail } from 'k6';

const baseUrl = (__ENV.BASE_URL || 'http://127.0.0.1:18080').replace(/\/$/, '');
const token = __ENV.TOKEN;
const sessionId = __ENV.SESSION_ID || '1';
const housePublishId = __ENV.HOUSE_PUBLISH_ID || '900000000000000001';
const targetRate = Number(__ENV.RATE || 5);
const steadyDuration = __ENV.DURATION || '1m';

export const options = {
  scenarios: {
    chat_message_send: {
      executor: 'ramping-arrival-rate',
      startRate: 1,
      timeUnit: '1s',
      preAllocatedVUs: Number(__ENV.PRE_VUS || 10),
      maxVUs: Number(__ENV.MAX_VUS || 50),
      stages: [
        { target: targetRate, duration: '30s' },
        { target: targetRate, duration: steadyDuration },
        { target: 0, duration: '30s' },
      ],
      gracefulStop: '10s',
    },
  },
  thresholds: {
    checks: ['rate>0.99'],
    http_req_failed: ['rate<0.01'],
    http_req_duration: ['p(95)<800', 'p(99)<1500'],
  },
};

const params = {
  headers: {
    Authorization: `Bearer ${token}`,
    'Content-Type': 'application/json',
  },
  tags: { api: 'chat-message-send' },
};

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

export function setup() {
  if (!token) {
    fail('TOKEN environment variable is required');
  }
  const response = http.post(
    `${baseUrl}/chat/session/has_house`,
    JSON.stringify({ sessionId, housePublishId }),
    params,
  );
  if (response.status !== 200 || response.json('code') !== 200000 || response.json('data') !== true) {
    fail(`preflight failed: status=${response.status}, body=${response.body}`);
  }
}

export default function () {
  const clientMessageId = `k6-${__VU}-${__ITER}-${Date.now()}`;
  const response = http.post(
    `${baseUrl}/chat/message/send`,
    JSON.stringify({
      sessionId,
      clientMessageId,
      type: 0,
      content: `k6 write test ${clientMessageId}`,
    }),
    params,
  );
  check(response, {
    'HTTP 200': (res) => res.status === 200,
    'message persisted': (res) => isSuccessful(res),
  });
}
