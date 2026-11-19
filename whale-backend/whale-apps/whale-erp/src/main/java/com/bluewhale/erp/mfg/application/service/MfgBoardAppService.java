package com.bluewhale.erp.mfg.application.service;

import com.bluewhale.erp.mfg.application.view.MfgBoardView;

/**
 * 制造看板聚合服务（内网只读）。
 *
 * @author BlueWhale
 */
public interface MfgBoardAppService {

    /**
     * 聚合生产订单与库存余额的实时数量。
     *
     * @param limit 单类记录上限（空或非正数取默认值，最大 200）
     * @return 看板视图
     */
    MfgBoardView board(Integer limit);
}
