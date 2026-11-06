package com.bluewhale.erp.mfg.infrastructure.adapter;

import com.bluewhale.erp.mfg.domain.port.StockAvailability;
import com.bluewhale.erp.scm.domain.repository.StockRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * 库存现货可用量适配器：跨仓库汇总物料余额。
 *
 * @author BlueWhale
 */
@Component
@RequiredArgsConstructor
public class ScmStockAvailabilityAdapter implements StockAvailability {

    private final StockRepository stockRepository;

    @Override
    public BigDecimal availableQuantity(Long itemId) {
        return stockRepository.sumQuantityByItemId(itemId);
    }
}
