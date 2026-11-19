package com.bluewhale.scene.application.service;

import com.bluewhale.scene.application.view.MfgBoardView;

/**
 * 3D 看板制造数据服务。
 *
 * @author BlueWhale
 */
public interface SceneBoardAppService {

    /**
     * 获取制造看板数据；数据源不可用时返回显式降级视图。
     *
     * @param tenantId 租户 ID
     * @param limit    单类记录上限（空或非正数取默认值）
     * @return 看板视图（degraded 标识数据源状态）
     */
    MfgBoardView manufacturingBoard(Long tenantId, Integer limit);
}
