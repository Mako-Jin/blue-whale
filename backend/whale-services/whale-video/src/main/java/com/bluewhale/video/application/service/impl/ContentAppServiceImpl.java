package com.bluewhale.video.application.service.impl;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.tenant.TenantContext;
import com.bluewhale.video.application.command.ContentQueryCommand;
import com.bluewhale.video.application.command.ContentSaveCommand;
import com.bluewhale.video.application.service.ContentAppService;
import com.bluewhale.video.application.support.ContentAssembler;
import com.bluewhale.video.application.view.ContentDetailView;
import com.bluewhale.video.application.view.ContentView;
import com.bluewhale.video.application.view.OwnerContentView;
import com.bluewhale.video.application.view.PublicContentView;
import com.bluewhale.video.config.VideoProperties;
import com.bluewhale.video.domain.exception.VideoErrorCode;
import com.bluewhale.video.domain.model.Category;
import com.bluewhale.video.domain.model.Content;
import com.bluewhale.video.domain.model.ContentQuery;
import com.bluewhale.video.domain.model.ContentType;
import com.bluewhale.video.domain.model.ContentVisibility;
import com.bluewhale.video.domain.model.CopyrightState;
import com.bluewhale.video.domain.model.FileAsset;
import com.bluewhale.video.domain.model.PublishState;
import com.bluewhale.video.domain.model.Series;
import com.bluewhale.video.domain.model.Tag;
import com.bluewhale.video.domain.model.UploadRule;
import com.bluewhale.video.domain.model.WatchHistory;
import com.bluewhale.video.domain.repository.CategoryRepository;
import com.bluewhale.video.domain.repository.ContentFavoriteRepository;
import com.bluewhale.video.domain.repository.ContentLikeRepository;
import com.bluewhale.video.domain.repository.ContentRepository;
import com.bluewhale.video.domain.repository.ContentStreamRepository;
import com.bluewhale.video.domain.repository.CreatorRepository;
import com.bluewhale.video.domain.repository.DeclarationRepository;
import com.bluewhale.video.domain.repository.FeedCacheRepository;
import com.bluewhale.video.domain.repository.FileAssetPort;
import com.bluewhale.video.domain.repository.HomeCacheRepository;
import com.bluewhale.video.domain.repository.PlayableCacheRepository;
import com.bluewhale.video.domain.repository.SeriesRepository;
import com.bluewhale.video.domain.repository.TagRepository;
import com.bluewhale.video.domain.repository.WatchHistoryRepository;
import com.bluewhale.video.domain.service.VideoUploadPolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 内容应用服务实现。
 *
 * <p>用户创建内容前必须签署当前版本上传声明；视频文件经 {@code whale-file}
 * 元数据与 magic bytes 双重校验；发布 / 下架 / 版权下架后立即失效可播与首页缓存，
 * 保证用户侧可见性与播放能力即时变化。</p>
 *
 * @author BlueWhale
 */
@Service
@RequiredArgsConstructor
public class ContentAppServiceImpl implements ContentAppService {

    /** 首页缓存分区名 */
    private static final String HOME_SECTION = "HOME";

    /** 单页最大条数 */
    private static final long MAX_PAGE_SIZE = 200L;

    /** 默认每页条数 */
    private static final long DEFAULT_PAGE_SIZE = 20L;

    /** 文件头读取字节数（MP4 需读到 ftyp 偏移之后的 4 字节） */
    private static final int HEAD_BYTES = 12;

    /** 封面代理地址前缀（用于判断是否已设置封面） */
    private static final String DEFAULT_SORT = "LATEST";

    private final ContentRepository contentRepository;

    private final ContentStreamRepository contentStreamRepository;

    private final FileAssetPort fileAssetPort;

    private final DeclarationRepository declarationRepository;

    private final TagRepository tagRepository;

    private final CategoryRepository categoryRepository;

    private final CreatorRepository creatorRepository;

    private final SeriesRepository seriesRepository;

    private final ContentLikeRepository contentLikeRepository;

    private final ContentFavoriteRepository contentFavoriteRepository;

    private final WatchHistoryRepository watchHistoryRepository;

    private final PlayableCacheRepository playableCacheRepository;

    private final HomeCacheRepository homeCacheRepository;

    private final FeedCacheRepository feedCacheRepository;

