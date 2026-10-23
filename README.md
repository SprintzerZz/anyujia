# AnyuJia

AnyuJia 是一个基于 Spring Cloud Alibaba 的租房平台后端项目，覆盖房源、租客与房东即时聊天、优惠券、文件存储和租房法律风险评估等业务场景。

项目重点围绕高并发业务和 AI Agent 工程化落地进行建设：聊天链路强调可靠投递与可恢复，优惠券链路强调库存与幂等，法律 Agent 链路强调多源证据、上下文一致性和可追溯结论。

> 仓库已完成源码脱敏。密码、Token、AK/SK、模型缓存和本地运行数据均不提交到 Git，应通过环境变量、容器密钥或私有配置中心注入。

## 技术栈

- Java 17、Spring Boot、Spring Cloud Alibaba、Nacos
- MyBatis-Plus、MySQL、ShardingSphere、Seata
- Redis、Redisson、Caffeine
- RabbitMQ、RocketMQ、Netty WebSocket、Spring WebSocket
- Docker Compose、Maven、Node.js
- Apache Tika、BM25/向量检索、RRF 融合、SSE

## 模块结构

| 模块 | 说明 |
| --- | --- |
| `anyujia-gateway` | API 网关、鉴权过滤、请求转发和 WebSocket 票据校验 |
| `anyujia-admin` | 后台管理、用户、字典、行政区划和地图相关能力 |
| `anyujia-house` | 房源发布、审核、公开查询和租赁商品信息 |
| `anyujia-chat` | 租客与房东聊天、消息持久化、离线同步和 WebSocket 推送 |
| `anyujia-coupon` | 优惠券、区域补贴、高并发申领、批量发券和用户券状态流转 |
| `anyujia-file` | 文件上传、私有对象存储、签名访问 |
| `anyujia-legal` | 合同解析、法律 RAG、风险识别、证据评估和独立 Agent 会话 |
| `deploy` | Docker 编排、数据库脚本、评估集、压测脚本和分析报告 |

## 法律 Agent

用户在租客与房东聊天页面点击法律助手后，会创建一个独立分析会话。Agent 通过 `landlordConversationId + contractId` 校验上下文绑定，分析结果写入独立会话，不污染原始聊天记录。

核心流程如下：

```text
合同 PDF/DOCX ── Apache Tika ──┐
租客/房东聊天 ────────────────┼─> 上下文快照 ─> 阶段化 Agent ─> 风险结论 + 证据引用
房源与商品信息 ──────────────┤          │
法律知识库 ── BM25/向量检索 ──┘          └─> SSE 增量推送 / 断线重放
```

主要能力：

- 合同、聊天、房源和法规分开建模，使用 Apache Tika 统一解析 PDF/DOCX。
- BM25、法律意图和向量召回经 RRF 融合重排，法规支持版本和失效过滤。
- 通过上下文指纹、风险条款摘要和关键聊天承诺实现上下文压缩；上下文变化时阻断过期答案。
- Agent 按解析、检索、分析、证据评估、结论生成阶段执行，持久化 checkpoint，支持失败阶段重放和任务 replay。
- 结论关联逐条证据，并进行语义级法律 entailment 评估；证据冲突时阻断高置信结论并进入人工审核队列。
- 异步任务使用 MySQL 状态与租约、Outbox、RabbitMQ 和 Redis Stream；支持重启恢复、租约接管、幂等投递和 SSE `Last-Event-ID` 重放。
- 本地 entailment 服务支持健康检查、动态批处理、超时、重试、熔断和多副本 HAProxy 转发；外部模型不可用时保留规则分析和引用兜底。

相关设计、测试和压测记录：

- [`deploy/data/legal-module-analysis.md`](deploy/data/legal-module-analysis.md)
- [`deploy/data/legal-resume.md`](deploy/data/legal-resume.md)
- [`deploy/eval/legal-rag/README.md`](deploy/eval/legal-rag/README.md)
- [`deploy/eval/legal-entailment/README.md`](deploy/eval/legal-entailment/README.md)

## 可靠性与性能验证

项目包含聊天、优惠券和法律 Agent 的评估与压测脚本，重点覆盖幂等、重试、断线恢复、租约接管、证据一致性和模型服务故障转移。

已记录的法律 entailment 服务验证结果包括：

| 场景 | 结果 |
| --- | --- |
| INT8 动态批处理，32 VU / 320 请求 | 320/320 成功，约 19.69 req/s，P95 2.74s |
| 双副本 + HAProxy，64 VU / 640 请求 | 629 成功，约 32.18 req/s，P95 2.67s |
| HAProxy 单副本故障转移，32 VU / 320 请求 | 320/320 成功，P95 1.62s，P99 1.83s |

RAG 离线评估已记录 Recall@4、HitRate@4、citation validity、coverage 和语义 entailment 指标。具体口径和样本集见 `deploy/eval` 与法律模块分析报告。

## 本地构建

```bash
mvn clean package -DskipTests
```

运行单个模块测试：

```bash
mvn -pl anyujia-coupon/anyujia-coupon-service test
mvn -pl anyujia-legal/anyujia-legal-service test
```

## Docker 开发环境

中间件编排位于 `deploy/dev/app`：

```bash
cd deploy/dev/app
docker compose -p frameworkjava -f docker-compose-mid.yml up -d
```

如需覆盖默认配置，请通过 Docker Compose 环境变量、Nacos 私有配置或本机未跟踪的 `.env` 文件注入，不要提交真实凭证。

法律 Agent 的本地 entailment 服务为可选组件：

```bash
docker compose -p frameworkjava -f docker-compose-entailment.yml up -d
```

模型服务首次启动可能需要下载模型。若网络或代理不可用，法律 Agent 仍可运行确定性规则分析和 RAG 引用链路，但不会提供语义 entailment 评分。

常用端口：

| 服务 | 端口 |
| --- | ---: |
| Gateway | `18080` |
| Admin | `18081` |
| Chat HTTP | `18083` |
| Chat Netty WebSocket | `18084` |
| Coupon | `18085` |
| Legal | `18086` |
| Nacos | `8848` |
| MySQL | `3306` |
| Redis | `6379` |

## 配置安全

不要将真实凭证写入源码、测试数据、Docker Compose 或 README。提交前可检查暂存区：

```bash
git grep -n -I -E "password|secret|token|accessKey|api-key" -- ':!deploy/dev/data/**'
```

本地数据库、模型缓存、压测结果、临时合同和临时 Agent 会话数据已通过 `.gitignore` 排除。

## 目录说明

```text
anyujia-frameworkjava
├── anyujia-admin
├── anyujia-chat
├── anyujia-common
├── anyujia-coupon
├── anyujia-file
├── anyujia-gateway
├── anyujia-house
├── anyujia-legal
├── anyujia-mstemplate
├── deploy
├── pom.xml
└── README.md
```
