package com.bluewhale.erp.scm.domain.model;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.erp.common.ErpErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link PriceList} 领域测试。
 *
 * @author BlueWhale
 */
class PriceListTest {

    @Test
    @DisplayName("单价必须大于 0")
    void shouldRejectNonPositivePrice() {
        PriceList priceList = priceList();
        priceList.setPrice(BigDecimal.ZERO);

        BizException exception = assertThrows(BizException.class, priceList::validate);

        assertEquals(ErpErrorCode.PRICE_INVALID, exception.getCode());
    }

    @Test
    @DisplayName("有效期止不得早于有效期起")
    void shouldRejectInvertedValidRange() {
        PriceList priceList = priceList();
        priceList.setValidFrom(LocalDate.of(2026, 11, 5));
        priceList.setValidTo(LocalDate.of(2026, 11, 4));

        BizException exception = assertThrows(BizException.class, priceList::validate);

        assertEquals(ErpErrorCode.DOC_DATE_INVALID, exception.getCode());
    }

    @Test
    @DisplayName("有效期可为空表示不限")
    void shouldAcceptEmptyValidRange() {
        PriceList priceList = priceList();
        priceList.setValidFrom(null);
        priceList.setValidTo(null);

        priceList.validate();

        assertTrue(priceList.matches(LocalDate.of(2026, 11, 5)));
    }

    @Test
    @DisplayName("启停在 ENABLED/DISABLED 间切换")
    void shouldToggleStatus() {
        PriceList priceList = priceList();
        priceList.setStatus(ScmStatus.ENABLED);

        priceList.toggle();
        assertEquals(ScmStatus.DISABLED, priceList.getStatus());

        priceList.toggle();
        assertEquals(ScmStatus.ENABLED, priceList.getStatus());
    }

    @Test
    @DisplayName("取价按启停与有效期判断")
    void shouldMatchDateWithStatusAndValidRange() {
        PriceList priceList = priceList();
        priceList.setStatus(ScmStatus.ENABLED);
        priceList.setValidFrom(LocalDate.of(2026, 11, 1));
        priceList.setValidTo(LocalDate.of(2026, 11, 30));

        assertTrue(priceList.matches(LocalDate.of(2026, 11, 5)));
        assertTrue(priceList.matches(LocalDate.of(2026, 11, 1)));
        assertTrue(priceList.matches(LocalDate.of(2026, 11, 30)));
        assertFalse(priceList.matches(LocalDate.of(2026, 10, 31)));
        assertFalse(priceList.matches(LocalDate.of(2026, 12, 1)));
        assertFalse(priceList.matches(null));
    }

    @Test
    @DisplayName("停用价目不参与取价")
    void shouldNotMatchWhenDisabled() {
        PriceList priceList = priceList();
        priceList.setStatus(ScmStatus.DISABLED);

        assertFalse(priceList.matches(LocalDate.of(2026, 11, 5)));
    }

    /**
     * 构造合法价目。
     *
     * @return 价目
     */
    private PriceList priceList() {
        PriceList priceList = new PriceList();
        priceList.setPriceType(PriceType.SALES);
        priceList.setPartnerId(1L);
        priceList.setItemId(2L);
        priceList.setPrice(new BigDecimal("12.5"));
        priceList.setStatus(ScmStatus.ENABLED);
        return priceList;
    }
}
