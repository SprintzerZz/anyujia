const SUCCESS_CODE = 200000;

export class HousePublishClient {
  constructor({ baseUrl = '', tokenProvider = () => '' } = {}) {
    this.baseUrl = baseUrl.replace(/\/$/, '');
    this.tokenProvider = tokenProvider;
  }

  create(payload) {
    return this.#json('/house/publish', {
      method: 'POST',
      body: JSON.stringify(payload),
    });
  }

  listMine() {
    return this.#json('/house/publish/mine', { method: 'GET' });
  }

  audit(publishId, action, remark = '') {
    return this.#json(`/house/publish/${publishId}/audit`, {
      method: 'POST',
      body: JSON.stringify({ action, remark }),
    });
  }

  listPublic(limit = 30) {
    return this.#json(`/house/publications?limit=${encodeURIComponent(limit)}`, { method: 'GET' });
  }

  detailPublic(publishId) {
    return this.#json(`/house/publications/${publishId}`, { method: 'GET' });
  }

  startChat(housePublishId) {
    return this.#json('/chat/session/add', {
      method: 'POST',
      body: JSON.stringify({ housePublishId }),
    });
  }

  async uploadImage(file) {
    const form = new FormData();
    form.append('file', file);
    return this.#request('/file/upload', {
      method: 'POST',
      body: form,
    }, false);
  }

  async #json(path, options) {
    return this.#request(path, {
      ...options,
      headers: {
        'Content-Type': 'application/json',
        ...(options.headers || {}),
      },
    });
  }

  async #request(path, options, mergeJsonHeader = true) {
    const token = String(await this.tokenProvider()).trim();
    if (!token) throw new Error('请输入访问令牌');
    const headers = {
      Authorization: token.startsWith('Bearer ') ? token : `Bearer ${token}`,
      ...(options.headers || {}),
    };
    if (!mergeJsonHeader) {
      delete headers['Content-Type'];
    }
    const response = await fetch(`${this.baseUrl}${path}`, {
      ...options,
      headers,
    });
    const payload = await response.json().catch(() => null);
    if (!response.ok || payload?.code !== SUCCESS_CODE) {
      throw new Error(payload?.msg || `请求失败，HTTP ${response.status}`);
    }
    return payload.data;
  }
}

function numberValue(formData, name, fallback = 0) {
  const raw = String(formData.get(name) ?? '').trim();
  return raw === '' ? fallback : Number(raw);
}

export function normalizePublicationForm(formData, publishNow, images = []) {
  const coverImageUrl = String(formData.get('coverImageUrl') ?? '').trim()
    || images[0]?.url
    || null;
  return {
    title: String(formData.get('title') ?? '').trim(),
    city: String(formData.get('city') ?? '').trim(),
    district: String(formData.get('district') ?? '').trim(),
    address: String(formData.get('address') ?? '').trim(),
    monthlyRent: numberValue(formData, 'monthlyRent'),
    areaSqm: numberValue(formData, 'areaSqm'),
    rentType: String(formData.get('rentType') || 'whole'),
    bedroomCount: numberValue(formData, 'bedroomCount', 1),
    livingRoomCount: numberValue(formData, 'livingRoomCount', 1),
    bathroomCount: numberValue(formData, 'bathroomCount', 1),
    depositMonths: numberValue(formData, 'depositMonths', 1),
    paymentCycleMonths: numberValue(formData, 'paymentCycleMonths', 1),
    propertyFee: numberValue(formData, 'propertyFee'),
    availableFrom: String(formData.get('availableFrom') || '') || null,
    description: String(formData.get('description') ?? '').trim() || null,
    rentalRequirements: String(formData.get('rentalRequirements') ?? '').trim() || null,
    facilities: formData.getAll('facilities').map(String),
    coverImageUrl,
    images,
    publishNow,
  };
}
