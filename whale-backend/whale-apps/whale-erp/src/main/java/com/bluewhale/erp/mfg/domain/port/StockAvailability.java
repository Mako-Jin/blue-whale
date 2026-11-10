package com.bluewhale.erp.mfg.domain.port;

import java.math.BigDecimal;

/**
 * 物料现货可用量端口。
 *
 * @author BlueWhale
 */
public interface StockAvailability {

    /**
     * 查询物料跨仓库现货可用量。
     *
     * @param itemId 物料主键
     * @return 可用量；无库存返回 0
     */
    BigDecimal availableQuantity(Long itemId);
}
