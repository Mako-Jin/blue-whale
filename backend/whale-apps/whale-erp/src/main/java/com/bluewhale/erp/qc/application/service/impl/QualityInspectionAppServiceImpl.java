package com.bluewhale.erp.qc.application.service.impl;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.erp.common.ErpErrorCode;
import com.bluewhale.erp.masterdata.domain.model.MasterDataStatus;
import com.bluewhale.erp.qc.application.command.InspectionCreateCommand;
import com.bluewhale.erp.qc.application.command.InspectionRecordCommand;
import com.bluewhale.erp.qc.application.command.NcrCloseCommand;
import com.bluewhale.erp.qc.application.command.NcrCreateCommand;
import com.bluewhale.erp.qc.application.command.NcrDisposeCommand;
import com.bluewhale.erp.qc.application.service.QualityInspectionAppService;
import com.bluewhale.erp.qc.application.view.InspectionView;
import com.bluewhale.erp.qc.application.view.NcrView;
import com.bluewhale.erp.qc.application.view.QualityCostView;
import com.bluewhale.erp.qc.domain.model.InspectionItem;
import com.bluewhale.erp.qc.domain.model.InspectionOrder;
import com.bluewhale.erp.qc.domain.model.MeasuringTool;
import com.bluewhale.erp.qc.domain.model.Nonconformity;
import com.bluewhale.erp.qc.domain.model.QcQuery;
import com.bluewhale.erp.qc.domain.model.QcStatus;
import com.bluewhale.erp.qc.domain.model.QualityCost;
import com.bluewhale.erp.qc.domain.repository.InspectionRepository;
import com.bluewhale.erp.qc.domain.repository.MetrologyRepository;
import com.bluewhale.erp.qc.domain.repository.NcrRepository;
import com.bluewhale.erp.qc.domain.repository.QualityCostRepository;
import com.bluewhale.erp.scm.domain.port.ItemCatalog;
import com.bluewhale.erp.scm.domain.port.ItemSnapshot;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 质量检验与不合格品应用服务实现。
 *
 * <p>检验单填写量具时校验校准有效期，过期量具拒绝检验；判定不合格时按「检验单号」
 * 幂等生成不合格品单并回写检验单；不合格品处置产生费用时自动登记质量成本。</p>
 *
 * @author BlueWhale
 */
@Service
@RequiredArgsConstructor
public class QualityInspectionAppServiceImpl implements QualityInspectionAppService {

    private final InspectionRepository inspectionRepository;

    private final NcrRepository ncrRepository;

    private final QualityCostRepository qualityCostRepository;

    private final MetrologyRepository metrologyRepository;

    private final ItemCatalog itemCatalog;

    @Override
    public PageResult<InspectionView> pageInspections(QcQuery query) {
        List<InspectionView> records = inspectionRepository.page(query).stream()
                .map(InspectionView::of).toList();
        return PageResult.of(records, inspectionRepository.count(query), query.getCurrent(), query.getSize());
    }

