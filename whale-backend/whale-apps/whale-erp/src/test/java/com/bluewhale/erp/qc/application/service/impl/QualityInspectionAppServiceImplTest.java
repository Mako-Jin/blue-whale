package com.bluewhale.erp.qc.application.service.impl;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.erp.common.ErpErrorCode;
import com.bluewhale.erp.masterdata.domain.model.MasterDataStatus;
import com.bluewhale.erp.qc.application.command.InspectionCreateCommand;
import com.bluewhale.erp.qc.application.command.NcrDisposeCommand;
import com.bluewhale.erp.qc.application.view.InspectionView;
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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link QualityInspectionAppServiceImpl} 单元测试。
 *
 * @author BlueWhale
 */
class QualityInspectionAppServiceImplTest {

    private final InspectionRepository inspectionRepository = mock(InspectionRepository.class);

    private final NcrRepository ncrRepository = mock(NcrRepository.class);

    private final QualityCostRepository qualityCostRepository = mock(QualityCostRepository.class);

    private final MetrologyRepository metrologyRepository = mock(MetrologyRepository.class);

    private final ItemCatalog itemCatalog = mock(ItemCatalog.class);

    private final QualityInspectionAppServiceImpl service = new QualityInspectionAppServiceImpl(
            inspectionRepository, ncrRepository, qualityCostRepository, metrologyRepository, itemCatalog);

    @Test
    @DisplayName("新增检验单解析物料并生成单号")
    void shouldCreateInspection() {
        when(itemCatalog.findByCode("M001")).thenReturn(Optional.of(item()));
        when(inspectionRepository.nextOrderNo(any())).thenReturn("QC-20261104-0001");

        InspectionView view = service.createInspection(command());

        assertEquals("QC-20261104-0001", view.getOrderNo());
        assertEquals(QcStatus.INSPECTION_DRAFT, view.getStatus());
        assertEquals("M001", view.getItemCode());
        assertEquals(2, view.getItems().size());
        verify(inspectionRepository).insert(any(InspectionOrder.class));
    }

    @Test
    @DisplayName("物料不存在时拒绝建单")
    void shouldRejectMissingItem() {
        when(itemCatalog.findByCode("M001")).thenReturn(Optional.empty());

        BizException exception = assertThrows(BizException.class, () -> service.createInspection(command()));

        assertEquals(ErpErrorCode.ITEM_INVALID, exception.getCode());
        verify(inspectionRepository, never()).insert(any(InspectionOrder.class));
    }

    @Test
    @DisplayName("量具校准过期时拒绝检验")
    void shouldRejectExpiredTool() {
        when(itemCatalog.findByCode("M001")).thenReturn(Optional.of(item()));
        when(metrologyRepository.findToolByNo("T001")).thenReturn(Optional.of(tool(false)));
        InspectionCreateCommand command = command();
        command.setToolNo("T001");

        BizException exception = assertThrows(BizException.class, () -> service.createInspection(command));

        assertEquals(ErpErrorCode.TOOL_EXPIRED, exception.getCode());
    }

    @Test
    @DisplayName("判定不合格自动生成不合格品单并回写")
    void shouldCreateNcrWhenFailed() {
        when(inspectionRepository.findById(1L)).thenReturn(Optional.of(inspection(QcStatus.RESULT_FAIL)));
        when(ncrRepository.findBySource(QcStatus.SOURCE_INSPECTION, "QC-20261104-0001")).thenReturn(Optional.empty());
        when(ncrRepository.nextNcrNo(any())).thenReturn("NCR-202611-0001");
        doAnswer(invocation -> {
            Nonconformity ncr = invocation.getArgument(0);
            ncr.setId(77L);
            return null;
        }).when(ncrRepository).insert(any(Nonconformity.class));

        InspectionView view = service.judgeInspection(1L);

        assertEquals(QcStatus.INSPECTION_FAILED, view.getStatus());
        assertEquals(77L, view.getNcrId());
        verify(ncrRepository).insert(any(Nonconformity.class));
        verify(inspectionRepository).update(any(InspectionOrder.class));
        verify(qualityCostRepository, never()).insert(any(QualityCost.class));
    }

    @Test
    @DisplayName("判定合格不生成不合格品单")
    void shouldNotCreateNcrWhenPassed() {
        when(inspectionRepository.findById(1L)).thenReturn(Optional.of(inspection(QcStatus.RESULT_PASS)));

        InspectionView view = service.judgeInspection(1L);

        assertEquals(QcStatus.INSPECTION_PASSED, view.getStatus());
        assertEquals(null, view.getNcrId());
        verify(ncrRepository, never()).insert(any(Nonconformity.class));
    }

    @Test
    @DisplayName("处置不合格品产生费用时自动登记质量成本")
    void shouldRegisterQualityCostOnDispose() {
        when(ncrRepository.findById(1L)).thenReturn(Optional.of(ncr()));
        when(qualityCostRepository.nextCostNo(any())).thenReturn("QCOST-202611-0001");

        service.disposeNcr(1L, disposeCommand());

        verify(ncrRepository).update(any(Nonconformity.class));
        verify(qualityCostRepository).insert(any(QualityCost.class));
    }

