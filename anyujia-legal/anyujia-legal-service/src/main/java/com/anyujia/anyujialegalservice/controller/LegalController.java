package com.anyujia.anyujialegalservice.controller;

import com.anyujia.anyujialegalapi.domain.dto.AssessmentStartReqDTO;
import com.anyujia.anyujialegalapi.domain.dto.ContractRegisterReqDTO;
import com.anyujia.anyujialegalapi.domain.vo.AssessmentJobVO;
import com.anyujia.anyujialegalapi.domain.vo.ContractVO;
import com.anyujia.anyujialegalapi.domain.vo.EmbeddingBackfillVO;
import com.anyujia.anyujialegalapi.feign.LegalFeignClient;
import com.anyujia.anyujialegalservice.constants.LegalConstants;
import com.anyujia.anyujialegalservice.service.LegalAssessmentService;
import com.anyujia.anyujialegalservice.service.LegalContractService;
import com.anyujia.anyujialegalservice.service.LegalDocumentIngestionService;
import com.anyujia.anyujialegalservice.service.LegalKnowledgeService;
import com.anyujia.anyujialegalservice.service.LegalKnowledgeSearchService;
import com.anyujia.anyujialegalservice.service.LegalAnswerService;
import com.anyujia.anyujialegalservice.service.LegalAgentConversationService;
import com.anyujia.anyujialegalservice.service.LegalAgentService;
import com.anyujia.anyujialegalservice.service.LegalAgentTaskService;
import com.anyujia.anyujialegalservice.service.LegalAgentTaskEventService;
import com.anyujia.anyujialegalservice.service.LegalAgentEventSubscriptionService;
import com.anyujia.anyujialegalservice.service.LegalAgentMetrics;
import com.anyujia.anyujialegalservice.domain.entity.LegalAgentTaskEvent;
import com.anyujia.anyujiacommondomain.domain.ApiResult;
import com.anyujia.anyujiacommondomain.domain.ResultCode;
import com.anyujia.anyujiacommondomain.exception.ServiceException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.web.multipart.MultipartFile;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Autowired;
import com.anyujia.anyujialegalapi.domain.dto.LegalSourceIngestReqDTO;
import com.anyujia.anyujialegalapi.domain.vo.LegalSourceVO;
import com.anyujia.anyujialegalapi.domain.dto.LegalSearchReqDTO;
import com.anyujia.anyujialegalapi.domain.vo.LegalSearchVO;
import com.anyujia.anyujialegalapi.domain.dto.LegalAnswerReqDTO;
import com.anyujia.anyujialegalapi.domain.dto.LegalAgentAnswerReqDTO;
import com.anyujia.anyujialegalapi.domain.dto.LegalAgentTaskSubmitReqDTO;
import com.anyujia.anyujialegalapi.domain.dto.LegalAgentBindReqDTO;
import com.anyujia.anyujialegalapi.domain.vo.LegalAnswerVO;
import com.anyujia.anyujialegalapi.domain.vo.LegalAgentAnswerVO;
import com.anyujia.anyujialegalapi.domain.vo.LegalAgentTaskVO;

@RestController
@RequestMapping("/legal")
@RequiredArgsConstructor
public class LegalController implements LegalFeignClient {
    private final LegalContractService contractService;
    private final LegalAssessmentService assessmentService;
    private final LegalDocumentIngestionService ingestionService;
    private final LegalKnowledgeService knowledgeService;
    private final LegalKnowledgeSearchService knowledgeSearchService;
    private final LegalAnswerService legalAnswerService;
    private final LegalAgentService legalAgentService;
    private final LegalAgentConversationService legalAgentConversationService;
    private final LegalAgentTaskService legalAgentTaskService;
    private final LegalAgentTaskEventService legalAgentTaskEventService;
    private final LegalAgentEventSubscriptionService legalAgentEventSubscriptionService;
    @Qualifier("legalSseScheduler")
    @Autowired
    private ScheduledExecutorService sseScheduler;
    @Qualifier("legalSseConnectionLimiter")
    @Autowired
    private Semaphore sseConnectionLimiter;
    @Qualifier("legalSseWriterExecutor")
    @Autowired
    private Executor sseWriterExecutor;
    @Autowired
    private LegalAgentMetrics sseMetrics;

