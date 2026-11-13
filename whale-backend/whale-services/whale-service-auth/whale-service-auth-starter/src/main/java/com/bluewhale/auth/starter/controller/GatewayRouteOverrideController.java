package com.bluewhale.auth.starter.controller;

import com.bluewhale.auth.application.model.command.SaveGatewayRouteOverrideCommand;
import com.bluewhale.auth.application.service.GatewayRouteOverrideAppService;
import com.bluewhale.auth.interfaces.api.GatewayRouteOverrideApi;
import com.bluewhale.auth.interfaces.dto.request.GatewayRouteOverrideRequest;
import com.bluewhale.auth.interfaces.dto.response.GatewayRouteOverrideResponse;
import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.auth.starter.assemble.GatewayRouteOverrideAssembler;
import com.bluewhale.common.audit.annotation.AuditLog;
import com.bluewhale.common.audit.enums.OperateType;
import com.bluewhale.common.core.result.Result;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 网关路由覆盖管理接口实现（Web 适配层）：协议适配 + DTO → Command 转换，业务编排在 application 层。
 *
 * <p>停用等于该路由全站 404，写操作全部落审计。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequiredArgsConstructor
public class GatewayRouteOverrideController implements GatewayRouteOverrideApi {

    private final GatewayRouteOverrideAppService gatewayRouteOverrideAppService;

    private final GatewayRouteOverrideAssembler gatewayRouteOverrideAssembler;

    /**
     * 查询路由覆盖列表（含停用与窗口外条目）
     *
     * @return 路由覆盖条目列表
     */
    @Override
    @RequirePermission("sys:gateway:route-override:list")
    public Result<List<GatewayRouteOverrideResponse>> list() {
        return Result.ok(gatewayRouteOverrideAssembler.toResponses(gatewayRouteOverrideAppService.listAll()));
    }

    /**
     * 新增路由覆盖
     *
     * @param routeId 路由 ID
     * @param request 保存请求
     * @return 操作结果
     */
    @Override
    @RequirePermission("sys:gateway:route-override:add")
    @AuditLog(operation = "新增网关路由覆盖", type = OperateType.CREATE, targetType = "sys_gateway_route_override",
            targetId = "#routeId")
    public Result<Void> create(@PathVariable String routeId, @Valid @RequestBody GatewayRouteOverrideRequest request) {
        gatewayRouteOverrideAppService.create(routeId, toCommand(request));
        return Result.ok();
    }

    /**
     * 更新路由覆盖
     *
     * @param routeId 路由 ID
     * @param request 保存请求
     * @return 操作结果
     */
    @Override
    @RequirePermission("sys:gateway:route-override:edit")
    @AuditLog(operation = "更新网关路由覆盖", type = OperateType.UPDATE, targetType = "sys_gateway_route_override",
            targetId = "#routeId")
    public Result<Void> update(@PathVariable String routeId, @Valid @RequestBody GatewayRouteOverrideRequest request) {
        gatewayRouteOverrideAppService.update(routeId, toCommand(request));
        return Result.ok();
    }

    /**
     * 删除路由覆盖（恢复路由默认行为）
     *
     * @param routeId 路由 ID
     * @return 操作结果
     */
    @Override
    @RequirePermission("sys:gateway:route-override:delete")
    @AuditLog(operation = "删除网关路由覆盖", type = OperateType.DELETE, targetType = "sys_gateway_route_override",
            targetId = "#routeId")
    public Result<Void> delete(@PathVariable String routeId) {
        gatewayRouteOverrideAppService.delete(routeId);
        return Result.ok();
    }

    /**
     * 请求参数转换为应用层命令
     *
     * @param request 保存请求
     * @return 保存命令
     */
    private SaveGatewayRouteOverrideCommand toCommand(GatewayRouteOverrideRequest request) {
        return new SaveGatewayRouteOverrideCommand(request.getEnabled(), request.getEffectiveFrom(),
                request.getEffectiveTo(), request.getRemark());
    }
}
