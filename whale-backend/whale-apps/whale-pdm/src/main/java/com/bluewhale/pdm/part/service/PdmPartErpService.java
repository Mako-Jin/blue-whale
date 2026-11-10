package com.bluewhale.pdm.part.service;

import com.bluewhale.pdm.part.vo.PdmPartErpStockVO;

/**
 * 零件 ERP 物料/库存只读查询服务
 *
 * @author BlueWhale
 */
public interface PdmPartErpService {

    /**
     * 查询零件对应的 ERP 物料与库存
     *
     * <p>ERP 不可用时返回降级结果（degraded=true），不抛异常阻断零件页。</p>
     *
     * @param partId 零件ID
     * @return ERP 物料与库存
     */
    PdmPartErpStockVO getStock(Long partId);
}
