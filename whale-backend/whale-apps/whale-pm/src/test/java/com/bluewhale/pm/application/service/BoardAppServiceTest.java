package com.bluewhale.pm.application.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.pm.infrastructure.entity.PmBoard;
import com.bluewhale.pm.infrastructure.entity.PmBoardColumn;
import com.bluewhale.pm.infrastructure.entity.PmProject;
import com.bluewhale.pm.infrastructure.entity.PmStatus;
import com.bluewhale.pm.infrastructure.entity.PmWorkItem;
import com.bluewhale.pm.infrastructure.mapper.PmBoardColumnMapper;
import com.bluewhale.pm.infrastructure.mapper.PmBoardMapper;
import com.bluewhale.pm.infrastructure.mapper.PmStatusMapper;
import com.bluewhale.pm.infrastructure.mapper.PmWorkItemMapper;
import com.bluewhale.pm.interfaces.dto.BoardColumnSaveRequest;
import com.bluewhale.pm.interfaces.dto.BoardDragRequest;
import com.bluewhale.pm.interfaces.vo.BoardColumnVO;
import com.bluewhale.pm.interfaces.vo.BoardViewVO;
import com.bluewhale.pm.interfaces.vo.WorkItemVO;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link BoardAppService} 看板配置、执行视图与拖拽用例单元测试。
 *
 * @author BlueWhale
 */
class BoardAppServiceTest {

    private static final Long PROJECT_ID = 1L;

    private static final Long BOARD_ID = 5001L;

    private static final Long COLUMN_ID = 5101L;

    private static final Long TODO_STATUS_ID = 1001L;

    private static final Long DOING_STATUS_ID = 1002L;

    private static final Long OPERATOR_ID = 9L;

    private final PmBoardMapper boardMapper = mock(PmBoardMapper.class);

    private final PmBoardColumnMapper columnMapper = mock(PmBoardColumnMapper.class);

    private final PmStatusMapper statusMapper = mock(PmStatusMapper.class);

    private final PmWorkItemMapper workItemMapper = mock(PmWorkItemMapper.class);

    private final ProjectGuard projectGuard = mock(ProjectGuard.class);

    private final ProjectAccessGuard projectAccessGuard = mock(ProjectAccessGuard.class);

    private final WorkItemGuard workItemGuard = mock(WorkItemGuard.class);

    private final WorkItemAppService workItemAppService = mock(WorkItemAppService.class);

    private final BoardAppService service = new BoardAppService(boardMapper, columnMapper, statusMapper,
            workItemMapper, projectGuard, projectAccessGuard, workItemGuard, workItemAppService);

