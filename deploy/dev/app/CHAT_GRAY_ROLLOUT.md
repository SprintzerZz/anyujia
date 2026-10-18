# Chat 分片灰度发布

灰度实例使用 `docker-compose-gray.yml` 覆盖配置，先保持旧表读兜底，再开启新旧表双写：

```powershell
docker compose -p frameworkjava `
  -f docker-compose-mid.yml `
  -f docker-compose-gray.yml `
  up -d frameworkjava-chat
```

灰度配置：

- `CHAT_MESSAGE_SHARDING_ENABLED=true`
- `CHAT_MESSAGE_SHARDING_DUAL_WRITE=true`
- `CHAT_MESSAGE_SHARDING_READ_FALLBACK=true`
- 开启分片校验任务

切换前置条件：

1. 已执行 V18、V19、V20 数据库迁移。
2. 先开启回填任务，确认旧表和分片表校验通过。
3. 灰度观察写入错误、分片缺失数和 MySQL 延迟。
4. 验证完成后再逐步关闭 `read-fallback`，最后关闭旧表双写。
