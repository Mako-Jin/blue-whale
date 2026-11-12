package com.bluewhale.auth.application.service;

import com.bluewhale.auth.application.model.vo.GatewayBlacklistVO;

import java.util.List;

/**
 * 网关黑名单应用服务：向网关提供只读下发能力。
 *
 * @author BlueWhale
 */
public interface GatewayBlacklistAppService {

    /**
     * 查询全部生效的平台级黑名单（按 {@code sort_no} 升序）
     *
     * @return 黑名单视图对象列表；无数据时返回空列表
     */
    List<GatewayBlacklistVO> listEnabled();
}
