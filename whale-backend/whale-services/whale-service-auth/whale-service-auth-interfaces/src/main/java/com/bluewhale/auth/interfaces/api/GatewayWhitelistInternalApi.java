package com.bluewhale.auth.interfaces.api;

import com.bluewhale.auth.interfaces.dto.response.GatewayWhitelistItemResponse;
import com.bluewhale.common.core.result.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.List;

/**
 * 网关白名单内网 API 契约：仅供 whale-gateway 定时拉取，不对外暴露。
 *
 * <p>路径以 {@code /auth/v1/internal} 开头：网关 internal-deny 统一 403 外部访问，
 * 服务侧由 {@code InternalTokenFilter} 校验 {@code X-Internal-Token}。</p>
 *
 * @author BlueWhale
 */
@RequestMapping(path = "/auth/v{version}/internal/gateway", version = "1")
public interface GatewayWhitelistInternalApi {

    /**
     * 查询全部启用的平台级白名单
     *
     * @return 白名单项列表（按 {@code sort_no} 升序）；无数据时为空列表
     */
    @GetMapping("/whitelist")
    Result<List<GatewayWhitelistItemResponse>> listEnabled();
}
