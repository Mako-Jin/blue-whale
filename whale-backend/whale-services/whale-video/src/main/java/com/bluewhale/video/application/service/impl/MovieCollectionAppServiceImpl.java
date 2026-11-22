package com.bluewhale.video.application.service.impl;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.tenant.TenantContext;
import com.bluewhale.video.application.command.CollectionQueryCommand;
import com.bluewhale.video.application.command.MovieCollectionItemCommand;
import com.bluewhale.video.application.command.MovieCollectionSaveCommand;
import com.bluewhale.video.application.service.MovieCollectionAppService;
import com.bluewhale.video.application.support.ContentAssembler;
import com.bluewhale.video.application.support.MovieViewAssembler;
import com.bluewhale.video.application.support.PublicContentViewAssembler;
import com.bluewhale.video.application.view.MovieCollectionDetailView;
import com.bluewhale.video.application.view.MovieCollectionView;
import com.bluewhale.video.application.view.MovieView;
import com.bluewhale.video.application.view.PublicContentView;
import com.bluewhale.video.config.VideoProperties;
import com.bluewhale.video.domain.exception.VideoErrorCode;
import com.bluewhale.video.domain.model.CollectionQuery;
import com.bluewhale.video.domain.model.Content;
import com.bluewhale.video.domain.model.ContentType;
import com.bluewhale.video.domain.model.FileAsset;
import com.bluewhale.video.domain.model.Movie;
import com.bluewhale.video.domain.model.MovieCollection;
import com.bluewhale.video.domain.model.PublishState;
import com.bluewhale.video.domain.repository.ContentRepository;
import com.bluewhale.video.domain.repository.FileAssetPort;
import com.bluewhale.video.domain.repository.MovieCollectionRepository;
import com.bluewhale.video.domain.repository.MovieRepository;
import com.bluewhale.video.domain.service.VideoUploadPolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 电影片单应用服务实现。
 *
 * <p>片单草稿 → 上架 → 下架；加片校验电影存在且同一片单内唯一，
 * 加片 / 移除后同步刷新条目数，单张片单最多 200 部。用户侧仅可见已上架片单，
 * 片单详情内的电影仍按内容自身的可见性过滤，片单下架不影响电影状态。</p>
 *
 * @author BlueWhale
 */
@Service
@RequiredArgsConstructor
public class MovieCollectionAppServiceImpl implements MovieCollectionAppService {

    /** 单页最大条数 */
    private static final long MAX_PAGE_SIZE = 200L;

    /** 默认每页条数 */
    private static final long DEFAULT_PAGE_SIZE = 20L;

    /** 片单封面代理地址前缀 */
    private static final String COVER_PATH_PREFIX = "/video/v1/collections/";

    /** 片单封面代理地址后缀 */
    private static final String COVER_PATH_SUFFIX = "/cover";

    private final MovieCollectionRepository movieCollectionRepository;

    private final MovieRepository movieRepository;

    private final ContentRepository contentRepository;

    private final FileAssetPort fileAssetPort;

    private final PublicContentViewAssembler contentViewAssembler;

    private final VideoProperties videoProperties;

    @Override
    public PageResult<MovieCollectionView> adminPage(CollectionQueryCommand command) {
        CollectionQuery query = toQuery(command, false);
        long current = Math.max(query.getCurrent(), 1L);
        long size = clampSize(query.getSize());
        query.setCurrent(current);
        query.setSize(size);
        List<MovieCollectionView> records = movieCollectionRepository.page(query).stream()
                .map(this::toView).toList();
        return PageResult.of(records, movieCollectionRepository.count(query), current, size);
    }

