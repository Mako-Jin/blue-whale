package com.bluewhale.forum.service.impl;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.tenant.TenantContext;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.forum.common.ForumActor;
import com.bluewhale.forum.common.ForumErrorCodes;
import com.bluewhale.forum.common.ForumPermissions;
import com.bluewhale.forum.config.ForumProperties;
import com.bluewhale.forum.dto.ThreadCreateRequest;
import com.bluewhale.forum.dto.ThreadDetailVO;
import com.bluewhale.forum.dto.ThreadUpdateRequest;
import com.bluewhale.forum.entity.ForumBoard;
import com.bluewhale.forum.entity.ForumThread;
import com.bluewhale.forum.entity.ForumThreadEditHistory;
import com.bluewhale.forum.mapper.ForumThreadEditHistoryMapper;
import com.bluewhale.forum.mapper.ForumThreadMapper;
import com.bluewhale.forum.service.BanService;
import com.bluewhale.forum.service.BoardService;
import com.bluewhale.forum.service.SensitiveWordService;
import com.bluewhale.forum.service.ViewCountService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 帖子服务单测：审核状态机、楼层起点、编辑风控、软删编号释放。
 *
 * @author BlueWhale
 */
@ExtendWith(MockitoExtension.class)
class ThreadServiceImplTest {

    @Mock
    private ForumThreadMapper threadMapper;

    @Mock
    private ForumThreadEditHistoryMapper editHistoryMapper;

    @Mock
    private BoardService boardService;

    @Mock
    private SensitiveWordService sensitiveWordService;

    @Mock
    private BanService banService;

    @Mock
    private ViewCountService viewCountService;

    @Mock
    private ReactionStateReader reactionStateReader;

    @Spy
    private ForumProperties properties = new ForumProperties();

    @InjectMocks
    private ThreadServiceImpl threadService;

