# RocketMQ cloud deployment

This configuration runs one NameServer and one asynchronous-master Broker.
It is sized for the current 4 GiB cloud server and intentionally omits Dashboard.

## Network

- Containers in `frameworkjava_network` use `frameworkjava-rocketmq-namesrv:9876`.
- The Broker advertises the private address configured by `ROCKETMQ_BROKER_IP`.
- Ports 10909, 10911 and 10912 are bound only to that private address.
- Do not expose RocketMQ ports to `0.0.0.0/0` in the cloud security group.

## Start

Run from `deploy/dev/app`:

```bash
export ROCKETMQ_BROKER_IP=10.1.0.16
docker compose -p framework-java -f docker-compose-mid.yml config
docker compose -p framework-java -f docker-compose-mid.yml pull frameworkjava-rocketmq-init frameworkjava-rocketmq-namesrv frameworkjava-rocketmq-broker frameworkjava-rocketmq-topic-init
docker compose -p framework-java -f docker-compose-mid.yml up -d frameworkjava-rocketmq-namesrv frameworkjava-rocketmq-broker frameworkjava-rocketmq-topic-init
```

The Compose stack runs `frameworkjava-rocketmq-init` first to assign the persistent
volumes to the non-root `rocketmq` image user.
`frameworkjava-rocketmq-topic-init` creates the coupon topic and consumer group
after the Broker becomes healthy. It exits with code 0 when initialization succeeds.

## Verify

```bash
docker compose -p framework-java -f docker-compose-mid.yml ps
docker logs --tail 100 frameworkjava-rocketmq-namesrv
docker logs --tail 100 frameworkjava-rocketmq-broker
docker exec frameworkjava-rocketmq-broker sh mqadmin clusterList \
  -n frameworkjava-rocketmq-namesrv:9876
docker exec frameworkjava-rocketmq-broker sh mqadmin topicList \
  -n frameworkjava-rocketmq-namesrv:9876
```

Coupon service containers must set:

```bash
ROCKETMQ_NAMESRV_ADDR=frameworkjava-rocketmq-namesrv:9876
```
