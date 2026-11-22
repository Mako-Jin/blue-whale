package com.bluewhale.oa.culture.interfaces.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.audit.annotation.AuditLog;
import com.bluewhale.common.audit.enums.OperateType;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.common.web.UserContext;
import com.bluewhale.common.web.WebHeaders;
import com.bluewhale.oa.culture.application.command.CultureCommands;
import com.bluewhale.oa.culture.application.service.SuggestionAppService;
import com.bluewhale.oa.culture.application.view.CultureViews;
import com.bluewhale.oa.culture.domain.model.CultureQuery;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 建议箱接口。
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping(path = "/oa/v{version}/suggestion", version = "1")
@RequiredArgsConstructor
public class SuggestionController {

    private static final String TARGET_TYPE = "oa_suggestion";

    private final SuggestionAppService suggestionAppService;

    /**
     * 建议分页（{@code mine=true} 仅看自己）。
     *
     * @param query 查询条件
     * @param request 请求上下文
     * @return 分页结果
     */
    @GetMapping("/page")
    @RequirePermission("sys:oa:suggestion:list")
    public Result<PageResult<CultureViews.Suggestion>> page(CultureQuery query, HttpServletRequest request) {
        return Result.ok(suggestionAppService.page(query, UserContext.requireUserId(request)));
    }

    /**
     * 建议详情。
     *
     * @param id 建议ID
     * @return 详情
     */
    @GetMapping("/{id}")
    @RequirePermission("sys:oa:suggestion:list")
    public Result<CultureViews.SuggestionDetail> detail(@PathVariable Long id) {
        return Result.ok(suggestionAppService.detail(id));
    }

    /**
     * 提交建议。
     *
     * @param command 提交命令
     * @param request 请求上下文
     * @return 建议
     */
    @PostMapping
    @RequirePermission("sys:oa:suggestion:add")
    @AuditLog(operation = "提交建议", type = OperateType.CREATE, targetType = TARGET_TYPE)
    public Result<CultureViews.Suggestion> create(@Valid @RequestBody CultureCommands.SuggestionSave command,
                                                  HttpServletRequest request) {
        return Result.ok(suggestionAppService.create(command,
                UserContext.requireUserId(request), WebHeaders.username(request)));
    }

    /**
     * 回复建议。
     *
     * @param id 建议ID
     * @param command 回复命令
     * @param request 请求上下文
     * @return 建议
     */
    @PostMapping("/{id}/reply")
    @RequirePermission("sys:oa:suggestion:reply")
    @AuditLog(operation = "回复建议", type = OperateType.UPDATE, targetType = TARGET_TYPE)
    public Result<CultureViews.Suggestion> reply(@PathVariable Long id,
                                                 @Valid @RequestBody CultureCommands.SuggestionReplySave command,
                                                 HttpServletRequest request) {
        return Result.ok(suggestionAppService.reply(id, command,
                UserContext.requireUserId(request), WebHeaders.username(request)));
    }

    /**
     * 关闭建议。
     *
     * @param id 建议ID
     * @return 空结果
     */
    @PutMapping("/{id}/close")
    @RequirePermission("sys:oa:suggestion:reply")
    @AuditLog(operation = "关闭建议", type = OperateType.UPDATE, targetType = TARGET_TYPE)
    public Result<Void> close(@PathVariable Long id) {
        suggestionAppService.close(id);
        return Result.ok();
    }
}