    @Override
    public MovieCollectionDetailView adminDetail(Long id) {
        MovieCollection collection = requireCollection(id);
        return toDetailView(collection, movieViews(movieCollectionRepository.listItemContentIds(id), false));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MovieCollectionView create(MovieCollectionSaveCommand command) {
        MovieCollection collection = new MovieCollection();
        collection.retitle(command.getTitle());
        collection.setDescription(normalizeText(command.getDescription()));
        collection.setCoverFileId(validateCover(command.getCoverFileId()));
        collection.setSortNo(command.getSortNo() == null ? 0 : command.getSortNo());
        collection.setPublishState(PublishState.DRAFT);
        collection.refreshItemCount(0);
        movieCollectionRepository.insert(collection);
        return toView(collection);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MovieCollectionView update(Long id, MovieCollectionSaveCommand command) {
        MovieCollection collection = requireCollection(id);
        collection.retitle(command.getTitle());
        collection.setDescription(normalizeText(command.getDescription()));
        applyCover(collection, command.getCoverFileId());
        if (command.getSortNo() != null) {
            collection.setSortNo(command.getSortNo());
        }
        movieCollectionRepository.update(collection);
        return toView(collection);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        requireCollection(id);
        movieCollectionRepository.delete(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MovieCollectionView publish(Long id) {
        MovieCollection collection = requireCollection(id);
        collection.publish();
        movieCollectionRepository.update(collection);
        return toView(collection);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MovieCollectionView offline(Long id) {
        MovieCollection collection = requireCollection(id);
        collection.offline();
        movieCollectionRepository.update(collection);
        return toView(collection);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MovieCollectionDetailView addItem(Long id, MovieCollectionItemCommand command) {
        MovieCollection collection = requireCollection(id);
        Long contentId = command.getContentId();
        requireMovieContent(contentId);
        if (movieCollectionRepository.existsItem(id, contentId)) {
            return toDetailView(collection, movieViews(movieCollectionRepository.listItemContentIds(id), false));
        }
        int currentCount = movieCollectionRepository.countItems(id);
        collection.assertCanAddItem(currentCount);
        movieCollectionRepository.addItem(id, contentId, sortNoOf(command.getSortNo(), currentCount));
        refreshItemCount(collection);
        return toDetailView(collection, movieViews(movieCollectionRepository.listItemContentIds(id), false));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MovieCollectionDetailView removeItem(Long id, Long contentId) {
        MovieCollection collection = requireCollection(id);
        if (contentId != null && contentId > 0 && movieCollectionRepository.existsItem(id, contentId)) {
            movieCollectionRepository.removeItem(id, contentId);
            refreshItemCount(collection);
        }
        return toDetailView(collection, movieViews(movieCollectionRepository.listItemContentIds(id), false));
    }

    @Override
    public PageResult<MovieCollectionView> publicPage(CollectionQueryCommand command) {
        CollectionQuery query = toQuery(command, true);
        long current = Math.max(query.getCurrent(), 1L);
        long size = clampSize(query.getSize());
        query.setCurrent(current);
        query.setSize(size);
        List<MovieCollectionView> records = movieCollectionRepository.page(query).stream()
                .filter(MovieCollection::published)
                .map(this::toView).toList();
        return PageResult.of(records, movieCollectionRepository.count(query), current, size);
    }

    @Override
    public MovieCollectionDetailView publicDetail(Long id) {
        MovieCollection collection = requireCollection(id);
        if (!collection.published()) {
            throw VideoErrorCode.exception(VideoErrorCode.COLLECTION_NOT_FOUND);
        }
        return toDetailView(collection,
                movieViews(movieCollectionRepository.listItemContentIds(id), true));
    }

    @Override
    public Long coverFileId(Long id) {
        Long fileId = requireCollection(id).getCoverFileId();
        return fileId == null || fileId <= 0 ? null : fileId;
    }

    /**
     * 重新统计并刷新片单条目数。
     *
     * @param collection 片单
     */
    private void refreshItemCount(MovieCollection collection) {
        collection.refreshItemCount(movieCollectionRepository.countItems(collection.getId()));
        movieCollectionRepository.update(collection);
    }

    /**
     * 校验加片目标为已入库的电影。
     *
     * @param contentId 内容 ID
     */
    private void requireMovieContent(Long contentId) {
        if (contentId == null || contentId <= 0) {
            throw VideoErrorCode.exception(VideoErrorCode.CONTENT_NOT_FOUND);
        }
        Content content = contentRepository.findById(contentId)
                .orElseThrow(() -> VideoErrorCode.exception(VideoErrorCode.CONTENT_NOT_FOUND));
        if (content.getContentType() != ContentType.MOVIE
                || movieRepository.findByContentId(contentId).isEmpty()) {
            throw VideoErrorCode.exception(VideoErrorCode.CONTENT_NOT_FOUND);
        }
    }

    /**
     * 计算片单内排序：入参非法时追加到末尾。
     *
     * @param sortNo 请求排序
     * @param currentCount 当前条目数
     * @return 片单内排序
     */
    private int sortNoOf(Integer sortNo, int currentCount) {
        return sortNo == null || sortNo <= 0 ? currentCount + 1 : sortNo;
    }

    /**
     * 校验新增片单封面：空值表示无封面。
     *
     * @param coverFileId 封面文件 ID
     * @return 校验通过的封面文件 ID；空值返回 {@code null}
     */
    private Long validateCover(Long coverFileId) {
        return coverFileId == null || coverFileId <= 0 ? null : validateImage(coverFileId);
    }

    /**
     * 更新片单封面：空值保留原封面，0 表示清除。
     *
     * @param collection 片单
     * @param coverFileId 封面文件 ID
     */
    private void applyCover(MovieCollection collection, Long coverFileId) {
        if (coverFileId == null) {
            return;
        }
        collection.setCoverFileId(coverFileId <= 0 ? null : validateImage(coverFileId));
    }

    /**
     * 校验图片文件（MIME 白名单）并返回文件 ID。
     *
     * @param fileId 图片文件 ID
     * @return 校验通过的文件 ID
     */
    private Long validateImage(Long fileId) {
        if (fileId == null || fileId <= 0) {
            throw VideoErrorCode.exception(VideoErrorCode.UPLOAD_FILE_INVALID);
        }
        Optional<FileAsset> asset = fileAssetPort.find(fileId, TenantContext.requireId());
        FileAsset image = asset.orElseThrow(
                () -> VideoErrorCode.exception(VideoErrorCode.UPLOAD_FILE_INVALID));
        VideoUploadPolicy.validateImage(image, imageMimeTypes());
        return fileId;
    }

    /**
     * 图片 MIME 白名单（小写）。
     *
     * @return MIME 集合
     */
    private Set<String> imageMimeTypes() {
        List<String> formats = videoProperties.getImageFormats();
        return formats == null ? Collections.emptySet()
                : formats.stream().map(item -> item.toLowerCase(Locale.ROOT)).collect(Collectors.toSet());
    }

    /**
     * 按片单内顺序组装电影视图。
     *
     * @param contentIds 片单内内容 ID（按片单排序）
     * @param onlyVisible 是否只保留用户侧可见电影
     * @return 电影视图列表
     */
    private List<MovieView> movieViews(List<Long> contentIds, boolean onlyVisible) {
        if (contentIds == null || contentIds.isEmpty()) {
            return Collections.emptyList();
        }
        Map<Long, Content> contents = contentRepository.findByIds(contentIds).stream()
                .collect(Collectors.toMap(Content::getId, item -> item));
        List<Content> ordered = new ArrayList<>(contentIds.size());
        for (Long contentId : contentIds) {
            Content content = contents.get(contentId);
            if (content == null || content.getContentType() != ContentType.MOVIE) {
                continue;
            }
            if (onlyVisible && !content.visibleTo(null, false)) {
                continue;
            }
            ordered.add(content);
        }
        if (ordered.isEmpty()) {
            return Collections.emptyList();
        }
        Map<Long, Movie> movies = movieRepository.findByContentIds(ordered.stream()
                .map(Content::getId).toList());
        List<PublicContentView> bases = contentViewAssembler.assemble(ordered);
        List<MovieView> views = new ArrayList<>(ordered.size());
        for (int index = 0; index < ordered.size(); index++) {
            Movie movie = movies.get(ordered.get(index).getId());
            if (movie == null) {
                continue;
            }
            MovieView view = new MovieView();
            ContentAssembler.copyPublic(view, bases.get(index));
            MovieViewAssembler.applyPublic(view, movie, ordered.get(index).getId());
            views.add(view);
        }
        return views;
    }

    /**
     * 组装片单详情视图。
     *
     * @param collection 片单
     * @param items 片单内电影
     * @return 片单详情视图
     */
    private MovieCollectionDetailView toDetailView(MovieCollection collection, List<MovieView> items) {
        MovieCollectionDetailView view = new MovieCollectionDetailView();
        fill(view, collection);
        view.setItems(items);
        return view;
    }

    /**
     * 片单转视图。
     *
     * @param collection 片单
     * @return 片单视图
     */
    private MovieCollectionView toView(MovieCollection collection) {
        MovieCollectionView view = new MovieCollectionView();
        fill(view, collection);
        return view;
    }

    /**
     * 写入片单公开字段。
     *
     * @param view 目标视图
     * @param collection 片单
     */
    private void fill(MovieCollectionView view, MovieCollection collection) {
        view.setId(collection.getId());
        view.setTitle(collection.getTitle());
        view.setDescription(collection.getDescription());
        view.setCoverUrl(collection.getCoverFileId() == null || collection.getCoverFileId() <= 0
                ? null : COVER_PATH_PREFIX + collection.getId() + COVER_PATH_SUFFIX);
        view.setPublishState(collection.getPublishState() == null
                ? null : collection.getPublishState().name());
        view.setItemCount(collection.getItemCount() == null ? 0 : collection.getItemCount());
        view.setSortNo(collection.getSortNo());
        view.setPublishedAt(collection.getPublishedAt());
        view.setCreateTime(collection.getCreateTime());
    }

    /**
     * 加载片单，不存在时抛出业务异常。
     *
     * @param id 片单 ID
     * @return 片单
     */
    private MovieCollection requireCollection(Long id) {
        return movieCollectionRepository.findById(id)
                .orElseThrow(() -> VideoErrorCode.exception(VideoErrorCode.COLLECTION_NOT_FOUND));
    }

    /**
     * 归一化简介文本。
     *
     * @param value 原始文本
     * @return 去空白后的文本；空白返回 {@code null}
     */
    private String normalizeText(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    /**
     * 构造片单查询条件。
     *
     * @param command 查询命令
     * @param publishedOnly 是否强制仅已上架（用户侧）
     * @return 查询条件
     */
    private CollectionQuery toQuery(CollectionQueryCommand command, boolean publishedOnly) {
        CollectionQuery query = new CollectionQuery();
        query.setKeyword(command.getKeyword());
        query.setPublishState(publishedOnly ? PublishState.PUBLISHED : parseState(command.getPublishState()));
        query.setCurrent(command.getCurrent());
        query.setSize(command.getSize());
        return query;
    }

    /**
     * 解析发布状态筛选，空或非法值返回 {@code null}（不筛选）。
     *
     * @param value 状态文本
     * @return 发布状态
     */
    private PublishState parseState(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return PublishState.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "片单发布状态非法");
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
