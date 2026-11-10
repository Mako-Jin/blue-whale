package com.bluewhale.video.application.service.impl;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.tenant.TenantContext;
import com.bluewhale.video.application.service.CatalogAppService;
import com.bluewhale.video.application.support.PublicContentViewAssembler;
import com.bluewhale.video.application.view.FeedView;
import com.bluewhale.video.application.view.PublicContentView;
import com.bluewhale.video.application.view.RankingView;
import com.bluewhale.video.config.VideoProperties;
import com.bluewhale.video.domain.model.Content;
import com.bluewhale.video.domain.model.ContentQuery;
import com.bluewhale.video.domain.model.ContentType;
import com.bluewhale.video.domain.model.ContentVisibility;
import com.bluewhale.video.domain.model.CopyrightState;
import com.bluewhale.video.domain.model.FeedCursor;
import com.bluewhale.video.domain.model.FeedQuery;
import com.bluewhale.video.domain.model.PublishState;
import com.bluewhale.video.domain.repository.ContentRepository;
import com.bluewhale.video.domain.repository.FeedCacheRepository;
import com.bluewhale.video.domain.repository.HomeCacheRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 短视频 feed 键集分页测试（M2-2）：首屏缓存、游标续读、排序与参数归一化。
 *
 * @author BlueWhale
 */
@ExtendWith(MockitoExtension.class)
class DiscoverAppServiceImplTest {

    /** 测试租户 ID */
    private static final long TENANT_ID = 1L;

    /** 测试用户 ID */
    private static final long USER_ID = 7L;

    /** 最新流 size=5 的首屏缓存分区 */
    private static final String LATEST_SECTION_5 = "SHORT_VIDEO:LATEST:0:5";

    /** 最新流 size=3 的首屏缓存分区 */
    private static final String LATEST_SECTION_3 = "SHORT_VIDEO:LATEST:0:3";

    /** 热门流 size=2 的首屏缓存分区 */
    private static final String HOT_SECTION_2 = "SHORT_VIDEO:HOT:0:2";

    /** 首页最新流锚点时间 */
    private static final LocalDateTime LATEST_TIME = LocalDateTime.of(2026, 1, 1, 10, 0);

    private final ObjectMapper objectMapper = JsonMapper.builder().build();

    private final VideoProperties videoProperties = new VideoProperties();

    @Mock
    private ContentRepository contentRepository;

    @Mock
    private HomeCacheRepository homeCacheRepository;

    @Mock
    private FeedCacheRepository feedCacheRepository;

    @Mock
    private CatalogAppService catalogAppService;

    @Mock
    private PublicContentViewAssembler contentViewAssembler;

    private DiscoverAppServiceImpl discoverAppService;

    /**
     * 初始化租户上下文与被测服务。
     */
    @BeforeEach
    void setUp() {
        TenantContext.set(TENANT_ID);
        discoverAppService = new DiscoverAppServiceImpl(contentRepository, homeCacheRepository, feedCacheRepository,
                catalogAppService, contentViewAssembler, videoProperties, objectMapper);
    }

    /**
     * 清理租户上下文。
     */
    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    /**
     * 首屏命中缓存时直接返回，不回表。
     *
     * @throws Exception 序列化缓存失败
     */
    @Test
    void shouldReturnCachedFirstPage() throws Exception {
        FeedView cached = new FeedView();
        cached.setHasMore(true);
        cached.setNextCursor("1712345678901|9");
        when(feedCacheRepository.find(TENANT_ID, LATEST_SECTION_5))
                .thenReturn(Optional.of(objectMapper.writeValueAsString(cached)));
        FeedView result = discoverAppService.shortFeed(null, null, null, 5, USER_ID);
        assertTrue(result.isHasMore());
        assertEquals("1712345678901|9", result.getNextCursor());
        assertTrue(result.getRecords().isEmpty());
        verifyNoInteractions(contentRepository);
    }

    /**
     * 首屏未命中时回表、多取 1 条判断下一页并写回 30 秒缓存。
     */
    @Test
    void shouldLoadFirstPageAndCache() {
        when(contentRepository.feed(any(FeedQuery.class))).thenReturn(List.of(
                content(101L, LATEST_TIME),
                content(102L, LATEST_TIME.minusMinutes(1)),
                content(103L, LATEST_TIME.minusMinutes(2)),
                content(104L, LATEST_TIME.minusMinutes(3))));
        when(contentViewAssembler.assemble(anyList())).thenReturn(List.of(view(101L), view(102L), view(103L)));
        FeedView result = discoverAppService.shortFeed(" latest ", null, null, 3, USER_ID);
        assertTrue(result.isHasMore());
        assertEquals(3, result.getRecords().size());
        assertEquals(epochMillis(LATEST_TIME.minusMinutes(2)) + "|103", result.getNextCursor());
        ArgumentCaptor<FeedQuery> captor = ArgumentCaptor.forClass(FeedQuery.class);
        verify(contentRepository).feed(captor.capture());
        FeedQuery query = captor.getValue();
        assertEquals(ContentType.SHORT_VIDEO, query.getContentType());
        assertEquals("LATEST", query.getSortBy());
        assertNull(query.getCursor());
        assertEquals(4L, query.getLimit());
        verify(feedCacheRepository).save(eq(TENANT_ID), eq(LATEST_SECTION_3), anyString(),
                eq(Duration.ofSeconds(30)));
    }