    @PostMapping("/contracts/upload")
    public ApiResult<ContractVO> uploadContract(@RequestHeader("userId") Long userId,
                                                @RequestHeader("userType") Integer userType,
                                                @RequestHeader("userFrom") String userFrom,
                                                @RequestParam("conversationId") Long conversationId,
                                                @RequestParam("file") MultipartFile file) {
        requireTenant(userType, userFrom);
        return ApiResult.success(ingestionService.ingest(userId, conversationId, file));
    }

    @Override
    public ApiResult<ContractVO> registerContract(Long userId, Integer userType, String userFrom,
                                                  @RequestBody @Valid ContractRegisterReqDTO request) {
        requireTenant(userType, userFrom);
        return ApiResult.success(contractService.register(userId, request));
    }

    @Override
    public ApiResult<AssessmentJobVO> startAssessment(Long userId, Integer userType, String userFrom,
                                                      @RequestBody @Valid AssessmentStartReqDTO request) {
        requireTenant(userType, userFrom);
        return ApiResult.success(assessmentService.start(userId, request));
    }

    @Override
    public ApiResult<AssessmentJobVO> getAssessment(Long userId, Integer userType, String userFrom, Long jobId) {
        requireTenant(userType, userFrom);
        return ApiResult.success(assessmentService.get(userId, jobId));
    }

    @GetMapping(value = "/assessments/{jobId}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamAssessment(@RequestHeader("userId") Long userId,
                                       @RequestHeader("userType") Integer userType,
                                       @RequestHeader("userFrom") String userFrom,
                                       @PathVariable("jobId") Long jobId) {
        requireTenant(userType, userFrom);
        if (!sseConnectionLimiter.tryAcquire()) {
            throw new ServiceException("法律助手实时连接数已达上限，请稍后重试",
                    ResultCode.ERROR.getCode());
        }
        SseEmitter emitter = new SseEmitter(120_000L);
        scheduleAssessmentStream(emitter, userId, jobId);
        return emitter;
    }

    @PostMapping("/agent/tasks")
    public ApiResult<LegalAgentTaskVO> submitAgentTask(@RequestHeader("userId") Long userId,
                                                       @RequestHeader("userType") Integer userType,
                                                       @RequestHeader("userFrom") String userFrom,
                                                       @RequestBody @Valid LegalAgentTaskSubmitReqDTO request) {
        requireTenant(userType, userFrom);
        long started = System.nanoTime();
        try {
            return ApiResult.success(legalAgentTaskService.submit(userId, request));
        } finally {
            sseMetrics.recordSubmitStage("controller", System.nanoTime() - started);
        }
    }

    @GetMapping("/agent/tasks/{taskId}")
    public ApiResult<LegalAgentTaskVO> getAgentTask(@RequestHeader("userId") Long userId,
                                                    @RequestHeader("userType") Integer userType,
                                                    @RequestHeader("userFrom") String userFrom,
                                                    @PathVariable("taskId") Long taskId) {
        requireTenant(userType, userFrom);
        return ApiResult.success(legalAgentTaskService.get(userId, taskId));
    }