    @Test
    @DisplayName("处置无费用时不登记质量成本")
    void shouldSkipCostWhenZero() {
        when(ncrRepository.findById(1L)).thenReturn(Optional.of(ncr()));
        NcrDisposeCommand command = disposeCommand();
        command.setCostAmount(BigDecimal.ZERO);

        service.disposeNcr(1L, command);

        verify(qualityCostRepository, never()).insert(any(QualityCost.class));
    }

    @Test
    @DisplayName("不合格品不存在时返回 404")
    void shouldRejectMissingNcr() {
        when(ncrRepository.findById(9L)).thenReturn(Optional.empty());

        BizException exception = assertThrows(BizException.class,
                () -> service.disposeNcr(9L, disposeCommand()));

        assertEquals(ErpErrorCode.NCR_NOT_FOUND, exception.getCode());
    }

    @Test
    @DisplayName("分页查询返回总数与页码")
    void shouldPageInspections() {
        QcQuery query = new QcQuery();
        when(inspectionRepository.page(query)).thenReturn(List.of(inspection(QcStatus.RESULT_PASS)));
        when(inspectionRepository.count(query)).thenReturn(1L);

        assertEquals(1L, service.pageInspections(query).getTotal());
        assertFalse(service.pageInspections(query).getRecords().isEmpty());
    }

    /**
     * 构造建单命令。
     *
     * @return 命令
     */
    private InspectionCreateCommand command() {
        InspectionCreateCommand command = new InspectionCreateCommand();
        command.setInspectionType(QcStatus.TYPE_IQC);
        command.setSourceType(QcStatus.SOURCE_PURCHASE_RECEIPT);
        command.setSourceNo("PR-001");
        command.setItemCode("M001");
        command.setQuantity(new BigDecimal("10"));
        command.setInspectDate(LocalDate.of(2026, 11, 4));
        InspectionCreateCommand.Item first = new InspectionCreateCommand.Item();
        first.setItemName("外观");
        InspectionCreateCommand.Item second = new InspectionCreateCommand.Item();
        second.setItemName("尺寸");
        command.setItems(List.of(first, second));
        return command;
    }

    /**
     * 构造处置命令。
     *
     * @return 命令
     */
    private NcrDisposeCommand disposeCommand() {
        NcrDisposeCommand command = new NcrDisposeCommand();
        command.setDisposition(QcStatus.DISPOSITION_REWORK);
        command.setDisposeDate(LocalDate.of(2026, 11, 4));
        command.setCostAmount(new BigDecimal("120"));
        return command;
    }

    /**
     * 构造物料快照。
     *
     * @return 物料快照
     */
    private ItemSnapshot item() {
        ItemSnapshot item = new ItemSnapshot();
        item.setId(11L);
        item.setCode("M001");
        item.setName("螺栓");
        item.setStatus(MasterDataStatus.ENABLED);
        return item;
    }

    /**
     * 构造量具。
     *
     * @param usable 是否可用
     * @return 量具
     */
    private MeasuringTool tool(boolean usable) {
        MeasuringTool tool = new MeasuringTool();
        tool.setId(5L);
        tool.setToolNo("T001");
        tool.setName("卡尺");
        tool.setCalibrationCycleMonths(12);
        tool.setNextCalibrationDate(usable ? LocalDate.of(2026, 12, 31) : LocalDate.of(2026, 10, 31));
        tool.setStatus(QcStatus.ENABLED);
        return tool;
    }

    /**
     * 构造检验单。
     *
     * @param secondResult 第二项结论
     * @return 检验单
     */
    private InspectionOrder inspection(String secondResult) {
        InspectionOrder order = new InspectionOrder();
        order.setId(1L);
        order.setOrderNo("QC-20261104-0001");
        order.setInspectionType(QcStatus.TYPE_IQC);
        order.setSourceType(QcStatus.SOURCE_PURCHASE_RECEIPT);
        order.setSourceNo("PR-001");
        order.setItemCode("M001");
        order.setItemName("螺栓");
        order.setQuantity(new BigDecimal("10"));
        order.setInspectedQuantity(BigDecimal.ZERO);
        order.setInspectDate(LocalDate.of(2026, 11, 4));
        order.setStatus(QcStatus.INSPECTION_INSPECTING);
        List<InspectionItem> items = new ArrayList<>();
        items.add(itemLine(1, QcStatus.RESULT_PASS));
        items.add(itemLine(2, secondResult));
        order.setItems(items);
        return order;
    }

    /**
     * 构造已录入检验项。
     *
     * @param lineNo 行号
     * @param result 结论
     * @return 检验项
     */
    private InspectionItem itemLine(int lineNo, String result) {
        InspectionItem item = new InspectionItem();
        item.setLineNo(lineNo);
        item.setItemName("项" + lineNo);
        item.setActualValue("实测");
        item.setResult(result);
        return item;
    }

    /**
     * 构造不合格品单。
     *
     * @return 不合格品单
     */
    private Nonconformity ncr() {
        Nonconformity ncr = new Nonconformity();
        ncr.setId(1L);
        ncr.setNcrNo("NCR-202611-0001");
        ncr.setSourceType(QcStatus.SOURCE_INSPECTION);
        ncr.setSourceNo("QC-20261104-0001");
        ncr.setItemName("螺栓");
        ncr.setQuantity(BigDecimal.ONE);
        ncr.setDefectDesc("尺寸超差");
        ncr.setStatus(QcStatus.NCR_OPEN);
        ncr.setCostAmount(BigDecimal.ZERO);
        return ncr;
    }
}
