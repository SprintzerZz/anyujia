import { HousePublishClient, normalizePublicationForm } from './house-publish-client.js';

const statusLabels = {
  0: '草稿',
  1: '已提交',
  2: '已下架',
  3: '已过期',
};

const auditLabels = {
  0: '待审核',
  1: '审核中',
  2: '已通过',
  3: '已驳回',
};

export function initHousePublishPage() {
  const state = {
    images: [],
    selectedPublic: null,
  };
  const els = collectElements();
  const landlordClient = new HousePublishClient({
    baseUrl: window.location.origin,
    tokenProvider: () => els.landlordToken.value,
  });
  const tenantClient = new HousePublishClient({
    baseUrl: window.location.origin,
    tokenProvider: () => els.tenantToken.value,
  });
  const adminClient = new HousePublishClient({
    baseUrl: window.location.origin,
    tokenProvider: () => els.adminToken.value,
  });

  restoreTokens(els);
  bindTabs(els);
  bindPublicationForm(els, state, landlordClient);
  bindTenantBrowse(els, state, tenantClient);
  bindAuditPanel(els, adminClient);
  renderPreview(els.form, els.preview, state.images);

  return {
    activate() {
      if (els.landlordToken.value.trim()) {
        void loadPublications(landlordClient, els);
      }
      if (els.tenantToken.value.trim()) {
        void loadPublicList(tenantClient, els, state);
      }
    },
  };
}

function collectElements() {
  return {
    landlordTab: document.querySelector('#landlordTabButton'),
    tenantTab: document.querySelector('#tenantTabButton'),
    auditTab: document.querySelector('#auditTabButton'),
    landlordPanel: document.querySelector('#landlordPanel'),
    tenantPanel: document.querySelector('#tenantPanel'),
    auditPanel: document.querySelector('#auditPanel'),
    form: document.querySelector('#publishForm'),
    landlordToken: document.querySelector('#publishTokenInput'),
    tenantToken: document.querySelector('#tenantTokenInput'),
    adminToken: document.querySelector('#adminTokenInput'),
    feedback: document.querySelector('#publishFeedback'),
    imageInput: document.querySelector('#publishImagesInput'),
    imageList: document.querySelector('#uploadedImageList'),
    mineList: document.querySelector('#publicationList'),
    mineEmpty: document.querySelector('#publicationEmpty'),
    refreshMine: document.querySelector('#refreshPublicationsButton'),
    publicList: document.querySelector('#publicHouseList'),
    publicEmpty: document.querySelector('#publicHouseEmpty'),
    publicFeedback: document.querySelector('#publicHouseFeedback'),
    refreshPublic: document.querySelector('#refreshPublicHousesButton'),
    detail: document.querySelector('#houseDetail'),
    startChat: document.querySelector('#startChatButton'),
    chatResult: document.querySelector('#startChatResult'),
    auditPublishId: document.querySelector('#auditPublishIdInput'),
    auditRemark: document.querySelector('#auditRemarkInput'),
    approve: document.querySelector('#approvePublicationButton'),
    reject: document.querySelector('#rejectPublicationButton'),
    auditFeedback: document.querySelector('#auditFeedback'),
    preview: {
      image: document.querySelector('#previewImage'),
      imagePlaceholder: document.querySelector('#previewImagePlaceholder'),
      title: document.querySelector('#previewTitle'),
      location: document.querySelector('#previewLocation'),
      price: document.querySelector('#previewPrice'),
      layout: document.querySelector('#previewLayout'),
      payment: document.querySelector('#previewPayment'),
      facilities: document.querySelector('#previewFacilities'),
      description: document.querySelector('#previewDescription'),
      gallery: document.querySelector('#previewGallery'),
    },
  };
}

function restoreTokens(els) {
  els.landlordToken.value = sessionStorage.getItem('housePublishToken')
    || sessionStorage.getItem('chatDebugToken') || '';
  els.tenantToken.value = sessionStorage.getItem('tenantBrowseToken')
    || sessionStorage.getItem('chatDebugToken') || '';
  els.adminToken.value = sessionStorage.getItem('houseAuditToken') || '';
}

