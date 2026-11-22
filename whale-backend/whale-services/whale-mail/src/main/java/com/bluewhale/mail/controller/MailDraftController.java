package com.bluewhale.mail.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.common.web.UserContext;
import com.bluewhale.mail.dto.MailDraftSaveRequest;
import com.bluewhale.mail.service.MailDraftService;
import com.bluewhale.mail.vo.MailDraftVO;
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

/**
 * 草稿接口。
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping(path = "/mail/v{version}/draft", version = "1")
@RequiredArgsConstructor
public class MailDraftController {

    private final MailDraftService draftService;

    /**
     * 保存草稿
     *
     * @param body 草稿内容
     * @param request 当前请求
     * @return 草稿视图
     */
    @PostMapping
    @RequirePermission("sys:mail:message:send")
    public Result<MailDraftVO> create(@Valid @RequestBody MailDraftSaveRequest body,
                                      HttpServletRequest request) {
        return Result.ok(draftService.save(null, body, UserContext.requireUserId(request)));
    }

    /**
     * 更新草稿
     *
     * @param id 草稿ID
     * @param body 草稿内容
     * @param request 当前请求
     * @return 草稿视图
     */
    @PutMapping("/{id}")
    @RequirePermission("sys:mail:message:send")
    public Result<MailDraftVO> update(@PathVariable Long id,
                                      @Valid @RequestBody MailDraftSaveRequest body,
                                      HttpServletRequest request) {
        return Result.ok(draftService.save(id, body, UserContext.requireUserId(request)));
    }

    /**
     * 草稿分页
     *
     * @param current 页码
     * @param size 每页条数
     * @param accountId 账号ID，为空取默认账号
     * @param request 当前请求
     * @return 分页结果
     */
    @GetMapping("/page")
    @RequirePermission("sys:mail:message:list")
    public Result<PageResult<MailDraftVO>> page(@RequestParam(defaultValue = "1") long current,
                                                @RequestParam(defaultValue = "10") long size,
                                                @RequestParam(required = false) Long accountId,
                                                HttpServletRequest request) {
        return Result.ok(draftService.page(current, size, accountId, UserContext.requireUserId(request)));
    }

    /**
     * 删除草稿
     *
     * @param id 草稿ID
     * @param request 当前请求
     * @return 空结果
     */
    @DeleteMapping("/{id}")
    @RequirePermission("sys:mail:message:send")
    public Result<Void> remove(@PathVariable Long id, HttpServletRequest request) {
        draftService.remove(id, UserContext.requireUserId(request));
        return Result.ok();
    }
}
