package com.bluewhale.auth.starter.controller;

import com.bluewhale.auth.application.model.command.SaveGatewayAccessRuleCommand;
import com.bluewhale.auth.application.service.GatewayWhitelistAppService;
import com.bluewhale.auth.interfaces.api.GatewayWhitelistApi;
import com.bluewhale.auth.interfaces.dto.request.GatewayAccessRuleRequest;
import com.bluewhale.auth.interfaces.dto.response.GatewayAccessRuleResponse;
import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.auth.starter.assemble.GatewayAccessRuleAssembler;
import com.bluewhale.common.audit.annotation.AuditLog;
import com.bluewhale.common.audit.enums.OperateType;
import com.bluewhale.common.core.result.Result;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 网关白名单管理接口实现（Web 适配层）：协议适配 + DTO ↔ Command 转换，业务编排在 application 层。
 *
 * @author BlueWhale
 */
@RestController
@RequiredArgsConstructor
public class GatewayWhitelistController implements GatewayWhitelistApi {

    private final GatewayWhitelistAppService gatewayWhitelistAppService;

    private final GatewayAccessRuleAssembler gatewayAccessRuleAssembler;

    /**
     * 查询白名单列表（含停用项）
     *
     * @param status 状态过滤；为空表示全部
     * @return 白名单条目列表
     */
    @Override
    @RequirePermission("sys:gateway:whitelist:list")
    public Result<List<GatewayAccessRuleResponse>> list(@RequestParam(required = false) Integer status) {
        return Result.ok(gatewayAccessRuleAssembler.toResponses(gatewayWhitelistAppService.list(status)));
    }

    /**
     * 新增白名单条目
     *
     * @param request 保存请求
     * @return 操作结果
     */
    @Override
    @RequirePermission("sys:gateway:whitelist:add")
    @AuditLog(operation = "新增网关白名单", type = OperateType.CREATE, targetType = "sys_gateway_whitelist")
    public Result<Void> create(@Valid @RequestBody GatewayAccessRuleRequest request) {
        gatewayWhitelistAppService.create(toCommand(request));
        return Result.ok();
    }

    /**
     * 更新白名单条目
     *
     * @param id 主键
     * @param request 保存请求
     * @return 操作结果
     */
    @Override
    @RequirePermission("sys:gateway:whitelist:edit")
    @AuditLog(operation = "更新网关白名单", type = OperateType.UPDATE, targetType = "sys_gateway_whitelist",
            targetId = "#id")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody GatewayAccessRuleRequest request) {
        gatewayWhitelistAppService.update(id, toCommand(request));
        return Result.ok();
    }

    /**
     * 删除白名单条目
     *
     * @param id 主键
     * @return 操作结果
     */
    @Override
    @RequirePermission("sys:gateway:whitelist:delete")
    @AuditLog(operation = "删除网关白名单", type = OperateType.DELETE, targetType = "sys_gateway_whitelist",
            targetId = "#id")
    public Result<Void> delete(@PathVariable Long id) {
        gatewayWhitelistAppService.delete(id);
        return Result.ok();
    }

    /**
     * 请求参数转换为应用层命令
     *
     * @param request 保存请求
     * @return 保存命令
     */
    private SaveGatewayAccessRuleCommand toCommand(GatewayAccessRuleRequest request) {
        return new SaveGatewayAccessRuleCommand(request.getPathPattern(), request.getHttpMethod(),
                request.getStatus(), request.getSortNo(), request.getRemark());
    }
}