function bindTabs(els) {
  const activate = (name) => {
    const isLandlord = name === 'landlord';
    const isTenant = name === 'tenant';
    els.landlordPanel.hidden = !isLandlord;
    els.tenantPanel.hidden = !isTenant;
    els.auditPanel.hidden = name !== 'audit';
    els.landlordTab.classList.toggle('nav-active', isLandlord);
    els.tenantTab.classList.toggle('nav-active', isTenant);
    els.auditTab.classList.toggle('nav-active', name === 'audit');
  };
  els.landlordTab.addEventListener('click', () => activate('landlord'));
  els.tenantTab.addEventListener('click', () => activate('tenant'));
  els.auditTab.addEventListener('click', () => activate('audit'));
  activate('landlord');
}

function bindPublicationForm(els, state, client) {
  els.form.addEventListener('input', () => renderPreview(els.form, els.preview, state.images));
  els.form.addEventListener('change', () => renderPreview(els.form, els.preview, state.images));
  els.imageInput.addEventListener('change', async () => {
    const files = [...els.imageInput.files].slice(0, 12 - state.images.length);
    if (files.length === 0) return;
    setFeedback(els.feedback, '正在上传图片...', 'loading');
    try {
      sessionStorage.setItem('housePublishToken', els.landlordToken.value.trim());
      for (const file of files) {
        const uploaded = await client.uploadImage(file);
        state.images.push({
          url: uploaded.url,
          key: uploaded.key || uploaded.objectName,
        });
      }
      renderImageList(els, state);
      renderPreview(els.form, els.preview, state.images);
      setFeedback(els.feedback, `已上传 ${state.images.length} 张图片`, 'success');
    } catch (error) {
      setFeedback(els.feedback, error.message, 'error');
    } finally {
      els.imageInput.value = '';
    }
  });
  els.form.addEventListener('submit', async (event) => {
    event.preventDefault();
    if (!els.form.reportValidity()) return;
    const publishNow = event.submitter?.dataset.action === 'submit';
    setBusy(els.form, true);
    setFeedback(els.feedback, '正在保存房源...', 'loading');
    try {
      sessionStorage.setItem('housePublishToken', els.landlordToken.value.trim());
      const result = await client.create(normalizePublicationForm(new FormData(els.form), publishNow, state.images));
      setFeedback(els.feedback, publishNow
        ? `房源 ${result.publishId} 已提交审核`
        : `草稿 ${result.publishId} 已保存`, 'success');
      els.auditPublishId.value = result.publishId;
      await loadPublications(client, els);
    } catch (error) {
      setFeedback(els.feedback, error.message, 'error');
    } finally {
      setBusy(els.form, false);
    }
  });
  els.refreshMine.addEventListener('click', () => loadPublications(client, els));
}

function bindTenantBrowse(els, state, client) {
  els.refreshPublic.addEventListener('click', () => loadPublicList(client, els, state));
  els.startChat.addEventListener('click', async () => {
    if (!state.selectedPublic) return;
    setFeedback(els.chatResult, '正在创建会话...', 'loading');
    try {
      sessionStorage.setItem('tenantBrowseToken', els.tenantToken.value.trim());
      const result = await client.startChat(state.selectedPublic.publishId);
      setFeedback(els.chatResult, `会话已创建：${result.sessionId}`, 'success');
      window.dispatchEvent(new CustomEvent('house-chat-created', {
        detail: {
          token: els.tenantToken.value.trim(),
          sessionId: result.sessionId,
        },
      }));
    } catch (error) {
      setFeedback(els.chatResult, error.message, 'error');
    }
  });
}

