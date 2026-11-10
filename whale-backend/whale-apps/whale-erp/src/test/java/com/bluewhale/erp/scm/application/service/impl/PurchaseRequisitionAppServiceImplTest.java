package com.bluewhale.erp.scm.application.service.impl;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.erp.common.ErpErrorCode;
import com.bluewhale.erp.masterdata.domain.model.MasterDataStatus;
import com.bluewhale.erp.scm.application.command.PurchaseOrderCreateCommand;
import com.bluewhale.erp.scm.application.command.PurchaseRequisitionCreateCommand;
import com.bluewhale.erp.scm.application.service.PurchaseAppService;
import com.bluewhale.erp.scm.application.view.PurchaseOrderView;
import com.bluewhale.erp.scm.application.view.PurchaseRequisitionView;
import com.bluewhale.erp.scm.domain.model.PurchaseRequisition;
import com.bluewhale.erp.scm.domain.model.PurchaseRequisitionLine;
import com.bluewhale.erp.scm.domain.model.ScmQuery;
import com.bluewhale.erp.scm.domain.model.ScmStatus;
import com.bluewhale.erp.scm.domain.port.ItemCatalog;
import com.bluewhale.erp.scm.domain.port.ItemSnapshot;
import com.bluewhale.erp.scm.domain.port.SupplierCatalog;
import com.bluewhale.erp.scm.domain.port.SupplierSnapshot;
import com.bluewhale.erp.scm.domain.repository.PurchaseRequisitionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
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
 * {@link PurchaseRequisitionAppServiceImpl} 单元测试。
 *
 * @author BlueWhale
 */
class PurchaseRequisitionAppServiceImplTest {

    private final PurchaseRequisitionRepository requisitionRepository =
            mock(PurchaseRequisitionRepository.class);

    private final PurchaseAppService purchaseAppService = mock(PurchaseAppService.class);

    private final SupplierCatalog supplierCatalog = mock(SupplierCatalog.class);

    private final ItemCatalog itemCatalog = mock(ItemCatalog.class);

    private final PurchaseRequisitionAppServiceImpl service = new PurchaseRequisitionAppServiceImpl(
            requisitionRepository, purchaseAppService, supplierCatalog, itemCatalog);

    @Test
    @DisplayName("建单：生成草稿申请单并汇总金额")
    void shouldCreateRequisition() {
        when(supplierCatalog.findById(1L)).thenReturn(Optional.of(supplier()));
        when(itemCatalog.findByCode("ERP-001")).thenReturn(Optional.of(item()));
        when(requisitionRepository.nextDocNo(LocalDate.of(2026, 11, 5))
        ).thenReturn("PA-20261105-0001");

        PurchaseRequisitionView view = service.create(command());

        assertEquals("PA-20261105-0001", view.getDocNo());
        assertEquals(ScmStatus.DRAFT, view.getStatus());
        assertEquals("供应商A", view.getSupplierName());
        assertEquals("物料A", view.getLines().get(0).getItemName());
        assertEquals(0, view.getTotalAmount().compareTo(new BigDecimal("50")));
        verify(requisitionRepository).insert(any(PurchaseRequisition.class));
    }

    @Test
    @DisplayName("建单：停用供应商拒绝")
    void shouldRejectDisabledSupplier() {
        SupplierSnapshot supplier = supplier();
        supplier.setStatus(MasterDataStatus.DISABLED);
        when(supplierCatalog.findById(1L)).thenReturn(Optional.of(supplier));

        BizException exception = assertThrows(BizException.class, () -> service.create(command()));

        assertEquals(ErpErrorCode.SUPPLIER_INVALID, exception.getCode());
        verify(requisitionRepository, never()).insert(any(PurchaseRequisition.class));
    }

    @Test
    @DisplayName("审核：草稿审核为已审核")
    void shouldApprove() {
        when(requisitionRepository.findById(2L)).thenReturn(Optional.of(draft()));

        PurchaseRequisitionView view = service.approve(2L);

        assertEquals(ScmStatus.APPROVED, view.getStatus());
        verify(requisitionRepository).update(any(PurchaseRequisition.class));
    }

    @Test
    @DisplayName("转单：生成采购订单并回写单号")
    void shouldConvert() {
        PurchaseRequisition requisition = draft();
        requisition.setStatus(ScmStatus.APPROVED);
        when(requisitionRepository.findById(2L)).thenReturn(Optional.of(requisition));
        when(purchaseAppService.create(any())).thenReturn(orderView("PO-20261105-0001"));

        PurchaseRequisitionView view = service.convert(2L);

        assertEquals(ScmStatus.CONVERTED, view.getStatus());
        assertEquals("PO-20261105-0001", view.getConvertedOrderNo());
        ArgumentCaptor<PurchaseOrderCreateCommand> captor =
                ArgumentCaptor.forClass(PurchaseOrderCreateCommand.class);
        verify(purchaseAppService).create(captor.capture());
        assertEquals(requisition.getRequiredDate(), captor.getValue().getExpectedDate());
        assertEquals(1L, captor.getValue().getSupplierId());
        assertEquals(7301L, captor.getValue().getWarehouseId());
        verify(requisitionRepository).update(any(PurchaseRequisition.class));
    }

