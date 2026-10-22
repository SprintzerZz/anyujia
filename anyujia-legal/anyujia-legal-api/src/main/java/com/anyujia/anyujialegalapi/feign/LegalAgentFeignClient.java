package com.anyujia.anyujialegalapi.feign;

import com.anyujia.anyujialegalapi.domain.dto.LegalAgentAnswerReqDTO;
import com.anyujia.anyujialegalapi.domain.dto.LegalAgentBindReqDTO;
import com.anyujia.anyujialegalapi.domain.dto.LegalAgentTaskSubmitReqDTO;
import com.anyujia.anyujialegalapi.domain.vo.LegalAgentAnswerVO;
import com.anyujia.anyujialegalapi.domain.vo.LegalAgentTaskVO;
import com.anyujia.anyujiacommondomain.domain.ApiResult;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(contextId = "legalAgentFeignClient", value = "anyujia-legal", path = "/legal")
public interface LegalAgentFeignClient {
    @PostMapping("/agent/answer")
    ApiResult<LegalAgentAnswerVO> answer(@RequestHeader("userId") Long userId,
                                         @RequestHeader("userType") Integer userType,
                                         @RequestHeader("userFrom") String userFrom,
                                         @RequestBody LegalAgentAnswerReqDTO request);

    @PostMapping("/agent/tasks")
    ApiResult<LegalAgentTaskVO> submitTask(@RequestHeader("userId") Long userId,
                                           @RequestHeader("userType") Integer userType,
                                           @RequestHeader("userFrom") String userFrom,
                                           @RequestBody LegalAgentTaskSubmitReqDTO request);

    @GetMapping("/agent/tasks/{taskId}")
    ApiResult<LegalAgentTaskVO> getTask(@RequestHeader("userId") Long userId,
                                        @RequestHeader("userType") Integer userType,
                                        @RequestHeader("userFrom") String userFrom,
                                        @PathVariable("taskId") Long taskId);

    @PostMapping("/agent/bind")
    ApiResult<Void> bind(@RequestHeader("userFrom") String userFrom,
                         @RequestBody LegalAgentBindReqDTO request);
}