function bindAuditPanel(els, client) {
  const audit = async (action) => {
    const publishId = els.auditPublishId.value.trim();
    if (!/^\d+$/.test(publishId)) {
      setFeedback(els.auditFeedback, '请输入待审核的发布 ID', 'error');
      return;
    }
    setFeedback(els.auditFeedback, '正在提交审核结果...', 'loading');
    try {
      sessionStorage.setItem('houseAuditToken', els.adminToken.value.trim());
      const result = await client.audit(publishId, action, els.auditRemark.value.trim());
      setFeedback(els.auditFeedback, `${result.publishId} ${auditLabels[result.auditStatus]}`, 'success');
    } catch (error) {
      setFeedback(els.auditFeedback, error.message, 'error');
    }
  };
  els.approve.addEventListener('click', () => audit('approve'));
  els.reject.addEventListener('click', () => audit('reject'));
}

function renderPreview(form, preview, images) {
  const data = normalizePublicationForm(new FormData(form), false, images);
  preview.title.textContent = data.title || '填写标题后在这里预览';
  preview.location.textContent = [data.city, data.district, data.address].filter(Boolean).join(' · ')
    || '房源位置';
  preview.price.textContent = data.monthlyRent > 0
    ? `¥${data.monthlyRent.toLocaleString('zh-CN')}/月` : '¥--/月';
  preview.layout.textContent = `${data.bedroomCount}室 ${data.livingRoomCount}厅 ${data.bathroomCount}卫 · ${data.areaSqm || '--'}㎡`;
  preview.payment.textContent = `押${data.depositMonths}付${data.paymentCycleMonths}`;
  preview.description.textContent = data.description || '房源亮点和居住体验会显示在这里。';
  renderTags(preview.facilities, data.facilities);
  const cover = data.coverImageUrl || images[0]?.url;
  preview.image.hidden = !cover;
  preview.imagePlaceholder.hidden = Boolean(cover);
  if (cover) preview.image.src = cover;
  preview.gallery.replaceChildren(...images.slice(0, 6).map((image) => imageThumb(image.url)));
}

function renderImageList(els, state) {
  els.imageList.replaceChildren(...state.images.map((image, index) => {
    const item = document.createElement('button');
    item.type = 'button';
    item.className = 'image-chip';
    item.title = '移除图片';
    item.innerHTML = `<img alt="房源图片 ${index + 1}" src="${image.url}"><span>移除</span>`;
    item.addEventListener('click', () => {
      state.images.splice(index, 1);
      renderImageList(els, state);
      renderPreview(els.form, els.preview, state.images);
    });
    return item;
  }));
}

async function loadPublications(client, els) {
  setFeedback(els.feedback, '正在加载我的房源...', 'loading');
  try {
    sessionStorage.setItem('housePublishToken', els.landlordToken.value.trim());
    const values = await client.listMine();
    els.mineList.querySelectorAll('.publication-item').forEach((node) => node.remove());
    els.mineEmpty.hidden = values.length > 0;
    values.forEach((value) => els.mineList.append(renderPublication(value, els)));
    setFeedback(els.feedback, `已加载 ${values.length} 套房源`, 'success');
  } catch (error) {
    setFeedback(els.feedback, error.message, 'error');
  }
}

async function loadPublicList(client, els, state) {
  setFeedback(els.publicFeedback, '正在加载公开房源...', 'loading');
  try {
    sessionStorage.setItem('tenantBrowseToken', els.tenantToken.value.trim());
    const values = await client.listPublic();
    els.publicList.querySelectorAll('.public-house-item').forEach((node) => node.remove());
    els.publicEmpty.hidden = values.length > 0;
    values.forEach((value) => els.publicList.append(renderPublicHouse(value, client, els, state)));
    setFeedback(els.publicFeedback, `已加载 ${values.length} 套可咨询房源`, 'success');
  } catch (error) {
    setFeedback(els.publicFeedback, error.message, 'error');
  }
}

