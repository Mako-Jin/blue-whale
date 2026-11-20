package com.bluewhale.forum.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.tenant.TenantContext;
import com.bluewhale.forum.common.ForumActor;
import com.bluewhale.forum.common.ForumErrorCodes;
import com.bluewhale.forum.common.ForumPermissions;
import com.bluewhale.forum.dto.BoardSaveRequest;
import com.bluewhale.forum.dto.BoardVO;
import com.bluewhale.forum.entity.ForumBoard;
import com.bluewhale.forum.mapper.ForumBoardMapper;
import com.bluewhale.forum.service.BoardService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 版块服务实现。
 *
 * <p>可见性只在本类判定（{@link #canView}）：帖子列表、详情、回帖准入全部复用，
 * 各处各写一份的后果是「列表看不到但直连 URL 能打开」，且不会有任何报错。</p>
 *
 * @author BlueWhale
 */
@Service
@RequiredArgsConstructor
public class BoardServiceImpl implements BoardService {

    /** 可见性：全员 */
    private static final String VISIBILITY_ALL = "ALL";

    /** 可见性：按角色 */
    private static final String VISIBILITY_ROLE = "ROLE";

    /** 可见性：按部门 */
    private static final String VISIBILITY_DEPT = "DEPT";

    /** 发帖策略：开放 */
    private static final String POLICY_OPEN = "OPEN";

    /** 发帖策略：仅管理侧发布 */
    private static final String POLICY_NOTICE_ONLY = "NOTICE_ONLY";

    private final ForumBoardMapper boardMapper;

    @Override
    public List<BoardVO> listVisible(ForumActor actor) {
        TenantContext.requireId();
        boolean manager = actor.hasPermission(ForumPermissions.MANAGE_BOARD);
        List<ForumBoard> boards = boardMapper.selectList(Wrappers.<ForumBoard>lambdaQuery()
                .eq(!manager, ForumBoard::getStatus, 1)
                .orderByAsc(ForumBoard::getParentId)
                .orderByAsc(ForumBoard::getSortNo)
                .orderByAsc(ForumBoard::getId));
        List<BoardVO> result = new ArrayList<>(boards.size());
        for (ForumBoard board : boards) {
            if (canView(board, actor)) {
                result.add(toVO(board));
            }
        }
        return result;
    }

    @Override
    public ForumBoard requireVisibleBoard(Long boardId, ForumActor actor) {
        return requireBoard(boardId, actor, false);
    }

    @Override
    public ForumBoard requirePostableBoard(Long boardId, ForumActor actor) {
        return requireBoard(boardId, actor, true);
    }

    @Override
    public boolean canView(ForumBoard board, ForumActor actor) {
        if (board == null || actor == null) {
            return false;
        }
        // 治理权限可见全部版块（含停用），否则无法在管理台配置
        if (actor.hasPermission(ForumPermissions.MANAGE_BOARD)) {
            return true;
        }
        if (board.getStatus() == null || board.getStatus() != 1) {
            return false;
        }
        String visibility = board.getVisibility() == null ? VISIBILITY_ALL : board.getVisibility();
        return switch (visibility) {
            case VISIBILITY_ROLE -> matchesAny(board.getVisibleRoles(), actor.permissions());
            case VISIBILITY_DEPT -> board.getVisibleDepts() != null
                    && actor.deptId() != null
                    && splitToSet(board.getVisibleDepts()).contains(actor.deptId());
            default -> true;
        };
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BoardVO create(BoardSaveRequest request, ForumActor actor) {
        requireBoardManager(actor);
        TenantContext.requireId();
        assertCodeFree(request.getCode(), null);
        ForumBoard board = new ForumBoard();
        applyRequest(board, request);
        boardMapper.insert(board);
        return toVO(board);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BoardVO update(Long id, BoardSaveRequest request, ForumActor actor) {
        requireBoardManager(actor);
        TenantContext.requireId();
        ForumBoard board = requireExisting(id);
        // 编码不可改：改编码会让已授权/已收藏的引用失效，静默丢权限
        board.setParentId(request.getParentId());
        board.setName(request.getName());
        board.setDescription(request.getDescription());
        board.setIcon(request.getIcon());
        board.setVisibility(request.getVisibility());
        board.setVisibleRoles(request.getVisibleRoles());
        board.setVisibleDepts(request.getVisibleDepts());
        board.setAuditMode(request.getAuditMode());
        board.setPostPolicy(request.getPostPolicy());
        board.setSortNo(request.getSortNo());
        board.setStatus(request.getStatus());
        boardMapper.updateById(board);
        return toVO(board);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id, ForumActor actor) {
        requireBoardManager(actor);
        TenantContext.requireId();
        ForumBoard board = requireExisting(id);
        // 先释放唯一键再逻辑删除：顺序反了会留下「墓碑仍占 code」的不可重建版块
        boardMapper.renameCodeForDelete(board.getId(), board.getCode() + "#D" + board.getId());
        boardMapper.deleteById(board.getId());
    }

    /**
     * 取版块并按需校验发帖准入。
     *
     * @param boardId 版块ID
     * @param actor 当前操作者
     * @param postable 是否校验发帖准入
     * @return 版块实体
     */
    private ForumBoard requireBoard(Long boardId, ForumActor actor, boolean postable) {
        TenantContext.requireId();
        ForumBoard board = boardId == null ? null : boardMapper.selectById(boardId);
        if (board == null || !canView(board, actor)) {
            throw new BizException(ForumErrorCodes.THREAD_NOT_FOUND, "forum.board.not-found-or-invisible",
                    "版块不存在或不可见");
        }
        if (!postable) {
            return board;
        }
        if (board.getStatus() == null || board.getStatus() != 1) {
            throw new BizException(ForumErrorCodes.BOARD_NOT_POSTABLE, "forum.board.disabled",
                    "版块已停用，暂不可发帖");
        }
        String policy = board.getPostPolicy() == null ? POLICY_OPEN : board.getPostPolicy();
        if (POLICY_OPEN.equals(policy)) {
            return board;
        }
        boolean manager = actor.hasPermission(ForumPermissions.MANAGE_DISPOSE)
                || actor.hasPermission(ForumPermissions.MANAGE_BOARD);
        if (POLICY_NOTICE_ONLY.equals(policy)) {
            if (!manager) {
                throw new BizException(ForumErrorCodes.BOARD_NOT_POSTABLE, "forum.board.notice-only",
                        "该版块仅限管理员发布公告");
            }
            return board;
        }
        throw new BizException(ForumErrorCodes.BOARD_NOT_POSTABLE, "forum.board.read-only", "该版块只读，禁止发帖");
    }

    /**
     * 查询未删除版块，不存在抛 30001。
     *
     * @param id 版块ID
     * @return 版块实体
     */
    private ForumBoard requireExisting(Long id) {
        ForumBoard board = id == null ? null : boardMapper.selectById(id);
        if (board == null) {
            throw new BizException(ForumErrorCodes.THREAD_NOT_FOUND, "forum.board.not-found", "版块不存在");
        }
        return board;
    }

    /**
     * 校验调用方具备版块治理权限。
     *
     * @param actor 当前操作者
     */
    private void requireBoardManager(ForumActor actor) {
        if (!actor.hasPermission(ForumPermissions.MANAGE_BOARD)) {
            throw new BizException(ErrorCode.FORBIDDEN, "forum.board.manage-forbidden", "无权管理版块");
        }
    }

    /**
     * 校验编码在租户内未被占用。
     *
     * @param code 版块编码
     * @param excludeId 排除的版块ID（更新场景，M1 更新不改码故传 null）
     */
    private void assertCodeFree(String code, Long excludeId) {
        Long exists = boardMapper.selectCount(Wrappers.<ForumBoard>lambdaQuery()
                .eq(ForumBoard::getCode, code)
                .ne(excludeId != null, ForumBoard::getId, excludeId));
        if (exists != null && exists > 0) {
            throw new BizException(ForumErrorCodes.BOARD_CODE_DUPLICATED, "forum.board.code-duplicated",
                    "版块编码已存在");
        }
    }

    /**
     * 将请求字段写入实体（编码只在新建时写入）。
     *
     * @param board 目标实体
     * @param request 保存请求
     */
    private void applyRequest(ForumBoard board, BoardSaveRequest request) {
        board.setParentId(request.getParentId());
        board.setCode(request.getCode());
        board.setName(request.getName());
        board.setDescription(request.getDescription());
        board.setIcon(request.getIcon());
        board.setVisibility(request.getVisibility());
        board.setVisibleRoles(request.getVisibleRoles());
        board.setVisibleDepts(request.getVisibleDepts());
        board.setAuditMode(request.getAuditMode());
        board.setPostPolicy(request.getPostPolicy());
        board.setSortNo(request.getSortNo());
        board.setStatus(request.getStatus());
    }

    /**
     * 实体转视图对象。
     *
     * @param board 版块实体
     * @return 视图对象
     */
    private BoardVO toVO(ForumBoard board) {
        BoardVO vo = new BoardVO();
        vo.setId(board.getId());
        vo.setParentId(board.getParentId());
        vo.setCode(board.getCode());
        vo.setName(board.getName());
        vo.setDescription(board.getDescription());
        vo.setIcon(board.getIcon());
        vo.setVisibility(board.getVisibility());
        vo.setAuditMode(board.getAuditMode());
        vo.setPostPolicy(board.getPostPolicy());
        vo.setSortNo(board.getSortNo());
        vo.setStatus(board.getStatus());
        return vo;
    }

    /**
     * 判断逗号分隔列表是否与给定集合有交集。
     *
     * @param csv 逗号分隔文本
     * @param values 待比对集合
     * @return 有交集返回 {@code true}
     */
    private boolean matchesAny(String csv, Set<String> values) {
        if (csv == null || csv.isBlank() || values == null || values.isEmpty()) {
            return false;
        }
        for (String item : splitToSet(csv)) {
            if (values.contains(item)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 拆分逗号分隔文本为集合（去空白、去空项、保持顺序）。
     *
     * @param csv 逗号分隔文本
     * @return 集合
     */
    private Set<String> splitToSet(String csv) {
        Set<String> set = new LinkedHashSet<>();
        for (String item : csv.split(",")) {
            String trimmed = item.trim();
            if (!trimmed.isEmpty()) {
                set.add(trimmed);
            }
        }
        return set;
    }
}
