package com.bluewhale.erp.scm.application.service.impl;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.erp.common.ErpErrorCode;
import com.bluewhale.erp.masterdata.domain.model.Customer;
import com.bluewhale.erp.masterdata.domain.model.MasterDataStatus;
import com.bluewhale.erp.masterdata.domain.repository.CustomerRepository;
import com.bluewhale.erp.scm.application.command.PriceListCreateCommand;
import com.bluewhale.erp.scm.application.command.PriceListUpdateCommand;
import com.bluewhale.erp.scm.application.view.PriceListView;
import com.bluewhale.erp.scm.domain.model.PriceList;
import com.bluewhale.erp.scm.domain.model.PriceListQuery;
import com.bluewhale.erp.scm.domain.model.PriceType;
import com.bluewhale.erp.scm.domain.model.ScmStatus;
import com.bluewhale.erp.scm.domain.port.ItemCatalog;
import com.bluewhale.erp.scm.domain.port.ItemSnapshot;
import com.bluewhale.erp.scm.domain.port.SupplierCatalog;
import com.bluewhale.erp.scm.domain.port.SupplierSnapshot;
import com.bluewhale.erp.scm.domain.repository.PriceListRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link PriceListAppServiceImpl} 单元测试。
 *
 * @author BlueWhale
 */
class PriceListAppServiceImplTest {

    private final PriceListRepository priceListRepository = mock(PriceListRepository.class);

    private final CustomerRepository customerRepository = mock(CustomerRepository.class);

    private final SupplierCatalog supplierCatalog = mock(SupplierCatalog.class);

    private final ItemCatalog itemCatalog = mock(ItemCatalog.class);

    private final PriceListAppServiceImpl service = new PriceListAppServiceImpl(priceListRepository,
            customerRepository, supplierCatalog, itemCatalog);

    @Test
    @DisplayName("新建客户售价：快照往来方与物料并置启用")
    void shouldCreateSalesPriceList() {
        when(customerRepository.findById(1L)).thenReturn(Optional.of(customer()));
        when(itemCatalog.findByCode("ERP-001")).thenReturn(Optional.of(item()));
        when(priceListRepository.find(PriceType.SALES, 1L, 3L)).thenReturn(Optional.empty());

        PriceListView view = service.create(command(PriceType.SALES, 1L));

        assertEquals(PriceType.SALES, view.getPriceType());
        assertEquals("客户A", view.getPartnerName());
        assertEquals("ERP-001", view.getItemCode());
        assertEquals(ScmStatus.ENABLED, view.getStatus());
        assertEquals("CNY", view.getCurrency());
        assertEquals(0, view.getPrice().compareTo(new BigDecimal("12.5")));
        verify(priceListRepository).insert(any(PriceList.class));
    }

    @Test
    @DisplayName("新建供应商采购价：快照供应商")
    void shouldCreatePurchasePriceList() {
        when(supplierCatalog.findById(2L)).thenReturn(Optional.of(supplier()));
        when(itemCatalog.findByCode("ERP-001")).thenReturn(Optional.of(item()));
        when(priceListRepository.find(PriceType.PURCHASE, 2L, 3L)).thenReturn(Optional.empty());

        PriceListView view = service.create(command(PriceType.PURCHASE, 2L));

        assertEquals(PriceType.PURCHASE, view.getPriceType());
        assertEquals("供应商A", view.getPartnerName());
        verify(priceListRepository).insert(any(PriceList.class));
    }

    @Test
    @DisplayName("同类型+往来方+物料重复新建拒绝")
    void shouldRejectDuplicate() {
        when(customerRepository.findById(1L)).thenReturn(Optional.of(customer()));
        when(itemCatalog.findByCode("ERP-001")).thenReturn(Optional.of(item()));
        when(priceListRepository.find(PriceType.SALES, 1L, 3L)).thenReturn(Optional.of(priceList()));

        BizException exception = assertThrows(BizException.class,
                () -> service.create(command(PriceType.SALES, 1L)));

        assertEquals(ErpErrorCode.PRICE_LIST_CONFLICT, exception.getCode());
        verify(priceListRepository, never()).insert(any(PriceList.class));
    }

    @Test
    @DisplayName("取价命中：启用且在有效期内")
    void shouldResolveMatchedPrice() {
        when(priceListRepository.find(PriceType.SALES, 1L, 3L)).thenReturn(Optional.of(priceList()));

        PriceListView view = service.resolve(PriceType.SALES, 1L, 3L, LocalDate.of(2026, 11, 5));

        assertEquals(0, view.getPrice().compareTo(new BigDecimal("12.5")));
    }

    @Test
    @DisplayName("取价未命中：无记录抛业务异常")
    void shouldRejectResolveWhenMissing() {
        when(priceListRepository.find(PriceType.SALES, 1L, 3L)).thenReturn(Optional.empty());

        BizException exception = assertThrows(BizException.class,
                () -> service.resolve(PriceType.SALES, 1L, 3L, LocalDate.of(2026, 11, 5)));

        assertEquals(ErpErrorCode.PRICE_LIST_NOT_FOUND, exception.getCode());
    }

