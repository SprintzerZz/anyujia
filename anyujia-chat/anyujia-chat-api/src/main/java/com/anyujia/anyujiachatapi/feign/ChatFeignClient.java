package com.anyujia.anyujiachatapi.feign;

import com.anyujia.anyujiachatapi.domain.dto.AgentMessageReqDTO;
import com.anyujia.anyujiachatapi.domain.dto.AgentSessionCreateReqDTO;
import com.anyujia.anyujiachatapi.domain.dto.ConversationCreateReqDTO;
import com.anyujia.anyujiachatapi.domain.dto.ConversationContextReqDTO;
import com.anyujia.anyujiachatapi.domain.dto.ConversationGetReqDTO;
import com.anyujia.anyujiachatapi.domain.dto.ConversationHouseReqDTO;
import com.anyujia.anyujiachatapi.domain.dto.ConversationListReqDTO;
import com.anyujia.anyujiachatapi.domain.dto.MessageListReqDTO;
import com.anyujia.anyujiachatapi.domain.dto.MessageReadReqDTO;
import com.anyujia.anyujiachatapi.domain.dto.MessageSendReqDTO;
import com.anyujia.anyujiachatapi.domain.dto.MessageSyncReqDTO;
import com.anyujia.anyujiachatapi.domain.dto.MessageVisitedReqDTO;
import com.anyujia.anyujiachatapi.domain.vo.ConversationCreateVO;
import com.anyujia.anyujiachatapi.domain.vo.ConversationContextVO;
import com.anyujia.anyujiachatapi.domain.vo.ConversationVO;
import com.anyujia.anyujiachatapi.domain.vo.MessageVO;
import com.anyujia.anyujiachatapi.domain.vo.WebSocketTicketVO;
import com.anyujia.anyujiacommondomain.domain.ApiResult;
import java.util.List;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

@FeignClient(contextId = "chatFeignClient", value = "anyujia-chat",
        url = "${CHAT_SERVICE_URL:}", path = "/chat")
public interface ChatFeignClient {
    @PostMapping("/session/add")
    ApiResult<ConversationCreateVO> create(@RequestHeader("userId") Long userId,
                                          @RequestHeader("userType") Integer userType,
                                          @RequestHeader("userFrom") String userFrom,
                                          @RequestBody ConversationCreateReqDTO request);

    @PostMapping("/agent/session/add")
    ApiResult<ConversationCreateVO> createAgentSession(@RequestHeader("userId") Long userId,
                                                       @RequestHeader("userFrom") String userFrom,
                                                       @RequestBody AgentSessionCreateReqDTO request);

    @PostMapping("/session/get")
    ApiResult<ConversationCreateVO> get(@RequestHeader("userId") Long userId,
                                       @RequestHeader("userType") Integer userType,
                                       @RequestHeader("userFrom") String userFrom,
                                       @RequestBody ConversationGetReqDTO request);

    @PostMapping("/session/list")
    ApiResult<List<ConversationVO>> list(@RequestHeader("userId") Long userId,
                                        @RequestHeader("userFrom") String userFrom,
                                        @RequestBody ConversationListReqDTO request);

    @PostMapping("/session/has_house")
    ApiResult<Boolean> hasHouse(@RequestHeader("userId") Long userId,
                                @RequestHeader("userFrom") String userFrom,
                                @RequestBody ConversationHouseReqDTO request);

    @PostMapping("/internal/session/context")
    ApiResult<ConversationContextVO> context(@RequestHeader("userFrom") String userFrom,
                                             @RequestBody ConversationContextReqDTO request);

    @PostMapping("/message/list")
    ApiResult<List<MessageVO>> messages(@RequestHeader("userId") Long userId,
                                        @RequestHeader("userFrom") String userFrom,
                                        @RequestBody MessageListReqDTO request);

    @PostMapping("/message/sync")
    ApiResult<List<MessageVO>> syncMessages(@RequestHeader("userId") Long userId,
                                            @RequestHeader("userFrom") String userFrom,
                                            @RequestBody MessageSyncReqDTO request);

    @PostMapping("/message/send")
    ApiResult<MessageVO> send(@RequestHeader("userId") Long userId,
                              @RequestHeader("userFrom") String userFrom,
                              @RequestBody MessageSendReqDTO request);

    @PostMapping("/agent/message")
    ApiResult<MessageVO> sendAgentMessage(@RequestHeader("userId") Long userId,
                                          @RequestHeader("userFrom") String userFrom,
                                          @RequestBody AgentMessageReqDTO request);

    @PostMapping("/message/batch_visited")
    ApiResult<Void> visited(@RequestHeader("userId") Long userId,
                            @RequestHeader("userFrom") String userFrom,
                            @RequestBody MessageVisitedReqDTO request);

    @PostMapping("/message/batch_read")
    ApiResult<Void> read(@RequestHeader("userId") Long userId,
                         @RequestHeader("userFrom") String userFrom,
                         @RequestBody MessageReadReqDTO request);

    @PostMapping("/websocket/ticket")
    ApiResult<WebSocketTicketVO> websocketTicket(@RequestHeader("userId") Long userId,
                                                  @RequestHeader("userName") String userName,
                                                  @RequestHeader("userFrom") String userFrom,
                                                  @RequestHeader(value = "userType", required = false) String userType);
}
