package com.bluewhale.video.application.service.impl;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.tenant.TenantContext;
import com.bluewhale.video.application.service.CatalogAppService;
import com.bluewhale.video.application.service.DiscoverAppService;
import com.bluewhale.video.application.support.PublicContentViewAssembler;
import com.bluewhale.video.application.view.FeedView;
import com.bluewhale.video.application.view.HomeView;
import com.bluewhale.video.application.view.PublicContentView;
import com.bluewhale.video.application.view.RankingView;
import com.bluewhale.video.config.VideoProperties;
import com.bluewhale.video.domain.model.Content;
import com.bluewhale.video.domain.model.ContentQuery;
import com.bluewhale.video.domain.model.ContentType;
import com.bluewhale.video.domain.model.ContentVisibility;
import com.bluewhale.video.domain.model.FeedCursor;
import com.bluewhale.video.domain.model.FeedQuery;
import com.bluewhale.video.domain.model.PublishState;
import com.bluewhale.video.domain.repository.ContentRepository;
import com.bluewhale.video.domain.repository.FeedCacheRepository;
import com.bluewhale.video.domain.repository.HomeCacheRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 发现应用服务实现（首页聚合与站内搜索）。
 *
 * <p>首页按租户缓存 60 秒并附加 0–10 秒随机抖动，内容发布 / 下架 / 版权下架时由
 * 内容服务主动失效；聚合与搜索只返回已发布、公开且版权有效的内容。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DiscoverAppServiceImpl implements DiscoverAppService {

    /** 首页缓存分区名 */
    private static final String HOME_SECTION = "HOME";

    /** 首页每种内容类型条数 */
    private static final long HOME_TOP = 8L;

    /** 首页缓存抖动上限（秒） */
    private static final int HOME_CACHE_JITTER_SECONDS = 10;

    /** 排序：最新 */
    private static final String SORT_LATEST = "LATEST";

    /** 排序：最热 */
    private static final String SORT_HOT = "HOT";

    /** feed 默认每批条数 */
    private static final long FEED_DEFAULT_SIZE = 10L;

    /** feed 每批最大条数 */
    private static final long FEED_MAX_SIZE = 50L;

    /** feed 缓存分区前缀（短视频类型固定） */
    private static final String FEED_SECTION_PREFIX = "SHORT_VIDEO:";

    /** 单页最大条数 */
    private static final long MAX_PAGE_SIZE = 200L;

    /** 默认每页条数 */
    private static final long DEFAULT_PAGE_SIZE = 20L;

    /** 榜单默认条数 */
    private static final long RANKING_DEFAULT_SIZE = 10L;

    private final ContentRepository contentRepository;

    private final HomeCacheRepository homeCacheRepository;

    private final FeedCacheRepository feedCacheRepository;

    private final CatalogAppService catalogAppService;

    private final PublicContentViewAssembler contentViewAssembler;

    private final VideoProperties videoProperties;

    private final ObjectMapper objectMapper;

    @Override
    public HomeView home(Long userId) {
        Long tenantId = TenantContext.requireId();
        Optional<String> cached = homeCacheRepository.find(tenantId, HOME_SECTION);
        if (cached.isPresent()) {
            try {
                return objectMapper.readValue(cached.get(), HomeView.class);
            } catch (Exception ex) {
                log.warn("首页缓存反序列化失败，回源重建: tenantId={}, error={}", tenantId, ex.getMessage());
            }
        }
        HomeView view = buildHome(userId);
        saveHome(tenantId, view);
        return view;
    }

    @Override
    public PageResult<PublicContentView> search(String keyword, long current, long size, Long userId) {
        long pageNo = Math.max(current, 1L);
        long pageSize = clampSize(size);
        String text = keyword == null ? "" : keyword.trim();
        if (text.isEmpty()) {
            return PageResult.of(List.of(), 0L, pageNo, pageSize);
        }
        ContentQuery query = publicQuery(null, pageNo, pageSize);
        query.setKeyword(text);
        List<Content> contents = contentRepository.page(query).stream()
                .filter(content -> content.visibleTo(userId, false)).toList();
        return PageResult.of(contentViewAssembler.assemble(contents),
                contentRepository.count(query), pageNo, pageSize);
    }

    @Override
    public FeedView shortFeed(String sort, Long categoryId, String cursor, long size, Long userId) {
        Long tenantId = TenantContext.requireId();
        String sortBy = resolveSort(sort);
        Long category = categoryId == null || categoryId <= 0 ? null : categoryId;
        long batchSize = clampFeedSize(size);
        FeedCursor parsed = resolveCursor(sortBy, cursor);
        String section = feedSection(sortBy, category, batchSize);
        if (parsed == null) {
            Optional<FeedView> cached = readFeedCache(tenantId, section);
            if (cached.isPresent()) {
                return cached.get();
            }
        }
        FeedView view = loadFeed(batchSize, sortBy, category, parsed, userId);
        if (parsed == null) {
            saveFeedCache(tenantId, section, view);
        }
        return view;
    }

    @Override
    public RankingView rankings(String type, long size, Long userId) {
        ContentType contentType = resolveType(type);
        long limit = clampRankingSize(size);
        RankingView view = new RankingView();
        view.setContentType(contentType == null ? null : contentType.name());
        view.setHot(rank(userId, contentType, SORT_HOT, limit));
        view.setLatest(rank(userId, contentType, SORT_LATEST, limit));
        return view;
    }

    /**
     * 按排序规则加载单个榜单。
     *
     * @param userId 当前用户 ID，可为 {@code null}
     * @param type 内容类型，{@code null} 表示跨类型
     * @param sortBy 排序标识
     * @param size 榜单条数
     * @return 用户侧视图列表
     */
    private List<PublicContentView> rank(Long userId, ContentType type, String sortBy, long size) {
        ContentQuery query = publicQuery(type, 1L, size);
        query.setSortBy(sortBy);
        List<Content> contents = contentRepository.page(query).stream()
                .filter(content -> content.visibleTo(userId, false)).toList();
        return contentViewAssembler.assemble(contents);
    }

    /**
     * 解析榜单内容类型，空白表示跨类型。
     *
     * @param type 类型文本
     * @return 内容类型；空白返回 {@code null}
     */
    private ContentType resolveType(String type) {
        if (type == null || type.isBlank()) {
            return null;
        }
        ContentType parsed = ContentType.parse(type);
        if (parsed == null) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "biz.video.application.leaderboard-content-type-invalid", "榜单内容类型非法");
        }
        return parsed;
    }

    /**
     * 限制榜单条数。
     *
     * @param size 请求条数
     * @return 归一化条数
     */
    private long clampRankingSize(long size) {
        return size <= 0 ? RANKING_DEFAULT_SIZE : Math.min(size, FEED_MAX_SIZE);
    }

    /**
     * 按游标加载一批 feed（多取 1 条用于判断是否还有下一页）。
     *
     * @param batchSize 本批条数
     * @param sortBy 排序标识
     * @param categoryId 分类 ID，可为 {@code null}
     * @param cursor 游标，{@code null} 表示首屏
     * @param userId 当前用户 ID，可为 {@code null}
     * @return feed 视图
     */
    private FeedView loadFeed(long batchSize, String sortBy, Long categoryId, FeedCursor cursor, Long userId) {
        FeedQuery query = new FeedQuery();
        query.setContentType(ContentType.SHORT_VIDEO);
        query.setCategoryId(categoryId);
        query.setSortBy(sortBy);
        query.setCursor(cursor);
        query.setLimit(batchSize + 1);
        List<Content> rows = contentRepository.feed(query);
        boolean hasMore = rows.size() > batchSize;
        List<Content> pageRows = hasMore ? rows.subList(0, (int) batchSize) : rows;
        List<Content> visibleRows = pageRows.stream().filter(content -> content.visibleTo(userId, false)).toList();
        FeedView view = new FeedView();
        view.setRecords(contentViewAssembler.assemble(visibleRows));
        view.setHasMore(hasMore);
        if (hasMore) {
            view.setNextCursor(cursorOf(sortBy, pageRows.get(pageRows.size() - 1)));
        }
        return view;
    }

    /**
     * 归一化排序标识，非法值回落为最新。
     *
     * @param sort 原始排序
     * @return LATEST / HOT
     */
    private String resolveSort(String sort) {
        return sort != null && SORT_HOT.equalsIgnoreCase(sort.trim()) ? SORT_HOT : SORT_LATEST;
    }

    /**
     * 限制 feed 每批条数。
     *
     * @param size 请求条数
     * @return 归一化条数
     */
    private long clampFeedSize(long size) {
        return size <= 0 ? FEED_DEFAULT_SIZE : Math.min(size, FEED_MAX_SIZE);
    }

    /**
     * 解析并校验游标，排序与锚点格式必须匹配。
     *
     * @param sortBy 排序标识
     * @param raw 原始游标
     * @return 游标；入参空白返回 {@code null}
     */
    private FeedCursor resolveCursor(String sortBy, String raw) {
        FeedCursor cursor = FeedCursor.parse(raw);
        if (cursor == null) {
            return null;
        }
        try {
            if (SORT_HOT.equals(sortBy)) {
                Double.parseDouble(cursor.getAnchor());
            } else {
                Long.parseLong(cursor.getAnchor());
            }
            return cursor;
        } catch (NumberFormatException ex) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "biz.video.application.invalid-feed-cursor-reload", "feed 游标非法，请重新加载");
        }
    }

    /**
     * 拼装 feed 缓存分区（类型 + 排序 + 分类 + 每批条数）。
     *
     * <p>首屏缓存包含内容条目，分区必须带上每批条数，否则先以 50 条写入的缓存
     * 会被 size=1 的请求命中，返回超出请求条数的记录。</p>
     *
     * @param sortBy 排序标识
     * @param categoryId 分类 ID，可为 {@code null}
     * @param batchSize 归一化后的每批条数
     * @return 缓存分区
     */
    private String feedSection(String sortBy, Long categoryId, long batchSize) {
        return FEED_SECTION_PREFIX + sortBy + ":" + (categoryId == null ? 0L : categoryId) + ":" + batchSize;
    }

    /**
     * 读取首屏缓存，反序列化失败按未命中处理。
     *
     * @param tenantId 租户 ID
     * @param section 缓存分区
     * @return feed 视图；未命中返回空
     */
    private Optional<FeedView> readFeedCache(Long tenantId, String section) {
        Optional<String> cached = feedCacheRepository.find(tenantId, section);
        if (cached.isEmpty()) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(cached.get(), FeedView.class));
        } catch (Exception ex) {
            log.warn("feed 缓存反序列化失败，回源重建: tenantId={}, section={}, error={}",
                    tenantId, section, ex.getMessage());
            return Optional.empty();
        }
    }

    /**
     * 写入首屏缓存，失败仅告警不影响主流程。
     *
     * @param tenantId 租户 ID
     * @param section 缓存分区
     * @param view feed 视图
     */
    private void saveFeedCache(Long tenantId, String section, FeedView view) {
        try {
            feedCacheRepository.save(tenantId, section, objectMapper.writeValueAsString(view),
                    Duration.ofSeconds(videoProperties.getFeedCacheSeconds()));
        } catch (Exception ex) {
            log.warn("feed 缓存写入失败，忽略: tenantId={}, section={}, error={}",
                    tenantId, section, ex.getMessage());
        }
    }

    /**
     * 由本批最后一条内容编码下一页游标。
     *
     * @param sortBy 排序标识
     * @param content 本批最后一条内容
     * @return 游标字符串
     */
    private String cursorOf(String sortBy, Content content) {
        if (SORT_HOT.equals(sortBy)) {
            BigDecimal score = content.getHotScore() == null ? BigDecimal.ZERO : content.getHotScore();
            return FeedCursor.of(score.toPlainString(), content.getId()).toValue();
        }
        LocalDateTime publishedAt = content.getPublishedAt() == null ? content.getCreateTime() : content.getPublishedAt();
        LocalDateTime anchorTime = publishedAt == null ? LocalDateTime.now() : publishedAt;
        long epochMillis = anchorTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        return FeedCursor.of(String.valueOf(epochMillis), content.getId()).toValue();
    }

    /**
     * 构造首页聚合数据。
     *
     * @param userId 当前用户 ID，可为 {@code null}
     * @return 首页视图
     */
    private HomeView buildHome(Long userId) {
        HomeView view = new HomeView();
        view.setShorts(latest(userId, ContentType.SHORT_VIDEO));
        view.setVideos(latest(userId, ContentType.VIDEO));
        view.setMovies(latest(userId, ContentType.MOVIE));
        view.setCategories(catalogAppService.categories());
        view.setTags(catalogAppService.tags());
        return view;
    }

    /**
     * 查询指定类型的最新公开内容。
     *
     * @param userId 当前用户 ID
     * @param type 内容类型
     * @return 用户侧视图列表
     */
    private List<PublicContentView> latest(Long userId, ContentType type) {
        ContentQuery query = publicQuery(type, 1L, HOME_TOP);
        List<Content> contents = contentRepository.page(query).stream()
                .filter(content -> content.visibleTo(userId, false)).toList();
        return contentViewAssembler.assemble(contents);
    }

    /**
     * 构造已发布且公开的查询条件。
     *
     * @param type 内容类型，可为 {@code null}
     * @param current 页码
     * @param size 每页条数
     * @return 查询条件
     */
    private ContentQuery publicQuery(ContentType type, long current, long size) {
        ContentQuery query = new ContentQuery();
        query.setContentType(type);
        query.setPublishState(PublishState.PUBLISHED);
        query.setVisibility(ContentVisibility.PUBLIC);
        query.setSortBy(SORT_LATEST);
        query.setCurrent(current);
        query.setSize(size);
        return query;
    }

    /**
     * 写入首页缓存，失败仅告警不影响主流程。
     *
     * @param tenantId 租户 ID
     * @param view 首页视图
     */
    private void saveHome(Long tenantId, HomeView view) {
        try {
            int ttl = videoProperties.getContentCacheSeconds()
                    + ThreadLocalRandom.current().nextInt(HOME_CACHE_JITTER_SECONDS + 1);
            homeCacheRepository.save(tenantId, HOME_SECTION,
                    objectMapper.writeValueAsString(view), Duration.ofSeconds(ttl));
        } catch (Exception ex) {
            log.warn("首页缓存写入失败，忽略: tenantId={}, error={}", tenantId, ex.getMessage());
        }
    }

    /**
     * 限制分页大小。
     *
     * @param size 请求条数
     * @return 归一化条数
     */
    private long clampSize(long size) {
        return size <= 0 ? DEFAULT_PAGE_SIZE : Math.min(size, MAX_PAGE_SIZE);
    }
}
