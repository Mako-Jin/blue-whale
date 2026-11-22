package com.bluewhale.mail.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.audit.annotation.AuditLog;
import com.bluewhale.common.audit.enums.OperateType;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.common.web.UserContext;
import com.bluewhale.mail.dto.MailAccountSaveRequest;
import com.bluewhale.mail.service.MailAccountService;
import com.bluewhale.mail.vo.MailAccountVO;
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
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 邮箱账号接口。
 *
 * <p>服务内路径 {@code /mail/account/**}，经网关按 {@code Path=/mail/**} 转发。
 * 账号归属校验在服务层完成，接口层不做数据范围裁剪。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping(path = "/mail/v{version}/account", version = "1")
@RequiredArgsConstructor
public class MailAccountController {

    /** 审计操作对象类型 */
    private static final String TARGET_TYPE = "mail_account";

    private final MailAccountService accountService;

    /**
     * 当前用户的默认邮箱账号
     *
     * @param request 当前请求
     * @return 账号视图；未绑定时 {@code data} 为 {@code null}
     */
    @GetMapping("/current")
    @RequirePermission("sys:mail:account:list")
    public Result<MailAccountVO> current(HttpServletRequest request) {
        return Result.ok(accountService.current(UserContext.requireUserId(request)));
    }

    /**
     * 当前用户绑定的全部邮箱账号
     *
     * @param request 当前请求
     * @return 账号列表
     */
    @GetMapping("/list")
    @RequirePermission("sys:mail:account:list")
    public Result<List<MailAccountVO>> list(HttpServletRequest request) {
        return Result.ok(accountService.listByUser(UserContext.requireUserId(request)));
    }

    /**
     * 绑定邮箱账号
     *
     * @param body 绑定请求
     * @param request 当前请求
     * @return 创建后的账号
     */
    @PostMapping
    @RequirePermission("sys:mail:account:add")
    @AuditLog(operation = "绑定邮箱账号", type = OperateType.CREATE, targetType = TARGET_TYPE)
    public Result<MailAccountVO> create(@Valid @RequestBody MailAccountSaveRequest body,
                                        HttpServletRequest request) {
        return Result.ok(accountService.create(body, UserContext.requireUserId(request)));
    }

    /**
     * 更新邮箱账号
     *
     * @param id 账号ID
     * @param body 更新请求
     * @param request 当前请求
     * @return 更新后的账号
     */
    @PutMapping("/{id}")
    @RequirePermission("sys:mail:account:edit")
    @AuditLog(operation = "更新邮箱账号", type = OperateType.UPDATE, targetType = TARGET_TYPE)
    public Result<MailAccountVO> update(@PathVariable Long id,
                                        @Valid @RequestBody MailAccountSaveRequest body,
                                        HttpServletRequest request) {
        return Result.ok(accountService.update(id, body, UserContext.requireUserId(request)));
    }

    /**
     * 解绑邮箱账号
     *
     * @param id 账号ID
     * @param request 当前请求
     * @return 空结果
     */
    @DeleteMapping("/{id}")
    @RequirePermission("sys:mail:account:remove")
    @AuditLog(operation = "解绑邮箱账号", type = OperateType.DELETE, targetType = TARGET_TYPE)
    public Result<Void> remove(@PathVariable Long id, HttpServletRequest request) {
        accountService.remove(id, UserContext.requireUserId(request));
        return Result.ok();
    }
}