    @GetMapping(value = "/agent/tasks/{taskId}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamAgentTask(@RequestHeader("userId") Long userId,
                                      @RequestHeader("userType") Integer userType,
                                      @RequestHeader("userFrom") String userFrom,
                                      @RequestHeader(value = "Last-Event-ID", required = false) Long lastEventId,
                                      @PathVariable("taskId") Long taskId) {
        requireTenant(userType, userFrom);
        legalAgentTaskService.get(userId, taskId);
        if (!sseConnectionLimiter.tryAcquire()) {
            throw new ServiceException("法律助手实时连接数已达上限，请稍后重试", ResultCode.ERROR.getCode());
        }
        SseEmitter emitter = new SseEmitter(120_000L);
        AtomicBoolean released = new AtomicBoolean();
        AtomicReference<ScheduledFuture<?>> heartbeatRef = new AtomicReference<>();
        AtomicReference<AutoCloseable> subscriptionRef = new AtomicReference<>();
        AtomicLong cursor = new AtomicLong(lastEventId == null ? 0L : lastEventId);
        AtomicBoolean flushScheduled = new AtomicBoolean();
        ArrayBlockingQueue<Long> wakeupQueue = new ArrayBlockingQueue<>(100);
        Runnable cleanup = () -> {
            if (released.compareAndSet(false, true)) {
                ScheduledFuture<?> heartbeat = heartbeatRef.get();
                if (heartbeat != null) {
                    heartbeat.cancel(false);
                }
                AutoCloseable subscription = subscriptionRef.get();
                if (subscription != null) {
                    try {
                        subscription.close();
                    } catch (Exception ignored) {
                        // cleanup must remain idempotent even if Redis subscription is already closed
                    }
                }
                sseConnectionLimiter.release();
            }
        };
        emitter.onCompletion(cleanup);
        emitter.onTimeout(() -> {
            cleanup.run();
            emitter.complete();
        });
        emitter.onError(ex -> cleanup.run());
        try {
            // 先刷新响应头并确认连接已建立，事件重放和心跳随后异步执行。
            emitter.send(SseEmitter.event().name("connected").data("ok"));
        } catch (java.io.IOException ex) {
            cleanup.run();
            emitter.completeWithError(ex);
            return emitter;
        }
        AtomicReference<Runnable> scheduleFlushRef = new AtomicReference<>();
        Runnable flush = () -> {
            long writeStartedAt = System.nanoTime();
            try {
                if (released.get()) {
                    return;
                }
                wakeupQueue.clear();
                var events = legalAgentTaskEventService.after(userId, taskId, cursor.get(), 50);
                for (LegalAgentTaskEvent event : events) {
                    emitter.send(SseEmitter.event().id(String.valueOf(event.getId()))
                            .name(event.getEventType()).data(event.getPayload()));
                    cursor.set(event.getId());
                    if ("result".equals(event.getEventType()) || "failed".equals(event.getEventType())) {
                        cleanup.run();
                        emitter.complete();
                        return;
                    }
                }
            } catch (RuntimeException | java.io.IOException ex) {
                cleanup.run();
                emitter.completeWithError(ex);
            } finally {
                sseMetrics.recordSseWrite(System.nanoTime() - writeStartedAt);
                flushScheduled.set(false);
                if (!wakeupQueue.isEmpty() && !released.get()) {
                    scheduleFlushRef.get().run();
                }
            }
        };
        Runnable scheduleFlush = () -> {
            if (flushScheduled.compareAndSet(false, true)) {
                try {
                    sseWriterExecutor.execute(flush);
                } catch (RejectedExecutionException ex) {
                    sseMetrics.recordSseWriterRejected();
                    cleanup.run();
                    emitter.completeWithError(ex);
                }
            }
        };
        scheduleFlushRef.set(scheduleFlush);
        subscriptionRef.set(legalAgentEventSubscriptionService.subscribe(taskId, message -> {
            String eventId = message.getValue().get("eventId");
            long wakeupId = eventId == null ? 0L : Long.parseLong(eventId);
            if (!wakeupQueue.offer(wakeupId)) {
                wakeupQueue.clear();
                wakeupQueue.offer(wakeupId);
                sseMetrics.recordSseQueueOverflow();
            }
            scheduleFlush.run();
        }));
        wakeupQueue.offer(cursor.get());
        scheduleFlush.run();
        heartbeatRef.set(sseScheduler.scheduleWithFixedDelay(() -> {
            try {
                if (!released.get()) {
                    emitter.send(SseEmitter.event().name("heartbeat").data("ok"));
                }
            } catch (java.io.IOException ex) {
                cleanup.run();
                emitter.completeWithError(ex);
            }
        }, 15L, 15L, TimeUnit.SECONDS));
        return emitter;
    }

