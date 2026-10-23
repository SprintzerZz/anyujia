import http from 'k6/http';
import { check } from 'k6';

const baseUrl = (__ENV.BASE_URL || 'http://127.0.0.1:18083').replace(/\/$/, '');
const targetRate = Number(__ENV.RATE || 40);
const steadyDuration = __ENV.DURATION || '5m';
const requestTimeout = __ENV.REQUEST_TIMEOUT || '5s';
const runId = (__ENV.RUN_ID || 'k6-direct').replace(/[^a-zA-Z0-9_-]/g, '-');
const targets = (__ENV.CHAT_TARGETS || '')
  .split(',')
  .map((value) => value.trim())
  .filter(Boolean)
  .map((value) => {
    const separator = value.indexOf(':');
    if (separator < 1) throw new Error(`Invalid CHAT_TARGETS entry: ${value}`);
    return { userId: value.slice(0, separator), sessionId: value.slice(separator + 1) };
  });
if (targets.length === 0) {
  const userIds = (__ENV.USER_IDS || __ENV.USER_ID || '1').split(',').map((id) => id.trim()).filter(Boolean);
  const sessionIds = (__ENV.SESSION_IDS || __ENV.SESSION_ID || '900000000000100001')
    .split(',').map((id) => id.trim()).filter(Boolean);
  for (let i = 0; i < Math.max(userIds.length, sessionIds.length); i += 1) {
    targets.push({ userId: userIds[i % userIds.length], sessionId: sessionIds[i % sessionIds.length] });
  }
}

export const options = {
  summaryTrendStats: ['avg', 'min', 'med', 'max', 'p(90)', 'p(95)', 'p(99)'],
  scenarios: {
    chat_message_throughput_direct: {
      executor: 'ramping-arrival-rate',
      startRate: 1,
      timeUnit: '1s',
      preAllocatedVUs: Number(__ENV.PRE_VUS || 64),
      maxVUs: Number(__ENV.MAX_VUS || 256),
      stages: [
        { target: targetRate, duration: __ENV.RAMP_DURATION || '30s' },
        { target: targetRate, duration: steadyDuration },
        { target: 0, duration: '10s' },
      ],
      gracefulStop: '10s',
    },
  },
  thresholds: {
    checks: [{
      threshold: `rate>${__ENV.MIN_CHECK_RATE || '0.99'}`,
      abortOnFail: true,
      delayAbortEval: '15s',
    }],
    http_req_failed: [{
      threshold: `rate<${__ENV.MAX_ERROR_RATE || '0.01'}`,
      abortOnFail: true,
      delayAbortEval: '15s',
    }],
    http_req_duration: [
      {
        threshold: `p(95)<${__ENV.MAX_P95_MS || '500'}`,
        abortOnFail: true,
        delayAbortEval: '20s',
      },
      {
        threshold: `p(99)<${__ENV.MAX_P99_MS || '1200'}`,
        abortOnFail: true,
        delayAbortEval: '20s',
      },
    ],
    dropped_iterations: [{ threshold: 'count<1', abortOnFail: true, delayAbortEval: '10s' }],
  },
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

export default function () {
  const target = targets[(__VU + __ITER) % targets.length];
  const userId = target.userId;
  const sessionId = target.sessionId;
  const clientMessageId = `${runId}-${__VU}-${__ITER}-${Date.now()}`;
  const response = http.post(
    `${baseUrl}/chat/message/send`,
    JSON.stringify({
      sessionId,
      clientMessageId,
      type: 0,
      content: `k6 direct throughput ${clientMessageId}`,
    }),
    {
      headers: {
        'Content-Type': 'application/json',
        userId,
        userFrom: 'app',
      },
      timeout: requestTimeout,
      tags: { api: 'chat-message-throughput-direct' },
    },
  );
  check(response, {
    'HTTP 200': (res) => res.status === 200,
    'message persisted': (res) => isSuccessful(res),
  });
}
