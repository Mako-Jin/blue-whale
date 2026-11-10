package com.bluewhale.mail.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.audit.annotation.AuditLog;
import com.bluewhale.common.audit.enums.OperateType;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.common.web.UserContext;
import com.bluewhale.mail.dto.MailMessageQueryRequest;
import com.bluewhale.mail.service.MailMessageService;
import com.bluewhale.mail.vo.MailMessageVO;
import com.bluewhale.mail.vo.MailUnreadVO;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 邮件查询与状态接口。
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping("/mail/message")
@RequiredArgsConstructor
public class MailMessageController {

    /** 审计操作对象类型 */
    private static final String TARGET_TYPE = "mail_message";

    private final MailMessageService messageService;

    /**
     * 分页查询邮件
     *
     * @param query 查询条件
     * @param request 当前请求
     * @return 分页结果
     */
    @GetMapping("/page")
    @RequirePermission("sys:mail:message:list")
    public Result<PageResult<MailMessageVO>> page(MailMessageQueryRequest query, HttpServletRequest request) {
        return Result.ok(messageService.page(query, UserContext.requireUserId(request)));
    }

    /**
     * 未读统计
     *
     * @param request 当前请求
     * @return 未读数
     */
    @GetMapping("/unread")
    @RequirePermission("sys:mail:message:list")
    public Result<MailUnreadVO> unread(HttpServletRequest request) {
        return Result.ok(messageService.unread(UserContext.requireUserId(request)));
    }

    /**
     * 邮件详情（可能触发正文回源）
     *
     * @param id 邮件ID
     * @param request 当前请求
     * @return 邮件详情
     */
    @GetMapping("/{id}")
    @RequirePermission("sys:mail:message:list")
    public Result<MailMessageVO> detail(@PathVariable Long id, HttpServletRequest request) {
        return Result.ok(messageService.detail(id, UserContext.requireUserId(request)));
    }

    /**
     * 标记已读/未读
     *
     * @param id 邮件ID
     * @param seen 1 已读 / 0 未读
     * @param request 当前请求
     * @return 空结果
     */
    @PutMapping("/{id}/seen")
    @RequirePermission("sys:mail:message:edit")
    public Result<Void> markSeen(@PathVariable Long id,
                                 @RequestParam(defaultValue = "1") Integer seen,
                                 HttpServletRequest request) {
        messageService.markSeen(id, seen, UserContext.requireUserId(request));
        return Result.ok();
    }

    /**
     * 标记星标
     *
     * @param id 邮件ID
     * @param flagged 1 星标 / 0 取消
     * @param request 当前请求
     * @return 空结果
     */
    @PutMapping("/{id}/flagged")
    @RequirePermission("sys:mail:message:edit")
    public Result<Void> markFlagged(@PathVariable Long id,
                                    @RequestParam(defaultValue = "1") Integer flagged,
                                    HttpServletRequest request) {
        messageService.markFlagged(id, flagged, UserContext.requireUserId(request));
        return Result.ok();
    }

    /**
     * 移入回收站
     *
     * @param id 邮件ID
     * @param request 当前请求
     * @return 空结果
     */
    @DeleteMapping("/{id}")
    @RequirePermission("sys:mail:message:remove")
    @AuditLog(operation = "删除邮件（移入回收站）", type = OperateType.DELETE, targetType = TARGET_TYPE)
    public Result<Void> remove(@PathVariable Long id, HttpServletRequest request) {
        messageService.moveToTrash(id, UserContext.requireUserId(request));
        return Result.ok();
    }
}