    /**
     * 初始化 MyBatis-Plus 实体元数据（lambda 列名缓存）
     */
    @BeforeAll
    static void initLambdaCache() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, PmBoard.class);
        TableInfoHelper.initTableInfo(assistant, PmBoardColumn.class);
        TableInfoHelper.initTableInfo(assistant, PmStatus.class);
        TableInfoHelper.initTableInfo(assistant, PmWorkItem.class);
    }

    @Test
    @DisplayName("addColumn：状态不属于当前项目拒绝")
    void shouldRejectForeignStatus() {
        when(boardMapper.selectById(BOARD_ID)).thenReturn(board());
        when(projectGuard.requireActiveProject(PROJECT_ID)).thenReturn(project());
        PmStatus status = status(TODO_STATUS_ID, "TODO");
        status.setProjectId(2L);
        when(statusMapper.selectById(TODO_STATUS_ID)).thenReturn(status);

        assertThrows(BizException.class,
                () -> service.addColumn(BOARD_ID, columnRequest(TODO_STATUS_ID, 2), OPERATOR_ID));

        verify(columnMapper, never()).insert(any(PmBoardColumn.class));
    }

    @Test
    @DisplayName("addColumn：同一状态重复映射拒绝")
    void shouldRejectDuplicatedStatusColumn() {
        when(boardMapper.selectById(BOARD_ID)).thenReturn(board());
        when(projectGuard.requireActiveProject(PROJECT_ID)).thenReturn(project());
        when(statusMapper.selectById(TODO_STATUS_ID)).thenReturn(status(TODO_STATUS_ID, "TODO"));
        when(columnMapper.selectCount(ArgumentMatchers.<Wrapper<PmBoardColumn>>any())).thenReturn(1L);

        assertThrows(BizException.class,
                () -> service.addColumn(BOARD_ID, columnRequest(TODO_STATUS_ID, 2), OPERATOR_ID));
    }

    @Test
    @DisplayName("addColumn：默认排序号与不限 WIP")
    void shouldAddColumnWithDefaults() {
        when(boardMapper.selectById(BOARD_ID)).thenReturn(board());
        when(projectGuard.requireActiveProject(PROJECT_ID)).thenReturn(project());
        when(statusMapper.selectById(TODO_STATUS_ID)).thenReturn(status(TODO_STATUS_ID, "TODO"));
        when(columnMapper.selectCount(ArgumentMatchers.<Wrapper<PmBoardColumn>>any())).thenReturn(0L);
        when(columnMapper.selectList(ArgumentMatchers.<Wrapper<PmBoardColumn>>any())).thenReturn(List.of());
        when(columnMapper.insert(any(PmBoardColumn.class))).thenAnswer(invocation -> {
            PmBoardColumn column = invocation.getArgument(0);
            column.setId(COLUMN_ID);
            return 1;
        });

        BoardColumnSaveRequest request = columnRequest(TODO_STATUS_ID, null);
        request.setSortNo(null);
        request.setWipLimit(null);

        BoardColumnVO vo = service.addColumn(BOARD_ID, request, OPERATOR_ID);

        assertEquals(1, vo.getSortNo());
        assertEquals(0, vo.getWipLimit());
        assertEquals("TODO", vo.getStatusCode());
    }

    @Test
    @DisplayName("deleteColumn：列不属于当前看板返回 404")
    void shouldRejectForeignColumnDelete() {
        when(boardMapper.selectById(BOARD_ID)).thenReturn(board());
        when(projectGuard.requireActiveProject(PROJECT_ID)).thenReturn(project());
        PmBoardColumn column = column(TODO_STATUS_ID, 0);
        column.setBoardId(5002L);
        when(columnMapper.selectById(COLUMN_ID)).thenReturn(column);

        assertThrows(BizException.class, () -> service.deleteColumn(BOARD_ID, COLUMN_ID, OPERATOR_ID));

        verify(columnMapper, never()).deleteById(anyLong());
    }

    @Test
    @DisplayName("drag：目标状态未配置看板列拒绝")
    void shouldRejectDragToUnmappedStatus() {
        when(boardMapper.selectById(BOARD_ID)).thenReturn(board());
        when(projectGuard.requireActiveProject(PROJECT_ID)).thenReturn(project());
        when(workItemGuard.requireInProject(3001L, PROJECT_ID)).thenReturn(item(TODO_STATUS_ID));
        when(columnMapper.selectOne(ArgumentMatchers.<Wrapper<PmBoardColumn>>any())).thenReturn(null);

        assertThrows(BizException.class,
                () -> service.drag(BOARD_ID, dragRequest(DOING_STATUS_ID), OPERATOR_ID));
    }

    @Test
    @DisplayName("drag：目标列超出 WIP 上限拒绝")
    void shouldRejectDragWhenWipExceeded() {
        when(boardMapper.selectById(BOARD_ID)).thenReturn(board());
        when(projectGuard.requireActiveProject(PROJECT_ID)).thenReturn(project());
        when(workItemGuard.requireInProject(3001L, PROJECT_ID)).thenReturn(item(TODO_STATUS_ID));
        PmBoardColumn column = column(DOING_STATUS_ID, 1);
        when(columnMapper.selectOne(ArgumentMatchers.<Wrapper<PmBoardColumn>>any())).thenReturn(column);
        when(workItemMapper.selectCount(ArgumentMatchers.<Wrapper<PmWorkItem>>any())).thenReturn(1L);

        assertThrows(BizException.class,
                () -> service.drag(BOARD_ID, dragRequest(DOING_STATUS_ID), OPERATOR_ID));

        verify(workItemAppService, never()).transition(anyLong(), any(), anyLong());
    }

    @Test
    @DisplayName("drag：未超限时委托状态流转")
    void shouldDelegateDragToTransition() {
        when(boardMapper.selectById(BOARD_ID)).thenReturn(board());
        when(projectGuard.requireActiveProject(PROJECT_ID)).thenReturn(project());
        when(workItemGuard.requireInProject(3001L, PROJECT_ID)).thenReturn(item(TODO_STATUS_ID));
        PmBoardColumn column = column(DOING_STATUS_ID, 5);
        when(columnMapper.selectOne(ArgumentMatchers.<Wrapper<PmBoardColumn>>any())).thenReturn(column);
        when(workItemMapper.selectCount(ArgumentMatchers.<Wrapper<PmWorkItem>>any())).thenReturn(2L);
        WorkItemVO expected = new WorkItemVO();
        expected.setId("3001");
        when(workItemAppService.transition(eq(3001L), any(), eq(OPERATOR_ID))).thenReturn(expected);

        WorkItemVO vo = service.drag(BOARD_ID, dragRequest(DOING_STATUS_ID), OPERATOR_ID);

        assertEquals("3001", vo.getId());
        verify(workItemAppService).transition(eq(3001L), any(), eq(OPERATOR_ID));
    }

    @Test
    @DisplayName("drag：拖入当前状态直接返回")
    void shouldReturnWhenDragIntoCurrentStatus() {
        when(boardMapper.selectById(BOARD_ID)).thenReturn(board());
        when(projectGuard.requireActiveProject(PROJECT_ID)).thenReturn(project());
        when(workItemGuard.requireInProject(3001L, PROJECT_ID)).thenReturn(item(TODO_STATUS_ID));
        when(statusMapper.selectById(TODO_STATUS_ID)).thenReturn(status(TODO_STATUS_ID, "TODO"));

        WorkItemVO vo = service.drag(BOARD_ID, dragRequest(TODO_STATUS_ID), OPERATOR_ID);

        assertEquals("TODO", vo.getStatusCode());
        verify(columnMapper, never()).selectOne(ArgumentMatchers.<Wrapper<PmBoardColumn>>any());
    }

    @Test
    @DisplayName("view：按状态分组并标记超出 WIP")
    void shouldBuildBoardView() {
        when(boardMapper.selectById(BOARD_ID)).thenReturn(board());
        when(columnMapper.selectList(ArgumentMatchers.<Wrapper<PmBoardColumn>>any()))
                .thenReturn(List.of(column(TODO_STATUS_ID, 1), column(DOING_STATUS_ID, 0)));
        when(statusMapper.selectList(ArgumentMatchers.<Wrapper<PmStatus>>any()))
                .thenReturn(List.of(status(TODO_STATUS_ID, "TODO"), status(DOING_STATUS_ID, "DOING")));
        when(workItemMapper.selectList(ArgumentMatchers.<Wrapper<PmWorkItem>>any()))
                .thenReturn(List.of(item(TODO_STATUS_ID), item(TODO_STATUS_ID)));

        BoardViewVO vo = service.view(BOARD_ID, OPERATOR_ID);

        assertEquals(2, vo.getColumns().size());
        assertEquals(2, vo.getColumns().get(0).getItemCount());
        assertTrue(vo.getColumns().get(0).getOverloaded());
        assertEquals(0, vo.getColumns().get(1).getItemCount());
    }

    /**
     * 构造看板实体
     *
     * @return 看板实体
     */
    private PmBoard board() {
        PmBoard board = new PmBoard();
        board.setId(BOARD_ID);
        board.setProjectId(PROJECT_ID);
        board.setName("演示看板");
        board.setBoardType("SCRUM");
        return board;
    }

    /**
     * 构造看板列实体
     *
     * @param statusId 状态ID
     * @param wipLimit WIP 上限
     * @return 列实体
     */
    private PmBoardColumn column(Long statusId, int wipLimit) {
        PmBoardColumn column = new PmBoardColumn();
        column.setId(statusId.equals(TODO_STATUS_ID) ? COLUMN_ID : COLUMN_ID + 1);
        column.setBoardId(BOARD_ID);
        column.setStatusId(statusId);
        column.setName(statusId.equals(TODO_STATUS_ID) ? "待办" : "进行中");
        column.setSortNo(statusId.equals(TODO_STATUS_ID) ? 1 : 2);
        column.setWipLimit(wipLimit);
        return column;
    }

    /**
     * 构造工作项实体
     *
     * @param statusId 状态ID
     * @return 工作项实体
     */
    private PmWorkItem item(Long statusId) {
        PmWorkItem item = new PmWorkItem();
        item.setId(3001L);
        item.setProjectId(PROJECT_ID);
        item.setStatusId(statusId);
        item.setItemKey("BW-1");
        item.setTitle("标题");
        item.setRankValue("000000001000");
        return item;
    }

    /**
     * 构造项目实体
     *
     * @return 项目实体
     */
    private PmProject project() {
        PmProject project = new PmProject();
        project.setId(PROJECT_ID);
        project.setStatus(ProjectGuard.STATUS_ACTIVE);
        return project;
    }

    /**
     * 构造状态实体
     *
     * @param id   状态ID
     * @param code 状态编码
     * @return 状态实体
     */
    private PmStatus status(Long id, String code) {
        PmStatus status = new PmStatus();
        status.setId(id);
        status.setProjectId(PROJECT_ID);
        status.setCode(code);
        status.setName(code);
        status.setCategory("DONE".equals(code) ? "DONE" : "TODO");
        return status;
    }

    /**
     * 构造看板列请求
     *
     * @param statusId 状态ID
     * @param wipLimit WIP 上限，可为 {@code null}
     * @return 列请求
     */
    private BoardColumnSaveRequest columnRequest(Long statusId, Integer wipLimit) {
        BoardColumnSaveRequest request = new BoardColumnSaveRequest();
        request.setStatusId(statusId);
        request.setName("待办");
        request.setWipLimit(wipLimit);
        return request;
    }

    /**
     * 构造拖拽请求
     *
     * @param targetStatusId 目标状态ID
     * @return 拖拽请求
     */
    private BoardDragRequest dragRequest(Long targetStatusId) {
        BoardDragRequest request = new BoardDragRequest();
        request.setWorkItemId(3001L);
        request.setTargetStatusId(targetStatusId);
        return request;
    }
}