    @Override
    public InspectionView detailInspection(Long id) {
        return InspectionView.of(requireInspection(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public InspectionView createInspection(InspectionCreateCommand command) {
        ItemSnapshot item = requireItem(command.getItemCode());
        InspectionOrder order = new InspectionOrder();
        order.setOrderNo(inspectionRepository.nextOrderNo(command.getInspectDate()));
        order.setInspectionType(command.getInspectionType());
        order.setSourceType(command.getSourceType() == null || command.getSourceType().isBlank()
                ? QcStatus.SOURCE_MANUAL : command.getSourceType());
        order.setSourceNo(command.getSourceNo());
        order.setItemId(item.getId());
        order.setItemCode(item.getCode());
        order.setItemName(item.getName());
        order.setLotNo(command.getLotNo());
        order.setQuantity(command.getQuantity());
        order.setInspectedQuantity(BigDecimal.ZERO);
        order.setSupplierCode(command.getSupplierCode());
        order.setStatus(QcStatus.INSPECTION_DRAFT);
        order.setInspector(command.getInspector());
        order.setInspectDate(command.getInspectDate());
        order.setRemark(command.getRemark());
        bindTool(order, command.getToolNo());
        List<InspectionItem> items = new ArrayList<>();
        int lineNo = 1;
        for (InspectionCreateCommand.Item source : command.getItems()) {
            InspectionItem itemLine = new InspectionItem();
            itemLine.setLineNo(lineNo++);
            itemLine.setItemName(source.getItemName());
            itemLine.setStandardValue(source.getStandardValue());
            items.add(itemLine);
        }
        order.setItems(items);
        order.validateForCreate();
        inspectionRepository.insert(order);
        return InspectionView.of(order);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public InspectionView recordInspection(Long id, InspectionRecordCommand command) {
        InspectionOrder order = requireInspection(id);
        for (InspectionRecordCommand.Line source : command.getLines()) {
            InspectionItem item = new InspectionItem();
            item.setLineNo(source.getLineNo());
            item.setActualValue(source.getActualValue());
            item.setResult(source.getResult());
            item.setRemark(source.getRemark());
            order.record(item);
        }
        inspectionRepository.update(order);
        return InspectionView.of(order);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public InspectionView judgeInspection(Long id) {
        InspectionOrder order = requireInspection(id);
        boolean passed = order.judge();
        if (!passed) {
            Nonconformity nonconformity = createNonconformity(order);
            order.markNonconformity(nonconformity.getId());
        }
        inspectionRepository.update(order);
        return InspectionView.of(order);
    }

    @Override
    public PageResult<NcrView> pageNcr(QcQuery query) {
        List<NcrView> records = ncrRepository.page(query).stream().map(NcrView::of).toList();
        return PageResult.of(records, ncrRepository.count(query), query.getCurrent(), query.getSize());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public NcrView createNcr(NcrCreateCommand command) {
        String sourceType = command.getSourceType() == null || command.getSourceType().isBlank()
                ? QcStatus.SOURCE_MANUAL : command.getSourceType();
        if (ncrRepository.findBySource(sourceType, command.getSourceNo()).isPresent()) {
            throw ErpErrorCode.exception(ErpErrorCode.NCR_ALREADY_EXISTS);
        }
        Nonconformity nonconformity = new Nonconformity();
        nonconformity.setNcrNo(ncrRepository.nextNcrNo(LocalDate.now()));
        nonconformity.setSourceType(sourceType);
        nonconformity.setSourceNo(command.getSourceNo());
        nonconformity.setItemName(command.getItemName());
        nonconformity.setLotNo(command.getLotNo());
        nonconformity.setQuantity(command.getQuantity());
        nonconformity.setDefectDesc(command.getDefectDesc());
        nonconformity.setStatus(QcStatus.NCR_OPEN);
        nonconformity.setCostAmount(BigDecimal.ZERO);
        nonconformity.setRemark(command.getRemark());
        nonconformity.validateForCreate();
        ncrRepository.insert(nonconformity);
        return NcrView.of(nonconformity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public NcrView disposeNcr(Long id, NcrDisposeCommand command) {
        Nonconformity nonconformity = requireNcr(id);
        nonconformity.dispose(command.getDisposition(), command.getDisposeDate(),
                command.getCostAmount(), command.getRemark());
        ncrRepository.update(nonconformity);
        if (nonconformity.costRequired()) {
            registerQualityCost(nonconformity);
        }
        return NcrView.of(nonconformity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public NcrView closeNcr(Long id, NcrCloseCommand command) {
        Nonconformity nonconformity = requireNcr(id);
        nonconformity.close(command.getCloseDate(), command.getRemark());
        ncrRepository.update(nonconformity);
        return NcrView.of(nonconformity);
    }

    @Override
    public PageResult<QualityCostView> pageQualityCost(QcQuery query) {
        List<QualityCostView> records = qualityCostRepository.page(query).stream()
                .map(QualityCostView::of).toList();
        return PageResult.of(records, qualityCostRepository.count(query), query.getCurrent(), query.getSize());
    }

    /**
     * 判定不合格时生成不合格品单，按来源幂等复用已有单据。
     *
     * @param order 检验单
     * @return 不合格品单
     */
    private Nonconformity createNonconformity(InspectionOrder order) {
        return ncrRepository.findBySource(QcStatus.SOURCE_INSPECTION, order.getOrderNo()).orElseGet(() -> {
            Nonconformity nonconformity = new Nonconformity();
            nonconformity.setNcrNo(ncrRepository.nextNcrNo(order.getInspectDate()));
            nonconformity.setSourceType(QcStatus.SOURCE_INSPECTION);
            nonconformity.setSourceNo(order.getOrderNo());
            nonconformity.setItemId(order.getItemId());
            nonconformity.setItemName(order.getItemName());
            nonconformity.setLotNo(order.getLotNo());
            nonconformity.setQuantity(order.getQuantity());
            nonconformity.setDefectDesc("检验单 " + order.getOrderNo() + " 判定不合格");
            nonconformity.setStatus(QcStatus.NCR_OPEN);
            nonconformity.setCostAmount(BigDecimal.ZERO);
            nonconformity.validateForCreate();
            ncrRepository.insert(nonconformity);
            return nonconformity;
        });
    }

    /**
     * 登记不合格品处置产生的质量成本。
     *
     * @param nonconformity 不合格品单
     */
    private void registerQualityCost(Nonconformity nonconformity) {
        QualityCost cost = new QualityCost();
        cost.setCostNo(qualityCostRepository.nextCostNo(nonconformity.getDisposeDate()));
        cost.setSourceType(QcStatus.SOURCE_NCR);
        cost.setSourceNo(nonconformity.getNcrNo());
        cost.setCostType(nonconformity.costType());
        cost.setAmount(nonconformity.getCostAmount());
        cost.setCostDate(nonconformity.getDisposeDate());
        cost.setRemark("不合格品处置：" + nonconformity.getDisposition());
        cost.validateForCreate();
        qualityCostRepository.insert(cost);
    }

    /**
     * 绑定并校验量具：必须存在、启用且校准未过期。
     *
     * @param order 检验单
     * @param toolNo 量具编号
     */
    private void bindTool(InspectionOrder order, String toolNo) {
        if (toolNo == null || toolNo.isBlank()) {
            return;
        }
        MeasuringTool tool = metrologyRepository.findToolByNo(toolNo)
                .orElseThrow(() -> ErpErrorCode.exception(ErpErrorCode.TOOL_NOT_FOUND, "量具 " + toolNo + " 不存在"));
        if (!tool.usableOn(order.getInspectDate())) {
            throw ErpErrorCode.exception(ErpErrorCode.TOOL_EXPIRED, "量具 " + toolNo + " 校准已过期或已停用");
        }
        order.setToolId(tool.getId());
        order.setToolNo(tool.getToolNo());
    }

    /**
     * 查询检验单，不存在按 404 处理。
     *
     * @param id 主键
     * @return 检验单
     */
    private InspectionOrder requireInspection(Long id) {
        return inspectionRepository.findById(id)
                .orElseThrow(() -> ErpErrorCode.exception(ErpErrorCode.INSPECTION_NOT_FOUND));
    }

    /**
     * 查询不合格品单，不存在按 404 处理。
     *
     * @param id 主键
     * @return 不合格品单
     */
    private Nonconformity requireNcr(Long id) {
        return ncrRepository.findById(id)
                .orElseThrow(() -> ErpErrorCode.exception(ErpErrorCode.NCR_NOT_FOUND));
    }

    /**
     * 校验物料存在且启用。
     *
     * @param itemCode 物料编码
     * @return 物料快照
     */
    private ItemSnapshot requireItem(String itemCode) {
        ItemSnapshot item = itemCatalog.findByCode(itemCode)
                .orElseThrow(() -> ErpErrorCode.exception(ErpErrorCode.ITEM_INVALID, "物料 " + itemCode + " 不存在"));
        if (!MasterDataStatus.ENABLED.equals(item.getStatus())) {
            throw ErpErrorCode.exception(ErpErrorCode.ITEM_INVALID, "物料 " + item.getCode() + " 已停用");
        }
        return item;
    }
}
