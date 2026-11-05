package com.bluewhale.pdm.change.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.pdm.change.dto.PdmChgOrderAssessRequest;
import com.bluewhale.pdm.change.dto.PdmChgOrderQueryRequest;
import com.bluewhale.pdm.change.dto.PdmChgOrderSaveRequest;
import com.bluewhale.pdm.change.dto.PdmChgTaskSaveRequest;
import com.bluewhale.pdm.change.entity.PdmChgOrder;
import com.bluewhale.pdm.change.entity.PdmChgRequest;
import com.bluewhale.pdm.change.entity.PdmChgTask;
import com.bluewhale.pdm.change.mapper.PdmChgOrderMapper;
import com.bluewhale.pdm.change.mapper.PdmChgRequestMapper;
import com.bluewhale.pdm.change.mapper.PdmChgTaskMapper;
import com.bluewhale.pdm.change.support.PdmChgOrderStatus;
import com.bluewhale.pdm.change.support.PdmChgRequestStatus;
import com.bluewhale.pdm.change.support.PdmChgTaskStatus;
import com.bluewhale.pdm.change.vo.PdmChgOrderVO;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 变更单服务单元测试。
 *
 * @author BlueWhale
 */
@ExtendWith(MockitoExtension.class)
class PdmChgOrderServiceImplTest {

    /** 影响对象类型：零件 */
    private static final String PART_TYPE = "PART";

    /** 非法影响对象类型 */
    private static final String UNKNOWN_TYPE = "UNKNOWN";

    /** 变更单ID */
    private static final Long ORDER_ID = 1L;

    /** 变更申请ID */
    private static final Long ECR_ID = 5L;

    /** 用户ID */
    private static final Long USER_ID = 9L;

    @Mock
    private PdmChgOrderMapper chgOrderMapper;

    @Mock
    private PdmChgTaskMapper chgTaskMapper;

    @Mock
    private PdmChgRequestMapper chgRequestMapper;

    @InjectMocks
    private PdmChgOrderServiceImpl chgOrderService;

