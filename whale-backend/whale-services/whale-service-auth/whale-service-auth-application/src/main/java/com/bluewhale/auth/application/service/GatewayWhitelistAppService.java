package com.bluewhale.auth.application.service;

import com.bluewhale.auth.application.model.vo.GatewayWhitelistVO;

import java.util.List;

/**
 * 网关白名单应用服务：向网关提供只读下发能力。
 *
 * @author BlueWhale
 */
public interface GatewayWhitelistAppService {

    /**
     * 查询全部启用的平台级白名单（按 {@code sort_no} 升序）
     *
     * @return 白名单视图对象列表；无数据时返回空列表
     */
    List<GatewayWhitelistVO> listEnabled();
}
