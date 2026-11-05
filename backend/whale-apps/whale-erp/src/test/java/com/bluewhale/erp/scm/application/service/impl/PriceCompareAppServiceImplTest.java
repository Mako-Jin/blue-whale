package com.bluewhale.erp.scm.application.service.impl;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.erp.common.ErpErrorCode;
import com.bluewhale.erp.scm.application.view.PriceTrendView;
import com.bluewhale.erp.scm.application.view.SupplierPriceCompareView;
import com.bluewhale.erp.scm.domain.model.PriceList;
import com.bluewhale.erp.scm.domain.model.PriceType;
import com.bluewhale.erp.scm.domain.model.PurchasePriceRecord;
import com.bluewhale.erp.scm.domain.model.ScmStatus;
import com.bluewhale.erp.scm.domain.repository.PriceListRepository;
import com.bluewhale.erp.scm.domain.repository.PurchaseOrderRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link PriceCompareAppServiceImpl} 单元测试。
 *
 * @author BlueWhale
 */
class PriceCompareAppServiceImplTest {

    private final PriceListRepository priceListRepository = mock(PriceListRepository.class);

    private final PurchaseOrderRepository purchaseOrderRepository = mock(PurchaseOrderRepository.class);

    private final PriceCompareAppServiceImpl service = new PriceCompareAppServiceImpl(priceListRepository,
            purchaseOrderRepository);

    @Test
    @DisplayName("比价：当前价取最低并计算差额，历史成交价取最近一次")
    void shouldCompare() {
        LocalDate today = LocalDate.now();
        when(priceListRepository.list(any())).thenReturn(List.of(
                price(1L, "供应商A", "10", today.minusDays(1), null),
                price(2L, "供应商B", "12", today.minusDays(1), null),
                price(3L, "供应商C", "9", today.minusDays(1), today.minusDays(1))));
        when(purchaseOrderRepository.listPurchasePrices(eq(7L), any())).thenReturn(List.of(
                record(2L, "供应商B", today.minusDays(10), "13"),
                record(2L, "供应商B", today.minusDays(3), "11")));

        List<SupplierPriceCompareView> views = service.compare(7L, today);

        assertEquals(2, views.size());
        assertEquals(1L, views.get(0).getSupplierId());
        assertTrue(views.get(0).getLowest());
        assertEquals(0, views.get(0).getGap().signum());
        assertEquals(0, views.get(1).getGap().compareTo(new BigDecimal("2")));
        assertEquals(0, views.get(1).getLastPurchasePrice().compareTo(new BigDecimal("11")));
    }

    @Test
    @DisplayName("比价：过期价目不参与当前价")
    void shouldIgnoreExpiredPrice() {
        LocalDate today = LocalDate.now();
        when(priceListRepository.list(any())).thenReturn(List.of(
                price(1L, "供应商A", "10", null, null),
                price(2L, "供应商B", "8", today.minusDays(30), today.minusDays(1))));
        when(purchaseOrderRepository.listPurchasePrices(eq(7L), any())).thenReturn(List.of());

        List<SupplierPriceCompareView> views = service.compare(7L, today);

        assertEquals(1, views.size());
        assertEquals(1L, views.get(0).getSupplierId());
    }

    @Test
    @DisplayName("趋势：按月加权平均并统计笔数")
    void shouldTrend() {
        LocalDate today = LocalDate.now();
        LocalDate lastMonth = today.withDayOfMonth(1).minusMonths(1).plusDays(4);
        when(purchaseOrderRepository.listPurchasePrices(eq(7L), any())).thenReturn(List.of(
                record(1L, "供应商A", today, "10", "2"),
                record(1L, "供应商A", today, "12", "1"),
                record(1L, "供应商A", lastMonth, "8", "5"),
                record(1L, "供应商A", today, "9", "1", ScmStatus.DRAFT)));

        List<PriceTrendView> views = service.trend(7L, 2);

        assertEquals(2, views.size());
        assertEquals(lastMonth.getYear() + "-" + String.format("%02d", lastMonth.getMonthValue()),
                views.get(0).getMonth());
        assertEquals(0, views.get(0).getAvgPrice().compareTo(new BigDecimal("8")));
        assertEquals(1, views.get(0).getOrderCount());
        assertEquals(0, views.get(1).getAvgPrice().compareTo(new BigDecimal("10.6667")));
        assertEquals(2, views.get(1).getOrderCount());
    }

    @Test
    @DisplayName("趋势：月数越界抛业务异常")
    void shouldRejectInvalidMonths() {
        BizException exception = assertThrows(BizException.class, () -> service.trend(7L, 0));

        assertEquals(ErpErrorCode.PRICE_INVALID, exception.getCode());
    }

    @Test
    @DisplayName("比价：未指定物料抛业务异常")
    void shouldRejectMissingItem() {
        BizException exception = assertThrows(BizException.class, () -> service.compare(null, null));

        assertEquals(ErpErrorCode.PRICE_INVALID, exception.getCode());
    }

    /**
     * 构造价目。
     *
     * @param supplierId 供应商主键
     * @param supplierName 供应商名称
     * @param price 单价
     * @param validFrom 有效期起
     * @param validTo 有效期止
     * @return 价目
     */
    private PriceList price(Long supplierId, String supplierName, String price, LocalDate validFrom,
                            LocalDate validTo) {
        PriceList priceList = new PriceList();
        priceList.setPriceType(PriceType.PURCHASE);
        priceList.setPartnerId(supplierId);
        priceList.setPartnerName(supplierName);
        priceList.setItemId(7L);
        priceList.setItemCode("ERP-007");
        priceList.setItemName("物料G");
        priceList.setPrice(new BigDecimal(price));
        priceList.setValidFrom(validFrom);
        priceList.setValidTo(validTo);
        priceList.setStatus(ScmStatus.ENABLED);
        return priceList;
    }

    /**
     * 构造采购价格记录。
     *
     * @param supplierId 供应商主键
     * @param supplierName 供应商名称
     * @param docDate 单据日期
     * @param unitPrice 单价
     * @return 记录
     */
    private PurchasePriceRecord record(Long supplierId, String supplierName, LocalDate docDate, String unitPrice) {
        return record(supplierId, supplierName, docDate, unitPrice, "1", ScmStatus.RECEIVED);
    }

    /**
     * 构造采购价格记录。
     *
     * @param supplierId 供应商主键
     * @param supplierName 供应商名称
     * @param docDate 单据日期
     * @param unitPrice 单价
     * @param quantity 数量
     * @return 记录
     */
    private PurchasePriceRecord record(Long supplierId, String supplierName, LocalDate docDate, String unitPrice,
                                       String quantity) {
        return record(supplierId, supplierName, docDate, unitPrice, quantity, ScmStatus.RECEIVED);
    }

    /**
     * 构造采购价格记录。
     *
     * @param supplierId 供应商主键
     * @param supplierName 供应商名称
     * @param docDate 单据日期
     * @param unitPrice 单价
     * @param quantity 数量
     * @param status 状态
     * @return 记录
     */
    private PurchasePriceRecord record(Long supplierId, String supplierName, LocalDate docDate, String unitPrice,
                                       String quantity, String status) {
        PurchasePriceRecord record = new PurchasePriceRecord();
        record.setSupplierId(supplierId);
        record.setSupplierName(supplierName);
        record.setDocDate(docDate);
        record.setStatus(status);
        record.setItemId(7L);
        record.setItemCode("ERP-007");
        record.setItemName("物料G");
        record.setUnitPrice(new BigDecimal(unitPrice));
        record.setQuantity(new BigDecimal(quantity));
        record.setCurrency("CNY");
        return record;
    }
}