    private final VideoProperties videoProperties;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ContentView create(ContentSaveCommand command, Long userId) {
        ContentType type = requireUserType(command.getContentType());
        requireDeclaration(userId);
        Content content = new Content();
        applyBase(content, command, userId, type);
        content.setVisibility(parseVisibility(command.getVisibility()));
        content.setPublishState(PublishState.DRAFT);
        content.setCopyrightState(CopyrightState.AUTHORIZED);
        content.setPlayCount(0L);
        content.setLikeCount(0L);
        content.setFavoriteCount(0L);
        content.setCommentCount(0L);
        content.setSortWeight(0);
        contentRepository.insert(content);
        contentRepository.replaceTags(content.getId(), command.getTagIds());
        if (command.isPublishNow()) {
            content.publish(maxDurationMs(type));
            contentRepository.update(content);
            evict(content.getId());
        }
        syncSeriesCounts(content.getSeriesId());
        return adminView(content);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ContentView update(Long id, ContentSaveCommand command, Long userId) {
        Content content = requireOwned(id, userId);
        Long previousSeriesId = content.getSeriesId();
        applyBase(content, command, userId, content.getContentType());
        if (command.getVisibility() != null && !command.getVisibility().isBlank()) {
            content.setVisibility(parseVisibility(command.getVisibility()));
        }
        contentRepository.update(content);
        contentRepository.replaceTags(id, command.getTagIds());
        evict(id);
        syncSeriesCounts(previousSeriesId, content.getSeriesId());
        return adminView(content);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id, Long userId) {
        Content content = requireOwned(id, userId);
        contentRepository.delete(content.getId());
        evict(content.getId());
        syncSeriesCounts(content.getSeriesId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ContentView publishByOwner(Long id, Long userId) {
        Content content = requireOwned(id, userId);
        content.publish(maxDurationMs(content.getContentType()));
        contentRepository.update(content);
        evict(content.getId());
        syncSeriesCounts(content.getSeriesId());
        return adminView(content);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ContentView offlineByOwner(Long id, Long userId) {
        Content content = requireOwned(id, userId);
        content.offline();
        contentRepository.update(content);
        evict(content.getId());
        syncSeriesCounts(content.getSeriesId());
        return adminView(content);
    }

    @Override
    public PageResult<OwnerContentView> myContent(ContentQueryCommand command, String contentType, Long userId) {
        ContentQuery query = toQuery(command);
        query.setUploaderId(userId);
        query.setContentType(ContentType.parse(contentType));
        return pageOwner(query);
    }

    @Override
    public ContentView ownerView(Long id, Long userId) {
        Content content = requireOwned(id, userId);
        return adminView(content);
    }

    @Override
    public ContentDetailView detail(Long id, Long userId) {
        Content content = contentRepository.findById(id)
                .filter(item -> item.visibleTo(userId, false))
                .orElseThrow(() -> VideoErrorCode.exception(VideoErrorCode.CONTENT_NOT_FOUND));
        ContentDetailView view = new ContentDetailView();
        PublicContentView base = ContentAssembler.toPublic(content, tagNames(content.getId()), null);
        view.setId(base.getId());
        view.setContentType(base.getContentType());
        view.setTitle(base.getTitle());
        view.setSubtitle(base.getSubtitle());
        view.setDescription(base.getDescription());
        view.setDurationMs(base.getDurationMs());
        view.setWidth(base.getWidth());
        view.setHeight(base.getHeight());
        view.setFormat(base.getFormat());
        view.setCategoryId(base.getCategoryId());
        view.setCreatorId(base.getCreatorId());
        view.setSeriesId(base.getSeriesId());
        view.setEpisodeNo(base.getEpisodeNo());
        view.setVisibility(base.getVisibility());
        view.setPlayCount(base.getPlayCount());
        view.setLikeCount(base.getLikeCount());
        view.setFavoriteCount(base.getFavoriteCount());
        view.setCommentCount(base.getCommentCount());
        view.setTags(base.getTags());
        view.setCoverUrl(base.getCoverUrl());
        view.setPlayable(base.isPlayable());
        view.setPublishedAt(base.getPublishedAt());
        view.setCommentable(content.commentable());
        view.setLiked(userId != null && contentLikeRepository.exists(userId, id));
        view.setFavorited(userId != null && contentFavoriteRepository.exists(userId, id));
        view.setProgressMs(progressOf(userId, id));
        view.setTicketUrl("/video/play/ticket");
        view.setCreatorName(creatorName(content.getCreatorId()));
        return view;
    }

    @Override
    public PageResult<PublicContentView> publicPage(ContentQueryCommand command, Long userId) {
        ContentQuery query = toQuery(command);
        query.setPublishState(PublishState.PUBLISHED);
        query.setVisibility(ContentVisibility.PUBLIC);
        return pagePublic(query, userId, false);
    }

    @Override
    public PageResult<ContentView> adminPage(ContentQueryCommand command) {
        ContentQuery query = toQuery(command);
        long current = Math.max(query.getCurrent(), 1L);
        long size = clampSize(query.getSize());
        query.setCurrent(current);
        query.setSize(size);
        List<Content> records = contentRepository.page(query);
        return PageResult.of(mapAdmin(records), contentRepository.count(query), current, size);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ContentView adminPublish(Long id) {
        Content content = requireContent(id);
        content.publish(maxDurationMs(content.getContentType()));
        contentRepository.update(content);
        evict(id);
        syncSeriesCounts(content.getSeriesId());
        return adminView(content);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ContentView adminOffline(Long id) {
        Content content = requireContent(id);
        content.offline();
        contentRepository.update(content);
        evict(id);
        syncSeriesCounts(content.getSeriesId());
        return adminView(content);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ContentView adminTakedown(Long id, String reason) {
        Content content = requireContent(id);
        content.takedown(reason);
        contentRepository.update(content);
        evict(id);
        syncSeriesCounts(content.getSeriesId());
        return adminView(content);
    }

    @Override
    public Long coverFileId(Long contentId, Long userId) {
        Content content = contentRepository.findById(contentId)
                .filter(item -> item.visibleTo(userId, false))
                .orElseThrow(() -> VideoErrorCode.exception(VideoErrorCode.CONTENT_NOT_FOUND));
        return content.getCoverFileId();
    }

    /**
     * 写入内容基础字段并校验视频文件。
     *
     * @param content 目标内容
     * @param command 保存命令
     * @param userId 上传者
     * @param type 内容类型
     */
    private void applyBase(Content content, ContentSaveCommand command, Long userId, ContentType type) {
        FileAsset video = requireAsset(command.getVideoFileId());
        byte[] head = contentStreamRepository.readHead(command.getVideoFileId(), TenantContext.requireId(), HEAD_BYTES);
        String format = VideoUploadPolicy.validate(video, head, videoMimeTypes(), rule(type));
        if (command.getCoverFileId() != null && command.getCoverFileId() > 0) {
            FileAsset cover = requireAsset(command.getCoverFileId());
            VideoUploadPolicy.validateImage(cover, imageMimeTypes());
            content.setCoverFileId(command.getCoverFileId());
        }
        content.setContentType(type);
        content.setTitle(command.getTitle());
        content.setSubtitle(command.getSubtitle());
        content.setDescription(command.getDescription());
        content.setVideoFileId(command.getVideoFileId());
        content.setDurationMs(command.getDurationMs() == null ? 0L : command.getDurationMs());
        content.setWidth(command.getWidth() == null ? 0 : command.getWidth());
        content.setHeight(command.getHeight() == null ? 0 : command.getHeight());
        content.setFormat(format);
        content.setSizeBytes(video.sizeBytes());
        content.setUploaderId(userId);
        content.setCreatorId(command.getCreatorId() == null ? 0L : command.getCreatorId());
        content.setCategoryId(resolveCategory(command.getCategoryId(), type));
        applySeries(content, command, type);
        content.applySource(resolveSource(command.getSource(), userId));
        content.assertSourceDeclared();
        if (content.getDurationMs() <= 0 || content.getDurationMs() > rule(type).maxDurationMs()) {
            throw VideoErrorCode.exception(VideoErrorCode.CONTENT_DURATION_INVALID);
        }
    }

    /**
     * 校验并写入系列与集数：同系列集数唯一，仅视频可入系列。
     *
     * @param content 目标内容
     * @param command 保存命令
     * @param type 内容类型
     */
    private void applySeries(Content content, ContentSaveCommand command, ContentType type) {
        Long seriesId = command.getSeriesId();
        if (seriesId == null || seriesId <= 0) {
            content.setSeriesId(0L);
            content.setEpisodeNo(0);
            return;
        }
        if (type != ContentType.VIDEO) {
            throw VideoErrorCode.exception(VideoErrorCode.SERIES_NOT_FOUND);
        }
        Series series = seriesRepository.findById(seriesId)
                .orElseThrow(() -> VideoErrorCode.exception(VideoErrorCode.SERIES_NOT_FOUND));
        Integer episodeNo = command.getEpisodeNo();
        if (episodeNo == null || episodeNo <= 0) {
            throw VideoErrorCode.exception(VideoErrorCode.EPISODE_NO_INVALID);
        }
        if (contentRepository.existsSeriesEpisode(series.getId(), episodeNo, content.getId())) {
            throw VideoErrorCode.exception(VideoErrorCode.EPISODE_CONFLICT);
        }
        content.setSeriesId(series.getId());
        content.setEpisodeNo(episodeNo);
    }

    /**
     * 刷新系列已发布集数（去重，忽略未入系列的 0 值）。
     *
     * @param seriesIds 系列 ID（可变参数，可为 {@code null}）
     */
    private void syncSeriesCounts(Long... seriesIds) {
        Set<Long> targets = new LinkedHashSet<>();
        for (Long seriesId : seriesIds) {
            if (seriesId != null && seriesId > 0) {
                targets.add(seriesId);
            }
        }
        for (Long seriesId : targets) {
            seriesRepository.updateEpisodeCount(seriesId, contentRepository.countPublishedEpisodes(seriesId));
        }
    }

    /**
     * 校验并读取文件资产。
     *
     * @param fileId 文件 ID
     * @return 文件资产
     */
    private FileAsset requireAsset(Long fileId) {
        Optional<FileAsset> asset = fileAssetPort.find(fileId, TenantContext.requireId());
        return asset.orElseThrow(() -> VideoErrorCode.exception(VideoErrorCode.UPLOAD_FILE_INVALID));
    }

    /**
     * 解析并校验内容来源：命令优先，其次取最近一次签署声明中的来源。
     *
     * @param value 命令中的来源
     * @param userId 用户 ID
     * @return 来源文本
     */
    private String resolveSource(String value, Long userId) {
        if (value != null && !value.isBlank()) {
            return value;
        }
        return declarationRepository.find(userId, videoProperties.getDeclarationVersion())
                .map(declaration -> declaration.getContentSource() == null ? null
                        : declaration.getContentSource().name())
                .orElse(null);
    }

    /**
     * 校验并返回分类 ID。
     *
     * @param categoryId 分类 ID
     * @param type 内容类型
     * @return 分类 ID，未指定返回 0
     */
    private Long resolveCategory(Long categoryId, ContentType type) {
        if (categoryId == null || categoryId <= 0) {
            return 0L;
        }
        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> VideoErrorCode.exception(VideoErrorCode.CATEGORY_NOT_FOUND));
        if (!category.appliesTo(type)) {
            throw VideoErrorCode.exception(VideoErrorCode.CATEGORY_INVALID);
        }
        return categoryId;
    }

    /**
     * 取内容类型的上传统计规则。
     *
     * @param type 内容类型
     * @return 规则
     */
    private UploadRule rule(ContentType type) {
        long seconds = type == ContentType.SHORT_VIDEO ? videoProperties.getShortMaxDurationSeconds()
                : type == ContentType.MOVIE ? videoProperties.getMovieMaxDurationSeconds()
                : videoProperties.getVideoMaxDurationSeconds();
        long bytes = type == ContentType.SHORT_VIDEO ? videoProperties.getShortMaxBytes()
                : type == ContentType.MOVIE ? videoProperties.getMovieMaxBytes()
                : videoProperties.getVideoMaxBytes();
        return new UploadRule(bytes, seconds * 1000L);
    }

    /**
     * 取内容类型的时长上限（毫秒）。
     *
     * @param type 内容类型
     * @return 时长上限
     */
    private long maxDurationMs(ContentType type) {
        return rule(type).maxDurationMs();
    }

    /**
     * 视频 MIME 白名单（小写）。
     *
     * @return MIME 集合
     */
    private java.util.Set<String> videoMimeTypes() {
        return lowerSet(videoProperties.getVideoFormats());
    }

    /**
     * 图片 MIME 白名单（小写）。
     *
     * @return MIME 集合
     */
    private java.util.Set<String> imageMimeTypes() {
        return lowerSet(videoProperties.getImageFormats());
    }

    /**
     * 转小写集合。
     *
     * @param values 原始值
     * @return 小写集合
     */
    private java.util.Set<String> lowerSet(List<String> values) {
        return values == null ? Collections.emptySet()
                : values.stream().map(item -> item.toLowerCase(Locale.ROOT)).collect(Collectors.toSet());
    }

    /**
     * 校验当前用户已签署声明。
     *
     * @param userId 用户 ID
     */
    private void requireDeclaration(Long userId) {
        if (declarationRepository.find(userId, videoProperties.getDeclarationVersion()).isEmpty()) {
            throw VideoErrorCode.exception(VideoErrorCode.DECLARATION_REQUIRED);
        }
    }

    /**
     * 校验用户可创建的内容类型（电影仅运营入库）。
     *
     * @param value 内容类型文本
     * @return 内容类型
     */
    private ContentType requireUserType(String value) {
        ContentType type = ContentType.parse(value);
        if (type == null) {
            throw VideoErrorCode.exception(VideoErrorCode.CONTENT_NOT_FOUND);
        }
        if (type == ContentType.MOVIE) {
            throw VideoErrorCode.exception(VideoErrorCode.CONTENT_NOT_OWNED);
        }
        return type;
    }

    /**
     * 解析可见性。
     *
     * @param value 可见性文本
     * @return 可见性
     */
    private ContentVisibility parseVisibility(String value) {
        return value == null || value.isBlank() ? ContentVisibility.PUBLIC : ContentVisibility.parse(value);
    }

    /**
     * 加载内容并按不存在处理。
     *
     * @param id 内容 ID
     * @return 内容
     */
    private Content requireContent(Long id) {
        return contentRepository.findById(id)
                .orElseThrow(() -> VideoErrorCode.exception(VideoErrorCode.CONTENT_NOT_FOUND));
    }

    /**
     * 加载内容并校验归属。
     *
     * <p>M5-1 越权口径：他人不可见的私密 / 未发布内容一律按不存在处理（40401），
     * 只有对当前用户可见的非本人内容才返回 40301，避免通过错误码探测资源存在性。</p>
     *
     * @param id 内容 ID
     * @param userId 当前用户 ID
     * @return 内容
     */
    private Content requireOwned(Long id, Long userId) {
        Content content = requireContent(id);
        if (!content.visibleTo(userId, false)) {
            throw VideoErrorCode.exception(VideoErrorCode.CONTENT_NOT_FOUND);
        }
        content.assertOwnedBy(userId);
        return content;
    }

    /**
     * 构造查询条件。
     *
     * @param command 查询命令
     * @return 查询条件
     */
    private ContentQuery toQuery(ContentQueryCommand command) {
        ContentQuery query = new ContentQuery();
        query.setContentType(ContentType.parse(command.getContentType()));
        query.setCategoryId(command.getCategoryId());
        query.setTagId(command.getTagId());
        query.setUploaderId(command.getUploaderId());
        query.setCreatorId(command.getCreatorId());
        query.setSeriesId(command.getSeriesId());
        query.setPublishState(command.getPublishState() == null ? null
                : PublishState.parse(command.getPublishState()));
        query.setVisibility(command.getVisibility() == null ? null
                : ContentVisibility.parse(command.getVisibility()));
        query.setKeyword(command.getKeyword());
        query.setSortBy(command.getSortBy() == null ? DEFAULT_SORT : command.getSortBy());
        query.setCurrent(command.getCurrent());
        query.setSize(command.getSize());
        return query;
    }

    /**
     * 分页查询并组装用户侧视图。
     *
     * @param query 查询条件
     * @param userId 当前用户 ID
     * @param includePrivate 是否包含私密作品（我的作品）
     * @return 用户侧分页
     */
    private PageResult<PublicContentView> pagePublic(ContentQuery query, Long userId, boolean includePrivate) {
        long current = Math.max(query.getCurrent(), 1L);
        long size = clampSize(query.getSize());
        query.setCurrent(current);
        query.setSize(size);
        List<Content> records = contentRepository.page(query);
        List<Content> visible = includePrivate ? records
                : records.stream().filter(item -> item.visibleTo(userId, false)).toList();
        return PageResult.of(mapPublic(visible, userId), contentRepository.count(query), current, size);
    }

    /**
     * 分页查询上传者作品并组装含发布 / 版权状态的视图。
     *
     * @param query 查询条件
     * @return 上传者侧分页
     */
    private PageResult<OwnerContentView> pageOwner(ContentQuery query) {
        long current = Math.max(query.getCurrent(), 1L);
        long size = clampSize(query.getSize());
        query.setCurrent(current);
        query.setSize(size);
        List<Content> records = contentRepository.page(query);
        return PageResult.of(mapOwner(records), contentRepository.count(query), current, size);
    }

    /**
     * 批量组装上传者侧视图（标签名称批量查询）。
     *
     * @param records 内容列表
     * @return 视图列表
     */
    private List<OwnerContentView> mapOwner(List<Content> records) {
        if (records.isEmpty()) {
            return Collections.emptyList();
        }
        Map<Long, List<Long>> tagMap = contentRepository.findTagIdsByContentIds(idsOf(records));
        Map<Long, String> tagNames = tagNameMap(tagMap.values());
        List<OwnerContentView> views = new ArrayList<>(records.size());
        for (Content content : records) {
            List<String> names = tagMap.getOrDefault(content.getId(), Collections.emptyList()).stream()
                    .map(tagNames::get).filter(name -> name != null).toList();
            views.add(ContentAssembler.toOwner(content, names));
        }
        return views;
    }

    /**
     * 批量组装用户侧视图（标签名称批量查询）。
     *
     * @param records 内容列表
     * @param userId 当前用户 ID
     * @return 视图列表
     */
    private List<PublicContentView> mapPublic(List<Content> records, Long userId) {
        if (records.isEmpty()) {
            return Collections.emptyList();
        }
        Map<Long, List<Long>> tagMap = contentRepository.findTagIdsByContentIds(idsOf(records));
        Map<Long, String> tagNames = tagNameMap(tagMap.values());
        List<PublicContentView> views = new ArrayList<>(records.size());
        for (Content content : records) {
            List<String> names = tagMap.getOrDefault(content.getId(), Collections.emptyList()).stream()
                    .map(tagNames::get).filter(name -> name != null).toList();
            views.add(ContentAssembler.toPublic(content, names, null));
        }
        return views;
    }

    /**
     * 批量组装运营侧视图。
     *
     * @param records 内容列表
     * @return 视图列表
     */
    private List<ContentView> mapAdmin(List<Content> records) {
        if (records.isEmpty()) {
            return Collections.emptyList();
        }
        Map<Long, List<Long>> tagMap = contentRepository.findTagIdsByContentIds(idsOf(records));
        return records.stream()
                .map(content -> ContentAssembler.toAdmin(content,
                        tagMap.getOrDefault(content.getId(), Collections.emptyList())))
                .toList();
    }

    /**
     * 组装单个运营侧视图。
     *
     * @param content 内容
     * @return 视图
     */
    private ContentView adminView(Content content) {
        return ContentAssembler.toAdmin(content, contentRepository.findTagIds(content.getId()));
    }

    /**
     * 查询内容标签名称。
     *
     * @param contentId 内容 ID
     * @return 标签名称列表
     */
    private List<String> tagNames(Long contentId) {
        return tagNameMap(List.of(contentRepository.findTagIds(contentId))).values().stream().toList();
    }

    /**
     * 批量把标签 ID 映射为名称。
     *
     * @param idLists 标签 ID 集合
     * @return 标签 ID → 名称
     */
    private Map<Long, String> tagNameMap(Collection<List<Long>> idLists) {
        List<Long> ids = idLists.stream().flatMap(List::stream).distinct().toList();
        if (ids.isEmpty()) {
            return Collections.emptyMap();
        }
        return tagRepository.findByIds(ids).stream().collect(Collectors.toMap(Tag::getId, Tag::getName));
    }

    /**
     * 查询创作者昵称。
     *
     * @param creatorId 创作者 ID
     * @return 昵称；未登记返回 {@code null}
     */
    private String creatorName(Long creatorId) {
        if (creatorId == null || creatorId <= 0) {
            return null;
        }
        return creatorRepository.findById(creatorId).map(creator -> creator.getDisplayName()).orElse(null);
    }

    /**
     * 查询观看进度。
     *
     * @param userId 用户 ID
     * @param contentId 内容 ID
     * @return 进度毫秒；未观看返回 0
     */
    private Long progressOf(Long userId, Long contentId) {
        if (userId == null) {
            return 0L;
        }
        return watchHistoryRepository.find(userId, contentId).map(WatchHistory::getProgressMs).orElse(0L);
    }

    /**
     * 提取内容 ID 集合。
     *
     * @param records 内容列表
     * @return 内容 ID 集合
     */
    private List<Long> idsOf(List<Content> records) {
        return records.stream().map(Content::getId).toList();
    }

    /**
     * 失效可播、首页与 feed 首屏缓存。
     *
     * @param contentId 内容 ID
     */
    private void evict(Long contentId) {
        playableCacheRepository.evict(contentId);
        homeCacheRepository.evict(TenantContext.get(), HOME_SECTION);
        feedCacheRepository.evictAll(TenantContext.get());
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
