package com.bluewhale.lms.interfaces.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.audit.annotation.AuditLog;
import com.bluewhale.common.audit.enums.OperateType;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.lms.common.LmsActor;
import com.bluewhale.lms.common.LmsPermissions;
import com.bluewhale.lms.credit.application.command.CreditRuleSaveCommand;
import com.bluewhale.lms.credit.application.service.CreditRuleAppService;
import com.bluewhale.lms.credit.application.view.CreditRuleView;
import com.bluewhale.lms.credit.domain.model.CreditRuleQuery;
import com.bluewhale.lms.interfaces.support.LmsActorContext;
import com.bluewhale.lms.interfaces.support.PageParams;
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
 * 学分规则接口（M4-2）。
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping("/lms/credit/rule")
@RequiredArgsConstructor
public class CreditRuleController {

    private final CreditRuleAppService creditRuleAppService;

    /**
     * 规则分页。
     *
     * @param keyword 关键字
     * @param sourceType 来源类型
     * @param status 状态
     * @param current 后端风格页码
     * @param pageNum 前端风格页码
     * @param size 后端风格条数
     * @param pageSize 前端风格条数
     * @param request 当前请求
     * @return 分页结果
     */
    @GetMapping("/page")
    @RequirePermission(LmsPermissions.CREDIT_MANAGE)
    public Result<PageResult<CreditRuleView>> page(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "sourceType", required = false) String sourceType,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "current", required = false) Long current,
            @RequestParam(value = "pageNum", required = false) Long pageNum,
            @RequestParam(value = "size", required = false) Long size,
            @RequestParam(value = "pageSize", required = false) Long pageSize,
            HttpServletRequest request) {
        CreditRuleQuery query = new CreditRuleQuery();
        query.setKeyword(keyword);
        query.setSourceType(sourceType);
        query.setStatus(status);
        query.setCurrent(PageParams.current(current, pageNum));
        query.setSize(PageParams.size(size, pageSize));
        return Result.ok(creditRuleAppService.page(query, actor(request)));
    }

    /**
     * 规则详情。
     *
     * @param id 规则 ID
     * @param request 当前请求
     * @return 详情
     */
    @GetMapping("/{id}")
    @RequirePermission(LmsPermissions.CREDIT_MANAGE)
    public Result<CreditRuleView> detail(@PathVariable Long id, HttpServletRequest request) {
        return Result.ok(creditRuleAppService.detail(id, actor(request)));
    }

    /**
     * 新建规则。
     *
     * @param command 保存命令
     * @param request 当前请求
     * @return 规则 ID
     */
    @PostMapping
    @RequirePermission(LmsPermissions.CREDIT_MANAGE)
    @AuditLog(operation = "新建学分规则", type = OperateType.CREATE, targetType = "lr_credit_rule")
    public Result<Long> create(@Valid @RequestBody CreditRuleSaveCommand command,
                               HttpServletRequest request) {
        return Result.ok(creditRuleAppService.create(command, actor(request)));
    }

    /**
     * 编辑规则。
     *
     * @param id 规则 ID
     * @param command 保存命令
     * @param request 当前请求
     * @return 空
     */
    @PutMapping("/{id}")
    @RequirePermission(LmsPermissions.CREDIT_MANAGE)
    @AuditLog(operation = "编辑学分规则", type = OperateType.UPDATE, targetType = "lr_credit_rule")
    public Result<Void> update(@PathVariable Long id,
                               @Valid @RequestBody CreditRuleSaveCommand command,
                               HttpServletRequest request) {
        creditRuleAppService.update(id, command, actor(request));
        return Result.ok();
    }

    /**
     * 删除规则。
     *
     * @param id 规则 ID
     * @param request 当前请求
     * @return 空
     */
    @DeleteMapping("/{id}")
    @RequirePermission(LmsPermissions.CREDIT_MANAGE)
    @AuditLog(operation = "删除学分规则", type = OperateType.DELETE, targetType = "lr_credit_rule")
    public Result<Void> delete(@PathVariable Long id, HttpServletRequest request) {
        creditRuleAppService.delete(id, actor(request));
        return Result.ok();
    }

    /**
     * 启用 / 停用规则。
     *
     * @param id 规则 ID
     * @param enabled 是否启用
     * @param request 当前请求
     * @return 空
     */
    @PutMapping("/{id}/status")
    @RequirePermission(LmsPermissions.CREDIT_MANAGE)
    @AuditLog(operation = "变更学分规则状态", type = OperateType.UPDATE, targetType = "lr_credit_rule")
    public Result<Void> changeStatus(@PathVariable Long id,
                                     @RequestParam("enabled") boolean enabled,
                                     HttpServletRequest request) {
        creditRuleAppService.changeStatus(id, enabled, actor(request));
        return Result.ok();
    }

    /**
     * 解析操作人。
     *
     * @param request 当前请求
     * @return 操作人
     */
    private LmsActor actor(HttpServletRequest request) {
        return LmsActorContext.from(request);
    }
}