    private void scheduleAssessmentStream(SseEmitter emitter, Long userId, Long jobId) {
        AtomicBoolean released = new AtomicBoolean();
        AtomicReference<ScheduledFuture<?>> futureRef = new AtomicReference<>();
        Runnable cleanup = () -> {
            if (released.compareAndSet(false, true)) {
                ScheduledFuture<?> future = futureRef.get();
                if (future != null) {
                    future.cancel(false);
                }
                sseConnectionLimiter.release();
            }
        };
        emitter.onCompletion(cleanup);
        emitter.onTimeout(() -> {
            cleanup.run();
            emitter.complete();
        });
        emitter.onError(ex -> cleanup.run());

        ScheduledFuture<?> future = sseScheduler.scheduleWithFixedDelay(() -> {
            try {
                AssessmentJobVO job = assessmentService.get(userId, jobId);
                emitter.send(SseEmitter.event().name("assessment").data(job));
                if (LegalConstants.JOB_COMPLETED.equals(job.getStatus())
                        || LegalConstants.JOB_FAILED.equals(job.getStatus())) {
                    cleanup.run();
                    emitter.complete();
                }
            } catch (RuntimeException ex) {
                cleanup.run();
                emitter.completeWithError(ex);
            } catch (java.io.IOException ex) {
                cleanup.run();
                emitter.completeWithError(ex);
            }
        }, 0L, 500L, TimeUnit.MILLISECONDS);
        futureRef.set(future);
    }

    @Override
    public ApiResult<LegalSourceVO> ingestLegalSource(String userFrom,
                                                      @RequestBody @Valid LegalSourceIngestReqDTO request) {
        if (!"sys".equals(userFrom) && !"service".equals(userFrom)) {
            throw new ServiceException("仅内部管理端可以维护法规库", ResultCode.TOKEN_CHECK_FAILED.getCode());
        }
        return ApiResult.success(knowledgeService.ingest(request));
    }

    @Override
    public ApiResult<EmbeddingBackfillVO> backfillEmbeddings(String userFrom, Integer limit) {
        if (!"sys".equals(userFrom) && !"service".equals(userFrom)) {
            throw new ServiceException("仅内部管理端可以维护法规库", ResultCode.TOKEN_CHECK_FAILED.getCode());
        }
        return ApiResult.success(knowledgeService.backfillEmbeddings(limit));
    }

    @Override
    public ApiResult<LegalSearchVO> searchKnowledge(String userFrom,
                                                    @RequestBody @Valid LegalSearchReqDTO request) {
        requireInternal(userFrom);
        return ApiResult.success(knowledgeSearchService.search(request));
    }

    @Override
    public ApiResult<LegalAnswerVO> answerKnowledge(String userFrom,
                                                    @RequestBody @Valid LegalAnswerReqDTO request) {
        requireInternal(userFrom);
        return ApiResult.success(legalAnswerService.answer(request));
    }

    @PostMapping("/agent/answer")
    public ApiResult<LegalAgentAnswerVO> answerAgent(@RequestHeader("userId") Long userId,
                                                     @RequestHeader("userType") Integer userType,
                                                     @RequestHeader("userFrom") String userFrom,
                                                     @RequestBody @Valid LegalAgentAnswerReqDTO request) {
        requireTenant(userType, userFrom);
        return ApiResult.success(legalAgentService.answer(userId, request));
    }

    @PostMapping("/agent/bind")
    public ApiResult<Void> bindAgent(@RequestHeader("userFrom") String userFrom,
                                     @RequestBody @Valid LegalAgentBindReqDTO request) {
        requireInternal(userFrom);
        legalAgentConversationService.bind(request);
        return ApiResult.success();
    }

    private void requireInternal(String userFrom) {
        if (!"sys".equals(userFrom) && !"service".equals(userFrom)) {
            throw new ServiceException("仅内部服务可以检索法规知识库", ResultCode.TOKEN_CHECK_FAILED.getCode());
        }
    }

    private void requireTenant(Integer userType, String userFrom) {
        if (!"app".equals(userFrom) || !Integer.valueOf(LegalConstants.USER_TYPE_TENANT).equals(userType)) {
            throw new ServiceException("仅租客可以使用合同风险评估", ResultCode.TOKEN_CHECK_FAILED.getCode());
        }
    }
}
