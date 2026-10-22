package com.bluewhale.agent.controller;

import com.bluewhale.agent.dto.ConversationVO;
import com.bluewhale.agent.dto.MessageVO;
import com.bluewhale.agent.service.AgentConversationService;
import com.bluewhale.agent.support.web.UserContext;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.core.result.Result;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 会话接口：我的会话分页、会话消息回放、删除会话（均仅本人可见）。
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping("/agent/conversation")
@RequiredArgsConstructor
public class AgentConversationController {

    private final AgentConversationService conversationService;

    /**
     * 我的会话分页。
     *
     * @param current     当前页（默认 1）
     * @param size        每页条数（默认 20，上限 100）
     * @param httpRequest 当前请求
     * @return 分页结果
     */
    @GetMapping("/page")
    public Result<PageResult<ConversationVO>> page(@RequestParam(value = "current", defaultValue = "1") long current,
                                                   @RequestParam(value = "size", defaultValue = "20") long size,
                                                   HttpServletRequest httpRequest) {
        return Result.ok(conversationService.page(UserContext.requireUserId(httpRequest), current, size));
    }

    /**
     * 会话消息（含引用块）。
     *
     * @param id          会话ID
     * @param httpRequest 当前请求
     * @return 消息列表
     */
    @GetMapping("/{id}/messages")
    public Result<List<MessageVO>> messages(@PathVariable("id") Long id, HttpServletRequest httpRequest) {
        return Result.ok(conversationService.messages(id, UserContext.requireUserId(httpRequest)));
    }

    /**
     * 删除会话及其消息。
     *
     * @param id          会话ID
     * @param httpRequest 当前请求
     * @return 空结果
     */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable("id") Long id, HttpServletRequest httpRequest) {
        conversationService.delete(id, UserContext.requireUserId(httpRequest));
        return Result.ok();
    }
}
