package com.bluewhale.video.application.service.impl;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.tenant.TenantContext;
import com.bluewhale.video.application.command.MovieQueryCommand;
import com.bluewhale.video.application.command.MovieSaveCommand;
import com.bluewhale.video.application.service.MovieAppService;
import com.bluewhale.video.application.support.ContentAssembler;
import com.bluewhale.video.application.support.MovieViewAssembler;
import com.bluewhale.video.application.support.PublicContentViewAssembler;
import com.bluewhale.video.application.view.ContentDetailView;
import com.bluewhale.video.application.view.MovieAdminView;
import com.bluewhale.video.application.view.MovieCollectionView;
import com.bluewhale.video.application.view.MovieDetailView;
import com.bluewhale.video.application.view.MovieView;
import com.bluewhale.video.application.view.PublicContentView;
import com.bluewhale.video.config.VideoProperties;
import com.bluewhale.video.domain.exception.VideoErrorCode;
import com.bluewhale.video.domain.model.Category;
import com.bluewhale.video.domain.model.Content;
import com.bluewhale.video.domain.model.ContentQuery;
import com.bluewhale.video.domain.model.ContentType;
import com.bluewhale.video.domain.model.ContentVisibility;
import com.bluewhale.video.domain.model.CopyrightSource;
import com.bluewhale.video.domain.model.CopyrightState;
import com.bluewhale.video.domain.model.FileAsset;
import com.bluewhale.video.domain.model.Movie;
import com.bluewhale.video.domain.model.MovieCollection;
import com.bluewhale.video.domain.model.MovieLicenseSource;
import com.bluewhale.video.domain.model.PublishState;
import com.bluewhale.video.domain.model.Tag;
import com.bluewhale.video.domain.model.UploadRule;
import com.bluewhale.video.domain.model.WatchHistory;
import com.bluewhale.video.domain.repository.CategoryRepository;
import com.bluewhale.video.domain.repository.ContentFavoriteRepository;
import com.bluewhale.video.domain.repository.ContentLikeRepository;
import com.bluewhale.video.domain.repository.ContentRepository;
import com.bluewhale.video.domain.repository.ContentStreamRepository;
import com.bluewhale.video.domain.repository.CreatorRepository;
import com.bluewhale.video.domain.repository.FeedCacheRepository;
import com.bluewhale.video.domain.repository.FileAssetPort;
import com.bluewhale.video.domain.repository.HomeCacheRepository;
import com.bluewhale.video.domain.repository.MovieCollectionRepository;
import com.bluewhale.video.domain.repository.MovieRepository;
import com.bluewhale.video.domain.repository.PlayableCacheRepository;
import com.bluewhale.video.domain.repository.TagRepository;
import com.bluewhale.video.domain.repository.WatchHistoryRepository;
import com.bluewhale.video.domain.service.VideoUploadPolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 电影应用服务实现。
 *
 * <p>电影内容复用 {@code vd_content}（类型 MOVIE、上传者为 0、公开可见、草稿起步），
 * 元数据写入 {@code vd_movie}；授权到期时间同步到 {@code copyright_expire_at}，
 * 由内容聚合统一阻断播放，并由定时任务在下线后主动失效缓存。</p>
 *
 * @author BlueWhale
 */
@Service
@RequiredArgsConstructor
public class MovieAppServiceImpl implements MovieAppService {

    /** 首页缓存分区名 */
    private static final String HOME_SECTION = "HOME";

    /** 排序：最新 */
    private static final String SORT_LATEST = "LATEST";

    /** 单页最大条数 */
    private static final long MAX_PAGE_SIZE = 200L;

    /** 默认每页条数 */
    private static final long DEFAULT_PAGE_SIZE = 20L;

    /** 文件头读取字节数（MP4 需读到 ftyp 偏移之后的 4 字节） */
    private static final int HEAD_BYTES = 12;

    /** 播放票据申请地址 */
    private static final String TICKET_URL = "/video/v1/play/ticket";

    /** 电影详情最多展示的片单条数 */
    private static final int RELATED_COLLECTION_LIMIT = 6;

    private final ContentRepository contentRepository;

    private final ContentStreamRepository contentStreamRepository;

    private final MovieRepository movieRepository;

    private final MovieCollectionRepository movieCollectionRepository;

    private final FileAssetPort fileAssetPort;

