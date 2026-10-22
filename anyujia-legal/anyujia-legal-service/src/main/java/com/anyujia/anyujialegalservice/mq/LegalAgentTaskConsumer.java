package com.anyujia.anyujialegalservice.mq;

import com.anyujia.anyujialegalservice.service.LegalAgentTaskService;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class LegalAgentTaskConsumer {
    private final LegalAgentTaskService taskService;

    @RabbitListener(queues = LegalAgentRabbitConfig.QUEUE,
            concurrency = "${legal.agent.consumer-concurrency:4}")
    public void consume(String taskId) {
        taskService.execute(Long.valueOf(taskId));
    }
}
