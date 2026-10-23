# Redis Sentinel HA

The local Docker Compose environment runs Redis in Sentinel mode:

- `frameworkjava-redis-master`: writable master, exposed on host port `6379`
- `frameworkjava-redis-replica-1`: replica, exposed on host port `6380`
- `frameworkjava-redis-replica-2`: replica, exposed on host port `6381`
- `frameworkjava-redis-sentinel-1..3`: Sentinel quorum, exposed on host ports `26379..26381`

Applications connect through Spring Boot Sentinel properties:

```yaml
spring:
  data:
    redis:
      password: ${REDIS_PASSWORD}
      sentinel:
        master: mymaster
        nodes:
          - frameworkjava-redis-sentinel-1:26379
          - frameworkjava-redis-sentinel-2:26379
          - frameworkjava-redis-sentinel-3:26379
```

Useful checks:

```bash
docker compose -p frameworkjava -f docker-compose-mid.yml up -d frameworkjava-redis-master frameworkjava-redis-replica-1 frameworkjava-redis-replica-2 frameworkjava-redis-sentinel-1 frameworkjava-redis-sentinel-2 frameworkjava-redis-sentinel-3
docker exec frameworkjava-redis-sentinel-1 redis-cli -p 26379 sentinel get-master-addr-by-name mymaster
docker exec frameworkjava-redis-master redis-cli -a "$REDIS_PASSWORD" info replication
```

Failover smoke test:

```bash
docker stop frameworkjava-redis-master
docker exec frameworkjava-redis-sentinel-1 redis-cli -p 26379 sentinel get-master-addr-by-name mymaster
```

After Sentinel elects a replica as the new master, Spring Data Redis and Redisson discover the new master through Sentinel.