    /**
     * 初始化 MyBatis-Plus 表元数据。
     */
    @BeforeAll
    static void initTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, PdmChgOrder.class);
        TableInfoHelper.initTableInfo(assistant, PdmChgTask.class);
    }

    /**
     * 仅审批通过的 ECR 可创建变更单。
     */
    @Test
    void createShouldRejectUnapprovedRequest() {
        when(chgRequestMapper.selectById(ECR_ID)).thenReturn(ecr(ECR_ID, PdmChgRequestStatus.SUBMITTED));

        assertThrows(BizException.class, () -> chgOrderService.create(saveRequest(ECR_ID), USER_ID));

        verify(chgOrderMapper, never()).insert(any(PdmChgOrder.class));
    }

    /**
     * 创建变更单快照 ECR 影响对象并生成 EO 编号。
     */
    @Test
    void createShouldSnapshotImpact() {
        PdmChgRequest ecr = ecr(ECR_ID, PdmChgRequestStatus.APPROVED);
        ecr.setImpactJson("[{\"objectType\":\"PART\",\"objectId\":1001,\"objectCode\":\"P-1\"}]");
        when(chgRequestMapper.selectById(ECR_ID)).thenReturn(ecr);
        when(chgOrderMapper.insert(any(PdmChgOrder.class))).thenReturn(1);

        PdmChgOrderVO vo = chgOrderService.create(saveRequest(ECR_ID), USER_ID);

        assertEquals(PdmChgOrderStatus.DRAFT, vo.getStatus());
        assertEquals(USER_ID, vo.getInitiatorId());
        assertEquals(ECR_ID, vo.getEcrId());
        assertTrue(vo.getEcoNo().startsWith("EO-"));
        assertEquals(1, vo.getImpactObjects().size());
        assertEquals("P-1", vo.getImpactObjects().get(0).getObjectCode());
        verify(chgOrderMapper).insert(any(PdmChgOrder.class));
    }

    /**
     * 评估仅草稿可调，重复评估拒绝。
     */
    @Test
    void assessShouldOnlyAllowDraftOnce() {
        PdmChgOrder entity = order(ORDER_ID, PdmChgOrderStatus.DRAFT);
        when(chgOrderMapper.selectById(ORDER_ID)).thenReturn(entity);

        PdmChgOrderVO vo = chgOrderService.assess(ORDER_ID, assessRequest("影响 2 个零件"));

        assertEquals(PdmChgOrderStatus.ASSESSING, vo.getStatus());
        assertEquals("影响 2 个零件", vo.getImpactAssessment());
        assertThrows(BizException.class, () -> chgOrderService.assess(ORDER_ID, assessRequest("重复")));
    }

    /**
     * 影响评估为空拒绝。
     */
    @Test
    void assessShouldRequireAssessment() {
        PdmChgOrder entity = order(ORDER_ID, PdmChgOrderStatus.DRAFT);
        when(chgOrderMapper.selectById(ORDER_ID)).thenReturn(entity);

        BizException ex = assertThrows(BizException.class,
                () -> chgOrderService.assess(ORDER_ID, assessRequest("  ")));

        assertTrue(ex.getMessage().contains("影响评估不能为空"));
        verify(chgOrderMapper, never()).updateById(any(PdmChgOrder.class));
    }

    /**
     * 提交缺少影响评估拒绝。
     */
    @Test
    void submitShouldRequireAssessment() {
        PdmChgOrder entity = order(ORDER_ID, PdmChgOrderStatus.ASSESSING);
        when(chgOrderMapper.selectById(ORDER_ID)).thenReturn(entity);

        BizException ex = assertThrows(BizException.class, () -> chgOrderService.submit(ORDER_ID));

        assertTrue(ex.getMessage().contains("影响评估不能为空"));
        verify(chgOrderMapper, never()).updateById(any(PdmChgOrder.class));
    }

    /**
     * 提交缺少任务拒绝。
     */
    @Test
    void submitShouldRequireTask() {
        PdmChgOrder entity = order(ORDER_ID, PdmChgOrderStatus.ASSESSING);
        entity.setImpactAssessment("影响 2 个零件");
        when(chgOrderMapper.selectById(ORDER_ID)).thenReturn(entity);
        when(chgTaskMapper.selectCount(any())).thenReturn(0L);

        BizException ex = assertThrows(BizException.class, () -> chgOrderService.submit(ORDER_ID));

        assertTrue(ex.getMessage().contains("变更任务不能为空"));
        verify(chgOrderMapper, never()).updateById(any(PdmChgOrder.class));
    }

    /**
     * 评估中提交后进入审批中。
     */
    @Test
    void submitShouldMoveToApproving() {
        PdmChgOrder entity = order(ORDER_ID, PdmChgOrderStatus.ASSESSING);
        entity.setImpactAssessment("影响 2 个零件");
        when(chgOrderMapper.selectById(ORDER_ID)).thenReturn(entity);
        when(chgTaskMapper.selectCount(any())).thenReturn(1L);
        when(chgOrderMapper.updateById(any(PdmChgOrder.class))).thenReturn(1);

        PdmChgOrderVO vo = chgOrderService.submit(ORDER_ID);

        assertEquals(PdmChgOrderStatus.APPROVING, vo.getStatus());
        assertNotNull(vo.getSubmitTime());
    }

    /**
     * 非草稿不可修改与删除。
     */
    @Test
    void updateAndDeleteShouldOnlyAllowDraft() {
        PdmChgOrder entity = order(ORDER_ID, PdmChgOrderStatus.APPROVED);
        when(chgOrderMapper.selectById(ORDER_ID)).thenReturn(entity);

        assertThrows(BizException.class, () -> chgOrderService.update(ORDER_ID, saveRequest(ECR_ID)));
        assertThrows(BizException.class, () -> chgOrderService.delete(ORDER_ID));

        verify(chgOrderMapper, never()).updateById(any(PdmChgOrder.class));
        verify(chgOrderMapper, never()).deleteById(any(Long.class));
        verify(chgTaskMapper, never()).delete(any());
    }

    /**
     * 删除草稿变更单级联删除任务。
     */
    @Test
    void deleteShouldCascadeTasks() {
        PdmChgOrder entity = order(ORDER_ID, PdmChgOrderStatus.DRAFT);
        when(chgOrderMapper.selectById(ORDER_ID)).thenReturn(entity);

        chgOrderService.delete(ORDER_ID);

        verify(chgTaskMapper).delete(any());
        verify(chgOrderMapper).deleteById(any(Long.class));
    }

    /**
     * 审批中/执行中不可增删改任务。
     */
    @Test
    void taskEditShouldBeFrozenAfterSubmit() {
        PdmChgOrder entity = order(ORDER_ID, PdmChgOrderStatus.APPROVING);
        when(chgOrderMapper.selectById(ORDER_ID)).thenReturn(entity);

        assertThrows(BizException.class, () -> chgOrderService.addTask(ORDER_ID, taskRequest()));
        assertThrows(BizException.class, () -> chgOrderService.updateTask(ORDER_ID, 2L, taskRequest()));
        assertThrows(BizException.class, () -> chgOrderService.deleteTask(ORDER_ID, 2L));

        verify(chgTaskMapper, never()).insert(any(PdmChgTask.class));
    }

    /**
     * 任务目标对象类型非法拒绝。
     */
    @Test
    void addTaskShouldRejectInvalidTargetType() {
        PdmChgOrder entity = order(ORDER_ID, PdmChgOrderStatus.DRAFT);
        when(chgOrderMapper.selectById(ORDER_ID)).thenReturn(entity);
        PdmChgTaskSaveRequest request = taskRequest();
        request.setTargetType(UNKNOWN_TYPE);

        assertThrows(BizException.class, () -> chgOrderService.addTask(ORDER_ID, request));

        verify(chgTaskMapper, never()).insert(any(PdmChgTask.class));
    }

    /**
     * 仅审批中可审批通过。
     */
    @Test
    void approveShouldRequireApproving() {
        PdmChgOrder entity = order(ORDER_ID, PdmChgOrderStatus.DRAFT);
        when(chgOrderMapper.selectById(ORDER_ID)).thenReturn(entity);
        assertThrows(BizException.class, () -> chgOrderService.approve(ORDER_ID, "同意", 10L));

        entity.setStatus(PdmChgOrderStatus.APPROVING);
        when(chgOrderMapper.updateById(any(PdmChgOrder.class))).thenReturn(1);

        PdmChgOrderVO vo = chgOrderService.approve(ORDER_ID, "同意", 10L);

        assertEquals(PdmChgOrderStatus.APPROVED, vo.getStatus());
        assertEquals(10L, vo.getApproveBy());
        assertEquals("同意", vo.getApproveComment());
        assertNotNull(vo.getApproveTime());
    }

    /**
     * 驳回意见必填。
     */
    @Test
    void rejectShouldRequireComment() {
        BizException ex = assertThrows(BizException.class, () -> chgOrderService.reject(ORDER_ID, " ", 10L));

        assertTrue(ex.getMessage().contains("驳回必须填写审批意见"));
        verify(chgOrderMapper, never()).updateById(any(PdmChgOrder.class));
    }

    /**
     * 驳回成功后写审批人、时间与意见。
     */
    @Test
    void rejectShouldMoveToRejected() {
        PdmChgOrder entity = order(ORDER_ID, PdmChgOrderStatus.APPROVING);
        when(chgOrderMapper.selectById(ORDER_ID)).thenReturn(entity);
        when(chgOrderMapper.updateById(any(PdmChgOrder.class))).thenReturn(1);

        PdmChgOrderVO vo = chgOrderService.reject(ORDER_ID, "资料不足", 10L);

        assertEquals(PdmChgOrderStatus.REJECTED, vo.getStatus());
        assertEquals("资料不足", vo.getApproveComment());
        assertNotNull(vo.getApproveTime());
    }

    /**
     * 未审批执行返回 CHANGE_NOT_APPROVED，已通过可进入执行中。
     */
    @Test
    void executeShouldRequireApproved() {
        PdmChgOrder entity = order(ORDER_ID, PdmChgOrderStatus.DRAFT);
        when(chgOrderMapper.selectById(ORDER_ID)).thenReturn(entity);

        BizException ex = assertThrows(BizException.class, () -> chgOrderService.execute(ORDER_ID));

        assertTrue(ex.getMessage().contains("CHANGE_NOT_APPROVED"));
        verify(chgOrderMapper, never()).updateById(any(PdmChgOrder.class));

        entity.setStatus(PdmChgOrderStatus.APPROVED);
        when(chgOrderMapper.updateById(any(PdmChgOrder.class))).thenReturn(1);

        PdmChgOrderVO vo = chgOrderService.execute(ORDER_ID);

        assertEquals(PdmChgOrderStatus.EXECUTING, vo.getStatus());
        assertNotNull(vo.getExecuteTime());
    }

    /**
     * 已完成任务不可重复完成。
     */
    @Test
    void completeTaskShouldRejectCompletedTask() {
        PdmChgOrder entity = order(ORDER_ID, PdmChgOrderStatus.EXECUTING);
        when(chgOrderMapper.selectById(ORDER_ID)).thenReturn(entity);
        PdmChgTask task = task(2L, ORDER_ID, PdmChgTaskStatus.COMPLETED);
        when(chgTaskMapper.selectById(2L)).thenReturn(task);

        BizException ex = assertThrows(BizException.class,
                () -> chgOrderService.completeTask(ORDER_ID, 2L, "V1.2"));

        assertTrue(ex.getMessage().contains("不可重复完成"));
        verify(chgTaskMapper, never()).updateById(any(PdmChgTask.class));
    }

    /**
     * 任务结果版本号格式非法拒绝。
     */
    @Test
    void completeTaskShouldRejectInvalidVersion() {
        PdmChgOrder entity = order(ORDER_ID, PdmChgOrderStatus.EXECUTING);
        when(chgOrderMapper.selectById(ORDER_ID)).thenReturn(entity);
        PdmChgTask task = task(2L, ORDER_ID, PdmChgTaskStatus.PENDING);
        when(chgTaskMapper.selectById(2L)).thenReturn(task);

        BizException ex = assertThrows(BizException.class,
                () -> chgOrderService.completeTask(ORDER_ID, 2L, "1.0"));

        assertTrue(ex.getMessage().contains("版本号格式"));
        verify(chgTaskMapper, never()).updateById(any(PdmChgTask.class));
    }

    /**
     * 任务不属于当前变更单拒绝。
     */
    @Test
    void completeTaskShouldRejectForeignTask() {
        PdmChgOrder entity = order(ORDER_ID, PdmChgOrderStatus.EXECUTING);
        when(chgOrderMapper.selectById(ORDER_ID)).thenReturn(entity);
        when(chgTaskMapper.selectById(2L)).thenReturn(task(2L, 3L, PdmChgTaskStatus.PENDING));

        BizException ex = assertThrows(BizException.class,
                () -> chgOrderService.completeTask(ORDER_ID, 2L, "V1.2"));

        assertTrue(ex.getMessage().contains("变更任务不存在"));
    }

    /**
     * 执行中完成任务并归一化版本号。
     */
    @Test
    void completeTaskShouldRecordVersion() {
        PdmChgOrder entity = order(ORDER_ID, PdmChgOrderStatus.EXECUTING);
        when(chgOrderMapper.selectById(ORDER_ID)).thenReturn(entity);
        PdmChgTask task = task(2L, ORDER_ID, PdmChgTaskStatus.PENDING);
        when(chgTaskMapper.selectById(2L)).thenReturn(task);
        when(chgTaskMapper.updateById(any(PdmChgTask.class))).thenReturn(1);
        when(chgTaskMapper.selectList(any())).thenReturn(List.of(task));

        PdmChgOrderVO vo = chgOrderService.completeTask(ORDER_ID, 2L, "V1.2");

        assertEquals(1, vo.getTasks().size());
        assertEquals(PdmChgTaskStatus.COMPLETED, vo.getTasks().get(0).getStatus());
        assertEquals("V1.2", vo.getTasks().get(0).getResultVersionNo());
        assertNotNull(vo.getTasks().get(0).getCompleteTime());
    }

    /**
     * 存在未完成任务时不可完成变更单。
     */
    @Test
    void completeShouldRequireAllTasksDone() {
        PdmChgOrder entity = order(ORDER_ID, PdmChgOrderStatus.EXECUTING);
        when(chgOrderMapper.selectById(ORDER_ID)).thenReturn(entity);
        when(chgTaskMapper.selectCount(any())).thenReturn(1L, 0L);
        when(chgOrderMapper.updateById(any(PdmChgOrder.class))).thenReturn(1);

        BizException ex = assertThrows(BizException.class, () -> chgOrderService.complete(ORDER_ID));
        assertTrue(ex.getMessage().contains("存在未完成的变更任务"));

        PdmChgOrderVO vo = chgOrderService.complete(ORDER_ID);
        assertEquals(PdmChgOrderStatus.COMPLETED, vo.getStatus());
        assertNotNull(vo.getCompleteTime());
    }

    /**
     * 取消与关闭的来源状态矩阵。
     */
    @Test
    void cancelAndCloseShouldRespectSourceStatus() {
        PdmChgOrder executing = order(1L, PdmChgOrderStatus.EXECUTING);
        PdmChgOrder draft = order(2L, PdmChgOrderStatus.DRAFT);
        PdmChgOrder rejected = order(3L, PdmChgOrderStatus.REJECTED);
        when(chgOrderMapper.selectById(1L)).thenReturn(executing);
        when(chgOrderMapper.selectById(2L)).thenReturn(draft);
        when(chgOrderMapper.selectById(3L)).thenReturn(rejected);

        assertThrows(BizException.class, () -> chgOrderService.cancel(1L));
        assertThrows(BizException.class, () -> chgOrderService.close(1L));

        assertEquals(PdmChgOrderStatus.CANCELLED, chgOrderService.cancel(2L).getStatus());
        PdmChgOrderVO closed = chgOrderService.close(3L);
        assertEquals(PdmChgOrderStatus.CLOSED, closed.getStatus());
        assertNotNull(closed.getCloseTime());
    }

    /**
     * 分页每页上限 200。
     */
    @Test
    void pageShouldClampSize() {
        Page<PdmChgOrder> returned = new Page<>(1, 200);
        returned.setRecords(List.of(order(ORDER_ID, PdmChgOrderStatus.DRAFT)));
        returned.setTotal(1);
        when(chgOrderMapper.selectPage(any(), any())).thenReturn(returned);

        PdmChgOrderQueryRequest query = new PdmChgOrderQueryRequest();
        query.setSize(500);

        assertEquals(1, chgOrderService.page(query).getRecords().size());
        verify(chgOrderMapper).selectPage(argThat(page -> page.getSize() == 200), any());
    }

    /**
     * 详情内嵌任务。
     */
    @Test
    void detailShouldContainTasks() {
        PdmChgOrder entity = order(ORDER_ID, PdmChgOrderStatus.DRAFT);
        when(chgOrderMapper.selectById(ORDER_ID)).thenReturn(entity);
        when(chgTaskMapper.selectList(any())).thenReturn(List.of(
                task(2L, ORDER_ID, PdmChgTaskStatus.PENDING),
                task(3L, ORDER_ID, PdmChgTaskStatus.COMPLETED)));

        PdmChgOrderVO vo = chgOrderService.detail(ORDER_ID);

        assertEquals(2, vo.getTasks().size());
        assertEquals(PART_TYPE, vo.getTasks().get(0).getTargetType());
    }

    /**
     * 构造变更单实体。
     *
     * @param id     变更单ID
     * @param status 状态
     * @return 变更单实体
     */
    private static PdmChgOrder order(Long id, String status) {
        PdmChgOrder entity = new PdmChgOrder();
        entity.setId(id);
        entity.setEcoNo("EO-20261105120000-0001");
        entity.setEcrId(ECR_ID);
        entity.setTitle("零件规格变更");
        entity.setStatus(status);
        entity.setInitiatorId(USER_ID);
        return entity;
    }

    /**
     * 构造变更申请实体。
     *
     * @param id     申请ID
     * @param status 状态
     * @return 申请实体
     */
    private static PdmChgRequest ecr(Long id, String status) {
        PdmChgRequest entity = new PdmChgRequest();
        entity.setId(id);
        entity.setChangeNo("CR-20261105120000-0001");
        entity.setChangeReason("设计变更");
        entity.setStatus(status);
        entity.setInitiatorId(USER_ID);
        return entity;
    }

    /**
     * 构造变更任务实体。
     *
     * @param id     任务ID
     * @param ecoId  变更单ID
     * @param status 状态
     * @return 任务实体
     */
    private static PdmChgTask task(Long id, Long ecoId, String status) {
        PdmChgTask entity = new PdmChgTask();
        entity.setId(id);
        entity.setEcoId(ecoId);
        entity.setTaskName("更新图纸");
        entity.setTaskType("DESIGN");
        entity.setTargetType(PART_TYPE);
        entity.setTargetId(1001L);
        entity.setTargetCode("P-1");
        entity.setStatus(status);
        entity.setSortNo(1);
        return entity;
    }

    /**
     * 构造变更单保存请求。
     *
     * @param ecrId 申请ID
     * @return 保存请求
     */
    private static PdmChgOrderSaveRequest saveRequest(Long ecrId) {
        PdmChgOrderSaveRequest request = new PdmChgOrderSaveRequest();
        request.setEcrId(ecrId);
        request.setTitle("零件规格变更");
        request.setImpactAssessment("影响 2 个零件");
        request.setExecutePlan("先更新图纸，再发布 BOM");
        return request;
    }

    /**
     * 构造影响评估请求。
     *
     * @param assessment 影响评估
     * @return 评估请求
     */
    private static PdmChgOrderAssessRequest assessRequest(String assessment) {
        PdmChgOrderAssessRequest request = new PdmChgOrderAssessRequest();
        request.setImpactAssessment(assessment);
        request.setExecutePlan("按顺序执行");
        return request;
    }

    /**
     * 构造变更任务请求。
     *
     * @return 任务请求
     */
    private static PdmChgTaskSaveRequest taskRequest() {
        PdmChgTaskSaveRequest request = new PdmChgTaskSaveRequest();
        request.setTaskName("更新图纸");
        request.setTaskType("DESIGN");
        request.setTargetType(PART_TYPE);
        request.setTargetId(1001L);
        request.setTargetCode("P-1");
        request.setPlanDesc("更新零件图并发布 V1.2");
        request.setSortNo(1);
        return request;
    }
}
