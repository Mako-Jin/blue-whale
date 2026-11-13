package com.bluewhale.auth.interfaces.api;

import com.bluewhale.auth.interfaces.dto.response.GatewayRouteOverrideItemResponse;
import com.bluewhale.common.core.result.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.List;

/**
 * 网关路由覆盖内网 API 契约：仅供 whale-service-gateway 定时拉取，不对外暴露。
 *
 * <p>路径以 {@code /auth/v1/internal} 开头：网关 internal-deny 统一 403 外部访问，
 * 服务侧由 {@code InternalTokenFilter} 校验 {@code X-Internal-Token}。</p>
 *
 * @author BlueWhale
 */
@RequestMapping(path = "/auth/v{version}/internal/gateway", version = "1")
public interface GatewayRouteOverrideInternalApi {

    /**
     * 查询全部路由覆盖条目（含停用与窗口外条目，网关按当前时间自行判定生效）
     *
     * @return 路由覆盖项列表（按路由 ID 升序）；无数据时为空列表
     */
    @GetMapping("/route-overrides")
    Result<List<GatewayRouteOverrideItemResponse>> listAll();
}
