package com.anyujia.anyujialegalservice.service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.stream.StreamListener;
import org.springframework.data.redis.stream.StreamMessageListenerContainer;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class LegalAgentEventSubscriptionService implements StreamListener<String, MapRecord<String, String, String>> {
    private final RedisConnectionFactory connectionFactory;
    private final LegalAgentMetrics metrics;
    @Value("${legal.agent.events.stream-key:legal:agent:events}")
    private String streamKey;
    private final ConcurrentHashMap<Long, CopyOnWriteArrayList<Runnable>> listeners = new ConcurrentHashMap<>();
    private StreamMessageListenerContainer<String, MapRecord<String, String, String>> container;

    @PostConstruct
    public void start() {
        StreamMessageListenerContainer.StreamMessageListenerContainerOptions<String, MapRecord<String, String, String>> options =
                StreamMessageListenerContainer.StreamMessageListenerContainerOptions.builder()
                        .pollTimeout(Duration.ofSeconds(1))
                        .batchSize(100)
                        .build();
        container = StreamMessageListenerContainer.create(connectionFactory, options);
        container.receive(StreamOffset.create(streamKey, ReadOffset.latest()), this);
        container.start();
        log.info("Legal Agent Redis Stream subscriber started, stream={}", streamKey);
    }

    @PreDestroy
    public void stop() {
        if (container != null) {
            container.stop();
        }
    }

    public AutoCloseable subscribe(Long taskId, Runnable listener) {
        listeners.computeIfAbsent(taskId, ignored -> new CopyOnWriteArrayList<>()).add(listener);
        AtomicBoolean closed = new AtomicBoolean();
        return () -> {
            if (closed.compareAndSet(false, true)) {
                List<Runnable> taskListeners = listeners.get(taskId);
                if (taskListeners != null) {
                    taskListeners.remove(listener);
                    if (taskListeners.isEmpty()) {
                        listeners.remove(taskId, taskListeners);
                    }
                }
            }
        };
    }

    @Override
    public void onMessage(MapRecord<String, String, String> message) {
        String taskId = message.getValue().get("taskId");
        if (taskId == null) {
            return;
        }
        List<Runnable> taskListeners = listeners.get(Long.valueOf(taskId));
        if (taskListeners == null) {
            return;
        }
        metrics.recordStreamNotification();
        taskListeners.forEach(listener -> {
            try {
                listener.run();
            } catch (RuntimeException ex) {
                log.warn("Legal Agent SSE listener failed, taskId={}", taskId, ex);
            }
        });
    }
}