function renderPublication(value, els) {
  const article = document.createElement('article');
  article.className = 'publication-item';
  const main = document.createElement('div');
  const title = document.createElement('strong');
  title.textContent = value.title;
  const detail = document.createElement('span');
  detail.textContent = `${value.city} ${value.district} · ¥${Number(value.monthlyRent).toLocaleString('zh-CN')}/月`;
  main.append(title, detail);
  const states = document.createElement('div');
  states.className = 'publication-states';
  states.append(badge(statusLabels[value.status] || '未知状态', `state-${value.status}`));
  states.append(badge(auditLabels[value.auditStatus] || '未知审核', `audit-${value.auditStatus}`));
  const auditButton = document.createElement('button');
  auditButton.type = 'button';
  auditButton.className = 'tiny-button';
  auditButton.textContent = '填入审核';
  auditButton.addEventListener('click', () => {
    els.auditPublishId.value = value.publishId;
    els.auditTab.click();
  });
  article.append(main, states, auditButton);
  return article;
}

function renderPublicHouse(value, client, els, state) {
  const article = document.createElement('article');
  article.className = 'public-house-item';
  const image = document.createElement('img');
  image.alt = value.title;
  image.src = value.coverImageUrl || value.images?.[0]?.url || '';
  image.hidden = !image.src;
  const body = document.createElement('div');
  const title = document.createElement('strong');
  title.textContent = value.title;
  const detail = document.createElement('span');
  detail.textContent = `${value.city} ${value.district} · ${value.bedroomCount}室 · ¥${Number(value.monthlyRent).toLocaleString('zh-CN')}/月`;
  const button = document.createElement('button');
  button.type = 'button';
  button.className = 'tiny-button';
  button.textContent = '查看详情';
  button.addEventListener('click', async () => {
    setFeedback(els.publicFeedback, '正在加载详情...', 'loading');
    try {
      const detailValue = await client.detailPublic(value.publishId);
      state.selectedPublic = detailValue;
      renderDetail(els, detailValue);
      setFeedback(els.publicFeedback, `已打开房源 ${detailValue.publishId}`, 'success');
    } catch (error) {
      setFeedback(els.publicFeedback, error.message, 'error');
    }
  });
  body.append(title, detail);
  article.append(image, body, button);
  return article;
}

function renderDetail(els, value) {
  els.detail.hidden = false;
  els.startChat.disabled = false;
  const images = value.images?.length ? value.images : [{ url: value.coverImageUrl }];
  els.detail.querySelector('[data-detail="title"]').textContent = value.title;
  els.detail.querySelector('[data-detail="price"]').textContent = `¥${Number(value.monthlyRent).toLocaleString('zh-CN')}/月`;
  els.detail.querySelector('[data-detail="location"]').textContent = `${value.city} ${value.district} ${value.address}`;
  els.detail.querySelector('[data-detail="layout"]').textContent =
    `${value.bedroomCount}室 ${value.livingRoomCount}厅 ${value.bathroomCount}卫 · ${value.areaSqm}㎡ · ${value.rentType === 'shared' ? '合租' : '整租'}`;
  els.detail.querySelector('[data-detail="description"]').textContent = value.description || '暂无房源说明';
  els.detail.querySelector('[data-detail="requirements"]').textContent = value.rentalRequirements || '暂无租住要求';
  renderTags(els.detail.querySelector('[data-detail="facilities"]'), value.facilities || []);
  els.detail.querySelector('[data-detail="gallery"]').replaceChildren(
    ...images.filter((image) => image?.url).map((image) => imageThumb(image.url)),
  );
  els.chatResult.textContent = '';
}

function renderTags(container, values) {
  container.replaceChildren(...values.map((value) => {
    const item = document.createElement('span');
    item.textContent = value;
    return item;
  }));
}

function imageThumb(url) {
  const image = document.createElement('img');
  image.src = url;
  image.alt = '房源图片';
  return image;
}

function badge(label, className) {
  const value = document.createElement('span');
  value.className = `state-pill ${className}`;
  value.textContent = label;
  return value;
}

function setBusy(form, busy) {
  form.querySelectorAll('button').forEach((button) => { button.disabled = busy; });
}

function setFeedback(element, message, state) {
  element.className = `publish-feedback feedback-${state}`;
  element.textContent = message;
}
