package com.bluewhale.pm.application.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.pm.application.assembler.PmAssembler;
import com.bluewhale.pm.domain.model.BoardType;
import com.bluewhale.pm.infrastructure.entity.PmBoard;
import com.bluewhale.pm.infrastructure.entity.PmBoardColumn;
import com.bluewhale.pm.infrastructure.entity.PmStatus;
import com.bluewhale.pm.infrastructure.entity.PmWorkItem;
import com.bluewhale.pm.infrastructure.mapper.PmBoardColumnMapper;
import com.bluewhale.pm.infrastructure.mapper.PmBoardMapper;
import com.bluewhale.pm.infrastructure.mapper.PmStatusMapper;
import com.bluewhale.pm.infrastructure.mapper.PmWorkItemMapper;
import com.bluewhale.pm.interfaces.dto.BoardColumnSaveRequest;
import com.bluewhale.pm.interfaces.dto.BoardDragRequest;
import com.bluewhale.pm.interfaces.dto.BoardSaveRequest;
import com.bluewhale.pm.interfaces.dto.WorkItemTransitionRequest;
import com.bluewhale.pm.interfaces.vo.BoardColumnVO;
import com.bluewhale.pm.interfaces.vo.BoardColumnViewVO;
import com.bluewhale.pm.interfaces.vo.BoardVO;
import com.bluewhale.pm.interfaces.vo.BoardViewVO;
import com.bluewhale.pm.interfaces.vo.WorkItemVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 看板应用服务：看板与列配置、执行视图与 WIP 受限的拖拽流转。
 *
 * <p>列必须映射同项目状态；WIP 上限大于 0 时，拖入导致超限会被拒绝。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BoardAppService {

    /** WIP 不限制 */
    private static final int WIP_UNLIMITED = 0;

    private final PmBoardMapper boardMapper;

    private final PmBoardColumnMapper columnMapper;

    private final PmStatusMapper statusMapper;

    private final PmWorkItemMapper workItemMapper;

    private final ProjectGuard projectGuard;

    private final ProjectAccessGuard projectAccessGuard;

    private final WorkItemGuard workItemGuard;

    private final WorkItemAppService workItemAppService;

    /**
     * 查询项目下全部看板（含列配置）。
     *
     * @param projectId  项目ID
     * @param operatorId 当前用户ID
     * @return 看板列表
     */
    public List<BoardVO> list(Long projectId, Long operatorId) {
        projectAccessGuard.requireReadable(projectId, operatorId);
        List<PmBoard> boards = boardMapper.selectList(new LambdaQueryWrapper<PmBoard>()
                .eq(PmBoard::getProjectId, projectId)
                .orderByAsc(PmBoard::getId));
        if (boards.isEmpty()) {
            return List.of();
        }
        List<Long> boardIds = boards.stream().map(PmBoard::getId).toList();
        Map<Long, List<PmBoardColumn>> columnsByBoard = columnMapper.selectList(
                        new LambdaQueryWrapper<PmBoardColumn>()
                                .in(PmBoardColumn::getBoardId, boardIds)
                                .orderByAsc(PmBoardColumn::getSortNo)
                                .orderByAsc(PmBoardColumn::getId)).stream()
                .collect(Collectors.groupingBy(PmBoardColumn::getBoardId, LinkedHashMap::new, Collectors.toList()));
        Map<Long, PmStatus> statuses = statusMap(projectId);
        return boards.stream()
                .map(board -> PmAssembler.toBoardVo(board,
                        columnsByBoard.getOrDefault(board.getId(), List.of()), statuses))
                .toList();
    }

    /**
     * 查询看板详情（含列配置）。
     *
     * @param id         看板ID
     * @param operatorId 当前用户ID
     * @return 看板详情
     */
    public BoardVO getDetail(Long id, Long operatorId) {
        PmBoard board = requireBoard(id);
        projectAccessGuard.requireReadable(board.getProjectId(), operatorId);
        return PmAssembler.toBoardVo(board, listColumns(id), statusMap(board.getProjectId()));
    }

    /**
     * 创建看板。
     *
     * @param request    创建请求
     * @param operatorId 当前用户ID
     * @return 创建后的看板
     */
    @Transactional(rollbackFor = Exception.class)
    public BoardVO create(BoardSaveRequest request, Long operatorId) {
        projectAccessGuard.requireWritable(request.getProjectId(), operatorId);
        projectGuard.requireActiveProject(request.getProjectId());
        String name = request.getName().trim();
        assertNameUnique(request.getProjectId(), name, null);
        PmBoard board = new PmBoard();
        board.setProjectId(request.getProjectId());
        board.setName(name);
        board.setBoardType(BoardType.from(
                request.getBoardType() == null ? BoardType.SCRUM.name() : request.getBoardType()).name());
        boardMapper.insert(board);
        log.info("看板已创建: projectId={}, boardId={}, name={}", board.getProjectId(), board.getId(), name);
        return PmAssembler.toBoardVo(board, List.of(), Map.of());
    }

    /**
     * 更新看板基础信息。
     *
     * @param id         看板ID
     * @param request    更新请求
     * @param operatorId 当前用户ID
     * @return 更新后的看板
     */
    @Transactional(rollbackFor = Exception.class)
    public BoardVO update(Long id, BoardSaveRequest request, Long operatorId) {
        PmBoard board = requireBoard(id);
        projectAccessGuard.requireWritable(board.getProjectId(), operatorId);
        projectGuard.requireActiveProject(board.getProjectId());
        String name = request.getName().trim();
        assertNameUnique(board.getProjectId(), name, id);
        board.setName(name);
        if (request.getBoardType() != null) {
            board.setBoardType(BoardType.from(request.getBoardType()).name());
        }
        boardMapper.updateById(board);
        return PmAssembler.toBoardVo(board, listColumns(id), statusMap(board.getProjectId()));
    }

    /**
     * 删除看板（逻辑删除，同时删除列）。
     *
     * @param id         看板ID
     * @param operatorId 当前用户ID
     */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id, Long operatorId) {
        PmBoard board = requireBoard(id);
        projectAccessGuard.requireWritable(board.getProjectId(), operatorId);
        projectGuard.requireActiveProject(board.getProjectId());
        columnMapper.delete(new LambdaQueryWrapper<PmBoardColumn>().eq(PmBoardColumn::getBoardId, id));
        boardMapper.deleteById(id);
        log.info("看板已删除: boardId={}", id);
    }

    /**
     * 新增看板列。
     *
     * @param boardId    看板ID
     * @param request    列请求
     * @param operatorId 当前用户ID
     * @return 新增后的列
     */
    @Transactional(rollbackFor = Exception.class)
    public BoardColumnVO addColumn(Long boardId, BoardColumnSaveRequest request, Long operatorId) {
        PmBoard board = requireBoard(boardId);
        projectAccessGuard.requireWritable(board.getProjectId(), operatorId);
        projectGuard.requireActiveProject(board.getProjectId());
        PmStatus status = requireProjectStatus(board.getProjectId(), request.getStatusId());
        assertStatusNotUsed(boardId, request.getStatusId(), null);
        PmBoardColumn column = new PmBoardColumn();
        column.setBoardId(boardId);
        column.setStatusId(status.getId());
        column.setName(request.getName().trim());
        column.setSortNo(request.getSortNo() == null ? nextSortNo(boardId) : request.getSortNo());
        column.setWipLimit(request.getWipLimit() == null ? WIP_UNLIMITED : request.getWipLimit());
        columnMapper.insert(column);
        return PmAssembler.toBoardColumnVo(column, status);
    }

    /**
     * 更新看板列。
     *
     * @param boardId    看板ID
     * @param columnId   列ID
     * @param request    列请求
     * @param operatorId 当前用户ID
     * @return 更新后的列
     */
    @Transactional(rollbackFor = Exception.class)
    public BoardColumnVO updateColumn(Long boardId, Long columnId, BoardColumnSaveRequest request,
            Long operatorId) {
        PmBoard board = requireBoard(boardId);
        projectAccessGuard.requireWritable(board.getProjectId(), operatorId);
        projectGuard.requireActiveProject(board.getProjectId());
        PmBoardColumn column = requireColumn(boardId, columnId);
        PmStatus status = requireProjectStatus(board.getProjectId(), request.getStatusId());
        assertStatusNotUsed(boardId, request.getStatusId(), columnId);
        column.setStatusId(status.getId());
        column.setName(request.getName().trim());
        if (request.getSortNo() != null) {
            column.setSortNo(request.getSortNo());
        }
        if (request.getWipLimit() != null) {
            column.setWipLimit(request.getWipLimit());
        }
        columnMapper.updateById(column);
        return PmAssembler.toBoardColumnVo(column, status);
    }

    /**
     * 删除看板列。
     *
     * @param boardId    看板ID
     * @param columnId   列ID
     * @param operatorId 当前用户ID
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteColumn(Long boardId, Long columnId, Long operatorId) {
        PmBoard board = requireBoard(boardId);
        projectAccessGuard.requireWritable(board.getProjectId(), operatorId);
        projectGuard.requireActiveProject(board.getProjectId());
        requireColumn(boardId, columnId);
        columnMapper.deleteById(columnId);
    }

    /**
     * 查询看板执行视图（列内工作项与 WIP 状态）。
     *
     * @param boardId    看板ID
     * @param operatorId 当前用户ID
     * @return 看板执行视图
     */
    public BoardViewVO view(Long boardId, Long operatorId) {
        PmBoard board = requireBoard(boardId);
        projectAccessGuard.requireReadable(board.getProjectId(), operatorId);
        List<PmBoardColumn> columns = listColumns(boardId);
        Map<Long, PmStatus> statuses = statusMap(board.getProjectId());
        List<PmWorkItem> items = workItemMapper.selectList(new LambdaQueryWrapper<PmWorkItem>()
                .eq(PmWorkItem::getProjectId, board.getProjectId())
                .orderByAsc(PmWorkItem::getRankValue)
                .orderByAsc(PmWorkItem::getId));
        Map<Long, List<PmWorkItem>> itemsByStatus = items.stream()
                .collect(Collectors.groupingBy(PmWorkItem::getStatusId, LinkedHashMap::new,
                        Collectors.toList()));
        List<BoardColumnViewVO> columnViews = new ArrayList<>(columns.size());
        for (PmBoardColumn column : columns) {
            List<WorkItemVO> columnItems = itemsByStatus.getOrDefault(column.getStatusId(), List.of()).stream()
                    .map(item -> PmAssembler.toWorkItemVo(item, statuses.get(item.getStatusId())))
                    .toList();
            columnViews.add(PmAssembler.toBoardColumnViewVo(column, statuses.get(column.getStatusId()),
                    columnItems));
        }
        BoardViewVO vo = new BoardViewVO();
        vo.setId(String.valueOf(board.getId()));
        vo.setProjectId(String.valueOf(board.getProjectId()));
        vo.setName(board.getName());
        vo.setBoardType(board.getBoardType());
        vo.setColumns(columnViews);
        return vo;
    }

    /**
     * 看板拖拽：校验 WIP 后按状态机流转。
     *
     * @param boardId    看板ID
     * @param request    拖拽请求
     * @param operatorId 当前用户ID
     * @return 流转后的工作项
     */
    @Transactional(rollbackFor = Exception.class)
    public WorkItemVO drag(Long boardId, BoardDragRequest request, Long operatorId) {
        PmBoard board = requireBoard(boardId);
        projectAccessGuard.requireWritable(board.getProjectId(), operatorId);
        projectGuard.requireActiveProject(board.getProjectId());
        PmWorkItem item = workItemGuard.requireInProject(request.getWorkItemId(), board.getProjectId());
        if (request.getTargetStatusId().equals(item.getStatusId())) {
            return PmAssembler.toWorkItemVo(item, statusMapper.selectById(item.getStatusId()));
        }
        PmBoardColumn column = columnMapper.selectOne(new LambdaQueryWrapper<PmBoardColumn>()
                .eq(PmBoardColumn::getBoardId, boardId)
                .eq(PmBoardColumn::getStatusId, request.getTargetStatusId()));
        if (column == null) {
            throw new BizException(ErrorCode.PARAM_ERROR, "biz.pm.application.target-status-configured-in-kanban-column", "目标状态未配置在当前看板列中");
        }
        int limit = column.getWipLimit() == null ? WIP_UNLIMITED : column.getWipLimit();
        if (limit > WIP_UNLIMITED) {
            Long current = workItemMapper.selectCount(new LambdaQueryWrapper<PmWorkItem>()
                    .eq(PmWorkItem::getProjectId, board.getProjectId())
                    .eq(PmWorkItem::getStatusId, request.getTargetStatusId()));
            if (current != null && current >= limit) {
                throw new BizException(ErrorCode.PARAM_ERROR, "biz.pm.application.target-column-exceeded-wip-limit-complete",
                              "目标列已超过 WIP 上限（{0}），请先完成在进行中的工作项", limit);
            }
        }
        WorkItemTransitionRequest transition = new WorkItemTransitionRequest();
        transition.setTargetStatusId(request.getTargetStatusId());
        transition.setComment(request.getComment());
        return workItemAppService.transition(item.getId(), transition, operatorId);
    }

    /**
     * 查询看板并要求存在。
     *
     * @param id 看板ID
     * @return 看板实体
     */
    private PmBoard requireBoard(Long id) {
        PmBoard board = id == null ? null : boardMapper.selectById(id);
        if (board == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "biz.pm.application.kanban-found", "看板不存在");
        }
        return board;
    }

    /**
     * 查询列并要求属于指定看板。
     *
     * @param boardId  看板ID
     * @param columnId 列ID
     * @return 列实体
     */
    private PmBoardColumn requireColumn(Long boardId, Long columnId) {
        PmBoardColumn column = columnId == null ? null : columnMapper.selectById(columnId);
        if (column == null || !boardId.equals(column.getBoardId())) {
            throw new BizException(ErrorCode.NOT_FOUND, "biz.pm.application.kanban-column-found", "看板列不存在");
        }
        return column;
    }

    /**
     * 查询看板列（按排序号）。
     *
     * @param boardId 看板ID
     * @return 列列表
     */
    private List<PmBoardColumn> listColumns(Long boardId) {
        return columnMapper.selectList(new LambdaQueryWrapper<PmBoardColumn>()
                .eq(PmBoardColumn::getBoardId, boardId)
                .orderByAsc(PmBoardColumn::getSortNo)
                .orderByAsc(PmBoardColumn::getId));
    }

    /**
     * 查询项目状态索引。
     *
     * @param projectId 项目ID
     * @return 状态索引（按状态ID）
     */
    private Map<Long, PmStatus> statusMap(Long projectId) {
        List<PmStatus> statuses = statusMapper.selectList(new LambdaQueryWrapper<PmStatus>()
                .eq(PmStatus::getProjectId, projectId));
        return statuses.stream().collect(Collectors.toMap(PmStatus::getId, Function.identity()));
    }

    /**
     * 校验状态属于指定项目。
     *
     * @param projectId 项目ID
     * @param statusId  状态ID
     * @return 状态实体
     */
    private PmStatus requireProjectStatus(Long projectId, Long statusId) {
        PmStatus status = statusId == null ? null : statusMapper.selectById(statusId);
        if (status == null || !projectId.equals(status.getProjectId())) {
            throw new BizException(ErrorCode.PARAM_ERROR, "biz.pm.application.status-exist-belong-project", "状态不存在或不属于当前项目");
        }
        return status;
    }

    /**
     * 校验同一看板内状态不重复映射。
     *
     * @param boardId  看板ID
     * @param statusId 状态ID
     * @param excludeId 需排除的列ID，可为 {@code null}
     */
    private void assertStatusNotUsed(Long boardId, Long statusId, Long excludeId) {
        Long count = columnMapper.selectCount(new LambdaQueryWrapper<PmBoardColumn>()
                .eq(PmBoardColumn::getBoardId, boardId)
                .eq(PmBoardColumn::getStatusId, statusId)
                .ne(excludeId != null, PmBoardColumn::getId, excludeId));
        if (count != null && count > 0) {
            throw new BizException(ErrorCode.PARAM_ERROR, "biz.pm.application.status-already-mapped-column-kanban", "该状态已映射到当前看板的其它列");
        }
    }

    /**
     * 计算追加列的排序号。
     *
     * @param boardId 看板ID
     * @return 排序号
     */
    private int nextSortNo(Long boardId) {
        List<PmBoardColumn> columns = listColumns(boardId);
        return columns.isEmpty() ? 1 : columns.get(columns.size() - 1).getSortNo() + 1;
    }

    /**
     * 校验看板名称在项目内唯一。
     *
     * @param projectId 项目ID
     * @param name      看板名称
     * @param excludeId 需排除的看板ID，可为 {@code null}
     */
    private void assertNameUnique(Long projectId, String name, Long excludeId) {
        Long count = boardMapper.selectCount(new LambdaQueryWrapper<PmBoard>()
                .eq(PmBoard::getProjectId, projectId)
                .eq(PmBoard::getName, name)
                .ne(excludeId != null, PmBoard::getId, excludeId));
        if (count != null && count > 0) {
            throw new BizException(ErrorCode.PARAM_ERROR, "biz.pm.application.kanban-name-already-exists", "看板名称已存在");
        }
    }
}
