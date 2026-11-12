package com.bluewhale.auth.starter.controller;

import com.bluewhale.auth.application.service.GatewayBlacklistAppService;
import com.bluewhale.auth.interfaces.api.GatewayBlacklistInternalApi;
import com.bluewhale.auth.interfaces.dto.response.GatewayBlacklistItemResponse;
import com.bluewhale.auth.starter.assemble.GatewayBlacklistAssembler;
import com.bluewhale.common.core.result.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 网关黑名单内网接口实现（Web 适配层）：仅供 whale-gateway 定时拉取。
 *
 * <p>外部访问由网关 internal-deny 拦截，服务侧令牌由 {@code InternalTokenFilter} 校验。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequiredArgsConstructor
public class GatewayBlacklistInternalController implements GatewayBlacklistInternalApi {

    private final GatewayBlacklistAppService gatewayBlacklistAppService;

    private final GatewayBlacklistAssembler gatewayBlacklistAssembler;

    /**
     * 查询全部生效的平台级黑名单
     *
     * @return 黑名单项列表
     */
    @Override
    public Result<List<GatewayBlacklistItemResponse>> listEnabled() {
        return Result.ok(gatewayBlacklistAssembler.toResponses(gatewayBlacklistAppService.listEnabled()));
    }
}
