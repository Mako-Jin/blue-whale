package com.bluewhale.scene.application.port;

import com.bluewhale.scene.application.view.MfgBoardView;

import java.util.Optional;

/**
 * 制造看板数据端口：向 ERP 内网取实时数量。
 *
 * @author BlueWhale
 */
public interface ManufacturingBoardPort {

    /**
     * 拉取制造看板数据。
     *
     * @param tenantId 租户 ID（ERP 行级隔离依据）
     * @param limit    单类记录上限
     * @return 看板数据；ERP 不可用或响应非法时返回 {@link Optional#empty()}（由上层显式降级）
     */
    Optional<MfgBoardView> fetch(Long tenantId, int limit);
}
