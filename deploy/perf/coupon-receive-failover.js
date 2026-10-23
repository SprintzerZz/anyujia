import exec from 'k6/execution';
import http from 'k6/http';
import { check, fail } from 'k6';

const baseUrl = (__ENV.BASE_URL || 'http://127.0.0.1:18080').replace(/\/$/, '');
const tokens = (__ENV.TOKENS || __ENV.TOKEN || '').split(',').map((token) => token.trim()).filter(Boolean);
const templateId = Number(__ENV.TEMPLATE_ID || 0);
const userType = Number(__ENV.USER_TYPE || 1);
const targetRate = Number(__ENV.RATE || 200);

export const options = {
  scenarios: {
    receive_during_redis_failover: {
      executor: 'ramping-arrival-rate',
      startRate: 10,
      timeUnit: '1s',
      preAllocatedVUs: Number(__ENV.PRE_VUS || 100),
      maxVUs: Number(__ENV.MAX_VUS || 1000),
      stages: [
        { target: targetRate, duration: __ENV.RAMP_DURATION || '20s' },
        { target: targetRate, duration: __ENV.STEADY_DURATION || '90s' },
        { target: 0, duration: '20s' },
      ],
      gracefulStop: '10s',
    },
  },
  thresholds: {
    http_req_duration: ['p(95)<800', 'p(99)<2000'],
  },
};

export function setup() {
  if (!tokens.length) {
    fail('TOKEN or TOKENS environment variable is required');
  }
  if (!templateId) {
    fail('TEMPLATE_ID environment variable is required');
  }
}

function pickToken() {
  return tokens[(exec.vu.idInTest + exec.scenario.iterationInTest) % tokens.length];
}

function userId() {
  return Number(__ENV.USER_ID || (1000000 + exec.vu.idInTest * 100000 + exec.scenario.iterationInTest));
}

function isExpectedResponse(response) {
  if (response.status !== 200) {
    return false;
  }
  const code = response.json('code');
  return code === 200000 || code === 400000 || code === 500000;
}

export default function () {
  const requestId = `k6-failover-${exec.vu.idInTest}-${exec.scenario.iterationInTest}-${Date.now()}`;
  const response = http.post(
    `${baseUrl}/coupon/receive/async`,
    JSON.stringify({ templateId, requestId }),
    {
      headers: {
        Authorization: `Bearer ${pickToken()}`,
        'Content-Type': 'application/json',
        userId: String(userId()),
        userType: String(userType),
        userFrom: 'app',
      },
      tags: { api: 'coupon-receive-failover' },
    },
  );
  check(response, {
    'expected receive response': isExpectedResponse,
  });
}
