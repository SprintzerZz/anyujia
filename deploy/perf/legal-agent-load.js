import http from 'k6/http';
import { check, fail } from 'k6';
import { Counter, Trend } from 'k6/metrics';

const baseUrl = (__ENV.BASE_URL || 'http://127.0.0.1:18080').replace(/\/$/, '');
const tokens = (__ENV.TOKENS || __ENV.TOKEN || '').split(',').map((value) => value.trim()).filter(Boolean);
const userId = __ENV.USER_ID || '10001';
const userType = __ENV.USER_TYPE || '1';
const userFrom = __ENV.USER_FROM || 'app';
const enqueueRate = Number(__ENV.ENQUEUE_RATE || 500);
const enqueueDuration = __ENV.ENQUEUE_DURATION || '2m';
const e2eTargetVus = Number(__ENV.E2E_TARGET_VUS || 200);

const enqueueLatency = new Trend('legal_agent_enqueue_latency', true);
const e2eLatency = new Trend('legal_agent_e2e_latency', true);
const enqueueFailures = new Counter('legal_agent_enqueue_failures');
const sseFailures = new Counter('legal_agent_sse_failures');

const scenarios = {
    enqueue: {
      executor: 'constant-arrival-rate',
      rate: enqueueRate,
      timeUnit: '1s',
      duration: enqueueDuration,
      preAllocatedVUs: Math.min(Math.max(enqueueRate, 100), 1000),
      maxVUs: Math.max(enqueueRate * 2, 2000),
      exec: 'enqueueOnly',
    },
    end_to_end: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { target: e2eTargetVus, duration: '1m' },
        { target: e2eTargetVus, duration: '2m' },
        { target: 0, duration: '30s' },
      ],
      exec: 'submitAndStream',
      startTime: enqueueDuration,
    },
};

const selectedScenarios = __ENV.SCENARIO === 'enqueue'
  ? { enqueue: scenarios.enqueue }
  : __ENV.SCENARIO === 'end_to_end'
    ? { end_to_end: scenarios.end_to_end }
    : scenarios;

export const options = {
  scenarios: selectedScenarios,
  thresholds: {
    legal_agent_enqueue_latency: ['p(95)<200', 'p(99)<500'],
    legal_agent_e2e_latency: ['p(95)<8000', 'p(99)<15000'],
    legal_agent_enqueue_failures: ['count<1'],
    legal_agent_sse_failures: ['count<1'],
  },
};

function headers() {
  if (!tokens.length) {
    fail('TOKEN or TOKENS is required');
  }
  return {
    Authorization: `Bearer ${tokens[(__VU - 1) % tokens.length]}`,
    'Content-Type': 'application/json',
    userId,
    userType,
    userFrom,
  };
}

function submit() {
  const requestId = `legal-load-${__VU}-${__ITER}-${Date.now()}`;
  const response = http.post(`${baseUrl}/legal/agent/tasks`, JSON.stringify({
    requestId,
    chatConversationId: Number(__ENV.CHAT_CONVERSATION_ID || 1),
    query: __ENV.QUERY || '请分析当前租赁合同的押金、维修和提前解约风险',
    regionCode: __ENV.REGION_CODE || 'GLOBAL',
    asOfDate: __ENV.AS_OF_DATE || '',
  }), { headers: headers(), tags: { api: 'legal-agent-submit' } });
  enqueueLatency.add(response.timings.duration);
  const ok = check(response, {
    'agent enqueue status is 200': (res) => res.status === 200,
    'agent enqueue returns task id': (res) => Boolean(res.json('data.taskId')),
  });
  if (!ok) {
    enqueueFailures.add(1);
    return null;
  }
  return String(response.json('data.taskId'));
}

export function enqueueOnly() {
  submit();
}

export function submitAndStream() {
  const startedAt = Date.now();
  const taskId = submit();
  if (!taskId) return;
  const response = http.get(`${baseUrl}/legal/agent/tasks/${taskId}/events`, {
    headers: headers(),
    timeout: __ENV.SSE_TIMEOUT || '30s',
    tags: { api: 'legal-agent-sse' },
  });
  const ok = check(response, {
    'agent SSE status is 200': (res) => res.status === 200,
    'agent SSE contains event frame': (res) => res.body.includes('event:'),
  });
  if (!ok) sseFailures.add(1);
  e2eLatency.add(Date.now() - startedAt);
}
