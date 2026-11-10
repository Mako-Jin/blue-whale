package com.bluewhale.erp.scm.application.service.impl;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.erp.common.ErpErrorCode;
import com.bluewhale.erp.masterdata.domain.model.MasterDataStatus;
import com.bluewhale.erp.scm.application.command.PurchaseOrderCreateCommand;
import com.bluewhale.erp.scm.application.command.PurchaseRequisitionCreateCommand;
import com.bluewhale.erp.scm.application.service.PurchaseAppService;
import com.bluewhale.erp.scm.application.service.PurchaseRequisitionAppService;
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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 采购申请应用服务实现。
 *
 * <p>转单闭环：已审核申请单 → 草稿采购订单（不改写订单状态、不触发记账），
 * 申请单回写 {@code convertedOrderNo} 并置 {@code CONVERTED}。</p>
 *
 * @author BlueWhale
 */
@Service
@RequiredArgsConstructor
public class PurchaseRequisitionAppServiceImpl implements PurchaseRequisitionAppService {

    /** 默认币种 */
    private static final String DEFAULT_CURRENCY = "CNY";

    private final PurchaseRequisitionRepository requisitionRepository;

    private final PurchaseAppService purchaseAppService;

    private final SupplierCatalog supplierCatalog;

    private final ItemCatalog itemCatalog;

    @Override
    public PageResult<PurchaseRequisitionView> page(ScmQuery query) {
        List<PurchaseRequisitionView> records = requisitionRepository.page(query).stream()
                .map(PurchaseRequisitionView::of).toList();
        return PageResult.of(records, requisitionRepository.count(query), query.getCurrent(), query.getSize());
    }

    @Override
    public PurchaseRequisitionView detail(Long id) {
        return PurchaseRequisitionView.of(requireRequisition(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PurchaseRequisitionView create(PurchaseRequisitionCreateCommand command) {
        SupplierSnapshot supplier = requireSupplier(command.getSupplierId());
        PurchaseRequisition requisition = new PurchaseRequisition();
        requisition.setDocNo(requisitionRepository.nextDocNo(command.getDocDate()));
        requisition.setSupplierId(supplier.getId());
        requisition.setSupplierName(supplier.getName());
        requisition.setDocDate(command.getDocDate());
        requisition.setRequiredDate(command.getRequiredDate());
        requisition.setWarehouseId(command.getWarehouseId());
        requisition.setStatus(ScmStatus.DRAFT);
        requisition.setCurrency(StringUtils.hasText(command.getCurrency())
                ? command.getCurrency() : DEFAULT_CURRENCY);
        requisition.setRemark(command.getRemark());
        List<PurchaseRequisitionLine> lines = new ArrayList<>();
        int lineNo = 1;
        for (PurchaseRequisitionCreateCommand.Line lineCommand : command.getLines()) {
            ItemSnapshot item = requireItem(lineCommand.getItemCode());
            PurchaseRequisitionLine line = new PurchaseRequisitionLine();
            line.setLineNo(lineNo++);
            line.setItemId(item.getId());
            line.setItemCode(item.getCode());
            line.setItemName(item.getName());
            line.setUom(lineCommand.getUom());
            line.setQuantity(lineCommand.getQuantity());
            line.setUnitPrice(lineCommand.getUnitPrice());
            lines.add(line);
        }
        requisition.setLines(lines);
        requisition.calculateTotal();
        requisitionRepository.insert(requisition);
        return PurchaseRequisitionView.of(requisition);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PurchaseRequisitionView approve(Long id) {
        PurchaseRequisition requisition = requireRequisition(id);
        requisition.approve();
        requisitionRepository.update(requisition);
        return PurchaseRequisitionView.of(requisition);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PurchaseRequisitionView convert(Long id) {
        PurchaseRequisition requisition = requireRequisition(id);
        if (!ScmStatus.APPROVED.equals(requisition.getStatus())) {
            throw ErpErrorCode.exception(ErpErrorCode.ORDER_STATE_CONFLICT, "只有已审核的采购申请单可以转单");
        }
        PurchaseOrderCreateCommand command = new PurchaseOrderCreateCommand();
        command.setSupplierId(requisition.getSupplierId());
        command.setDocDate(LocalDate.now());
        command.setExpectedDate(requisition.getRequiredDate());
        command.setWarehouseId(requisition.getWarehouseId());
        command.setCurrency(requisition.getCurrency());
        command.setRemark("采购申请 " + requisition.getDocNo());
        List<PurchaseOrderCreateCommand.Line> lines = new ArrayList<>();
        for (PurchaseRequisitionLine line : requisition.getLines()) {
            PurchaseOrderCreateCommand.Line lineCommand = new PurchaseOrderCreateCommand.Line();
            lineCommand.setItemCode(line.getItemCode());
            lineCommand.setQuantity(line.getQuantity());
            lineCommand.setUnitPrice(line.getUnitPrice());
            lineCommand.setUom(line.getUom());
            lines.add(lineCommand);
        }
        command.setLines(lines);
        PurchaseOrderView order = purchaseAppService.create(command);
        requisition.convert(order.getDocNo());
        requisitionRepository.update(requisition);
        return PurchaseRequisitionView.of(requisition);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PurchaseRequisitionView cancel(Long id) {
        PurchaseRequisition requisition = requireRequisition(id);
        requisition.cancel();
        requisitionRepository.update(requisition);
        return PurchaseRequisitionView.of(requisition);
    }

    /**
     * 按主键查询申请单，不存在抛业务异常。
     *
     * @param id 主键
     * @return 采购申请单
     */
    private PurchaseRequisition requireRequisition(Long id) {
        return requisitionRepository.findById(id)
                .orElseThrow(() -> ErpErrorCode.exception(ErpErrorCode.PURCHASE_REQUISITION_NOT_FOUND));
    }

    /**
     * 查询启用状态的供应商。
     *
     * @param supplierId 供应商主键
     * @return 供应商快照
     */
    private SupplierSnapshot requireSupplier(Long supplierId) {
        SupplierSnapshot supplier = supplierCatalog.findById(supplierId)
                .orElseThrow(() -> ErpErrorCode.exception(ErpErrorCode.SUPPLIER_INVALID));
        if (!MasterDataStatus.ENABLED.equals(supplier.getStatus())) {
            throw ErpErrorCode.exception(ErpErrorCode.SUPPLIER_INVALID, "供应商 " + supplier.getName() + " 已停用");
        }
        return supplier;
    }

    /**
     * 查询启用状态的物料。
     *
     * @param itemCode 物料编码
     * @return 物料快照
     */
    private ItemSnapshot requireItem(String itemCode) {
        ItemSnapshot item = itemCatalog.findByCode(itemCode)
                .orElseThrow(() -> ErpErrorCode.exception(ErpErrorCode.ITEM_INVALID,
                        "物料 " + itemCode + " 不存在"));
        if (!MasterDataStatus.ENABLED.equals(item.getStatus())) {
            throw ErpErrorCode.exception(ErpErrorCode.ITEM_INVALID, "物料 " + item.getCode() + " 已停用");
        }
        return item;
    }
}
