package com.bluewhale.auth.starter.controller;

import com.bluewhale.auth.application.model.command.SaveGatewayAccessRuleCommand;
import com.bluewhale.auth.application.service.GatewayBlacklistAppService;
import com.bluewhale.auth.interfaces.api.GatewayBlacklistApi;
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
 * 网关黑名单管理接口实现（Web 适配层）：协议适配 + DTO ↔ Command 转换，业务编排在 application 层。
 *
 * <p>黑名单优先于白名单，写操作需谨慎（误封登录路径将导致全站不可登录）。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequiredArgsConstructor
public class GatewayBlacklistController implements GatewayBlacklistApi {

    private final GatewayBlacklistAppService gatewayBlacklistAppService;

    private final GatewayAccessRuleAssembler gatewayAccessRuleAssembler;

    /**
     * 查询黑名单列表（含已解除项）
     *
     * @param status 状态过滤；为空表示全部
     * @return 黑名单条目列表
     */
    @Override
    @RequirePermission("sys:gateway:blacklist:list")
    public Result<List<GatewayAccessRuleResponse>> list(@RequestParam(required = false) Integer status) {
        return Result.ok(gatewayAccessRuleAssembler.toResponses(gatewayBlacklistAppService.list(status)));
    }

    /**
     * 新增黑名单条目
     *
     * @param request 保存请求
     * @return 操作结果
     */
    @Override
    @RequirePermission("sys:gateway:blacklist:add")
    @AuditLog(operation = "新增网关黑名单", type = OperateType.CREATE, targetType = "sys_gateway_blacklist")
    public Result<Void> create(@Valid @RequestBody GatewayAccessRuleRequest request) {
        gatewayBlacklistAppService.create(toCommand(request));
        return Result.ok();
    }

    /**
     * 更新黑名单条目
     *
     * @param id 主键
     * @param request 保存请求
     * @return 操作结果
     */
    @Override
    @RequirePermission("sys:gateway:blacklist:edit")
    @AuditLog(operation = "更新网关黑名单", type = OperateType.UPDATE, targetType = "sys_gateway_blacklist",
            targetId = "#id")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody GatewayAccessRuleRequest request) {
        gatewayBlacklistAppService.update(id, toCommand(request));
        return Result.ok();
    }

    /**
     * 删除黑名单条目
     *
     * @param id 主键
     * @return 操作结果
     */
    @Override
    @RequirePermission("sys:gateway:blacklist:delete")
    @AuditLog(operation = "删除网关黑名单", type = OperateType.DELETE, targetType = "sys_gateway_blacklist",
            targetId = "#id")
    public Result<Void> delete(@PathVariable Long id) {
        gatewayBlacklistAppService.delete(id);
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
                null, null, request.getStatus(), request.getSortNo(), request.getRemark());
    }
}
