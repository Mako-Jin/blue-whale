package com.bluewhale.erp.mfg.application.service.impl;

import com.bluewhale.erp.masterdata.domain.model.MasterDataStatus;
import com.bluewhale.erp.mfg.application.command.MpsCreateCommand;
import com.bluewhale.erp.mfg.application.view.MpsPlanView;
import com.bluewhale.erp.mfg.domain.model.MfgQuery;
import com.bluewhale.erp.mfg.domain.model.MfgStatus;
import com.bluewhale.erp.mfg.domain.model.MpsPlan;
import com.bluewhale.erp.mfg.domain.model.MpsPlanLine;
import com.bluewhale.erp.mfg.domain.repository.MpsRepository;
import com.bluewhale.erp.scm.domain.port.ItemCatalog;
import com.bluewhale.erp.scm.domain.port.ItemSnapshot;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link MpsAppServiceImpl} 单元测试。
 *
 * @author BlueWhale
 */
class MpsAppServiceImplTest {

    private final MpsRepository mpsRepository = mock(MpsRepository.class);

    private final ItemCatalog itemCatalog = mock(ItemCatalog.class);

    private final MpsAppServiceImpl service = new MpsAppServiceImpl(mpsRepository, itemCatalog);

    @Test
    @DisplayName("新增计划：快照物料并汇总计划数量")
    void shouldCreatePlan() {
        when(itemCatalog.findByCode("ERP-001")).thenReturn(Optional.of(item()));
        when(mpsRepository.nextPlanNo("2026-11")).thenReturn("MP-202611-0001");

        MpsPlanView view = service.create(command());

        assertEquals("MP-202611-0001", view.getPlanNo());
        assertEquals(MfgStatus.DRAFT, view.getStatus());
        assertEquals(0, view.getTotalPlanned().compareTo(new BigDecimal("10")));
        assertEquals(BigDecimal.ZERO, view.getLines().get(0).getReleasedQuantity());
        verify(mpsRepository).insert(any(MpsPlan.class));
    }

    @Test
    @DisplayName("下达计划：已下达数量回写")
    void shouldRelease() {
        MpsPlan plan = draftPlan();
        when(mpsRepository.findById(1L)).thenReturn(Optional.of(plan));

        MpsPlanView view = service.release(1L);

        assertEquals(MfgStatus.RELEASED, view.getStatus());
        assertEquals(0, view.getLines().get(0).getReleasedQuantity().compareTo(new BigDecimal("10")));
        verify(mpsRepository).update(any(MpsPlan.class));
    }

    @Test
    @DisplayName("取消计划")
    void shouldCancel() {
        MpsPlan plan = draftPlan();
        when(mpsRepository.findById(1L)).thenReturn(Optional.of(plan));

        MpsPlanView view = service.cancel(1L);

        assertEquals(MfgStatus.CANCELLED, view.getStatus());
    }

    @Test
    @DisplayName("分页查询返回总条数")
    void shouldPage() {
        when(mpsRepository.page(any())).thenReturn(List.of(draftPlan()));
        when(mpsRepository.count(any())).thenReturn(1L);

        var page = service.page(new MfgQuery());

        assertEquals(1, page.getRecords().size());
        assertEquals(1L, page.getTotal());
    }

    /**
     * 构造创建命令。
     *
     * @return 命令
     */
    private MpsCreateCommand command() {
        MpsCreateCommand command = new MpsCreateCommand();
        command.setPeriod("2026-11");
        MpsCreateCommand.Line line = new MpsCreateCommand.Line();
        line.setItemCode("ERP-001");
        line.setPlannedQuantity(new BigDecimal("10"));
        command.setLines(List.of(line));
        return command;
    }

    /**
     * 构造草稿主生产计划。
     *
     * @return 计划
     */
    private MpsPlan draftPlan() {
        MpsPlan plan = new MpsPlan();
        plan.setId(1L);
        plan.setPlanNo("MP-001");
        plan.setPeriod("2026-11");
        plan.setStatus(MfgStatus.DRAFT);
        MpsPlanLine line = new MpsPlanLine();
        line.setLineNo(1);
        line.setItemId(3L);
        line.setItemCode("ERP-001");
        line.setItemName("物料A");
        line.setPlannedQuantity(new BigDecimal("10"));
        line.setReleasedQuantity(BigDecimal.ZERO);
        plan.setLines(List.of(line));
        plan.setTotalPlanned(new BigDecimal("10"));
        return plan;
    }

    /**
     * 构造物料快照。
     *
     * @return 快照
     */
    private ItemSnapshot item() {
        ItemSnapshot snapshot = new ItemSnapshot();
        snapshot.setId(3L);
        snapshot.setCode("ERP-001");
        snapshot.setName("物料A");
        snapshot.setStatus(MasterDataStatus.ENABLED);
        return snapshot;
    }
}
