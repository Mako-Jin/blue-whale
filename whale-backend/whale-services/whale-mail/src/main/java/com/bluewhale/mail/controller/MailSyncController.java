package com.bluewhale.mail.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.audit.annotation.AuditLog;
import com.bluewhale.common.audit.enums.OperateType;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.common.web.UserContext;
import com.bluewhale.mail.service.MailSyncService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 收信同步接口（手动触发）。
 *
 * <p>手动同步是排障与首次接入的主要入口：定时同步默认关闭，避免在未验证索引正确性前
 * 让后台任务批量写入。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping("/mail/sync")
@RequiredArgsConstructor
public class MailSyncController {

    private final MailSyncService syncService;

    /**
     * 同步指定账号
     *
     * @param accountId 账号ID
     * @param request 当前请求
     * @return 本次新增邮件数
     */
    @PostMapping("/{accountId}")
    @RequirePermission("sys:mail:sync")
    @AuditLog(operation = "手动同步邮箱", type = OperateType.UPDATE, targetType = "mail_account")
    public Result<Integer> sync(@PathVariable Long accountId, HttpServletRequest request) {
        return Result.ok(syncService.syncAccount(accountId, UserContext.requireUserId(request)));
    }
}