    @Test
    @DisplayName("转单：重复转单拒绝")
    void shouldRejectDuplicateConvert() {
        PurchaseRequisition requisition = draft();
        requisition.setStatus(ScmStatus.CONVERTED);
        when(requisitionRepository.findById(2L)).thenReturn(Optional.of(requisition));

        BizException exception = assertThrows(BizException.class, () -> service.convert(2L));

        assertEquals(ErpErrorCode.ORDER_STATE_CONFLICT, exception.getCode());
        verify(purchaseAppService, never()).create(any());
    }

    @Test
    @DisplayName("取消：草稿状态变更为已取消")
    void shouldCancel() {
        when(requisitionRepository.findById(2L)).thenReturn(Optional.of(draft()));

        PurchaseRequisitionView view = service.cancel(2L);

        assertEquals(ScmStatus.CANCELLED, view.getStatus());
        verify(requisitionRepository).update(any(PurchaseRequisition.class));
    }

    @Test
    @DisplayName("分页查询返回总条数")
    void shouldPage() {
        when(requisitionRepository.page(any())).thenReturn(List.of(draft()));
        when(requisitionRepository.count(any())).thenReturn(1L);

        var page = service.page(new ScmQuery());

        assertEquals(1, page.getRecords().size());
        assertEquals(1L, page.getTotal());
    }

    @Test
    @DisplayName("详情：不存在抛业务异常")
    void shouldRejectMissingRequisition() {
        when(requisitionRepository.findById(9L)).thenReturn(Optional.empty());

        BizException exception = assertThrows(BizException.class, () -> service.detail(9L));

        assertEquals(ErpErrorCode.PURCHASE_REQUISITION_NOT_FOUND, exception.getCode());
    }

    /**
     * 构造创建命令。
     *
     * @return 命令
     */
    private PurchaseRequisitionCreateCommand command() {
        PurchaseRequisitionCreateCommand command = new PurchaseRequisitionCreateCommand();
        command.setSupplierId(1L);
        command.setDocDate(LocalDate.of(2026, 11, 5));
        command.setRequiredDate(LocalDate.of(2026, 11, 9));
        command.setWarehouseId(7301L);
        command.setCurrency("CNY");
        PurchaseRequisitionCreateCommand.Line line = new PurchaseRequisitionCreateCommand.Line();
        line.setItemCode("ERP-001");
        line.setQuantity(new BigDecimal("5"));
        line.setUnitPrice(new BigDecimal("10"));
        line.setUom("个");
        command.setLines(List.of(line));
        return command;
    }

    /**
     * 构造草稿申请单。
     *
     * @return 申请单
     */
    private PurchaseRequisition draft() {
        PurchaseRequisition requisition = new PurchaseRequisition();
        requisition.setId(2L);
        requisition.setDocNo("PA-001");
        requisition.setSupplierId(1L);
        requisition.setSupplierName("供应商A");
        requisition.setDocDate(LocalDate.of(2026, 11, 5));
        requisition.setRequiredDate(LocalDate.of(2026, 11, 9));
        requisition.setWarehouseId(7301L);
        requisition.setStatus(ScmStatus.DRAFT);
        requisition.setCurrency("CNY");
        PurchaseRequisitionLine line = new PurchaseRequisitionLine();
        line.setLineNo(1);
        line.setItemId(3L);
        line.setItemCode("ERP-001");
        line.setItemName("物料A");
        line.setUom("个");
        line.setQuantity(new BigDecimal("5"));
        line.setUnitPrice(new BigDecimal("10"));
        requisition.setLines(new ArrayList<>(List.of(line)));
        requisition.calculateTotal();
        return requisition;
    }

    /**
     * 构造供应商快照。
     *
     * @return 快照
     */
    private SupplierSnapshot supplier() {
        SupplierSnapshot supplier = new SupplierSnapshot();
        supplier.setId(1L);
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

    /**
     * 构造采购订单视图。
     *
     * @param docNo 订单号
     * @return 视图
     */
    private PurchaseOrderView orderView(String docNo) {
        PurchaseOrderView view = new PurchaseOrderView();
        view.setDocNo(docNo);
        return view;
    }
}
