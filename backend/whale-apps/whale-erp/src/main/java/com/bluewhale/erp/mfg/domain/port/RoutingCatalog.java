package com.bluewhale.erp.mfg.domain.port;

import com.bluewhale.erp.mfg.domain.model.RoutingSnapshot;

import java.util.Optional;

/**
 * PDM 工艺路线只读端口。
 *
 * @author BlueWhale
 */
public interface RoutingCatalog {

    /**
     * 拉取零件最新已发布工艺路线快照。
     *
     * @param partCode 零件编码
     * @return 快照；无已发布路线时为空
     */
    Optional<RoutingSnapshot> latestReleased(String partCode);
}
