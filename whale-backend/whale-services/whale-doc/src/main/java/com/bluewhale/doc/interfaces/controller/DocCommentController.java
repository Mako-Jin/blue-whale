package com.bluewhale.doc.interfaces.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.audit.annotation.AuditLog;
import com.bluewhale.common.audit.enums.OperateType;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.doc.application.service.DocCommentService;
import com.bluewhale.doc.interfaces.dto.DocCommentCreateRequest;
import com.bluewhale.doc.interfaces.vo.DocCommentUnreadVO;
import com.bluewhale.doc.interfaces.vo.DocCommentVO;
import com.bluewhale.doc.support.web.UserContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 页面评论接口。
 *
 * <p>评论挂在 {@code /doc/page/{id}/comment}，解决与删除挂在 {@code /doc/comment/{id}}。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping(path = "/doc/v{version}", version = "1")
@RequiredArgsConstructor
public class DocCommentController {

    /** 审计操作对象类型 */
    private static final String TARGET_TYPE = "doc_comment";

    private final DocCommentService commentService;

    /**
     * 查询页面评论。
     *
     * @param pageId      页面ID
     * @param resolved    解决状态过滤
     * @param httpRequest 当前请求
     * @return 评论列表
     */
    @GetMapping("/page/{pageId}/comment")
    @RequirePermission("sys:doc:comment:list")
    public Result<List<DocCommentVO>> list(@PathVariable Long pageId,
                                           @RequestParam(required = false) Boolean resolved,
                                           HttpServletRequest httpRequest) {
        return Result.ok(commentService.list(pageId, resolved, UserContext.requireUserId(httpRequest)));
    }

    /**
     * 新增评论或回复。
     *
     * @param pageId      页面ID
     * @param request     评论内容
     * @param httpRequest 当前请求
     * @return 新建评论
     */
    @PostMapping("/page/{pageId}/comment")
    @RequirePermission("sys:doc:comment:add")
    @AuditLog(operation = "新增文档评论", type = OperateType.CREATE, targetType = TARGET_TYPE,
            recordParams = false)
    public Result<DocCommentVO> create(@PathVariable Long pageId,
                                       @Valid @RequestBody DocCommentCreateRequest request,
                                       HttpServletRequest httpRequest) {
        return Result.ok(commentService.create(pageId, request, UserContext.requireUserId(httpRequest)));
    }

    /**
     * 解决或重新打开评论。
     *
     * @param id          评论ID
     * @param resolved    目标解决状态，默认 true
     * @param httpRequest 当前请求
     * @return 更新后的评论
     */
    @PutMapping("/comment/{id}/resolve")
    @RequirePermission("sys:doc:comment:manage")
    @AuditLog(operation = "更新文档评论解决状态", type = OperateType.UPDATE, targetType = TARGET_TYPE,
            targetId = "#id")
    public Result<DocCommentVO> resolve(@PathVariable Long id,
                                        @RequestParam(defaultValue = "true") Boolean resolved,
                                        HttpServletRequest httpRequest) {
        return Result.ok(commentService.resolve(id, resolved, UserContext.requireUserId(httpRequest)));
    }

    /**
     * 查询页面评论未读数。
     *
     * @param pageId      页面ID
     * @param httpRequest 当前请求
     * @return 未读信息
     */
    @GetMapping("/page/{pageId}/comment/unread")
    @RequirePermission("sys:doc:comment:list")
    public Result<DocCommentUnreadVO> unread(@PathVariable Long pageId, HttpServletRequest httpRequest) {
        return Result.ok(commentService.unread(pageId, UserContext.requireUserId(httpRequest)));
    }

    /**
     * 标记页面评论已读。
     *
     * @param pageId      页面ID
     * @param httpRequest 当前请求
     * @return 标记后的未读信息
     */
    @PostMapping("/page/{pageId}/comment/read")
    @RequirePermission("sys:doc:comment:list")
    public Result<DocCommentUnreadVO> markRead(@PathVariable Long pageId, HttpServletRequest httpRequest) {
        return Result.ok(commentService.markRead(pageId, UserContext.requireUserId(httpRequest)));
    }

    /**
     * 删除评论。
     *
     * @param id          评论ID
     * @param httpRequest 当前请求
     * @return 空响应体
     */
    @DeleteMapping("/comment/{id}")
    @RequirePermission("sys:doc:comment:manage")
    @AuditLog(operation = "删除文档评论", type = OperateType.DELETE, targetType = TARGET_TYPE, targetId = "#id")
    public Result<Void> delete(@PathVariable Long id, HttpServletRequest httpRequest) {
        commentService.delete(id, UserContext.requireUserId(httpRequest));
        return Result.ok();
    }
}
