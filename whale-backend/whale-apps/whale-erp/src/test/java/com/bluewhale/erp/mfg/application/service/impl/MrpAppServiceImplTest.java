package com.bluewhale.erp.mfg.application.service.impl;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.erp.common.ErpErrorCode;
import com.bluewhale.erp.masterdata.domain.model.MasterDataStatus;
import com.bluewhale.erp.mfg.application.command.MrpRunCommand;
import com.bluewhale.erp.mfg.application.view.MrpRunView;
import com.bluewhale.erp.mfg.domain.model.MfgStatus;
import com.bluewhale.erp.mfg.domain.model.MrpQuery;
import com.bluewhale.erp.mfg.domain.model.MrpRun;
import com.bluewhale.erp.mfg.domain.model.MrpRunLine;
import com.bluewhale.erp.mfg.domain.repository.MrpRepository;
import com.bluewhale.erp.scm.domain.port.ItemCatalog;
import com.bluewhale.erp.scm.domain.port.ItemSnapshot;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link MrpAppServiceImpl} 单元测试。
 *
 * @author BlueWhale
 */
class MrpAppServiceImplTest {

    private final MrpRepository mrpRepository = mock(MrpRepository.class);

    private final MrpRunExecutor mrpRunExecutor = mock(MrpRunExecutor.class);

    private final ItemCatalog itemCatalog = mock(ItemCatalog.class);

    private final MrpAppServiceImpl service = new MrpAppServiceImpl(
            mrpRepository, mrpRunExecutor, itemCatalog);

    @Test
    @DisplayName("PDM 失败：运行记录标记 FAILED 且不产出计划订单")
    void shouldMarkRunFailedWhenBomFetchFails() {
        when(itemCatalog.findByCode("P1")).thenReturn(Optional.of(item()));
        when(mrpRepository.nextRunNo(anyString())).thenReturn("MRP-20261107-0001");
        AtomicReference<MrpRun> stored = new AtomicReference<>();
        doAnswer(invocation -> {
            MrpRun run = invocation.getArgument(0);
            run.setId(1L);
            stored.set(run);
            return null;
        }).when(mrpRepository).insertRun(any());
        doThrow(ErpErrorCode.exception(ErpErrorCode.MRP_BOM_FETCH_FAILED))
                .when(mrpRunExecutor).execute(any());
        when(mrpRepository.findRunById(1L)).thenAnswer(invocation -> Optional.of(stored.get()));
        when(mrpRepository.listOrdersByRunId(1L)).thenReturn(List.of());

        MrpRunView view = service.run(command());

        assertEquals(MfgStatus.FAILED, view.getStatus());
        assertTrue(view.getErrorMessage().contains("BOM 快照"));
        assertEquals(Integer.valueOf(0), view.getPlannedOrderCount());
        verify(mrpRepository, never()).insertPlannedOrders(any());
    }

    @Test
    @DisplayName("需求物料不存在：建单前拒绝")
    void shouldRejectUnknownDemandItem() {
        when(itemCatalog.findByCode("P1")).thenReturn(Optional.empty());

        assertThrows(BizException.class, () -> service.run(command()));
        verify(mrpRepository, never()).insertRun(any());
    }

    @Test
    @DisplayName("重跑：按原输入生成新的运行记录")
    void shouldRerunFromStoredLines() {
        MrpRun original = new MrpRun();
        original.setId(10L);
        original.setRunNo("MRP-20261107-0001");
        original.setStatus(MfgStatus.FAILED);
        MrpRunLine line = new MrpRunLine();
        line.setLineNo(1);
        line.setItemCode("P1");
        line.setBomNo("BOM-P1");
        line.setBomVersion("V1");
        line.setQuantity(BigDecimal.TEN);
        line.setDueDate(LocalDate.of(2026, 11, 10));
        original.setLines(List.of(line));
        when(mrpRepository.findRunById(10L)).thenReturn(Optional.of(original));
        when(itemCatalog.findByCode("P1")).thenReturn(Optional.of(item()));
        when(mrpRepository.nextRunNo(anyString())).thenReturn("MRP-20261107-0002");
        AtomicReference<MrpRun> stored = new AtomicReference<>();
        doAnswer(invocation -> {
            MrpRun run = invocation.getArgument(0);
            run.setId(20L);
            stored.set(run);
            return null;
        }).when(mrpRepository).insertRun(any());
        when(mrpRepository.findRunById(20L)).thenAnswer(invocation -> Optional.of(stored.get()));
        when(mrpRepository.listOrdersByRunId(20L)).thenReturn(List.of());

        MrpRunView view = service.rerun(10L);

        assertEquals("MRP-20261107-0002", view.getRunNo());
        assertEquals(Integer.valueOf(1), view.getDemandLineCount());
        assertEquals(1, view.getLines().size());
        assertEquals("P1", view.getLines().get(0).getItemCode());
    }

    @Test
    @DisplayName("运行记录分页返回总条数")
    void shouldPageRuns() {
        MrpRun run = new MrpRun();
        run.setId(1L);
        run.setRunNo("MRP-20261107-0001");
        run.setStatus(MfgStatus.COMPLETED);
        when(mrpRepository.pageRuns(any())).thenReturn(List.of(run));
        when(mrpRepository.countRuns(any())).thenReturn(1L);

        var page = service.pageRuns(new MrpQuery());

        assertEquals(1, page.getRecords().size());
        assertEquals(1L, page.getTotal());
    }

    /**
     * 构造一条需求行的命令。
     *
     * @return 命令
     */
    private MrpRunCommand command() {
        MrpRunCommand command = new MrpRunCommand();
        MrpRunCommand.Line line = new MrpRunCommand.Line();
        line.setItemCode("P1");
        line.setBomNo("BOM-P1");
        line.setVersionNo("V1");
        line.setQuantity(new BigDecimal("10"));
        line.setDueDate(LocalDate.of(2026, 11, 10));
        command.setLines(new ArrayList<>(List.of(line)));
        return command;
    }

    /**
     * 构造物料快照。
     *
     * @return 物料快照
     */
    private ItemSnapshot item() {
        ItemSnapshot item = new ItemSnapshot();
        item.setId(1L);
        item.setCode("P1");
        item.setName("产品P1");
        item.setStatus(MasterDataStatus.ENABLED);
        return item;
    }
}
