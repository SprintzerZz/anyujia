import http from 'k6/http';
import { check } from 'k6';
import { Counter, Trend } from 'k6/metrics';

const baseUrl = (__ENV.BASE_URL || 'http://127.0.0.1:18091').replace(/\/$/, '');
const vus = Number(__ENV.VUS || 100);
const iterations = Number(__ENV.ITERATIONS || 1000);

const latency = new Trend('legal_entailment_latency', true);
const failures = new Counter('legal_entailment_failures');

export const options = {
  scenarios: {
    entailment: {
      executor: 'shared-iterations',
      vus,
      iterations,
      maxDuration: __ENV.MAX_DURATION || '10m',
    },
  },
  thresholds: {
    legal_entailment_latency: ['p(95)<3000', 'p(99)<5000'],
    legal_entailment_failures: ['count<1'],
    http_req_failed: ['rate<0.01'],
  },
};

const cases = [
  {
    premise: '出租人应承担房屋自然损耗',
    hypothesis: '正常使用造成的自然损耗由出租人承担',
  },
  {
    premise: '租赁期间自然损耗由出租人承担',
    hypothesis: '正常使用造成的自然损耗不应由承租人承担',
  },
  {
    premise: '合同约定承租人承担全部维修费用',
    hypothesis: '房屋主体结构维修通常不当然由承租人承担',
  },
];

export default function () {
  const item = cases[(__VU + __ITER) % cases.length];
  const response = http.post(`${baseUrl}/entailment`, JSON.stringify(item), {
    headers: { 'Content-Type': 'application/json' },
    tags: { api: 'legal-entailment' },
    timeout: '30s',
  });
  latency.add(response.timings.duration);
  const ok = check(response, {
    'entailment status is 200': (res) => res.status === 200,
    'entailment returns nli': (res) => res.status === 200 && res.json('model_type') === 'nli',
    'entailment returns label': (res) => res.status === 200 && Boolean(res.json('label')),
  });
  if (!ok) failures.add(1);
}