    @Test
    @DisplayName("取价失效：停用或日期超出有效期抛业务异常")
    void shouldRejectResolveWhenDisabledOrExpired() {
        PriceList disabled = priceList();
        disabled.setStatus(ScmStatus.DISABLED);
        when(priceListRepository.find(PriceType.SALES, 1L, 3L)).thenReturn(Optional.of(disabled));

        BizException disabledException = assertThrows(BizException.class,
                () -> service.resolve(PriceType.SALES, 1L, 3L, LocalDate.of(2026, 11, 5)));
        assertEquals(ErpErrorCode.PRICE_LIST_NOT_FOUND, disabledException.getCode());

        PriceList expired = priceList();
        expired.setValidTo(LocalDate.of(2026, 10, 31));
        when(priceListRepository.find(PriceType.SALES, 1L, 3L)).thenReturn(Optional.of(expired));

        BizException expiredException = assertThrows(BizException.class,
                () -> service.resolve(PriceType.SALES, 1L, 3L, LocalDate.of(2026, 11, 5)));
        assertEquals(ErpErrorCode.PRICE_LIST_NOT_FOUND, expiredException.getCode());
    }

    @Test
    @DisplayName("启停：状态切换并持久化")
    void shouldToggleStatus() {
        PriceList priceList = priceList();
        when(priceListRepository.findById(5L)).thenReturn(Optional.of(priceList));

        PriceListView view = service.toggle(5L);

        assertEquals(ScmStatus.DISABLED, view.getStatus());
        verify(priceListRepository).update(any(PriceList.class));
    }

    @Test
    @DisplayName("更新：改单价、有效期、备注与状态")
    void shouldUpdatePriceList() {
        when(priceListRepository.findById(5L)).thenReturn(Optional.of(priceList()));

        PriceListUpdateCommand command = new PriceListUpdateCommand();
        command.setPrice(new BigDecimal("20"));
        command.setValidFrom(LocalDate.of(2026, 11, 1));
        command.setValidTo(LocalDate.of(2026, 12, 31));
        command.setRemark("年度协议价");
        command.setStatus(ScmStatus.DISABLED);

        PriceListView view = service.update(5L, command);

        assertEquals(0, view.getPrice().compareTo(new BigDecimal("20")));
        assertEquals(LocalDate.of(2026, 11, 1), view.getValidFrom());
        assertEquals(LocalDate.of(2026, 12, 31), view.getValidTo());
        assertEquals("年度协议价", view.getRemark());
        assertEquals(ScmStatus.DISABLED, view.getStatus());
        verify(priceListRepository).update(any(PriceList.class));
    }

    @Test
    @DisplayName("详情：不存在抛业务异常")
    void shouldRejectMissingDetail() {
        when(priceListRepository.findById(9L)).thenReturn(Optional.empty());

        BizException exception = assertThrows(BizException.class, () -> service.detail(9L));

        assertEquals(ErpErrorCode.PRICE_LIST_NOT_FOUND, exception.getCode());
    }

    @Test
    @DisplayName("分页查询返回总条数")
    void shouldPage() {
        when(priceListRepository.page(any())).thenReturn(List.of(priceList()));
        when(priceListRepository.count(any())).thenReturn(1L);

        var page = service.page(new PriceListQuery());

        assertEquals(1, page.getRecords().size());
        assertEquals(1L, page.getTotal());
    }

    /**
     * 构造创建命令。
     *
     * @param priceType 价格类型
     * @param partnerId 往来方主键
     * @return 命令
     */
    private PriceListCreateCommand command(PriceType priceType, Long partnerId) {
        PriceListCreateCommand command = new PriceListCreateCommand();
        command.setPriceType(priceType);
        command.setPartnerId(partnerId);
        command.setItemCode("ERP-001");
        command.setPrice(new BigDecimal("12.5"));
        command.setValidFrom(LocalDate.of(2026, 1, 1));
        command.setValidTo(LocalDate.of(2026, 12, 31));
        return command;
    }

    /**
     * 构造价目。
     *
     * @return 价目
     */
    private PriceList priceList() {
        PriceList priceList = new PriceList();
        priceList.setId(5L);
        priceList.setPriceType(PriceType.SALES);
        priceList.setPartnerId(1L);
        priceList.setPartnerName("客户A");
        priceList.setItemId(3L);
        priceList.setItemCode("ERP-001");
        priceList.setItemName("物料A");
        priceList.setPrice(new BigDecimal("12.5"));
        priceList.setCurrency("CNY");
        priceList.setValidFrom(LocalDate.of(2026, 1, 1));
        priceList.setValidTo(LocalDate.of(2026, 12, 31));
        priceList.setStatus(ScmStatus.ENABLED);
        return priceList;
    }

    /**
     * 构造客户。
     *
     * @return 客户
     */
    private Customer customer() {
        Customer customer = new Customer();
        customer.setId(1L);
        customer.setCode("CUS-001");
        customer.setName("客户A");
        customer.setStatus(MasterDataStatus.ENABLED);
        return customer;
    }

    /**
     * 构造供应商快照。
     *
     * @return 快照
     */
    private SupplierSnapshot supplier() {
        SupplierSnapshot supplier = new SupplierSnapshot();
        supplier.setId(2L);
        supplier.setCode("SUP-001");
        supplier.setName("供应商A");
        supplier.setStatus(MasterDataStatus.ENABLED);
        return supplier;
    }

    /**
     * 构造物料快照。
     *
     * @return 快照
     */
    private ItemSnapshot item() {
        ItemSnapshot item = new ItemSnapshot();
        item.setId(3L);
        item.setCode("ERP-001");
        item.setName("物料A");
        item.setStatus(MasterDataStatus.ENABLED);
        return item;
    }
}
