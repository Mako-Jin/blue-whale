package com.bluewhale.auth.starter.controller;

import com.bluewhale.auth.application.service.GatewayWhitelistAppService;
import com.bluewhale.auth.interfaces.api.GatewayWhitelistInternalApi;
import com.bluewhale.auth.interfaces.dto.response.GatewayWhitelistItemResponse;
import com.bluewhale.auth.starter.assemble.GatewayWhitelistAssembler;
import com.bluewhale.common.core.result.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 网关白名单内网接口实现（Web 适配层）：仅供 whale-gateway 定时拉取。
 *
 * <p>外部访问由网关 internal-deny 拦截，服务侧令牌由 {@code InternalTokenFilter} 校验。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequiredArgsConstructor
public class GatewayWhitelistInternalController implements GatewayWhitelistInternalApi {

    private final GatewayWhitelistAppService gatewayWhitelistAppService;

    private final GatewayWhitelistAssembler gatewayWhitelistAssembler;

    /**
     * 查询全部启用的平台级白名单
     *
     * @return 白名单项列表
     */
    @Override
    public Result<List<GatewayWhitelistItemResponse>> listEnabled() {
        return Result.ok(gatewayWhitelistAssembler.toResponses(gatewayWhitelistAppService.listEnabled()));
    }
}
