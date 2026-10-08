import { LegalAgentClient, sha256Hex } from './legal-agent-client.js';

const DEMO_CONTRACT = `房屋租赁合同
出租人：王房东。承租人：李租客。
房屋地址：上海市浦东新区滨江花园 1 栋 1001 室。
租期：2026-10-15 至 2027-10-14。
租金：每月3200元，按月支付。
押金：6400元。合同未明确押金返还时间和扣除情形。
出租人可因管理、检查、维修需要自行进入房屋。
承租人逾期支付租金或违反本合同任一条款，出租人有权立即解除合同并没收押金。
出租人确认有权出租该房屋，但未向承租人提供产权证明或转租授权材料。
维修责任：房屋主体和设施自然损坏由双方另行协商。
本合同未约定物业费、清洁服务费、季度付款、禁止养宠等房源发布要求。`;

export function initLegalAgentPanel({ tokenInput, userIdInput, sessionIdInput, addEvent }) {
  const els = {
    contractText: document.querySelector('#legalContractText'),
    contractId: document.querySelector('#legalContractIdInput'),
    jobId: document.querySelector('#legalJobIdInput'),
    status: document.querySelector('#legalStatusValue'),
    feedback: document.querySelector('#legalFeedback'),
    riskList: document.querySelector('#legalRiskList'),
    empty: document.querySelector('#legalRiskEmpty'),
    register: document.querySelector('#legalRegisterButton'),
    assess: document.querySelector('#legalAssessButton'),
    refresh: document.querySelector('#legalRefreshButton'),
    demo: document.querySelector('#legalDemoContractButton'),
  };

  const client = new LegalAgentClient({
    baseUrl: window.location.origin,
    tokenProvider: () => tokenInput.value,
    userIdProvider: () => userIdInput.value,
  });

  els.contractText.value = localStorage.getItem('legalAgentContractText') || '';
  els.contractId.value = localStorage.getItem('legalAgentContractId') || '';
  els.jobId.value = localStorage.getItem('legalAgentJobId') || '';

  els.demo.addEventListener('click', () => {
    els.contractText.value = DEMO_CONTRACT;
    persistContractText();
    setFeedback('已填入演示合同，可直接注册并评估。', 'success');
  });
  els.contractText.addEventListener('input', persistContractText);
  els.register.addEventListener('click', () => void registerContract());
  els.assess.addEventListener('click', () => void startAssessment());
  els.refresh.addEventListener('click', () => void refreshAssessment());

  if (els.jobId.value) {
    void refreshAssessment(false);
  }

  async function registerContract() {
    const conversationId = sessionIdInput.value.trim();
    const extractedText = els.contractText.value.trim();
    if (!/^\d+$/.test(conversationId)) {
      setFeedback('会话 ID 必须是正整数。', 'error');
      return;
    }
    if (!extractedText) {
      setFeedback('请先粘贴合同正文。', 'error');
      return;
    }
    setBusy(true);
    try {
      const sha256 = await sha256Hex(extractedText);
      const contract = await client.registerContract({
        conversationId: Number(conversationId),
        objectKey: `web/legal/contracts/${sha256.slice(0, 16)}.txt`,
        originalFilename: `contract-${conversationId}.txt`,
        sha256,
        extractedText,
      });
      els.contractId.value = contract.contractId;
      localStorage.setItem('legalAgentContractId', contract.contractId);
      setFeedback(`合同已注册：${contract.contractId}`, 'success');
      addEvent?.('法律合同已注册', `contract=${contract.contractId}`);
    } catch (error) {
      setFeedback(error.message, 'error');
      addEvent?.('法律合同注册失败', error.message, 'error');
    } finally {
      setBusy(false);
    }
  }

  async function startAssessment() {
    const contractId = els.contractId.value.trim();
    if (!/^\d+$/.test(contractId)) {
      setFeedback('请先注册合同，或填入有效合同 ID。', 'error');
      return;
    }
    setBusy(true);
    try {
      const job = await client.startAssessment({
        contractId: Number(contractId),
        requestId: `web-${contractId}-${Date.now()}`,
        regionCode: 'CN',
        asOfDate: new Date().toISOString().slice(0, 10),
      });
      els.jobId.value = job.jobId;
      localStorage.setItem('legalAgentJobId', job.jobId);
      renderAssessment(job);
      setFeedback(`评估已提交：${job.jobId}`, 'success');
      addEvent?.('法律评估已提交', `job=${job.jobId}`);
      await pollAssessment(job.jobId);
    } catch (error) {
      setFeedback(error.message, 'error');
      addEvent?.('法律评估提交失败', error.message, 'error');
    } finally {
      setBusy(false);
    }
  }

  async function refreshAssessment(showError = true) {
    const jobId = els.jobId.value.trim();
    if (!/^\d+$/.test(jobId)) {
      if (showError) setFeedback('请先发起评估，或填入有效 Job ID。', 'error');
      return;
    }
    setBusy(true);
    try {
      const job = await client.getAssessment(jobId);
      renderAssessment(job);
      setFeedback(`已刷新：${job.status}`, 'success');
    } catch (error) {
      if (showError) setFeedback(error.message, 'error');
    } finally {
      setBusy(false);
    }
  }

  async function pollAssessment(jobId) {
    for (let attempt = 0; attempt < 30; attempt += 1) {
      await delay(1000);
      const job = await client.getAssessment(jobId);
      renderAssessment(job);
      if (job.status === 'COMPLETED' || job.status === 'FAILED') {
        setFeedback(job.status === 'COMPLETED' ? `评估完成：${job.risks?.length || 0} 项风险` : job.errorMessage || '评估失败',
          job.status === 'COMPLETED' ? 'success' : 'error');
        return;
      }
    }
    setFeedback('评估仍在运行，请稍后刷新。', 'loading');
  }

  function renderAssessment(job) {
    els.status.textContent = job.status || '-';
    els.jobId.value = job.jobId || els.jobId.value;
    localStorage.setItem('legalAgentJobId', els.jobId.value);
    els.riskList.replaceChildren();
    const risks = job.risks || [];
    els.empty.hidden = risks.length > 0;
    risks.forEach((risk) => els.riskList.append(renderRisk(risk)));
  }

  function renderRisk(risk) {
    const item = document.createElement('article');
    item.className = `legal-risk-card risk-${String(risk.level || '').toLowerCase()}`;
    const header = document.createElement('div');
    header.className = 'legal-risk-header';
    const title = document.createElement('strong');
    title.textContent = risk.title || risk.riskCode;
    const badge = document.createElement('span');
    badge.textContent = `${risk.level || '-'} · ${risk.confidence ?? '-'}%`;
    header.append(title, badge);

    const conclusion = document.createElement('p');
    conclusion.textContent = risk.conclusion || '';
    const evidence = document.createElement('blockquote');
    evidence.textContent = risk.evidenceQuote || '暂无证据摘录';
    const basis = document.createElement('small');
    basis.textContent = risk.legalBasis ? `依据：${risk.legalBasis}` : '';
    item.append(header, conclusion, evidence, basis);
    return item;
  }

  function persistContractText() {
    localStorage.setItem('legalAgentContractText', els.contractText.value);
  }

  function setBusy(value) {
    els.register.disabled = value;
    els.assess.disabled = value;
    els.refresh.disabled = value;
    els.demo.disabled = value;
  }

  function setFeedback(message, state) {
    els.feedback.textContent = message;
    els.feedback.className = `publish-feedback feedback-${state}`;
  }
}

function delay(ms) {
  return new Promise((resolve) => {
    setTimeout(resolve, ms);
  });
}
