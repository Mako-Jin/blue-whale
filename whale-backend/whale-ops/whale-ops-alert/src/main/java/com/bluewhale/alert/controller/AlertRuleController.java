package com.bluewhale.alert.controller;

import com.bluewhale.alert.dto.AlertRuleQueryRequest;
import com.bluewhale.alert.dto.AlertRuleSaveRequest;
import com.bluewhale.alert.dto.AlertRuleStatusRequest;
import com.bluewhale.alert.service.AlertRuleService;
import com.bluewhale.alert.vo.AlertRuleVO;
import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.audit.annotation.AuditLog;
import com.bluewhale.common.audit.enums.OperateType;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.core.result.Result;
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

/**
 * 告警规则管理接口
 *
 * <p>服务内路径 {@code /alert/v1/rule/**}；经网关按 {@code Path=/alert/**} 规则转发，
 * 外部路径与本文档一致。全部接口返回统一响应体 {@link Result}，受
 * {@code @RequirePermission("sys:alert:rule:*")} 保护（由网关权限码头
 * {@code X-Permission-Codes} + starter {@code RequirePermissionInterceptor} 判定），
 * 写操作均标注 {@link AuditLog} 留痕。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping("/alert/v1/rule")
@RequiredArgsConstructor
public class AlertRuleController {

    /** 审计操作对象类型 */
    private static final String TARGET_TYPE = "alert_rule";

    private final AlertRuleService alertRuleService;

    /**
     * 分页查询告警规则
     *
     * @param request 查询条件
     * @return 分页结果
     */
    @GetMapping("/page")
    @RequirePermission("sys:alert:rule:list")
    public Result<PageResult<AlertRuleVO>> page(AlertRuleQueryRequest request) {
        return Result.ok(alertRuleService.page(request));
    }

    /**
     * 告警规则详情
     *
     * @param id 规则ID
     * @return 规则视图对象
     */
    @GetMapping("/{id}")
    @RequirePermission("sys:alert:rule:list")
    public Result<AlertRuleVO> detail(@PathVariable Long id) {
        return Result.ok(alertRuleService.getDetail(id));
    }

    /**
     * 新增告警规则
     *
     * @param request 新增请求
     * @return 新增后的规则视图对象
     */
    @PostMapping
    @RequirePermission("sys:alert:rule:add")
    @AuditLog(operation = "新增告警规则", type = OperateType.CREATE, targetType = TARGET_TYPE)
    public Result<AlertRuleVO> create(@Valid @RequestBody AlertRuleSaveRequest request) {
        return Result.ok(alertRuleService.create(request));
    }

    /**
     * 更新告警规则
     *
     * @param id      规则ID
     * @param request 更新请求
     * @return 更新后的规则视图对象
     */
    @PutMapping("/{id}")
    @RequirePermission("sys:alert:rule:edit")
    @AuditLog(operation = "更新告警规则", type = OperateType.UPDATE, targetType = TARGET_TYPE, targetId = "#id")
    public Result<AlertRuleVO> update(@PathVariable Long id, @Valid @RequestBody AlertRuleSaveRequest request) {
        return Result.ok(alertRuleService.update(id, request));
    }

    /**
     * 删除告警规则（逻辑删除）
     *
     * @param id 规则ID
     * @return 空响应体
     */
    @DeleteMapping("/{id}")
    @RequirePermission("sys:alert:rule:delete")
    @AuditLog(operation = "删除告警规则", type = OperateType.DELETE, targetType = TARGET_TYPE, targetId = "#id")
    public Result<Void> delete(@PathVariable Long id) {
        alertRuleService.delete(id);
        return Result.ok();
    }

    /**
     * 启停告警规则
     *
     * @param id      规则ID
     * @param request 启停请求，{@code status}：1 启用 / 0 停用
     * @return 空响应体
     */
    @PutMapping("/{id}/status")
    @RequirePermission("sys:alert:rule:edit")
    @AuditLog(operation = "启停告警规则", type = OperateType.UPDATE, targetType = TARGET_TYPE, targetId = "#id")
    public Result<Void> updateStatus(@PathVariable Long id, @Valid @RequestBody AlertRuleStatusRequest request) {
        alertRuleService.updateStatus(id, request.getStatus());
        return Result.ok();
    }
}
