package com.bluewhale.video.application.service.impl;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.tenant.TenantContext;
import com.bluewhale.video.application.command.SeriesQueryCommand;
import com.bluewhale.video.application.command.SeriesSaveCommand;
import com.bluewhale.video.application.service.SeriesAppService;
import com.bluewhale.video.application.support.ContentAssembler;
import com.bluewhale.video.application.view.SeriesDetailView;
import com.bluewhale.video.application.view.SeriesView;
import com.bluewhale.video.config.VideoProperties;
import com.bluewhale.video.domain.exception.VideoErrorCode;
import com.bluewhale.video.domain.model.Category;
import com.bluewhale.video.domain.model.Content;
import com.bluewhale.video.domain.model.ContentType;
import com.bluewhale.video.domain.model.FileAsset;
import com.bluewhale.video.domain.model.PublishState;
import com.bluewhale.video.domain.model.Series;
import com.bluewhale.video.domain.model.SeriesQuery;
import com.bluewhale.video.domain.repository.CategoryRepository;
import com.bluewhale.video.domain.repository.ContentRepository;
import com.bluewhale.video.domain.repository.CreatorRepository;
import com.bluewhale.video.domain.repository.FileAssetPort;
import com.bluewhale.video.domain.repository.SeriesRepository;
import com.bluewhale.video.domain.service.VideoUploadPolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 系列应用服务实现。
 *
 * <p>系列仅支持视频内容；封面经 {@code whale-file} 图片白名单校验后保存文件 ID，
 * 公开视图只返回代理地址。用户侧详情仅已上架系列可见，且只展示已发布集数，
 * 未上架系列统一按不存在处理，不泄露存在性。</p>
 *
 * @author BlueWhale
 */
@Service
@RequiredArgsConstructor
public class SeriesAppServiceImpl implements SeriesAppService {

    /** 单页最大条数 */
    private static final long MAX_PAGE_SIZE = 200L;

    /** 默认每页条数 */
    private static final long DEFAULT_PAGE_SIZE = 20L;

    /** 系列封面代理地址前缀 */
    private static final String COVER_PATH_PREFIX = "/video/v1/series/";

    /** 系列封面代理地址后缀 */
    private static final String COVER_PATH_SUFFIX = "/cover";

    private final SeriesRepository seriesRepository;

    private final ContentRepository contentRepository;

    private final CategoryRepository categoryRepository;

    private final CreatorRepository creatorRepository;

    private final FileAssetPort fileAssetPort;

    private final VideoProperties videoProperties;

    @Override
    public PageResult<SeriesView> adminPage(SeriesQueryCommand command) {
        return page(command, false);
    }

    @Override
    public PageResult<SeriesView> publishedPage(SeriesQueryCommand command) {
        return page(command, true);
    }

