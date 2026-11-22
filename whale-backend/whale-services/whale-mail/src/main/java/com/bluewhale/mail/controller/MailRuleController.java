package com.bluewhale.mail.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.common.web.UserContext;
import com.bluewhale.mail.dto.MailRuleSaveRequest;
import com.bluewhale.mail.service.MailRuleService;
import com.bluewhale.mail.vo.MailRuleApplyVO;
import com.bluewhale.mail.vo.MailRuleVO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 收信规则接口。
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping(path = "/mail/v{version}/rule", version = "1")
@RequiredArgsConstructor
public class MailRuleController {

    private final MailRuleService ruleService;

    /**
     * 规则列表（按优先级升序）
     *
     * @param accountId 账号ID，为空取默认账号
     * @param request 当前请求
     * @return 规则列表
     */
    @GetMapping("/list")
    @RequirePermission("sys:mail:message:list")
    public Result<List<MailRuleVO>> list(@RequestParam(required = false) Long accountId,
                                         HttpServletRequest request) {
        Long userId = UserContext.requireUserId(request);
        return Result.ok(ruleService.list(accountId, userId));
    }

    /**
     * 新建或更新规则
     *
     * @param body 保存请求
     * @param request 当前请求
     * @return 保存后的规则
     */
    @PostMapping("/save")
    @RequirePermission("sys:mail:rule:manage")
    public Result<MailRuleVO> save(@Valid @RequestBody MailRuleSaveRequest body, HttpServletRequest request) {
        Long userId = UserContext.requireUserId(request);
        return Result.ok(ruleService.save(body, userId));
    }

    /**
     * 启用或停用规则
     *
     * @param id 规则ID
     * @param enabled 是否启用
     * @param request 当前请求
     * @return 空结果
     */
    @PostMapping("/enabled")
    @RequirePermission("sys:mail:rule:manage")
    public Result<Void> setEnabled(@RequestParam Long id,
                                   @RequestParam Boolean enabled,
                                   HttpServletRequest request) {
        Long userId = UserContext.requireUserId(request);
        ruleService.setEnabled(id, enabled, userId);
        return Result.ok();
    }

    /**
     * 删除规则
     *
     * @param id 规则ID
     * @param request 当前请求
     * @return 空结果
     */
    @PostMapping("/remove")
    @RequirePermission("sys:mail:rule:manage")
    public Result<Void> remove(@RequestParam Long id, HttpServletRequest request) {
        Long userId = UserContext.requireUserId(request);
        ruleService.remove(id, userId);
        return Result.ok();
    }

    /**
     * 对收件箱存量邮件回填规则
     *
     * @param accountId 账号ID，为空取默认账号
     * @param request 当前请求
     * @return 执行结果
     */
    @PostMapping("/apply")
    @RequirePermission("sys:mail:rule:manage")
    public Result<MailRuleApplyVO> apply(@RequestParam(required = false) Long accountId,
                                         HttpServletRequest request) {
        Long userId = UserContext.requireUserId(request);
        return Result.ok(ruleService.applyToInbox(accountId, userId));
    }
}