    /**
     * 带游标续读时不再读写首屏缓存，并在不足一页时返回 {@code hasMore=false}。
     */
    @Test
    void shouldContinueWithCursorWithoutCache() {
        FeedCursor cursor = FeedCursor.of("1712345678901", 900L);
        when(contentRepository.feed(any(FeedQuery.class))).thenReturn(List.of(
                content(901L, LATEST_TIME), content(902L, LATEST_TIME.minusMinutes(1))));
        when(contentViewAssembler.assemble(anyList())).thenReturn(List.of(view(901L), view(902L)));
        FeedView result = discoverAppService.shortFeed("LATEST", 0L, cursor.toValue(), 2, USER_ID);
        assertFalse(result.isHasMore());
        assertNull(result.getNextCursor());
        ArgumentCaptor<FeedQuery> captor = ArgumentCaptor.forClass(FeedQuery.class);
        verify(contentRepository).feed(captor.capture());
        assertEquals(900L, captor.getValue().getCursor().getId());
        assertNull(captor.getValue().getCategoryId());
        verify(feedCacheRepository, never()).find(eq(TENANT_ID), anyString());
        verify(feedCacheRepository, never()).save(any(), anyString(), anyString(), any());
    }

    /**
     * 非法游标返回参数错误（400）。
     */
    @Test
    void shouldRejectMalformedCursor() {
        BizException exception = assertThrows(BizException.class,
                () -> discoverAppService.shortFeed("LATEST", null, "not-a-cursor", 2, USER_ID));
        assertEquals(ErrorCode.PARAM_ERROR.getCode(), exception.getCode());
    }

    /**
     * 游标格式与排序不匹配（最新流收到热度小数锚点）返回参数错误。
     */
    @Test
    void shouldRejectCursorFromOtherSort() {
        BizException exception = assertThrows(BizException.class,
                () -> discoverAppService.shortFeed("LATEST", null, "0.00|9", 2, USER_ID));
        assertEquals(ErrorCode.PARAM_ERROR.getCode(), exception.getCode());
    }

    /**
     * 热门流按热度分排序，下一页游标由热度分与内容 ID 组成。
     */
    @Test
    void shouldUseHotScoreAsCursor() {
        Content first = content(201L, LATEST_TIME);
        first.setHotScore(new BigDecimal("12.50"));
        Content second = content(202L, LATEST_TIME.minusMinutes(1));
        second.setHotScore(new BigDecimal("8.00"));
        Content third = content(203L, LATEST_TIME.minusMinutes(2));
        third.setHotScore(new BigDecimal("5.00"));
        when(contentRepository.feed(any(FeedQuery.class))).thenReturn(List.of(first, second, third));
        when(contentViewAssembler.assemble(anyList())).thenReturn(List.of(view(201L), view(202L)));
        FeedView result = discoverAppService.shortFeed("HOT", null, null, 2, USER_ID);
        assertTrue(result.isHasMore());
        assertEquals("8.00|202", result.getNextCursor());
        verify(feedCacheRepository).save(eq(TENANT_ID), eq(HOT_SECTION_2), anyString(), any());
    }

    /**
     * 默认条数为 10、上限为 50；分类 0 归一化为不筛选。
     */
    @Test
    void shouldClampSizeAndNormalizeCategory() {
        when(contentRepository.feed(any(FeedQuery.class))).thenReturn(List.of());
        when(contentViewAssembler.assemble(anyList())).thenReturn(List.of());
        discoverAppService.shortFeed(null, 0L, null, 0, USER_ID);
        ArgumentCaptor<FeedQuery> captor = ArgumentCaptor.forClass(FeedQuery.class);
        verify(contentRepository).feed(captor.capture());
        assertEquals(11L, captor.getValue().getLimit());
        assertNull(captor.getValue().getCategoryId());
    }

    /**
     * 超过上限的条数截断为 50，并保留分类筛选。
     */
    @Test
    void shouldCapSizeAtFifty() {
        when(contentRepository.feed(any(FeedQuery.class))).thenReturn(List.of());
        when(contentViewAssembler.assemble(anyList())).thenReturn(List.of());
        discoverAppService.shortFeed("LATEST", 3L, null, 100, USER_ID);
        ArgumentCaptor<FeedQuery> captor = ArgumentCaptor.forClass(FeedQuery.class);
        verify(contentRepository).feed(captor.capture());
        assertEquals(51L, captor.getValue().getLimit());
        assertEquals(3L, captor.getValue().getCategoryId());
        verify(feedCacheRepository).save(eq(TENANT_ID), eq("SHORT_VIDEO:LATEST:3:50"), anyString(), any());
    }

