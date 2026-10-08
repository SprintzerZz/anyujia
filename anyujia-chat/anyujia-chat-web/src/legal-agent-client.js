const SUCCESS_CODE = 200000;

export class LegalAgentClient {
  constructor({ baseUrl = '', tokenProvider = () => '', userIdProvider = () => '' } = {}) {
    this.baseUrl = baseUrl.replace(/\/$/, '');
    this.tokenProvider = tokenProvider;
    this.userIdProvider = userIdProvider;
  }

  registerContract(payload) {
    return this.#json('/legal/contracts', {
      method: 'POST',
      body: JSON.stringify(payload),
    });
  }

  startAssessment(payload) {
    return this.#json('/legal/assessments', {
      method: 'POST',
      body: JSON.stringify(payload),
    });
  }

  getAssessment(jobId) {
    return this.#json(`/legal/assessments/${encodeURIComponent(jobId)}`, {
      method: 'GET',
    });
  }

  async #json(path, options) {
    const userId = String(await this.userIdProvider()).trim();
    if (!/^\d+$/.test(userId)) {
      throw new Error('当前用户 ID 必须是正整数');
    }
    const token = String(await this.tokenProvider()).trim();
    const headers = {
      'Content-Type': 'application/json; charset=utf-8',
      userId,
      userType: '1',
      userFrom: 'app',
      ...(options.headers || {}),
    };
    if (token) {
      headers.Authorization = token.startsWith('Bearer ') ? token : `Bearer ${token}`;
    }
    const response = await fetch(`${this.baseUrl}${path}`, {
      ...options,
      headers,
    });
    const payload = await response.json().catch(() => null);
    if (!response.ok || payload?.code !== SUCCESS_CODE) {
      throw new Error(payload?.msg || `请求失败：HTTP ${response.status}`);
    }
    return payload.data;
  }
}

export async function sha256Hex(text) {
  const bytes = new TextEncoder().encode(text);
  const digest = await crypto.subtle.digest('SHA-256', bytes);
  return [...new Uint8Array(digest)]
    .map((value) => value.toString(16).padStart(2, '0'))
    .join('');
}