    @BeforeEach
    void setUp() {
        TenantContext.set(1L);
        // 默认关闭「新用户前 N 帖必审」，让用例只校验版块审核口径；
        // 「新用户必审」由 createByNewUserGoesPending 单独覆盖
        lenient().doReturn(0).when(properties).getNewUserAuditThreads();
        // 默认无命中：个别用例再覆盖成 BLOCK/PENDING
        lenient().when(sensitiveWordService.scan(anyString())).thenAnswer(invocation ->
                new SensitiveWordService.ScanResult(SensitiveWordService.ScanResult.NONE, null, -1,
                        invocation.getArgument(0)));
        lenient().when(threadMapper.selectCount(any())).thenReturn(0L);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("新用户前 N 帖必审：已发布 0 帖且阈值为 3 时落 PENDING")
    void createByNewUserGoesPending() {
        doReturn(3).when(properties).getNewUserAuditThreads();
        when(boardService.requirePostableBoard(anyLong(), any())).thenReturn(board(0));
        when(threadMapper.selectCount(any())).thenReturn(0L);
        when(threadMapper.insert(any(ForumThread.class))).thenReturn(1);

        ThreadDetailVO vo = threadService.create(request("新人的第一帖", "<p>正文</p>"), author());

        assertThat(vo.getStatus()).isEqualTo("PENDING");
        assertThat(vo.getPublishTime()).isNull();
    }

    @Test
    @DisplayName("管理者的首帖不受新用户必审限制")
    void managerBypassesNewUserAudit() {
        doReturn(3).when(properties).getNewUserAuditThreads();
        when(boardService.requirePostableBoard(anyLong(), any())).thenReturn(board(0));
        when(threadMapper.insert(any(ForumThread.class))).thenReturn(1);

        ThreadDetailVO vo = threadService.create(request("版主发帖", "<p>正文</p>"), manager());

        assertThat(vo.getStatus()).isEqualTo("PUBLISHED");
    }

    @Test
    @DisplayName("敏感词 BLOCK：拦截并留痕，不落库")
    void createBlockedBySensitiveWord() {
        when(boardService.requirePostableBoard(anyLong(), any())).thenReturn(board(0));
        when(sensitiveWordService.scan(anyString())).thenAnswer(invocation -> {
            String text = invocation.getArgument(0);
            boolean hit = text.contains("代开发票");
            return new SensitiveWordService.ScanResult(
                    hit ? SensitiveWordService.ScanResult.BLOCK : SensitiveWordService.ScanResult.NONE,
                    hit ? "代开发票" : null, hit ? 0 : -1, text);
        });

        assertThatThrownBy(() -> threadService.create(request("标题", "<p>代开发票找我</p>"), author()))
                .isInstanceOf(BizException.class)
                .satisfies(e -> assertThat(((BizException) e).getCode())
                        .isEqualTo(ForumErrorCodes.SENSITIVE_BLOCKED));
        verify(threadMapper, never()).insert(any(ForumThread.class));
        verify(sensitiveWordService, org.mockito.Mockito.atLeastOnce())
                .recordHit(anyString(), any(), anyLong(), any());
    }

    @Test
    @DisplayName("敏感词 PENDING：即便开放版块也转待审")
    void createByPendingWordGoesPending() {
        when(boardService.requirePostableBoard(anyLong(), any())).thenReturn(board(0));
        when(sensitiveWordService.scan(anyString())).thenAnswer(invocation -> {
            String text = invocation.getArgument(0);
            boolean hit = text.contains("刷单");
            return new SensitiveWordService.ScanResult(
                    hit ? SensitiveWordService.ScanResult.PENDING : SensitiveWordService.ScanResult.NONE,
                    hit ? "刷单" : null, hit ? 0 : -1, text);
        });
        when(threadMapper.insert(any(ForumThread.class))).thenReturn(1);

        ThreadDetailVO vo = threadService.create(request("标题", "<p>刷单兼职</p>"), author());

        assertThat(vo.getStatus()).isEqualTo("PENDING");
    }

    @Test
    @DisplayName("审核版块发帖落 PENDING 且无发布时间，回复从 2 楼起（1 楼是楼主）")
    void createInAuditBoardGoesPending() {
        when(boardService.requirePostableBoard(anyLong(), any())).thenReturn(board(1));
        when(threadMapper.selectCount(any())).thenReturn(0L);
        when(threadMapper.insert(any(ForumThread.class))).thenReturn(1);

        ThreadDetailVO vo = threadService.create(request("标题", "<p>正文</p>"), author());

        assertThat(vo.getStatus()).isEqualTo("PENDING");
        assertThat(vo.getPublishTime()).isNull();
        assertThat(vo.getNextFloor()).isEqualTo(2);
        assertThat(vo.getSummary()).isEqualTo("正文");

        ArgumentCaptor<ForumThread> captor = ArgumentCaptor.forClass(ForumThread.class);
        verify(threadMapper).insert(captor.capture());
        ForumThread saved = captor.getValue();
        assertThat(saved.getThreadNo()).startsWith("FM-");
        assertThat(saved.getAuthorId()).isEqualTo(100L);
        assertThat(saved.getNextFloor()).isEqualTo(2);
        assertThat(saved.getReplyCount()).isZero();
    }

    @Test
    @DisplayName("非审核版块发帖直接 PUBLISHED 并写发布时间")
    void createInOpenBoardPublishes() {
        when(boardService.requirePostableBoard(anyLong(), any())).thenReturn(board(0));
        when(threadMapper.selectCount(any())).thenReturn(0L);
        when(threadMapper.insert(any(ForumThread.class))).thenReturn(1);

        ThreadDetailVO vo = threadService.create(request("标题", "<p>正文</p>"), author());

        assertThat(vo.getStatus()).isEqualTo("PUBLISHED");
        assertThat(vo.getPublishTime()).isNotNull();
    }

    @Test
    @DisplayName("发帖间隔未到返回 30042")
    void createRejectedByPostInterval() {
        when(boardService.requirePostableBoard(anyLong(), any())).thenReturn(board(0));
        when(threadMapper.selectCount(any())).thenReturn(1L);

        assertThatThrownBy(() -> threadService.create(request("标题", "正文"), author()))
                .isInstanceOf(BizException.class)
                .satisfies(e -> assertThat(((BizException) e).getCode())
                        .isEqualTo(ForumErrorCodes.RATE_LIMITED));
        verify(threadMapper, never()).insert(any(ForumThread.class));
    }

    @Test
    @DisplayName("正文含 script 时 30030 拒绝，且不落库")
    void createRejectedByDangerousContent() {
        when(boardService.requirePostableBoard(anyLong(), any())).thenReturn(board(0));

        assertThatThrownBy(() -> threadService.create(request("标题", "<script>alert(1)</script>"), author()))
                .isInstanceOf(BizException.class)
                .satisfies(e -> assertThat(((BizException) e).getCode())
                        .isEqualTo(ForumErrorCodes.CONTENT_REJECTED));
        verify(threadMapper, never()).insert(any(ForumThread.class));
    }

    @Test
    @DisplayName("标题超配置上限返回 30002")
    void createRejectedByTitleLength() {
        properties.setMaxTitleLength(5);
        when(boardService.requirePostableBoard(anyLong(), any())).thenReturn(board(0));

        assertThatThrownBy(() -> threadService.create(request("一二三四五六", "正文"), author()))
                .isInstanceOf(BizException.class)
                .satisfies(e -> assertThat(((BizException) e).getCode())
                        .isEqualTo(ForumErrorCodes.TITLE_TOO_LONG));
    }

    @Test
    @DisplayName("审核版块内作者编辑已发布帖：退回 PENDING 并留原文快照")
    void updatePublishedThreadGoesBackToPending() {
        ForumThread thread = publishedThread();
        when(threadMapper.selectById(11L)).thenReturn(thread);
        when(boardService.requireVisibleBoard(anyLong(), any())).thenReturn(board(1));
        when(threadMapper.updateById(any(ForumThread.class))).thenReturn(1);
        when(editHistoryMapper.insert(any(ForumThreadEditHistory.class))).thenReturn(1);

        ThreadUpdateRequest request = new ThreadUpdateRequest();
        request.setTitle("改后标题");
        request.setContent("<p>改后正文</p>");
        request.setReason("措辞调整");
        ThreadDetailVO vo = threadService.update(11L, request, author());

        assertThat(vo.getStatus()).isEqualTo("PENDING");
        assertThat(vo.getPublishTime()).isNull();
        assertThat(vo.getTitle()).isEqualTo("改后标题");

        ArgumentCaptor<ForumThreadEditHistory> captor = ArgumentCaptor.forClass(ForumThreadEditHistory.class);
        verify(editHistoryMapper).insert(captor.capture());
        assertThat(captor.getValue().getContentBefore()).contains("原标题正文");
        assertThat(captor.getValue().getReason()).isEqualTo("措辞调整");
    }

    @Test
    @DisplayName("非作者且无治理权限编辑返回 30060")
    void updateByStrangerRejected() {
        when(threadMapper.selectById(11L)).thenReturn(publishedThread());
        when(boardService.requireVisibleBoard(anyLong(), any())).thenReturn(board(0));

        ThreadUpdateRequest request = new ThreadUpdateRequest();
        request.setTitle("改标题");

        assertThatThrownBy(() -> threadService.update(11L, request,
                new ForumActor(999L, Set.of(ForumPermissions.THREAD_EDIT), null)))
                .isInstanceOf(BizException.class)
                .satisfies(e -> assertThat(((BizException) e).getCode())
                        .isEqualTo(ForumErrorCodes.NOT_THREAD_OWNER));
    }

    @Test
    @DisplayName("作者超出编辑时间窗返回 30061")
    void updateAfterWindowRejected() {
        ForumThread thread = publishedThread();
        thread.setCreateTime(LocalDateTime.now().minusHours(2));
        when(threadMapper.selectById(11L)).thenReturn(thread);
        when(boardService.requireVisibleBoard(anyLong(), any())).thenReturn(board(0));

        ThreadUpdateRequest request = new ThreadUpdateRequest();
        request.setContent("改正文");

        assertThatThrownBy(() -> threadService.update(11L, request, author()))
                .isInstanceOf(BizException.class)
                .satisfies(e -> assertThat(((BizException) e).getCode())
                        .isEqualTo(ForumErrorCodes.EDIT_WINDOW_EXPIRED));
    }

    @Test
    @DisplayName("删帖先改写编号释放唯一键再逻辑删除")
    void deleteRewritesNoBeforeSoftDelete() {
        when(threadMapper.selectById(11L)).thenReturn(publishedThread());
        when(threadMapper.renameNoForDelete(anyLong(), anyString())).thenReturn(1);
        when(threadMapper.deleteById(11L)).thenReturn(1);

        threadService.delete(11L, author());

        verify(threadMapper).renameNoForDelete(11L, "FM-20261112-00011#D11");
        verify(threadMapper).deleteById(11L);
    }

    @Test
    @DisplayName("详情：去重窗口内不再累加浏览数")
    void detailSkipsViewWhenDeduplicated() {
        when(threadMapper.selectById(11L)).thenReturn(publishedThread());
        when(boardService.requireVisibleBoard(anyLong(), any())).thenReturn(board(0));
        when(viewCountService.touch(11L, 100L)).thenReturn(false);

        ThreadDetailVO vo = threadService.detail(11L, author());

        assertThat(vo.getViewCount()).isZero();
        verify(threadMapper, never()).increaseViewCount(11L);
    }

    @Test
    @DisplayName("详情：窗口过期时浏览 +1 并回填计数")
    void detailCountsViewWhenWindowExpired() {
        when(threadMapper.selectById(11L)).thenReturn(publishedThread());
        when(boardService.requireVisibleBoard(anyLong(), any())).thenReturn(board(0));
        when(viewCountService.touch(11L, 100L)).thenReturn(true);
        when(threadMapper.increaseViewCount(11L)).thenReturn(1);

        ThreadDetailVO vo = threadService.detail(11L, author());

        assertThat(vo.getViewCount()).isEqualTo(1);
        verify(threadMapper).increaseViewCount(11L);
    }

    @Test
    @DisplayName("详情：按互动记录装填投花/藏帖按钮态")
    void detailFillsReactionState() {
        when(threadMapper.selectById(11L)).thenReturn(publishedThread());
        when(boardService.requireVisibleBoard(anyLong(), any())).thenReturn(board(0));
        when(viewCountService.touch(11L, 100L)).thenReturn(false);
        when(reactionStateReader.typesOf(11L, 100L)).thenReturn(Set.of("LIKE"));

        ThreadDetailVO vo = threadService.detail(11L, author());

        assertThat(vo.getLiked()).isTrue();
        assertThat(vo.getCollected()).isFalse();
    }

    @Test
    @DisplayName("我的收藏：联查分页透传总数与当前页")
    void collectionsPassThroughPage() {
        Page<ForumThread> page = new Page<>(1, 20);
        page.setRecords(List.of(publishedThread()));
        page.setTotal(1);
        when(threadMapper.selectCollectedPage(any(), anyLong(), anyLong(), anyString())).thenReturn(page);

        var result = threadService.collections(1, 20, author());

        assertThat(result.getTotal()).isEqualTo(1);
        assertThat(result.getRecords()).hasSize(1);
        assertThat(result.getRecords().get(0).getId()).isEqualTo(11L);
    }

    /**
     * 构造版块。
     *
     * @param auditMode 审核模式
     * @return 版块
     */
    private ForumBoard board(int auditMode) {
        ForumBoard board = new ForumBoard();
        board.setId(9L);
        board.setTenantId(1L);
        board.setAuditMode(auditMode);
        board.setVisibility("ALL");
        board.setPostPolicy("OPEN");
        board.setStatus(1);
        return board;
    }

    /**
     * 构造发帖请求。
     *
     * @param title 标题
     * @param content 正文
     * @return 请求
     */
    private ThreadCreateRequest request(String title, String content) {
        ThreadCreateRequest request = new ThreadCreateRequest();
        request.setBoardId(9L);
        request.setTitle(title);
        request.setContent(content);
        return request;
    }

    /**
     * 构造作者操作者。
     *
     * @return 操作者
     */
    private ForumActor author() {
        return new ForumActor(100L, Set.of(ForumPermissions.BROWSE, ForumPermissions.THREAD_ADD,
                ForumPermissions.THREAD_EDIT, ForumPermissions.THREAD_REMOVE), null);
    }

    /**
     * 构造带审核权限的管理者（验证新用户必审的豁免分支）。
     *
     * @return 管理者操作者
     */
    private ForumActor manager() {
        return new ForumActor(200L, Set.of(ForumPermissions.BROWSE, ForumPermissions.THREAD_ADD,
                ForumPermissions.MANAGE_AUDIT), null);
    }

    /**
     * 构造已发布帖子。
     *
     * @return 帖子实体
     */
    private ForumThread publishedThread() {
        ForumThread thread = new ForumThread();
        thread.setId(11L);
        thread.setTenantId(1L);
        thread.setBoardId(9L);
        thread.setThreadNo("FM-20261112-00011");
        thread.setTitle("原标题");
        thread.setContent("<p>原标题正文</p>");
        thread.setSummary("原标题正文");
        thread.setAuthorId(100L);
        thread.setStatus("PUBLISHED");
        thread.setPinned(0);
        thread.setEssence(0);
        thread.setViewCount(0);
        thread.setReplyCount(0);
        thread.setLikeCount(0);
        thread.setNextFloor(3);
        thread.setPublishTime(LocalDateTime.now());
        thread.setCreateTime(LocalDateTime.now());
        return thread;
    }
}