    /**
     * 系列分页：用户侧强制只查询已上架系列，并补充创作者昵称。
     *
     * @param command 查询命令
     * @param publishedOnly 是否仅已上架
     * @return 系列分页结果
     */
    private PageResult<SeriesView> page(SeriesQueryCommand command, boolean publishedOnly) {
        SeriesQuery query = toQuery(command);
        if (publishedOnly) {
            query.setPublishState(PublishState.PUBLISHED);
        }
        long current = Math.max(query.getCurrent(), 1L);
        long size = clampSize(query.getSize());
        query.setCurrent(current);
        query.setSize(size);
        List<SeriesView> records = seriesRepository.page(query).stream()
                .map(series -> publishedOnly ? toPublicView(series) : toView(series))
                .toList();
        return PageResult.of(records, seriesRepository.count(query), current, size);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SeriesView create(SeriesSaveCommand command, Long userId) {
        Series series = new Series();
        series.setContentType(ContentType.VIDEO);
        series.retitle(command.getTitle());
        applyFields(series, command);
        series.setUploaderId(userId == null ? 0L : userId);
        series.setPublishState(PublishState.DRAFT);
        series.setEpisodeCount(0);
        seriesRepository.insert(series);
        return toView(series);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SeriesView update(Long id, SeriesSaveCommand command) {
        Series series = requireSeries(id);
        series.retitle(command.getTitle());
        applyFields(series, command);
        seriesRepository.update(series);
        return toView(series);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        Series series = requireSeries(id);
        if (series.getEpisodeCount() != null && series.getEpisodeCount() > 0) {
            throw VideoErrorCode.exception(VideoErrorCode.CONTENT_STATE_CONFLICT);
        }
        seriesRepository.delete(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SeriesView publish(Long id) {
        Series series = requireSeries(id);
        series.publish();
        seriesRepository.update(series);
        return toView(series);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SeriesView offline(Long id) {
        Series series = requireSeries(id);
        series.offline();
        seriesRepository.update(series);
        return toView(series);
    }

    @Override
    public SeriesDetailView detail(Long id) {
        Series series = requireSeries(id);
        if (!series.published()) {
            throw VideoErrorCode.exception(VideoErrorCode.SERIES_NOT_FOUND);
        }
        SeriesDetailView view = new SeriesDetailView();
        copy(view, series);
        view.setCreatorName(creatorName(series.getCreatorId()));
        List<Content> episodes = contentRepository.listPublishedEpisodes(id);
        view.setEpisodeCount(episodes.size());
        view.setEpisodes(episodes.stream()
                .map(content -> ContentAssembler.toPublic(content, Collections.emptyList(), null))
                .toList());
        return view;
    }

    @Override
    public Long coverFileId(Long id) {
        Series series = requireSeries(id);
        if (!series.published()) {
            throw VideoErrorCode.exception(VideoErrorCode.SERIES_NOT_FOUND);
        }
        return series.getCoverFileId();
    }

    /**
     * 写入系列可编辑字段。
     *
     * @param series 目标系列
     * @param command 保存命令
     */
    private void applyFields(Series series, SeriesSaveCommand command) {
        series.setDescription(command.getDescription());
        series.setCategoryId(resolveCategory(command.getCategoryId()));
        series.setCreatorId(command.getCreatorId() == null || command.getCreatorId() < 0
                ? 0L : command.getCreatorId());
        series.setSortNo(command.getSortNo() == null ? 0 : command.getSortNo());
        applyCover(series, command.getCoverFileId());
    }

    /**
     * 校验并写入封面：空值保留原封面，0 表示清除。
     *
     * @param series 目标系列
     * @param coverFileId 封面文件 ID
     */
    private void applyCover(Series series, Long coverFileId) {
        if (coverFileId == null || (coverFileId == 0 && series.getCoverFileId() == null)) {
            return;
        }
        if (coverFileId <= 0) {
            series.setCoverFileId(null);
            return;
        }
        FileAsset cover = fileAssetPort.find(coverFileId, TenantContext.requireId())
                .orElseThrow(() -> VideoErrorCode.exception(VideoErrorCode.UPLOAD_FILE_INVALID));
        VideoUploadPolicy.validateImage(cover, imageMimeTypes());
        series.setCoverFileId(coverFileId);
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
        if (!category.appliesTo(ContentType.VIDEO)) {
            throw VideoErrorCode.exception(VideoErrorCode.CATEGORY_INVALID);
        }
        return categoryId;
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
     * 加载系列，不存在时抛出业务异常。
     *
     * @param id 系列 ID
     * @return 系列
     */
    private Series requireSeries(Long id) {
        return seriesRepository.findById(id)
                .orElseThrow(() -> VideoErrorCode.exception(VideoErrorCode.SERIES_NOT_FOUND));
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
     * 构造查询条件。
     *
     * @param command 查询命令
     * @return 查询条件
     */
    private SeriesQuery toQuery(SeriesQueryCommand command) {
        SeriesQuery query = new SeriesQuery();
        query.setKeyword(command.getKeyword());
        query.setContentType(ContentType.parse(command.getContentType()));
        query.setPublishState(command.getPublishState() == null || command.getPublishState().isBlank()
                ? null : PublishState.parse(command.getPublishState()));
        query.setCategoryId(command.getCategoryId());
        query.setCreatorId(command.getCreatorId());
        query.setCurrent(command.getCurrent());
        query.setSize(command.getSize());
        return query;
    }

    /**
     * 系列转视图。
     *
     * @param series 系列
     * @return 系列视图
     */
    private SeriesView toView(Series series) {
        SeriesView view = new SeriesView();
        copy(view, series);
        return view;
    }

    /**
     * 系列转用户侧视图（补充创作者昵称）。
     *
     * @param series 系列
     * @return 系列视图
     */
    private SeriesView toPublicView(Series series) {
        SeriesView view = toView(series);
        view.setCreatorName(creatorName(series.getCreatorId()));
        return view;
    }

    /**
     * 复制系列基础字段到视图。
     *
     * @param view 目标视图
     * @param series 系列
     */
    private void copy(SeriesView view, Series series) {
        view.setId(series.getId());
        view.setTitle(series.getTitle());
        view.setDescription(series.getDescription());
        view.setContentType(series.getContentType() == null ? null : series.getContentType().name());
        view.setCategoryId(series.getCategoryId());
        view.setCreatorId(series.getCreatorId());
        view.setPublishState(series.getPublishState() == null ? null : series.getPublishState().name());
        view.setEpisodeCount(series.getEpisodeCount() == null ? 0 : series.getEpisodeCount());
        view.setSortNo(series.getSortNo());
        view.setCoverUrl(coverUrl(series));
        view.setPublishedAt(series.getPublishedAt());
        view.setCreateTime(series.getCreateTime());
    }

    /**
     * 构造封面代理地址。
     *
     * @param series 系列
     * @return 代理地址；无封面返回 {@code null}
     */
    private String coverUrl(Series series) {
        if (series.getId() == null || series.getCoverFileId() == null || series.getCoverFileId() <= 0) {
            return null;
        }
        return COVER_PATH_PREFIX + series.getId() + COVER_PATH_SUFFIX;
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
