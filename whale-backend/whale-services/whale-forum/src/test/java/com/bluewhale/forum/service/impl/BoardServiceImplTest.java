package com.bluewhale.forum.service.impl;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.tenant.TenantContext;
import com.bluewhale.forum.common.ForumActor;
import com.bluewhale.forum.common.ForumErrorCodes;
import com.bluewhale.forum.common.ForumPermissions;
import com.bluewhale.forum.entity.ForumBoard;
import com.bluewhale.forum.mapper.ForumBoardMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * 版块可见性与发帖准入单测。
 *
 * <p>这几条是「列表看不到但直连能打开」的唯一防线，改动 {@code canView} 必须同步改测试。</p>
 *
 * @author BlueWhale
 */
@ExtendWith(MockitoExtension.class)
class BoardServiceImplTest {

    @Mock
    private ForumBoardMapper boardMapper;

    @InjectMocks
    private BoardServiceImpl boardService;

    @BeforeEach
    void setUp() {
        TenantContext.set(1L);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("ALL 版块：普通员工可见")
    void allVisibilityVisibleForEveryone() {
        ForumBoard board = board("ALL", 1);
        assertThat(boardService.canView(board, actor(7L, Set.of(ForumPermissions.BROWSE), null))).isTrue();
    }

    @Test
    @DisplayName("ROLE 版块：角色码命中才可见，权限码不命中角色时不放行")
    void roleVisibilityRequiresMatchingRole() {
        ForumBoard board = board("ROLE", 1);
        board.setVisibleRoles("forum_moderator,hr_admin");

        assertThat(boardService.canView(board, actor(7L, Set.of("hr_admin"), null))).isTrue();
        assertThat(boardService.canView(board, actor(7L, Set.of(ForumPermissions.BROWSE), null))).isFalse();
    }

    @Test
    @DisplayName("DEPT 版块：缺部门上下文时按不可见处理（fail-closed）")
    void deptVisibilityFailsClosedWithoutDeptContext() {
        ForumBoard board = board("DEPT", 1);
        board.setVisibleDepts("100,200");

        assertThat(boardService.canView(board, actor(7L, Set.of(ForumPermissions.BROWSE), null))).isFalse();
        assertThat(boardService.canView(board, actor(7L, Set.of(ForumPermissions.BROWSE), "200"))).isTrue();
    }

    @Test
    @DisplayName("停用版块对普通用户不可见，对版块管理者可见（否则管理台配不了）")
    void disabledBoardVisibleOnlyToManager() {
        ForumBoard board = board("ALL", 0);
        assertThat(boardService.canView(board, actor(7L, Set.of(ForumPermissions.BROWSE), null))).isFalse();
        assertThat(boardService.canView(board, actor(7L, Set.of(ForumPermissions.MANAGE_BOARD), null))).isTrue();
    }

    @Test
    @DisplayName("只读版块发帖返回 30040")
    void readonlyBoardRejectsPosting() {
        ForumBoard board = board("ALL", 1);
        board.setPostPolicy("READONLY");
        when(boardMapper.selectById(9L)).thenReturn(board);

        assertThatThrownBy(() -> boardService.requirePostableBoard(9L, actor(7L, Set.of(ForumPermissions.THREAD_ADD), null)))
                .isInstanceOf(BizException.class)
                .satisfies(e -> assertThat(((BizException) e).getCode())
                        .isEqualTo(ForumErrorCodes.BOARD_NOT_POSTABLE));
    }

    @Test
    @DisplayName("仅管理发布版块：普通员工 30040，治理角色放行")
    void noticeOnlyBoardAllowsManager() {
        ForumBoard board = board("ALL", 1);
        board.setPostPolicy("NOTICE_ONLY");
        when(boardMapper.selectById(9L)).thenReturn(board);

        assertThatThrownBy(() -> boardService.requirePostableBoard(9L, actor(7L, Set.of(ForumPermissions.THREAD_ADD), null)))
                .isInstanceOf(BizException.class);
        assertThat(boardService.requirePostableBoard(9L,
                actor(8L, Set.of(ForumPermissions.MANAGE_DISPOSE), null))).isSameAs(board);
    }

    @Test
    @DisplayName("不可见版块按 30001 返回，不泄露存在性")
    void invisibleBoardTreatedAsNotFound() {
        ForumBoard board = board("ROLE", 1);
        board.setVisibleRoles("forum_moderator");
        when(boardMapper.selectById(9L)).thenReturn(board);

        assertThatThrownBy(() -> boardService.requireVisibleBoard(9L, actor(7L, Set.of(ForumPermissions.BROWSE), null)))
                .isInstanceOf(BizException.class)
                .satisfies(e -> assertThat(((BizException) e).getCode())
                        .isEqualTo(ForumErrorCodes.THREAD_NOT_FOUND));
    }

    /**
     * 构造版块。
     *
     * @param visibility 可见性
     * @param status 状态
     * @return 版块实体
     */
    private ForumBoard board(String visibility, int status) {
        ForumBoard board = new ForumBoard();
        board.setId(9L);
        board.setTenantId(1L);
        board.setCode("tech");
        board.setName("鲸百工坊");
        board.setVisibility(visibility);
        board.setAuditMode(0);
        board.setPostPolicy("OPEN");
        board.setStatus(status);
        return board;
    }

    /**
     * 构造操作者。
     *
     * @param userId 用户ID
     * @param permissions 权限/角色码
     * @param deptId 部门ID
     * @return 操作者
     */
    private ForumActor actor(Long userId, Set<String> permissions, String deptId) {
        return new ForumActor(userId, permissions, deptId);
    }
}
