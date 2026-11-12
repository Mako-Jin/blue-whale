package com.bluewhale.forum.service.impl;

import com.bluewhale.common.tenant.TenantContext;
import com.bluewhale.forum.common.ForumActor;
import com.bluewhale.forum.common.ForumPermissions;
import com.bluewhale.forum.config.ForumProperties;
import com.bluewhale.forum.dto.BoardVO;
import com.bluewhale.forum.dto.HotCandidate;
import com.bluewhale.forum.dto.HotItemVO;
import com.bluewhale.forum.entity.ForumHotSnapshot;
import com.bluewhale.forum.mapper.ForumHotSnapshotMapper;
import com.bluewhale.forum.mapper.ForumThreadMapper;
import com.bluewhale.forum.service.BoardService;
import com.bluewhale.forum.service.ExpService;
import com.bluewhale.forum.service.HotService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 热榜服务单测：只读快照、可见版块过滤、空快照兜底、多租户刷新。
 *
 * @author BlueWhale
 */
@ExtendWith(MockitoExtension.class)
class HotServiceImplTest {

    @Mock
    private ForumThreadMapper threadMapper;

    @Mock
    private ForumHotSnapshotMapper snapshotMapper;

    @Mock
    private HotSnapshotStore snapshotStore;

    @Mock
    private BoardService boardService;

    @Mock
    private ExpService expService;

    @Spy
    private ForumProperties properties = new ForumProperties();

    @InjectMocks
    private HotServiceImpl hotService;

    @BeforeEach
    void setUp() {
        TenantContext.set(1L);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("读快照：不可见版块的热榜条目不返回")
    void listFiltersInvisibleBoards() {
        when(snapshotMapper.selectList(any())).thenReturn(List.of(snapshot(1, 21L, 9L), snapshot(2, 22L, 77L)));
        when(boardService.listVisible(actor())).thenReturn(List.of(board(9L)));

        List<HotItemVO> items = hotService.list(null, null, actor());

        assertThat(items).hasSize(1);
        assertThat(items.get(0).getThreadId()).isEqualTo(21L);
    }

    @Test
    @DisplayName("窗口参数：非法值按 7d 兜底，空快照仍可实时兜底")
    void illegalWindowFallsBackTo7d() {
        when(snapshotMapper.selectList(any())).thenReturn(List.of());
        when(boardService.listVisible(actor())).thenReturn(List.of(board(9L)));
        when(threadMapper.selectHotCandidates(anyLong(), any(LocalDateTime.class), anyDouble(), anyDouble(),
                anyDouble(), anyDouble(), anyDouble(), anyInt(), anyInt()))
                .thenReturn(List.of(candidate(21L, 9L)));

        List<HotItemVO> items = hotService.list("30d", 5, actor());

        assertThat(items).hasSize(1);
    }

    @Test
    @DisplayName("快照为空时用实时打分兜底")
    void emptySnapshotFallsBackToLive() {
        when(snapshotMapper.selectList(any())).thenReturn(List.of());
        when(boardService.listVisible(actor())).thenReturn(List.of(board(9L)));
        when(threadMapper.selectHotCandidates(anyLong(), any(LocalDateTime.class), anyDouble(), anyDouble(),
                anyDouble(), anyDouble(), anyDouble(), anyInt(), anyInt()))
                .thenReturn(List.of(candidate(21L, 9L)));

        List<HotItemVO> items = hotService.list(HotService.WINDOW_24H, 5, actor());

        assertThat(items).hasSize(1);
        assertThat(items.get(0).getRankNo()).isEqualTo(1);
        assertThat(items.get(0).getThreadId()).isEqualTo(21L);
    }

    @Test
    @DisplayName("刷新：24h 与 7d 两档分别整表覆盖并返回合计条数")
    void refreshReplacesBothWindows() {
        List<HotCandidate> candidates = List.of(candidate(21L, 9L), candidate(22L, 9L));
        when(threadMapper.selectHotCandidates(anyLong(), any(LocalDateTime.class), anyDouble(), anyDouble(),
                anyDouble(), anyDouble(), anyDouble(), anyInt(), anyInt())).thenReturn(candidates);
        when(snapshotStore.replace(eq(1L), eq(HotService.WINDOW_24H), eq(candidates), any(LocalDateTime.class)))
                .thenReturn(2);
        when(snapshotStore.replace(eq(1L), eq(HotService.WINDOW_7D), eq(candidates), any(LocalDateTime.class)))
                .thenReturn(2);

        assertThat(hotService.refresh()).isEqualTo(4);
        verify(snapshotStore).replace(eq(1L), eq(HotService.WINDOW_24H), eq(candidates), any(LocalDateTime.class));
        verify(snapshotStore).replace(eq(1L), eq(HotService.WINDOW_7D), eq(candidates), any(LocalDateTime.class));
        verify(expService).award(eq(100L), eq(ExpService.EVENT_HOT_TOP1), anyInt(), eq(21L));
    }

    @Test
    @DisplayName("定时刷新遍历所有租户并清理租户上下文")
    void refreshAllTenantsIteratesAndClearsContext() {
        when(threadMapper.selectActiveTenantIds()).thenReturn(List.of(1L, 2L));
        when(threadMapper.selectHotCandidates(anyLong(), any(LocalDateTime.class), anyDouble(), anyDouble(),
                anyDouble(), anyDouble(), anyDouble(), anyInt(), anyInt()))
                .thenReturn(List.of(candidate(21L, 9L)));
        when(snapshotStore.replace(anyLong(), anyString(), any(), any(LocalDateTime.class))).thenReturn(1);

        assertThat(hotService.refreshAllTenants()).isEqualTo(4);
        verify(snapshotStore, times(4)).replace(anyLong(), anyString(), any(), any(LocalDateTime.class));
        assertThat(TenantContext.get()).isNull();
    }

    /**
     * 构造操作者。
     *
     * @return 操作者
     */
    private ForumActor actor() {
        return new ForumActor(100L, Set.of(ForumPermissions.BROWSE), null);
    }

    /**
     * 构造版块视图。
     *
     * @param id 版块ID
     * @return 版块
     */
    private BoardVO board(Long id) {
        BoardVO board = new BoardVO();
        board.setId(id);
        return board;
    }

    /**
     * 构造快照行。
     *
     * @param rank 名次
     * @param threadId 帖子ID
     * @param boardId 版块ID
     * @return 快照行
     */
    private ForumHotSnapshot snapshot(int rank, Long threadId, Long boardId) {
        ForumHotSnapshot snapshot = new ForumHotSnapshot();
        snapshot.setRankNo(rank);
        snapshot.setThreadId(threadId);
        snapshot.setBoardId(boardId);
        snapshot.setTitle("标题" + threadId);
        snapshot.setReplyCount(1);
        snapshot.setLikeCount(1);
        snapshot.setViewCount(10);
        snapshot.setScore(BigDecimal.TEN);
        snapshot.setSnapshotTime(LocalDateTime.now());
        return snapshot;
    }

    /**
     * 构造候选。
     *
     * @param threadId 帖子ID
     * @param boardId 版块ID
     * @return 候选
     */
    private HotCandidate candidate(Long threadId, Long boardId) {
        HotCandidate candidate = new HotCandidate();
        candidate.setId(threadId);
        candidate.setBoardId(boardId);
        candidate.setAuthorId(100L);
        candidate.setAnonymous(0);
        candidate.setTitle("标题" + threadId);
        candidate.setReplyCount(1);
        candidate.setLikeCount(1);
        candidate.setViewCount(10);
        candidate.setScore(BigDecimal.TEN);
        return candidate;
    }
}
