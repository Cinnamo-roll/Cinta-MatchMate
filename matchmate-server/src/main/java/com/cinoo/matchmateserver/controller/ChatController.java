package com.cinoo.matchmateserver.controller;

import com.cinoo.matchmateserver.common.BaseResponse;
import com.cinoo.matchmateserver.common.ResultUtils;
import com.cinoo.matchmateserver.model.request.SendMessageRequest;
import com.cinoo.matchmateserver.model.vo.ConversationVO;
import com.cinoo.matchmateserver.model.vo.MessageVO;
import com.cinoo.matchmateserver.service.ChatService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/chat")
@Tag(name = "聊天管理", description = "单聊消息相关接口")
public class ChatController {

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    @Operation(summary = "发送消息", description = "向指定用户发送一条文本消息，自动创建或复用会话")
    @PostMapping("/message/send")
    public BaseResponse<MessageVO> sendMessage(
            @Valid @RequestBody SendMessageRequest sendRequest,
            HttpServletRequest request) {
        return ResultUtils.success(
                chatService.sendMessage(sendRequest.getReceiverId(), sendRequest.getContent(), request)
        );
    }

    @Operation(summary = "查询消息历史", description = "分页获取会话中的消息历史，同时标记当前用户的未读消息为已读")
    @GetMapping("/messages")
    public BaseResponse<List<MessageVO>> getMessages(
            @RequestParam Long conversationId,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long pageSize,
            HttpServletRequest request) {
        return ResultUtils.success(
                chatService.getMessages(conversationId, page, pageSize, request)
        );
    }

    @Operation(summary = "查询会话列表", description = "获取当前用户的所有会话，含对方用户信息和未读消息数")
    @GetMapping("/conversations")
    public BaseResponse<List<ConversationVO>> getConversations(HttpServletRequest request) {
        return ResultUtils.success(chatService.getConversations(request));
    }

    @GetMapping("/conversation/{conversationId}")
    public BaseResponse<ConversationVO> getConversation(
            @PathVariable Long conversationId,
            HttpServletRequest request) {
        return ResultUtils.success(chatService.getConversation(conversationId, request));
    }

    @Operation(summary = "打开会话", description = "进入会话时调用：清空 Redis 未读计数，标记 MySQL 消息已读，记录当前会话")
    @PutMapping("/conversation/{conversationId}/read")
    public BaseResponse<Void> openConversation(
            @PathVariable Long conversationId,
            HttpServletRequest request) {
        chatService.openConversation(conversationId, request);
        return ResultUtils.success(null);
    }

    @PutMapping("/conversation/{conversationId}/close")
    public BaseResponse<Void> closeConversation(
            @PathVariable Long conversationId,
            HttpServletRequest request) {
        chatService.closeConversation(conversationId, request);
        return ResultUtils.success(null);
    }

    @Operation(summary = "查找会话", description = "查找当前用户与目标用户之间的已有会话 ID，没有则返回 null")
    @GetMapping("/conversation/with/{targetUserId}")
    public BaseResponse<Long> findConversationId(
            @PathVariable Long targetUserId,
            HttpServletRequest request) {
        return ResultUtils.success(chatService.findConversationId(targetUserId, request));
    }
}
