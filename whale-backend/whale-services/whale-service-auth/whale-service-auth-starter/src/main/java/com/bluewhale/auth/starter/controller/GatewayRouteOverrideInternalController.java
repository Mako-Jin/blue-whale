package com.bluewhale.auth.starter.controller;

import com.bluewhale.auth.application.service.GatewayRouteOverrideAppService;
import com.bluewhale.auth.interfaces.api.GatewayRouteOverrideInternalApi;
import com.bluewhale.auth.interfaces.dto.response.GatewayRouteOverrideItemResponse;
import com.bluewhale.auth.starter.assemble.GatewayRouteOverrideAssembler;
import com.bluewhale.common.core.result.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 网关路由覆盖内网接口实现（Web 适配层）：仅供 whale-service-gateway 定时拉取。
 *
 * <p>外部访问由网关 internal-deny 拦截，服务侧令牌由 {@code InternalTokenFilter} 校验。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequiredArgsConstructor
public class GatewayRouteOverrideInternalController implements GatewayRouteOverrideInternalApi {

    private final GatewayRouteOverrideAppService gatewayRouteOverrideAppService;

    private final GatewayRouteOverrideAssembler gatewayRouteOverrideAssembler;

    /**
     * 查询全部路由覆盖条目
     *
     * @return 路由覆盖项列表
     */
    @Override
    public Result<List<GatewayRouteOverrideItemResponse>> listAll() {
        return Result.ok(gatewayRouteOverrideAssembler.toItemResponses(
                gatewayRouteOverrideAppService.listAll()));
    }
}
