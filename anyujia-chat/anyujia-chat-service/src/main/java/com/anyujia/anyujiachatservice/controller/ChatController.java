package com.anyujia.anyujiachatservice.controller;

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
import com.anyujia.anyujiachatapi.feign.ChatFeignClient;
import com.anyujia.anyujiachatservice.service.ConversationService;
import com.anyujia.anyujiachatservice.service.MessageService;
import com.anyujia.anyujiachatservice.realtime.ChatWebSocketTicketService;
import com.anyujia.anyujiacommondomain.domain.ApiResult;
import com.anyujia.anyujiacommondomain.domain.ResultCode;
import com.anyujia.anyujiacommondomain.exception.ServiceException;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/chat")
@RequiredArgsConstructor
public class ChatController implements ChatFeignClient {
    private final ConversationService conversationService;
    private final MessageService messageService;
    private final ChatWebSocketTicketService webSocketTicketService;

    @Override
    @PostMapping("/session/add")
    public ApiResult<ConversationCreateVO> create(Long userId, Integer userType, String userFrom,
                                                  @RequestBody @Valid ConversationCreateReqDTO request) {
        requireApp(userFrom);
        return ApiResult.success(conversationService.create(userId, userType, request));
    }

    @Override
    @PostMapping("/session/get")
    public ApiResult<ConversationCreateVO> get(Long userId, Integer userType, String userFrom,
                                               @RequestBody @Valid ConversationGetReqDTO request) {
        requireApp(userFrom);
        return ApiResult.success(conversationService.get(userId, userType, request));
    }

    @Override
    @PostMapping("/session/list")
    public ApiResult<List<ConversationVO>> list(Long userId, String userFrom,
                                                @RequestBody @Valid ConversationListReqDTO request) {
        requireApp(userFrom);
        return ApiResult.success(conversationService.list(userId, request));
    }

    @Override
    @PostMapping("/session/has_house")
    public ApiResult<Boolean> hasHouse(Long userId, String userFrom,
                                       @RequestBody @Valid ConversationHouseReqDTO request) {
        requireApp(userFrom);
        return ApiResult.success(conversationService.hasHouse(userId, request));
    }

    @Override
    @PostMapping("/internal/session/context")
    public ApiResult<ConversationContextVO> context(String userFrom,
                                                    @RequestBody @Valid ConversationContextReqDTO request) {
        requireInternal(userFrom);
        return ApiResult.success(conversationService.context(request));
    }

    @Override
    @PostMapping("/message/list")
    public ApiResult<List<MessageVO>> messages(Long userId, String userFrom,
                                               @RequestBody @Valid MessageListReqDTO request) {
        requireApp(userFrom);
        return ApiResult.success(messageService.list(userId, request));
    }

    @Override
    @PostMapping("/message/sync")
    public ApiResult<List<MessageVO>> syncMessages(Long userId, String userFrom,
                                                   @RequestBody @Valid MessageSyncReqDTO request) {
        requireApp(userFrom);
        return ApiResult.success(messageService.sync(userId, request));
    }

    @Override
    @PostMapping("/message/send")
    public ApiResult<MessageVO> send(Long userId, String userFrom, @RequestBody @Valid MessageSendReqDTO request) {
        requireApp(userFrom);
        return ApiResult.success(messageService.send(userId, request));
    }

    @PostMapping("/agent/session/add")
    public ApiResult<ConversationCreateVO> createAgentSession(@RequestHeader("userId") Long userId,
                                                              @RequestHeader("userFrom") String userFrom,
                                                              @RequestBody @Valid AgentSessionCreateReqDTO request) {
        requireApp(userFrom);
        return ApiResult.success(conversationService.createAgentSession(userId, request));
    }

    @Override
    @PostMapping("/agent/message")
    public ApiResult<MessageVO> sendAgentMessage(Long userId, String userFrom,
                                                 @RequestBody @Valid AgentMessageReqDTO request) {
        requireInternal(userFrom);
        return ApiResult.success(messageService.sendAgentMessage(userId, request));
    }

    @Override
    @PostMapping("/message/batch_visited")
    public ApiResult<Void> visited(Long userId, String userFrom, @RequestBody @Valid MessageVisitedReqDTO request) {
        requireApp(userFrom);
        messageService.markVisited(userId, request);
        return ApiResult.success();
    }

    @Override
    @PostMapping("/message/batch_read")
    public ApiResult<Void> read(Long userId, String userFrom, @RequestBody @Valid MessageReadReqDTO request) {
        requireApp(userFrom);
        messageService.markRead(userId, request);
        return ApiResult.success();
    }

    @Override
    @PostMapping("/websocket/ticket")
    public ApiResult<WebSocketTicketVO> websocketTicket(Long userId, String userName,
                                                        String userFrom, String userType) {
        requireApp(userFrom);
        return ApiResult.success(webSocketTicketService.issue(userId, userName, userFrom, userType));
    }

    private void requireApp(String userFrom) {
        if (!"app".equals(userFrom)) {
            throw new ServiceException("Only C-end users can use chat", ResultCode.TOKEN_CHECK_FAILED.getCode());
        }
    }

    private void requireInternal(String userFrom) {
        if (!"sys".equals(userFrom) && !"service".equals(userFrom)) {
            throw new ServiceException("Only internal services can query chat context",
                    ResultCode.TOKEN_CHECK_FAILED.getCode());
        }
    }
}