    private final CategoryRepository categoryRepository;

    private final CreatorRepository creatorRepository;

    private final TagRepository tagRepository;

    private final ContentLikeRepository contentLikeRepository;

    private final ContentFavoriteRepository contentFavoriteRepository;

    private final WatchHistoryRepository watchHistoryRepository;

    private final PlayableCacheRepository playableCacheRepository;

    private final HomeCacheRepository homeCacheRepository;

    private final FeedCacheRepository feedCacheRepository;

    private final PublicContentViewAssembler contentViewAssembler;

    private final VideoProperties videoProperties;

    @Override
    public PageResult<MovieAdminView> adminPage(MovieQueryCommand command) {
        ContentQuery query = toQuery(command);
        long current = Math.max(query.getCurrent(), 1L);
        long size = clampSize(query.getSize());
        query.setCurrent(current);
        query.setSize(size);
        List<Content> records = contentRepository.page(query);
        return PageResult.of(mapAdmin(records), contentRepository.count(query), current, size);
    }

    @Override
    public MovieAdminView adminDetail(Long id) {
        Content content = requireMovie(id);
        Movie movie = requireMetadata(id);
        return toAdminView(content, movie, contentRepository.findTagIds(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MovieAdminView create(MovieSaveCommand command) {
        Movie movie = new Movie();
        applyMeta(movie, command);
        movie.applyLicense(command.getLicenseSource(), command.getLicenseNo(),
                command.getLicenseExpireAt(), LocalDateTime.now());
        Content content = buildContent(command, movie);
        contentRepository.insert(content);
        movie.setContentId(content.getId());
        applyPoster(movie, content, command.getPosterFileId());
        applyBackdrop(movie, command.getBackdropFileId());
        movieRepository.insert(movie);
        return toAdminView(content, movie, Collections.emptyList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MovieAdminView update(Long id, MovieSaveCommand command) {
        Content content = requireMovie(id);
        Movie movie = requireMetadata(id);
        applyMeta(movie, command);
        movie.applyLicense(command.getLicenseSource(), command.getLicenseNo(),
                command.getLicenseExpireAt(), LocalDateTime.now());
        applyEditableFields(content, command);
        applyPoster(movie, content, command.getPosterFileId());
        applyBackdrop(movie, command.getBackdropFileId());
        content.setCopyrightExpireAt(movie.getLicenseExpireAt());
        content.setSource(copyrightSourceOf(movie.getLicenseSource()));
        contentRepository.update(content);
        movieRepository.update(movie);
        evict(id);
        return toAdminView(content, movie, contentRepository.findTagIds(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        Content content = requireMovie(id);
        contentRepository.delete(content.getId());
        movieRepository.delete(content.getId());
        evict(content.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MovieAdminView publish(Long id) {
        Content content = requireMovie(id);
        Movie movie = requireMetadata(id);
        movie.assertLicenseValid(LocalDateTime.now());
        content.publish(movieMaxDurationMs());
        contentRepository.update(content);
        evict(id);
        return toAdminView(content, movie, contentRepository.findTagIds(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MovieAdminView offline(Long id) {
        Content content = requireMovie(id);
        Movie movie = requireMetadata(id);
        content.offline();
        contentRepository.update(content);
        evict(id);
        return toAdminView(content, movie, contentRepository.findTagIds(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MovieAdminView takedown(Long id, String reason) {
        Content content = requireMovie(id);
        Movie movie = requireMetadata(id);
        content.takedown(reason);
        contentRepository.update(content);
        evict(id);
        return toAdminView(content, movie, contentRepository.findTagIds(id));
    }

    @Override
    public PageResult<MovieView> publicPage(MovieQueryCommand command, Long userId) {
        ContentQuery query = toQuery(command);
        query.setPublishState(PublishState.PUBLISHED);
        query.setVisibility(ContentVisibility.PUBLIC);
        long current = Math.max(query.getCurrent(), 1L);
        long size = clampSize(query.getSize());
        query.setCurrent(current);
        query.setSize(size);
        List<Content> visible = contentRepository.page(query).stream()
                .filter(content -> content.visibleTo(userId, false)).toList();
        return PageResult.of(mapPublic(visible), contentRepository.count(query), current, size);
    }

    @Override
    public MovieDetailView detail(Long id, Long userId) {
        Content content = contentRepository.findById(id)
                .filter(item -> item.visibleTo(userId, false))
                .filter(item -> item.getContentType() == ContentType.MOVIE)
                .orElseThrow(() -> VideoErrorCode.exception(VideoErrorCode.CONTENT_NOT_FOUND));
        Movie movie = requireMetadata(id);
        MovieDetailView view = new MovieDetailView();
        ContentAssembler.copyDetail(view, buildDetail(content, userId));
        MovieViewAssembler.applyDetail(view, movie, content.getId());
        view.setCollections(relatedCollections(content.getId()));
        return view;
    }

    @Override
    public Long posterFileId(Long contentId, Long userId) {
        return fileIdOf(contentId, userId, true);
    }

    @Override
    public Long backdropFileId(Long contentId, Long userId) {
        return fileIdOf(contentId, userId, false);
    }

    /**
     * 读取电影可见性与图片文件 ID。
     *
     * @param contentId 内容 ID
     * @param userId 当前用户 ID
     * @param poster 是否读取海报（否则读取背景图）
     * @return 文件 ID；内容不可见或未设置返回 {@code null}
     */
    private Long fileIdOf(Long contentId, Long userId, boolean poster) {
        Content content = contentRepository.findById(contentId)
                .filter(item -> item.visibleTo(userId, false))
                .filter(item -> item.getContentType() == ContentType.MOVIE)
                .orElseThrow(() -> VideoErrorCode.exception(VideoErrorCode.CONTENT_NOT_FOUND));
        Movie movie = requireMetadata(content.getId());
        Long fileId = poster ? movie.getPosterFileId() : movie.getBackdropFileId();
        return fileId == null || fileId <= 0 ? null : fileId;
    }

    /**
     * 组装用户侧详情（可见性已在调用前校验）。
     *
     * @param content 内容
     * @param userId 当前用户 ID，可为 {@code null}
     * @return 详情基础视图
     */
    private ContentDetailView buildDetail(Content content, Long userId) {
        ContentDetailView view = new ContentDetailView();
        ContentAssembler.copyPublic(view,
                ContentAssembler.toPublic(content, tagNames(content.getId()), creatorName(content.getCreatorId())));
        view.setCommentable(content.commentable());
        view.setLiked(userId != null && contentLikeRepository.exists(userId, content.getId()));
        view.setFavorited(userId != null && contentFavoriteRepository.exists(userId, content.getId()));
        view.setProgressMs(progressOf(userId, content.getId()));
        view.setTicketUrl(TICKET_URL);
        return view;
    }

    /**
     * 校验并构造电影内容（草稿、公开、上传者为 0）。
     *
     * @param command 保存命令
     * @param movie 已写入授权信息的元数据
     * @return 内容聚合
     */
    private Content buildContent(MovieSaveCommand command, Movie movie) {
        FileAsset video = requireAsset(command.getVideoFileId());
        byte[] head = contentStreamRepository.readHead(command.getVideoFileId(), TenantContext.requireId(),
                HEAD_BYTES);
        String format = VideoUploadPolicy.validate(video, head, videoMimeTypes(), movieRule());
        Content content = new Content();
        content.setContentType(ContentType.MOVIE);
        content.setTitle(command.getTitle());
        content.setSubtitle(command.getSubtitle());
        content.setDescription(command.getDescription());
        content.setVideoFileId(command.getVideoFileId());
        content.setDurationMs(command.getDurationMs() == null ? 0L : command.getDurationMs());
        content.setWidth(command.getWidth() == null ? 0 : command.getWidth());
        content.setHeight(command.getHeight() == null ? 0 : command.getHeight());
        content.setFormat(format);
        content.setSizeBytes(video.sizeBytes());
        content.setUploaderId(0L);
        content.setCreatorId(command.getCreatorId() == null || command.getCreatorId() < 0
                ? 0L : command.getCreatorId());
        content.setCategoryId(resolveCategory(command.getCategoryId()));
        content.setSeriesId(0L);
        content.setEpisodeNo(0);
        content.setVisibility(ContentVisibility.PUBLIC);
        content.setPublishState(PublishState.DRAFT);
        content.setCopyrightState(CopyrightState.AUTHORIZED);
        content.setSource(copyrightSourceOf(movie.getLicenseSource()));
        content.setCopyrightExpireAt(movie.getLicenseExpireAt());
        content.setPlayCount(0L);
        content.setLikeCount(0L);
        content.setFavoriteCount(0L);
        content.setCommentCount(0L);
        content.setSortWeight(0);
        if (content.getDurationMs() <= 0 || content.getDurationMs() > movieRule().maxDurationMs()) {
            throw VideoErrorCode.exception(VideoErrorCode.CONTENT_DURATION_INVALID);
        }
        return content;
    }

    /**
     * 写入电影可编辑字段（标题 / 简介 / 分类 / 创作者 / 可选视频替换）。
     *
     * @param content 目标内容
     * @param command 保存命令
     */
    private void applyEditableFields(Content content, MovieSaveCommand command) {
        content.setTitle(command.getTitle());
        content.setSubtitle(command.getSubtitle());
        content.setDescription(command.getDescription());
        content.setCreatorId(command.getCreatorId() == null || command.getCreatorId() < 0
                ? 0L : command.getCreatorId());
        content.setCategoryId(resolveCategory(command.getCategoryId()));
        if (command.getVideoFileId() != null && command.getVideoFileId() > 0) {
            FileAsset video = requireAsset(command.getVideoFileId());
            byte[] head = contentStreamRepository.readHead(command.getVideoFileId(),
                    TenantContext.requireId(), HEAD_BYTES);
            content.setVideoFileId(command.getVideoFileId());
            content.setFormat(VideoUploadPolicy.validate(video, head, videoMimeTypes(), movieRule()));
            content.setSizeBytes(video.sizeBytes());
        }
        if (command.getDurationMs() != null && command.getDurationMs() > 0) {
            content.setDurationMs(command.getDurationMs());
        }
        if (command.getWidth() != null && command.getWidth() > 0) {
            content.setWidth(command.getWidth());
        }
        if (command.getHeight() != null && command.getHeight() > 0) {
            content.setHeight(command.getHeight());
        }
    }

    /**
     * 写入电影元数据字段。
     *
     * @param movie 目标元数据
     * @param command 保存命令
     */
    private void applyMeta(Movie movie, MovieSaveCommand command) {
        movie.applyMeta(command.getOriginalTitle(), command.getDirector(), command.getActors(),
                command.getRegion(), command.getLanguage(), command.getReleaseDate(), command.getRating());
    }

    /**
     * 校验并写入海报：空值保留原海报，0 表示清除；海报同步为内容封面。
     *
     * @param movie 目标元数据
     * @param content 目标内容
     * @param posterFileId 海报文件 ID
     */
    private void applyPoster(Movie movie, Content content, Long posterFileId) {
        if (posterFileId == null) {
            return;
        }
        if (posterFileId <= 0) {
            movie.setPosterFileId(null);
            content.setCoverFileId(0L);
            return;
        }
        Long valid = validateImage(posterFileId);
        movie.setPosterFileId(valid);
        content.setCoverFileId(valid);
    }

    /**
     * 校验并写入背景图：空值保留原背景图，0 表示清除。
     *
     * @param movie 目标元数据
     * @param backdropFileId 背景图文件 ID
     */
    private void applyBackdrop(Movie movie, Long backdropFileId) {
        if (backdropFileId == null) {
            return;
        }
        movie.setBackdropFileId(backdropFileId <= 0 ? null : validateImage(backdropFileId));
    }

    /**
     * 批量组装运营侧电影视图。
     *
     * @param records 内容列表
     * @return 运营侧视图列表
     */
    private List<MovieAdminView> mapAdmin(List<Content> records) {
        if (records.isEmpty()) {
            return Collections.emptyList();
        }
        Map<Long, Movie> movies = movieRepository.findByContentIds(idsOf(records));
        Map<Long, List<Long>> tagMap = contentRepository.findTagIdsByContentIds(idsOf(records));
        List<MovieAdminView> views = new ArrayList<>(records.size());
        for (Content content : records) {
            Movie movie = movies.get(content.getId());
            if (movie != null) {
                views.add(toAdminView(content, movie,
                        tagMap.getOrDefault(content.getId(), Collections.emptyList())));
            }
        }
        return views;
    }

    /**
     * 批量组装用户侧电影视图。
     *
     * @param records 内容列表
     * @return 用户侧视图列表
     */
    private List<MovieView> mapPublic(List<Content> records) {
        if (records.isEmpty()) {
            return Collections.emptyList();
        }
        List<PublicContentView> bases = contentViewAssembler.assemble(records);
        Map<Long, Movie> movies = movieRepository.findByContentIds(idsOf(records));
        List<MovieView> views = new ArrayList<>(records.size());
        for (int index = 0; index < records.size(); index++) {
            Movie movie = movies.get(records.get(index).getId());
            if (movie == null) {
                continue;
            }
            MovieView view = new MovieView();
            ContentAssembler.copyPublic(view, bases.get(index));
            MovieViewAssembler.applyPublic(view, movie, records.get(index).getId());
            views.add(view);
        }
        return views;
    }

    /**
     * 组装观众可见的相关片单（已上架）。
     *
     * @param contentId 内容 ID
     * @return 片单视图列表
     */
    private List<MovieCollectionView> relatedCollections(Long contentId) {
        return movieCollectionRepository.listPublishedByContentId(contentId, RELATED_COLLECTION_LIMIT).stream()
                .map(this::toCollectionView)
                .toList();
    }

    /**
     * 组装单个运营侧电影视图。
     *
     * @param content 内容
     * @param movie 电影元数据
     * @param tagIds 标签 ID
     * @return 运营侧视图
     */
    private MovieAdminView toAdminView(Content content, Movie movie, List<Long> tagIds) {
        MovieAdminView view = new MovieAdminView();
        ContentAssembler.copyAdmin(view, ContentAssembler.toAdmin(content, tagIds));
        view.setOriginalTitle(movie.getOriginalTitle());
        view.setDirector(movie.getDirector());
        view.setActors(movie.getActors());
        view.setRegion(movie.getRegion());
        view.setLanguage(movie.getLanguage());
        view.setReleaseDate(movie.getReleaseDate());
        view.setRating(movie.getRating());
        view.setLicenseSource(movie.getLicenseSource() == null ? null : movie.getLicenseSource().name());
        view.setLicenseNo(movie.getLicenseNo());
        view.setLicenseExpireAt(movie.getLicenseExpireAt());
        view.setPosterFileId(movie.getPosterFileId());
        view.setBackdropFileId(movie.getBackdropFileId());
        view.setPosterUrl(MovieViewAssembler.posterUrl(content.getId(), movie.getPosterFileId()));
        view.setBackdropUrl(MovieViewAssembler.backdropUrl(content.getId(), movie.getBackdropFileId()));
        return view;
    }

    /**
     * 片单转视图。
     *
     * @param collection 片单
     * @return 片单视图
     */
    private MovieCollectionView toCollectionView(MovieCollection collection) {
        MovieCollectionView view = new MovieCollectionView();
        view.setId(collection.getId());
        view.setTitle(collection.getTitle());
        view.setDescription(collection.getDescription());
        view.setCoverUrl(collection.getId() == null ? null : "/video/v1/collections/" + collection.getId() + "/cover");
        view.setPublishState(collection.getPublishState() == null ? null : collection.getPublishState().name());
        view.setItemCount(collection.getItemCount() == null ? 0 : collection.getItemCount());
        view.setSortNo(collection.getSortNo());
        view.setPublishedAt(collection.getPublishedAt());
        view.setCreateTime(collection.getCreateTime());
        return view;
    }

    /**
     * 加载电影内容，类型不符按不存在处理。
     *
     * @param id 内容 ID
     * @return 内容
     */
    private Content requireMovie(Long id) {
        return contentRepository.findById(id)
                .filter(item -> item.getContentType() == ContentType.MOVIE)
                .orElseThrow(() -> VideoErrorCode.exception(VideoErrorCode.CONTENT_NOT_FOUND));
    }

    /**
     * 加载电影元数据，不存在按内容不存在处理。
     *
     * @param contentId 内容 ID
     * @return 电影元数据
     */
    private Movie requireMetadata(Long contentId) {
        return movieRepository.findByContentId(contentId)
                .orElseThrow(() -> VideoErrorCode.exception(VideoErrorCode.CONTENT_NOT_FOUND));
    }

    /**
     * 校验并读取文件资产。
     *
     * @param fileId 文件 ID
     * @return 文件资产
     */
    private FileAsset requireAsset(Long fileId) {
        if (fileId == null || fileId <= 0) {
            throw VideoErrorCode.exception(VideoErrorCode.UPLOAD_FILE_INVALID);
        }
        Optional<FileAsset> asset = fileAssetPort.find(fileId, TenantContext.requireId());
        return asset.orElseThrow(() -> VideoErrorCode.exception(VideoErrorCode.UPLOAD_FILE_INVALID));
    }

    /**
     * 校验图片文件（MIME 白名单）并返回文件 ID。
     *
     * @param fileId 图片文件 ID
     * @return 校验通过的文件 ID
     */
    private Long validateImage(Long fileId) {
        FileAsset image = requireAsset(fileId);
        VideoUploadPolicy.validateImage(image, imageMimeTypes());
        return fileId;
    }

    /**
     * 校验并返回分类 ID。
     *
     * @param categoryId 分类 ID
     * @return 分类 ID，未指定返回 0
     */
    private Long resolveCategory(Long categoryId) {
        if (categoryId == null || categoryId <= 0) {
            return 0L;
        }
        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> VideoErrorCode.exception(VideoErrorCode.CATEGORY_NOT_FOUND));
        if (!category.appliesTo(ContentType.MOVIE)) {
            throw VideoErrorCode.exception(VideoErrorCode.CATEGORY_INVALID);
        }
        return categoryId;
    }

    /**
     * 电影内容的上传统计规则。
     *
     * @return 规则
     */
    private UploadRule movieRule() {
        return new UploadRule(videoProperties.getMovieMaxBytes(),
                videoProperties.getMovieMaxDurationSeconds() * 1000L);
    }

    /**
     * 电影时长上限（毫秒）。
     *
     * @return 时长上限
     */
    private long movieMaxDurationMs() {
        return movieRule().maxDurationMs();
    }

    /**
     * 授权来源映射为内容来源声明。
     *
     * @param source 授权来源
     * @return 内容来源声明
     */
    private CopyrightSource copyrightSourceOf(MovieLicenseSource source) {
        if (source == MovieLicenseSource.OWNED) {
            return CopyrightSource.ORIGINAL;
        }
        return source == MovieLicenseSource.PUBLIC_DOMAIN
                ? CopyrightSource.ROYALTY_FREE : CopyrightSource.AUTHORIZED;
    }

    /**
     * 视频 MIME 白名单（小写）。
     *
     * @return MIME 集合
     */
    private Set<String> videoMimeTypes() {
        return lowerSet(videoProperties.getVideoFormats());
    }

    /**
     * 图片 MIME 白名单（小写）。
     *
     * @return MIME 集合
     */
    private Set<String> imageMimeTypes() {
        return lowerSet(videoProperties.getImageFormats());
    }

    /**
     * 转小写集合。
     *
     * @param values 原始值
     * @return 小写集合
     */
    private Set<String> lowerSet(List<String> values) {
        return values == null ? Collections.emptySet()
                : values.stream().map(item -> item.toLowerCase(Locale.ROOT)).collect(Collectors.toSet());
    }

    /**
     * 构造查询条件（固定类型为电影）。
     *
     * @param command 查询命令
     * @return 查询条件
     */
    private ContentQuery toQuery(MovieQueryCommand command) {
        ContentQuery query = new ContentQuery();
        query.setContentType(ContentType.MOVIE);
        query.setCategoryId(command.getCategoryId());
        query.setCreatorId(command.getCreatorId());
        query.setPublishState(command.getPublishState() == null || command.getPublishState().isBlank()
                ? null : PublishState.parse(command.getPublishState()));
        query.setKeyword(command.getKeyword());
        query.setSortBy(command.getSortBy() == null || command.getSortBy().isBlank()
                ? SORT_LATEST : command.getSortBy());
        query.setCurrent(command.getCurrent());
        query.setSize(command.getSize());
        return query;
    }

    /**
     * 查询标签名称。
     *
     * @param contentId 内容 ID
     * @return 标签名称列表
     */
    private List<String> tagNames(Long contentId) {
        List<Long> tagIds = contentRepository.findTagIds(contentId);
        if (tagIds.isEmpty()) {
            return Collections.emptyList();
        }
        return tagRepository.findByIds(tagIds).stream().map(Tag::getName).toList();
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
    private List<Long> idsOf(Collection<Content> records) {
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
