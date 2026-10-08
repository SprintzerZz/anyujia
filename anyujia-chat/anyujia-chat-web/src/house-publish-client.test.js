import assert from 'node:assert/strict';
import test from 'node:test';
import { HousePublishClient, normalizePublicationForm } from './house-publish-client.js';

test('normalizes structured publication form values', () => {
  const form = new FormData();
  form.set('title', '  南山整租两居室  ');
  form.set('city', '深圳');
  form.set('district', '南山');
  form.set('address', '科技园 1 号');
  form.set('monthlyRent', '5200');
  form.set('areaSqm', '68.5');
  form.set('rentType', 'whole');
  form.set('bedroomCount', '2');
  form.set('livingRoomCount', '1');
  form.set('bathroomCount', '1');
  form.set('depositMonths', '1');
  form.set('paymentCycleMonths', '3');
  form.append('facilities', '空调');
  form.append('facilities', '洗衣机');

  const value = normalizePublicationForm(form, true);
  assert.equal(value.title, '南山整租两居室');
  assert.equal(value.monthlyRent, 5200);
  assert.equal(value.areaSqm, 68.5);
  assert.deepEqual(value.facilities, ['空调', '洗衣机']);
  assert.equal(value.publishNow, true);
});

test('client sends bearer token and surfaces application errors', async () => {
  const originalFetch = globalThis.fetch;
  let request;
  globalThis.fetch = async (url, options) => {
    request = { url, options };
    return { ok: true, status: 200, json: async () => ({ code: 401004, msg: '仅房东账号可以发布房源' }) };
  };
  try {
    const client = new HousePublishClient({ baseUrl: 'http://localhost:5173/', tokenProvider: () => 'abc' });
    await assert.rejects(() => client.listMine(), /仅房东账号/);
    assert.equal(request.url, 'http://localhost:5173/house/publish/mine');
    assert.equal(request.options.headers.Authorization, 'Bearer abc');
  } finally {
    globalThis.fetch = originalFetch;
  }
});
