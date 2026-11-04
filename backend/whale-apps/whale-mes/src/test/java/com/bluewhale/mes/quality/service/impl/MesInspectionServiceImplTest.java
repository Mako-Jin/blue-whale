package com.bluewhale.mes.quality.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.mes.quality.dto.MesInspectionPlanItemRequest;
import com.bluewhale.mes.quality.dto.MesInspectionPlanSaveRequest;
import com.bluewhale.mes.quality.dto.MesInspectionResultItemRequest;
import com.bluewhale.mes.quality.dto.MesInspectionResultSubmitRequest;
import com.bluewhale.mes.quality.dto.MesInspectionJudgeRequest;
import com.bluewhale.mes.quality.entity.MesInspectionDefect;
import com.bluewhale.mes.quality.entity.MesInspectionPlan;
import com.bluewhale.mes.quality.entity.MesInspectionPlanItem;
import com.bluewhale.mes.quality.entity.MesInspectionResult;
import com.bluewhale.mes.quality.entity.MesInspectionTask;
import com.bluewhale.mes.quality.entity.MesNonconformingRecord;
import com.bluewhale.mes.quality.entity.MesReworkOrder;
import com.bluewhale.mes.quality.entity.MesSpcSample;
import com.bluewhale.mes.quality.mapper.MesDispositionMapper;
import com.bluewhale.mes.quality.mapper.MesInspectionDefectMapper;
import com.bluewhale.mes.quality.mapper.MesInspectionPlanItemMapper;
import com.bluewhale.mes.quality.mapper.MesInspectionPlanMapper;
import com.bluewhale.mes.quality.mapper.MesInspectionResultMapper;
import com.bluewhale.mes.quality.mapper.MesInspectionTaskMapper;
import com.bluewhale.mes.quality.mapper.MesNonconformingRecordMapper;
import com.bluewhale.mes.quality.mapper.MesReworkOrderMapper;
import com.bluewhale.mes.quality.mapper.MesSpcSampleMapper;
import com.bluewhale.mes.quality.service.MesNcrService;
import com.bluewhale.mes.quality.vo.MesFirstArticleGateVO;
import com.bluewhale.mes.quality.vo.MesInspectionTaskDetailVO;
import com.bluewhale.mes.quality.vo.MesInspectionTaskVO;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * MES 车间检验服务单元测试（M3）。
 *
 * <p>覆盖检验项归属校验、结果录入状态推进、判定前完整性校验、失败自动生成 NCR、首件门禁。</p>
 *
 * @author BlueWhale
 */
@ExtendWith(MockitoExtension.class)
class MesInspectionServiceImplTest {

    @Mock
    private MesInspectionPlanMapper planMapper;

    @Mock
    private MesInspectionPlanItemMapper planItemMapper;

    @Mock
    private MesInspectionTaskMapper taskMapper;

    @Mock
    private MesInspectionResultMapper resultMapper;

    @Mock
    private MesInspectionDefectMapper defectMapper;

    @Mock
    private MesNonconformingRecordMapper ncrMapper;

    @Mock
    private MesDispositionMapper dispositionMapper;

    @Mock
    private MesReworkOrderMapper reworkMapper;

    @Mock
    private MesSpcSampleMapper spcSampleMapper;

    @Mock
    private MesNcrService ncrService;

    @InjectMocks
    private MesInspectionServiceImpl inspectionService;

