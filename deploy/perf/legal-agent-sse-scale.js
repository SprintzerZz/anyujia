import http from 'k6/http';
import { check, fail } from 'k6';
import { Counter, Trend } from 'k6/metrics';

const baseUrl = (__ENV.BASE_URL || 'http://127.0.0.1:18080').replace(/\/$/, '');
const tokens = (__ENV.TOKENS || __ENV.TOKEN || '').split(',').map((value) => value.trim()).filter(Boolean);
const taskIds = (__ENV.TASK_IDS || '').split(',').map((value) => value.trim()).filter(Boolean);
const targetVus = Number(__ENV.SSE_TARGET_VUS || 1000);
const rampDuration = __ENV.SSE_RAMP_DURATION || '5m';
const holdDuration = __ENV.SSE_HOLD_DURATION || '10m';
const userId = __ENV.USER_ID || '10001';
const userType = __ENV.USER_TYPE || '1';
const userFrom = __ENV.USER_FROM || 'app';
const connectLatency = new Trend('legal_agent_sse_connect_latency', true);
const connectFailures = new Counter('legal_agent_sse_connect_failures');

export const options = {
  scenarios: {
    sse_scale: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { target: targetVus, duration: rampDuration },
        { target: targetVus, duration: holdDuration },
        { target: 0, duration: '2m' },
      ],
      gracefulRampDown: '30s',
    },
  },
  thresholds: {
    legal_agent_sse_connect_latency: ['p(95)<1000', 'p(99)<3000'],
    legal_agent_sse_connect_failures: ['count<1'],
  },
};

function authHeaders() {
  if (!tokens.length) fail('TOKEN or TOKENS is required');
  return {
    Authorization: `Bearer ${tokens[(__VU - 1) % tokens.length]}`,
    userId,
    userType,
    userFrom,
  };
}

export default function () {
  if (!taskIds.length) {
    fail('TASK_IDS must contain pre-created pending Agent task IDs for SSE capacity tests');
  }
  const taskId = taskIds[(__VU - 1) % taskIds.length];
  const startedAt = Date.now();
  const response = http.get(`${baseUrl}/legal/agent/tasks/${taskId}/events`, {
    headers: authHeaders(),
    timeout: __ENV.SSE_TIMEOUT || '2m',
    tags: { api: 'legal-agent-sse-scale' },
  });
  connectLatency.add(Date.now() - startedAt);
  const ok = check(response, {
    'SSE connection response is 200': (res) => res.status === 200,
  });
  if (!ok) connectFailures.add(1);
}