    /**
     * 空结果首屏返回稳定空视图且不写下一页游标。
     */
    @Test
    void shouldReturnStableEmptyFeed() {
        when(contentRepository.feed(any(FeedQuery.class))).thenReturn(List.of());
        when(contentViewAssembler.assemble(anyList())).thenReturn(List.of());
        FeedView result = discoverAppService.shortFeed(null, null, null, 5, USER_ID);
        assertTrue(result.getRecords().isEmpty());
        assertFalse(result.isHasMore());
        assertNull(result.getNextCursor());
    }

    /**
     * 榜单同时返回热门榜与新片榜，并携带请求的内容类型。
     */
    @Test
    void shouldReturnHotAndLatestRankings() {
        when(contentRepository.page(any(ContentQuery.class))).thenReturn(List.of(content(301L, LATEST_TIME)));
        when(contentViewAssembler.assemble(anyList())).thenReturn(List.of(view(301L)));
        RankingView result = discoverAppService.rankings("movie", 3L, USER_ID);
        assertEquals(ContentType.MOVIE.name(), result.getContentType());
        assertEquals(1, result.getHot().size());
        assertEquals(1, result.getLatest().size());
        ArgumentCaptor<ContentQuery> captor = ArgumentCaptor.forClass(ContentQuery.class);
        verify(contentRepository, org.mockito.Mockito.times(2)).page(captor.capture());
        List<ContentQuery> queries = captor.getAllValues();
        assertEquals(ContentType.MOVIE, queries.get(0).getContentType());
        assertEquals("HOT", queries.get(0).getSortBy());
        assertEquals(ContentType.MOVIE, queries.get(1).getContentType());
        assertEquals("LATEST", queries.get(1).getSortBy());
        assertEquals(3L, queries.get(0).getSize());
        assertEquals(1L, queries.get(0).getCurrent());
    }

    /**
     * 条数为 0 时回落默认 10 条，超过上限截断为 50 条。
     */
    @Test
    void shouldClampRankingSize() {
        when(contentRepository.page(any(ContentQuery.class))).thenReturn(List.of());
        when(contentViewAssembler.assemble(anyList())).thenReturn(List.of());
        discoverAppService.rankings("SHORT_VIDEO", 0L, USER_ID);
        discoverAppService.rankings("SHORT_VIDEO", 100L, USER_ID);
        ArgumentCaptor<ContentQuery> captor = ArgumentCaptor.forClass(ContentQuery.class);
        verify(contentRepository, org.mockito.Mockito.times(4)).page(captor.capture());
        List<ContentQuery> queries = captor.getAllValues();
        assertEquals(10L, queries.get(0).getSize());
        assertEquals(10L, queries.get(1).getSize());
        assertEquals(50L, queries.get(2).getSize());
        assertEquals(50L, queries.get(3).getSize());
    }

    /**
     * 内容类型为空时跨类型取榜，视图不携带类型。
     */
    @Test
    void shouldRankAcrossTypesWhenTypeBlank() {
        when(contentRepository.page(any(ContentQuery.class))).thenReturn(List.of());
        when(contentViewAssembler.assemble(anyList())).thenReturn(List.of());
        RankingView result = discoverAppService.rankings("  ", 5L, USER_ID);
        assertNull(result.getContentType());
        ArgumentCaptor<ContentQuery> captor = ArgumentCaptor.forClass(ContentQuery.class);
        verify(contentRepository, org.mockito.Mockito.times(2)).page(captor.capture());
        assertNull(captor.getAllValues().get(0).getContentType());
        assertNull(captor.getAllValues().get(1).getContentType());
    }

    /**
     * 非法内容类型返回参数错误（400），且不触发查询。
     */
    @Test
    void shouldRejectInvalidRankingType() {
        BizException exception = assertThrows(BizException.class,
                () -> discoverAppService.rankings("unknown", 10L, USER_ID));
        assertEquals(ErrorCode.PARAM_ERROR.getCode(), exception.getCode());
        verifyNoInteractions(contentRepository);
    }

    /**
     * 构造已发布、公开且版权有效的内容。
     *
     * @param id 内容 ID
     * @param publishedAt 上架时间
     * @return 内容
     */
    private Content content(long id, LocalDateTime publishedAt) {
        Content content = new Content();
        content.setId(id);
        content.setContentType(ContentType.SHORT_VIDEO);
        content.setTitle("标题" + id);
        content.setDurationMs(1000L);
        content.setPublishState(PublishState.PUBLISHED);
        content.setCopyrightState(CopyrightState.AUTHORIZED);
        content.setVisibility(ContentVisibility.PUBLIC);
        content.setPublishedAt(publishedAt);
        return content;
    }

    /**
     * 构造用户侧内容视图。
     *
     * @param id 内容 ID
     * @return 视图
     */
    private PublicContentView view(long id) {
        PublicContentView view = new PublicContentView();
        view.setId(id);
        view.setTitle("标题" + id);
        return view;
    }

    /**
     * 计算上架时间的毫秒时间戳（与游标编码一致）。
     *
     * @param time 上架时间
     * @return 毫秒时间戳
     */
    private long epochMillis(LocalDateTime time) {
        return time.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }
}
