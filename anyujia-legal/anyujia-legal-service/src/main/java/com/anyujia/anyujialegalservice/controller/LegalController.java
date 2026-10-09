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
import org.springframework.web.multipart.MultipartFile;
import com.anyujia.anyujialegalapi.domain.dto.LegalSourceIngestReqDTO;
import com.anyujia.anyujialegalapi.domain.vo.LegalSourceVO;
import com.anyujia.anyujialegalapi.domain.dto.LegalSearchReqDTO;
import com.anyujia.anyujialegalapi.domain.vo.LegalSearchVO;
import com.anyujia.anyujialegalapi.domain.dto.LegalAnswerReqDTO;
import com.anyujia.anyujialegalapi.domain.dto.LegalAgentAnswerReqDTO;
import com.anyujia.anyujialegalapi.domain.dto.LegalAgentBindReqDTO;
import com.anyujia.anyujialegalapi.domain.vo.LegalAnswerVO;
import com.anyujia.anyujialegalapi.domain.vo.LegalAgentAnswerVO;

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
