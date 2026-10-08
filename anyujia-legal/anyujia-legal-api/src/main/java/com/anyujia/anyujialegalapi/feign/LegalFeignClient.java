package com.anyujia.anyujialegalapi.feign;

import com.anyujia.anyujialegalapi.domain.dto.AssessmentStartReqDTO;
import com.anyujia.anyujialegalapi.domain.dto.ContractRegisterReqDTO;
import com.anyujia.anyujialegalapi.domain.dto.LegalSourceIngestReqDTO;
import com.anyujia.anyujialegalapi.domain.dto.LegalSearchReqDTO;
import com.anyujia.anyujialegalapi.domain.dto.LegalAnswerReqDTO;
import com.anyujia.anyujialegalapi.domain.vo.AssessmentJobVO;
import com.anyujia.anyujialegalapi.domain.vo.ContractVO;
import com.anyujia.anyujialegalapi.domain.vo.EmbeddingBackfillVO;
import com.anyujia.anyujialegalapi.domain.vo.LegalSourceVO;
import com.anyujia.anyujialegalapi.domain.vo.LegalSearchVO;
import com.anyujia.anyujialegalapi.domain.vo.LegalAnswerVO;
import com.anyujia.anyujiacommondomain.domain.ApiResult;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(contextId = "legalFeignClient", value = "anyujia-legal", path = "/legal")
public interface LegalFeignClient {
    @PostMapping("/contracts")
    ApiResult<ContractVO> registerContract(@RequestHeader("userId") Long userId,
                                           @RequestHeader("userType") Integer userType,
                                           @RequestHeader("userFrom") String userFrom,
                                           @RequestBody ContractRegisterReqDTO request);

    @PostMapping("/assessments")
    ApiResult<AssessmentJobVO> startAssessment(@RequestHeader("userId") Long userId,
                                               @RequestHeader("userType") Integer userType,
                                               @RequestHeader("userFrom") String userFrom,
                                               @RequestBody AssessmentStartReqDTO request);

    @GetMapping("/assessments/{jobId}")
    ApiResult<AssessmentJobVO> getAssessment(@RequestHeader("userId") Long userId,
                                             @RequestHeader("userType") Integer userType,
                                             @RequestHeader("userFrom") String userFrom,
                                             @PathVariable("jobId") Long jobId);

    @PostMapping("/knowledge/sources")
    ApiResult<LegalSourceVO> ingestLegalSource(@RequestHeader("userFrom") String userFrom,
                                               @RequestBody LegalSourceIngestReqDTO request);

    @PostMapping("/knowledge/embeddings/backfill")
    ApiResult<EmbeddingBackfillVO> backfillEmbeddings(@RequestHeader("userFrom") String userFrom,
                                                       @RequestParam(value = "limit", defaultValue = "100")
                                                       Integer limit);

    @PostMapping("/knowledge/search")
    ApiResult<LegalSearchVO> searchKnowledge(@RequestHeader("userFrom") String userFrom,
                                             @RequestBody LegalSearchReqDTO request);

    @PostMapping("/knowledge/answer")
    ApiResult<LegalAnswerVO> answerKnowledge(@RequestHeader("userFrom") String userFrom,
                                             @RequestBody LegalAnswerReqDTO request);
}
