package com.bluewhale.iot.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.audit.annotation.AuditLog;
import com.bluewhale.common.audit.enums.OperateType;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.iot.dto.RuleSaveRequest;
import com.bluewhale.iot.dto.RuleVO;
import com.bluewhale.iot.service.IotRuleService;
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
 * 规则接口：创建/更新/启停/删除与分页查询。
 *
 * <p>权限码 {@code sys:iot:rule}（规则可触发告警，与设备管理分权）。规则命中后的
 * 告警落库由 {@link com.bluewhale.iot.service.IotRuleEngine} 在遥测链路中完成，
 * 本控制器不做评估。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping(path = "/iot/v{version}/rules", version = "1")
@RequiredArgsConstructor
public class IotRuleController {

    private final IotRuleService ruleService;

    /**
     * 创建规则。
     *
     * @param request 保存请求
     * @return 规则视图
     */
    @PostMapping
    @RequirePermission("sys:iot:rule")
    @AuditLog(operation = "创建IoT规则", type = OperateType.CREATE, targetType = "iot_rule")
    public Result<RuleVO> create(@Valid @RequestBody RuleSaveRequest request) {
        return Result.ok(ruleService.create(request));
    }

    /**
     * 规则分页。
     *
     * @param productId 产品过滤（可空）
     * @param keyword   名称模糊过滤（可空）
     * @param current   当前页
     * @param size      每页条数
     * @return 分页结果
     */
    @GetMapping("/page")
    @RequirePermission("sys:iot:rule")
    public Result<PageResult<RuleVO>> page(
            @RequestParam(value = "productId", required = false) Long productId,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "current", defaultValue = "1") long current,
            @RequestParam(value = "size", defaultValue = "20") long size) {
        return Result.ok(ruleService.page(productId, keyword, current, size));
    }

    /**
     * 规则详情。
     *
     * @param id 规则ID
     * @return 规则视图
     */
    @GetMapping("/{id}")
    @RequirePermission("sys:iot:rule")
    public Result<RuleVO> get(@PathVariable("id") Long id) {
        return Result.ok(ruleService.get(id));
    }

    /**
     * 更新规则。
     *
     * @param id      规则ID
     * @param request 保存请求
     * @return 规则视图
     */
    @PutMapping("/{id}")
    @RequirePermission("sys:iot:rule")
    @AuditLog(operation = "更新IoT规则", type = OperateType.UPDATE, targetType = "iot_rule")
    public Result<RuleVO> update(@PathVariable("id") Long id,
                                 @Valid @RequestBody RuleSaveRequest request) {
        return Result.ok(ruleService.update(id, request));
    }

    /**
     * 启用/停用规则。
     *
     * @param id      规则ID
     * @param enabled true 启用（默认）/ false 停用
     * @return 规则视图
     */
    @PostMapping("/{id}/enable")
    @RequirePermission("sys:iot:rule")
    @AuditLog(operation = "启停IoT规则", type = OperateType.UPDATE, targetType = "iot_rule")
    public Result<RuleVO> enable(@PathVariable("id") Long id,
                                 @RequestParam(value = "enabled", defaultValue = "true") boolean enabled) {
        return Result.ok(ruleService.setEnabled(id, enabled));
    }

    /**
     * 删除规则（逻辑删除）。
     *
     * @param id 规则ID
     * @return 空结果
     */
    @DeleteMapping("/{id}")
    @RequirePermission("sys:iot:rule")
    @AuditLog(operation = "删除IoT规则", type = OperateType.DELETE, targetType = "iot_rule")
    public Result<Void> delete(@PathVariable("id") Long id) {
        ruleService.delete(id);
        return Result.ok();
    }
}