    /**
     * 初始化 MyBatis-Plus 表元数据
     */
    @BeforeAll
    static void initTableInfo() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        for (Class<?> type : List.of(MesInspectionPlan.class, MesInspectionPlanItem.class,
                MesInspectionTask.class, MesInspectionResult.class, MesInspectionDefect.class,
                MesNonconformingRecord.class, MesReworkOrder.class, MesSpcSample.class)) {
            TableInfoHelper.initTableInfo(assistant, type);
        }
    }

    /**
     * 保存计划：检验项序号重复拒绝
     */
    @Test
    @DisplayName("检验计划：检验项序号唯一")
    void savePlanShouldRejectDuplicateItemNo() {
        MesInspectionPlanSaveRequest request = planRequest();
        MesInspectionPlanItemRequest duplicate = new MesInspectionPlanItemRequest();
        duplicate.setItemNo(1);
        duplicate.setItemName("重复项");
        request.getItems().add(duplicate);

        assertThrows(BizException.class, () -> inspectionService.savePlan(request));
        verify(planMapper, never()).insert(any(MesInspectionPlan.class));
    }

    /**
     * 录入结果：检验项不属于任务计划时拒绝
     */
    @Test
    @DisplayName("检验结果：检验项必须属于任务计划")
    void submitResultsShouldRejectForeignPlanItem() {
        when(taskMapper.selectById(1L)).thenReturn(task("PENDING"));
        when(planItemMapper.selectList(any())).thenReturn(List.of(planItem(11L, 1)));
        MesInspectionResultSubmitRequest request = resultRequest(99L, "OK");

        assertThrows(BizException.class, () -> inspectionService.submitResults(1L, request));
        verify(resultMapper, never()).insert(any(MesInspectionResult.class));
    }

    /**
     * 录入结果：OK 值越出上下限拒绝
     */
    @Test
    @DisplayName("检验结果：实测值不得越出标准上下限")
    void submitResultsShouldRejectOutOfRangeValue() {
        when(taskMapper.selectById(1L)).thenReturn(task("PENDING"));
        when(planItemMapper.selectList(any())).thenReturn(List.of(planItem(11L, 1)));
        MesInspectionResultSubmitRequest request = resultRequest(11L, "OK");
        request.getResults().get(0).setNumericValue(new BigDecimal("20"));

        assertThrows(BizException.class, () -> inspectionService.submitResults(1L, request));
        verify(resultMapper, never()).insert(any(MesInspectionResult.class));
    }

    /**
     * 录入结果：任务推进为已录入并统计已检项数
     */
    @Test
    @DisplayName("检验结果：录入后状态为 INSPECTED")
    void submitResultsShouldAdvanceStatus() {
        MesInspectionTask task = task("PENDING");
        when(taskMapper.selectById(1L)).thenReturn(task);
        when(planItemMapper.selectList(any())).thenReturn(List.of(planItem(11L, 1)));
        when(resultMapper.insert(any(MesInspectionResult.class))).thenAnswer(invocation -> {
            MesInspectionResult result = invocation.getArgument(0);
            result.setId(200L);
            return 1;
        });
        when(resultMapper.selectList(any())).thenReturn(List.of(result(11L, "OK")));
        when(taskMapper.updateById(any(MesInspectionTask.class))).thenReturn(1);

        MesInspectionTaskDetailVO detail = inspectionService.submitResults(1L, resultRequest(11L, "OK"));

        assertEquals("INSPECTED", task.getStatus());
        assertEquals(1, task.getInspectedQuantity());
        assertNotNull(detail);
        assertEquals(1, detail.getCompletedItemCount());
    }

    /**
     * 判定：检验项未全部录入拒绝
     */
    @Test
    @DisplayName("检验判定：检验项未完成不可判定")
    void judgeShouldRejectIncompleteItems() {
        when(taskMapper.selectById(1L)).thenReturn(task("INSPECTED"));
        when(planItemMapper.selectList(any())).thenReturn(List.of(planItem(11L, 1), planItem(12L, 2)));
        when(resultMapper.selectList(any())).thenReturn(List.of(result(11L, "OK")));

        assertThrows(BizException.class, () -> inspectionService.judge(1L, judgeRequest()));
        verify(ncrService, never()).createFromInspectionTask(any());
    }

    /**
     * 判定：全部 OK 置为 PASSED
     */
    @Test
    @DisplayName("检验判定：全部合格为 PASSED")
    void judgeShouldPassWhenAllOk() {
        MesInspectionTask task = task("INSPECTED");
        when(taskMapper.selectById(1L)).thenReturn(task);
        when(planItemMapper.selectList(any())).thenReturn(List.of(planItem(11L, 1), planItem(12L, 2)));
        when(resultMapper.selectList(any())).thenReturn(List.of(result(11L, "OK"), result(12L, "OK")));
        when(defectMapper.selectList(any())).thenReturn(List.of());
        when(taskMapper.updateById(any(MesInspectionTask.class))).thenReturn(1);

        MesInspectionTaskVO vo = inspectionService.judge(1L, judgeRequest());

        assertEquals("PASSED", vo.getStatus());
        verify(ncrService, never()).createFromInspectionTask(any());
    }

    /**
     * 判定：存在 NG 时 FAILED 并自动生成 NCR
     */
    @Test
    @DisplayName("检验判定：不合格必生成 NCR")
    void judgeShouldFailAndCreateNcr() {
        MesInspectionTask task = task("INSPECTED");
        when(taskMapper.selectById(1L)).thenReturn(task);
        when(planItemMapper.selectList(any())).thenReturn(List.of(planItem(11L, 1)));
        when(resultMapper.selectList(any())).thenReturn(List.of(result(11L, "NG")));
        when(ncrService.createFromInspectionTask(any(MesInspectionTask.class))).thenReturn(88L);
        when(taskMapper.updateById(any(MesInspectionTask.class))).thenReturn(1);

        MesInspectionTaskVO vo = inspectionService.judge(1L, judgeRequest());

        assertEquals("FAILED", vo.getStatus());
        assertEquals(88L, vo.getNcrId());
        verify(ncrService).createFromInspectionTask(any(MesInspectionTask.class));
    }

    /**
     * 首件门禁：无首件任务时阻断
     */
    @Test
    @DisplayName("首件门禁：无任务阻断批量释放")
    void gateShouldBlockWithoutTask() {
        when(taskMapper.selectList(any())).thenReturn(List.of());

        MesFirstArticleGateVO vo = inspectionService.firstArticleGate(1L, "OP-10");

        assertFalse(vo.getPassed());
        assertNotNull(vo.getMessage());
    }

    /**
     * 首件门禁：最近一次首件合格放行
     */
    @Test
    @DisplayName("首件门禁：最近一次 PASSED 放行")
    void gateShouldPassWhenLatestPassed() {
        MesInspectionTask latest = task("PASSED");
        latest.setTaskNo("QCT-1");
        when(taskMapper.selectList(any())).thenReturn(List.of(latest));

        MesFirstArticleGateVO vo = inspectionService.firstArticleGate(1L, "OP-10");

        assertTrue(vo.getPassed());
        assertEquals("QCT-1", vo.getTaskNo());
    }

    /**
     * 构建计划请求
     *
     * @return 计划请求
     */
    private MesInspectionPlanSaveRequest planRequest() {
        MesInspectionPlanSaveRequest request = new MesInspectionPlanSaveRequest();
        request.setPlanName("首件计划");
        request.setInspectionType("FIRST_ARTICLE");
        MesInspectionPlanItemRequest item = new MesInspectionPlanItemRequest();
        item.setItemNo(1);
        item.setItemName("外径");
        item.setMinValue(new BigDecimal("5"));
        item.setMaxValue(new BigDecimal("10"));
        request.setItems(new java.util.ArrayList<>(List.of(item)));
        return request;
    }

    /**
     * 构建检验任务
     *
     * @param status 状态
     * @return 任务
     */
    private MesInspectionTask task(String status) {
        MesInspectionTask task = new MesInspectionTask();
        task.setId(1L);
        task.setTaskNo("QCT-1");
        task.setTaskType("FIRST_ARTICLE");
        task.setPlanId(10L);
        task.setWorkOrderId(1L);
        task.setOperationCode("OP-10");
        task.setSampleSize(1);
        task.setInspectedQuantity(0);
        task.setStatus(status);
        return task;
    }

    /**
     * 构建计划项
     *
     * @param id     检验项 ID
     * @param itemNo 序号
     * @return 检验项
     */
    private MesInspectionPlanItem planItem(Long id, int itemNo) {
        MesInspectionPlanItem item = new MesInspectionPlanItem();
        item.setId(id);
        item.setPlanId(10L);
        item.setItemNo(itemNo);
        item.setItemName("检验项-" + itemNo);
        item.setMinValue(new BigDecimal("1"));
        item.setMaxValue(new BigDecimal("10"));
        return item;
    }

    /**
     * 构建结果
     *
     * @param planItemId 检验项 ID
     * @param result     判定
     * @return 结果
     */
    private MesInspectionResult result(Long planItemId, String result) {
        MesInspectionResult entity = new MesInspectionResult();
        entity.setTaskId(1L);
        entity.setPlanItemId(planItemId);
        entity.setItemNo(1);
        entity.setSampleNo(1);
        entity.setResult(result);
        return entity;
    }

    /**
     * 构建结果录入请求
     *
     * @param planItemId 检验项 ID
     * @param result     判定
     * @return 录入请求
     */
    private MesInspectionResultSubmitRequest resultRequest(Long planItemId, String result) {
        MesInspectionResultItemRequest item = new MesInspectionResultItemRequest();
        item.setPlanItemId(planItemId);
        item.setSampleNo(1);
        item.setNumericValue(new BigDecimal("6"));
        item.setResult(result);
        MesInspectionResultSubmitRequest request = new MesInspectionResultSubmitRequest();
        request.setInspector("tester");
        request.setResults(List.of(item));
        return request;
    }

    /**
     * 构建判定请求
     *
     * @return 判定请求
     */
    private MesInspectionJudgeRequest judgeRequest() {
        MesInspectionJudgeRequest request = new MesInspectionJudgeRequest();
        request.setJudge("tester");
        request.setConclusion("判定完成");
        return request;
    }
}
