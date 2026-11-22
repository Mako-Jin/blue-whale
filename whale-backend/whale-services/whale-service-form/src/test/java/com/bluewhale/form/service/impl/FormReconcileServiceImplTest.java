package com.bluewhale.form.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.form.mapper.FormDataMapper;
import com.bluewhale.form.mapper.FormReconcileDiffMapper;
import com.bluewhale.form.mapper.FormTableRegistryMapper;
import com.bluewhale.form.model.request.FormReconcileDiffQueryRequest;
import com.bluewhale.form.model.vo.FormReconcileDiffVO;
import com.bluewhale.form.po.FormDataPo;
import com.bluewhale.form.po.FormReconcileDiffPo;
import com.bluewhale.form.po.FormTableRegistryPo;
import com.bluewhale.form.service.FormReconcileService;
import com.bluewhale.form.support.FormDataEngine;
import com.bluewhale.form.support.FormMetrics;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link FormReconcileServiceImpl} 主从数据对账与差异修复单元测试。
 *
 * @author BlueWhale
 */
@ExtendWith(MockitoExtension.class)
class FormReconcileServiceImplTest {

    /**
     * 初始化 MyBatis-Plus 表元数据：单测没有 Mapper 扫描过程，
     * LambdaQueryWrapper 的方法引用无法解析列名，会抛
     * "can not find lambda cache for this entity"。
     */
    @BeforeAll
    static void initTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, FormTableRegistryPo.class);
        TableInfoHelper.initTableInfo(assistant, FormDataPo.class);
        TableInfoHelper.initTableInfo(assistant, FormReconcileDiffPo.class);
    }

    private static final int PARAM_ERROR_CODE = 400;
    private static final int NOT_FOUND_CODE = 404;
    private static final long TENANT = 1L;
    private static final String CODE = "fm_a";
    private static final String TABLE = "form_data_fm_a_v1";

    @Mock
    private FormReconcileDiffMapper formReconcileDiffMapper;

    @Mock
    private FormTableRegistryMapper formTableRegistryMapper;

    @Mock
    private FormDataMapper formDataMapper;

    @Mock
    private FormDataEngine formDataEngine;

    @Mock
    private FormMetrics formMetrics;

    @InjectMocks
    private FormReconcileServiceImpl reconcileService;

    /**
     * 构造物理表登记桩对象（旧模型）。
     *
     * @return 登记实体
     */
    private static FormTableRegistryPo registry() {
        FormTableRegistryPo registry = new FormTableRegistryPo();
        registry.setId(1L);
        registry.setDefinitionCode(CODE);
        registry.setDefinitionVersion(1);
        registry.setPhysicalTable(TABLE);
        registry.setSchemaJson("{\"fields\":[{\"key\":\"name\",\"label\":\"姓名\",\"type\":\"text\"}]}");
        registry.setStatus(1);
        return registry;
    }

    /**
     * 构造主表记录桩对象（仅主键参与对账比对）。
     *
     * @param id 主键ID
     * @return 表单数据实体
     */
    private static FormDataPo data(Long id) {
        FormDataPo entity = new FormDataPo();
        entity.setId(id);
        entity.setTenantId(TENANT);
        entity.setDefinitionCode(CODE);
        entity.setDefinitionVersion(1);
        entity.setDataJson("{\"name\":\"张三\"}");
        return entity;
    }

    @Test
    @DisplayName("reconcile：双向感知差异并分别登记类型 1 / 类型 2")
    void shouldRegisterBothDirectionDiffs() {
        when(formTableRegistryMapper.selectList(ArgumentMatchers.<Wrapper<FormTableRegistryPo>>any()))
                .thenReturn(List.of(registry()));
        when(formDataEngine.listIds(eq(TABLE), eq(TENANT), anyInt())).thenReturn(List.of(1L, 2L));
        when(formDataMapper.selectList(ArgumentMatchers.<Wrapper<FormDataPo>>any()))
                .thenReturn(List.of(data(2L), data(3L)));

        FormReconcileService.ReconcileSummary summary = reconcileService.reconcile(TENANT);

        assertEquals(1, summary.tableCount());
        assertEquals(2, summary.diffCount());
        assertFalse(summary.truncated());
        ArgumentCaptor<FormReconcileDiffPo> captor = ArgumentCaptor.forClass(FormReconcileDiffPo.class);
        verify(formReconcileDiffMapper, times(2)).insert(captor.capture());
        List<Integer> types = captor.getAllValues().stream().map(FormReconcileDiffPo::getDiffType).toList();
        assertTrue(types.contains(1), "应登记「主表有物理表无」差异");
        assertTrue(types.contains(2), "应登记「物理表有主表无」差异");
        assertEquals(TENANT, captor.getAllValues().get(0).getTenantId());
    }

    @Test
    @DisplayName("reconcile：物理表扫描被截断时跳过「主表有物理表无」比对，避免误报")
    void shouldSkipMainOnlyWhenPhysicalScanTruncated() {
        when(formTableRegistryMapper.selectList(ArgumentMatchers.<Wrapper<FormTableRegistryPo>>any()))
                .thenReturn(List.of(registry()));
        List<Long> ids = new ArrayList<>();
        for (long id = 1L; id <= 20001L; id++) {
            ids.add(id);
        }
        when(formDataEngine.listIds(eq(TABLE), eq(TENANT), anyInt())).thenReturn(ids);
        List<FormDataPo> mains = ids.stream().map(FormReconcileServiceImplTest::data).toList();
        when(formDataMapper.selectList(ArgumentMatchers.<Wrapper<FormDataPo>>any())).thenReturn(mains);

        FormReconcileService.ReconcileSummary summary = reconcileService.reconcile(TENANT);

        assertTrue(summary.truncated());
        assertEquals(0, summary.diffCount());
        verify(formReconcileDiffMapper, never()).insert(any(FormReconcileDiffPo.class));
    }

    @Test
    @DisplayName("reconcile：租户ID为空抛参数错误")
    void shouldRejectNullTenant() {
        BizException ex = assertThrows(BizException.class, () -> reconcileService.reconcile(null));

        assertEquals(PARAM_ERROR_CODE, ex.getCode());
    }

    @Test
    @DisplayName("repair：类型 1 按主记录快照补写物理表并置为已修复")
    void shouldRepairMainOnlyDiff() {
        FormReconcileDiffPo diff = new FormReconcileDiffPo();
        diff.setId(9L);
        diff.setTenantId(TENANT);
        diff.setPhysicalTable(TABLE);
        diff.setDataId(3L);
        diff.setDiffType(1);
        diff.setStatus(0);
        when(formReconcileDiffMapper.selectById(9L)).thenReturn(diff);
        when(formDataMapper.selectById(3L)).thenReturn(data(3L));
        when(formTableRegistryMapper.selectOne(ArgumentMatchers.<Wrapper<FormTableRegistryPo>>any()))
                .thenReturn(registry());

        reconcileService.repair(9L, TENANT);

        verify(formDataEngine).insert(eq(TABLE), any(FormDataPo.class), anyList(), anyMap());
        assertEquals(1, diff.getStatus().intValue());
        verify(formReconcileDiffMapper).updateById(diff);
    }

    @Test
    @DisplayName("repair：类型 2 逻辑删除物理表残留记录并置为已修复")
    void shouldRepairPhysicalOnlyDiff() {
        FormReconcileDiffPo diff = new FormReconcileDiffPo();
        diff.setId(10L);
        diff.setTenantId(TENANT);
        diff.setPhysicalTable(TABLE);
        diff.setDataId(4L);
        diff.setDiffType(2);
        diff.setStatus(0);
        when(formReconcileDiffMapper.selectById(10L)).thenReturn(diff);

        reconcileService.repair(10L, TENANT);

        verify(formDataEngine).logicDelete(TABLE, 4L, TENANT);
        assertEquals(1, diff.getStatus().intValue());
        verify(formDataMapper, never()).selectById(any());
    }

    @Test
    @DisplayName("repair：非待处理状态幂等返回，不重复修复")
    void shouldSkipRepairWhenAlreadyHandled() {
        FormReconcileDiffPo diff = new FormReconcileDiffPo();
        diff.setId(11L);
        diff.setTenantId(TENANT);
        diff.setDiffType(2);
        diff.setStatus(1);
        when(formReconcileDiffMapper.selectById(11L)).thenReturn(diff);

        reconcileService.repair(11L, TENANT);

        verify(formDataEngine, never()).logicDelete(anyString(), anyLong(), anyLong());
        verify(formReconcileDiffMapper, never()).updateById(any(FormReconcileDiffPo.class));
    }

    @Test
    @DisplayName("repair：跨租户或记录不存在抛 NOT_FOUND")
    void shouldRejectMissingOrCrossTenantDiff() {
        FormReconcileDiffPo diff = new FormReconcileDiffPo();
        diff.setId(12L);
        diff.setTenantId(2L);
        diff.setStatus(0);
        when(formReconcileDiffMapper.selectById(12L)).thenReturn(diff);

        BizException ex = assertThrows(BizException.class, () -> reconcileService.repair(12L, TENANT));

        assertEquals(NOT_FOUND_CODE, ex.getCode());
    }

    @Test
    @DisplayName("page：按租户分页查询并映射为视图对象")
    void shouldPageDiffs() {
        FormReconcileDiffPo diff = new FormReconcileDiffPo();
        diff.setId(5L);
        diff.setTenantId(TENANT);
        diff.setBatchNo("20260101030000");
        diff.setPhysicalTable(TABLE);
        diff.setDataId(7L);
        diff.setDiffType(1);
        diff.setStatus(0);
        Page<FormReconcileDiffPo> page = new Page<>(1, 10);
        page.setRecords(List.of(diff));
        page.setTotal(1L);
        when(formReconcileDiffMapper.selectPage(any(), ArgumentMatchers.<Wrapper<FormReconcileDiffPo>>any()))
                .thenReturn(page);
        FormReconcileDiffQueryRequest request = new FormReconcileDiffQueryRequest();

        PageResult<FormReconcileDiffVO> result = reconcileService.page(request, TENANT);

        assertEquals(1, result.getRecords().size());
        assertEquals("20260101030000", result.getRecords().get(0).getBatchNo());
        assertEquals(1L, result.getTotal());
    }
}
