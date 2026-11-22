package com.bluewhale.mail.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.audit.annotation.AuditLog;
import com.bluewhale.common.audit.enums.OperateType;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.common.web.UserContext;
import com.bluewhale.mail.dto.MailSendRequest;
import com.bluewhale.mail.service.MailSendService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 发信接口。
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping(path = "/mail/v{version}/send", version = "1")
@RequiredArgsConstructor
public class MailSendController {

    /** 审计操作对象类型 */
    private static final String TARGET_TYPE = "mail_message";

    private final MailSendService sendService;

    /**
     * 发送邮件
     *
     * @param body 发信请求
     * @param request 当前请求
     * @return 空结果
     */
    @PostMapping
    @RequirePermission("sys:mail:message:send")
    @AuditLog(operation = "发送邮件", type = OperateType.CREATE, targetType = TARGET_TYPE)
    public Result<Void> send(@Valid @RequestBody MailSendRequest body, HttpServletRequest request) {
        sendService.send(body, UserContext.requireUserId(request));
        return Result.ok();
    }
}
