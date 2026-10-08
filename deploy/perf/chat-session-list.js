import http from 'k6/http';
import { check, fail } from 'k6';

const baseUrl = (__ENV.BASE_URL || 'http://127.0.0.1:18080').replace(/\/$/, '');
const token = __ENV.TOKEN;
const targetRate = Number(__ENV.RATE || 20);
const steadyDuration = __ENV.DURATION || '2m';

export const options = {
  scenarios: {
    chat_session_list: {
      executor: 'ramping-arrival-rate',
      startRate: 1,
      timeUnit: '1s',
      preAllocatedVUs: Number(__ENV.PRE_VUS || 20),
      maxVUs: Number(__ENV.MAX_VUS || 100),
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
    http_req_duration: ['p(95)<500', 'p(99)<1000'],
  },
};

const params = {
  headers: {
    Authorization: `Bearer ${token}`,
    'Content-Type': 'application/json',
  },
  tags: { api: 'chat-session-list' },
};

function requestSessionList() {
  return http.post(`${baseUrl}/chat/session/list`, JSON.stringify({ count: 20 }), params);
}

function isSuccessful(response) {
  if (response.status !== 200) {
    return false;
  }
  try {
    return response.json('code') === 200000;
  } catch (_) {
    return false;
  }
}

export function setup() {
  if (!token) {
    fail('TOKEN environment variable is required');
  }
  const response = requestSessionList();
  if (!isSuccessful(response)) {
    fail(`preflight failed: status=${response.status}, body=${response.body}`);
  }
}

export default function () {
  const response = requestSessionList();
  check(response, {
    'HTTP 200': (res) => res.status === 200,
    'business code 200000': (res) => isSuccessful(res),
  });
}
