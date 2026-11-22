package com.bluewhale.video.application.service.impl;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.tenant.TenantContext;
import com.bluewhale.video.application.command.SeriesQueryCommand;
import com.bluewhale.video.application.command.SeriesSaveCommand;
import com.bluewhale.video.application.view.SeriesDetailView;
import com.bluewhale.video.application.view.SeriesView;
import com.bluewhale.video.config.VideoProperties;
import com.bluewhale.video.domain.exception.VideoErrorCode;
import com.bluewhale.video.domain.model.Category;
import com.bluewhale.video.domain.model.Content;
import com.bluewhale.video.domain.model.ContentType;
import com.bluewhale.video.domain.model.ContentVisibility;
import com.bluewhale.video.domain.model.CopyrightState;
import com.bluewhale.video.domain.model.Creator;
import com.bluewhale.video.domain.model.FileAsset;
import com.bluewhale.video.domain.model.PublishState;
import com.bluewhale.video.domain.model.Series;
import com.bluewhale.video.domain.model.SeriesQuery;
import com.bluewhale.video.domain.repository.CategoryRepository;
import com.bluewhale.video.domain.repository.ContentRepository;
import com.bluewhale.video.domain.repository.CreatorRepository;
import com.bluewhale.video.domain.repository.FileAssetPort;
import com.bluewhale.video.domain.repository.SeriesRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 系列应用服务单元测试（M3-1 / M3-5）：标题与封面校验、状态流转、删除约束与系列页集数。
 *
 * @author BlueWhale
 */
@ExtendWith(MockitoExtension.class)
class SeriesAppServiceImplTest {

    /** 测试租户 */
    private static final Long TENANT_ID = 1L;

    /** 测试运营用户 */
    private static final Long USER_ID = 7L;

    /** 测试系列 */
    private static final Long SERIES_ID = 21L;

    /** 测试分类 */
    private static final Long CATEGORY_ID = 12L;

    /** 测试封面文件 */
    private static final Long COVER_ID = 11L;

    @Mock
    private SeriesRepository seriesRepository;

    @Mock
    private ContentRepository contentRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private CreatorRepository creatorRepository;

    @Mock
    private FileAssetPort fileAssetPort;

    @Spy
    private VideoProperties videoProperties = new VideoProperties();

    @InjectMocks
    private SeriesAppServiceImpl seriesAppService;

    /**
     * 绑定租户上下文，供封面文件读取使用。
     */
    @BeforeEach
    void bindTenant() {
        TenantContext.set(TENANT_ID);
    }

    /**
     * 清理租户上下文，避免线程复用串租户。
     */
    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    /**
     * 合法创建系列草稿：状态 DRAFT、集数 0 且返回封面代理地址。
     */
    @Test
    void shouldCreateDraftSeriesWithCover() {
        doAnswer(invocation -> {
            Series series = invocation.getArgument(0);
            series.setId(SERIES_ID);
            return null;
        }).when(seriesRepository).insert(any());
        when(categoryRepository.findById(CATEGORY_ID)).thenReturn(Optional.of(category("VIDEO")));
        when(fileAssetPort.find(COVER_ID, TENANT_ID))
                .thenReturn(Optional.of(new FileAsset(COVER_ID, "image/png", 512L, USER_ID, true)));

        SeriesView view = seriesAppService.create(command(), USER_ID);

        assertEquals(SERIES_ID, view.getId());
        assertEquals(PublishState.DRAFT.name(), view.getPublishState());
        assertEquals(0, view.getEpisodeCount());
        assertEquals("/video/v1/series/21/cover", view.getCoverUrl());
    }

    /**
     * 封面不是图片时拒绝创建（40004）。
     */
    @Test
    void shouldRejectNonImageCover() {
        when(categoryRepository.findById(CATEGORY_ID)).thenReturn(Optional.of(category("VIDEO")));
        when(fileAssetPort.find(COVER_ID, TENANT_ID))
                .thenReturn(Optional.of(new FileAsset(COVER_ID, "application/pdf", 512L, USER_ID, true)));

        assertEquals(VideoErrorCode.IMAGE_FORMAT_NOT_ALLOWED,
                assertThrows(BizException.class,
                        () -> seriesAppService.create(command(), USER_ID)).getCode());
        verify(seriesRepository, never()).insert(any());
    }

    /**
     * 分类不存在时拒绝创建（40402）。
     */
    @Test
    void shouldRejectMissingCategory() {
        when(categoryRepository.findById(CATEGORY_ID)).thenReturn(Optional.empty());

        assertEquals(VideoErrorCode.CATEGORY_NOT_FOUND,
                assertThrows(BizException.class,
                        () -> seriesAppService.create(command(), USER_ID)).getCode());
        verify(seriesRepository, never()).insert(any());
    }

    /**
     * 分类范围不含视频时拒绝创建（40012）。
     */
    @Test
    void shouldRejectCategoryOutOfVideoScope() {
        when(categoryRepository.findById(CATEGORY_ID)).thenReturn(Optional.of(category("MOVIE")));

        assertEquals(VideoErrorCode.CATEGORY_INVALID,
                assertThrows(BizException.class,
                        () -> seriesAppService.create(command(), USER_ID)).getCode());
        verify(seriesRepository, never()).insert(any());
    }

    /**
     * 编辑不存在的系列按 40404 返回。
     */
    @Test
    void shouldRejectUpdateMissingSeries() {
        when(seriesRepository.findById(SERIES_ID)).thenReturn(Optional.empty());
        assertEquals(VideoErrorCode.SERIES_NOT_FOUND,
                assertThrows(BizException.class,
                        () -> seriesAppService.update(SERIES_ID, command())).getCode());
        verify(seriesRepository, never()).update(any());
    }

    /**
     * 存在已发布集数的系列不允许删除（40901）。
     */
    @Test
    void shouldRejectDeleteSeriesWithEpisodes() {
        when(seriesRepository.findById(SERIES_ID)).thenReturn(Optional.of(series(3, PublishState.PUBLISHED)));
        assertEquals(VideoErrorCode.CONTENT_STATE_CONFLICT,
                assertThrows(BizException.class, () -> seriesAppService.delete(SERIES_ID)).getCode());
        verify(seriesRepository, never()).delete(any());
    }

    /**
     * 空系列允许删除。
     */
    @Test
    void shouldDeleteEmptySeries() {
        when(seriesRepository.findById(SERIES_ID)).thenReturn(Optional.of(series(0, PublishState.DRAFT)));
        seriesAppService.delete(SERIES_ID);
        verify(seriesRepository).delete(SERIES_ID);
    }

    /**
     * 草稿上架后切到 PUBLISHED 并持久化。
     */
    @Test
    void shouldPublishDraftSeries() {
        when(seriesRepository.findById(SERIES_ID)).thenReturn(Optional.of(series(0, PublishState.DRAFT)));
        SeriesView view = seriesAppService.publish(SERIES_ID);
        assertEquals(PublishState.PUBLISHED.name(), view.getPublishState());
        verify(seriesRepository).update(any());
    }

    /**
     * 草稿不可下架（40901）。
     */
    @Test
    void shouldRejectOfflineDraftSeries() {
        when(seriesRepository.findById(SERIES_ID)).thenReturn(Optional.of(series(0, PublishState.DRAFT)));
        assertEquals(VideoErrorCode.CONTENT_STATE_CONFLICT,
                assertThrows(BizException.class, () -> seriesAppService.offline(SERIES_ID)).getCode());
        verify(seriesRepository, never()).update(any());
    }

    /**
     * 未上架系列的用户侧详情按不存在处理（40404）。
     */
    @Test
    void shouldHideUnpublishedSeriesDetail() {
        when(seriesRepository.findById(SERIES_ID)).thenReturn(Optional.of(series(0, PublishState.DRAFT)));
        assertEquals(VideoErrorCode.SERIES_NOT_FOUND,
                assertThrows(BizException.class, () -> seriesAppService.detail(SERIES_ID)).getCode());
    }

    /**
     * 已上架系列详情返回集数列表，并以实际集数覆盖存储计数。
     */
    @Test
    void shouldReturnPublishedEpisodesInDetail() {
        Series series = series(1, PublishState.PUBLISHED);
        series.setCoverFileId(COVER_ID);
        when(seriesRepository.findById(SERIES_ID)).thenReturn(Optional.of(series));
        when(contentRepository.listPublishedEpisodes(SERIES_ID))
                .thenReturn(List.of(episode(1), episode(2)));

        SeriesDetailView view = seriesAppService.detail(SERIES_ID);

        assertEquals(2, view.getEpisodeCount());
        assertEquals(2, view.getEpisodes().size());
        assertEquals(1, view.getEpisodes().get(0).getEpisodeNo());
        assertEquals("/video/v1/series/21/cover", view.getCoverUrl());
    }

    /**
     * 运营分页按条件查询并组装视图。
     */
    @Test
    void shouldPageAdminSeries() {
        when(seriesRepository.page(any())).thenReturn(List.of(series(1, PublishState.PUBLISHED)));
        when(seriesRepository.count(any())).thenReturn(1L);
        SeriesQueryCommand command = new SeriesQueryCommand();
        command.setPublishState("PUBLISHED");

        PageResult<SeriesView> page = seriesAppService.adminPage(command);

        assertEquals(1L, page.getTotal());
        assertEquals(1, page.getRecords().size());
        assertEquals(PublishState.PUBLISHED.name(), page.getRecords().get(0).getPublishState());
    }

    /**
     * 用户侧系列分页强制只查已上架，并补充创作者昵称。
     */
    @Test
    void shouldPagePublishedSeriesOnly() {
        Series series = series(1, PublishState.PUBLISHED);
        series.setCreatorId(8L);
        when(seriesRepository.page(any())).thenReturn(List.of(series));
        when(seriesRepository.count(any())).thenReturn(1L);
        Creator creator = new Creator();
        creator.setId(8L);
        creator.setDisplayName("深海工作室");
        when(creatorRepository.findById(8L)).thenReturn(Optional.of(creator));
        SeriesQueryCommand command = new SeriesQueryCommand();
        command.setPublishState("DRAFT");

        PageResult<SeriesView> page = seriesAppService.publishedPage(command);

        assertEquals(1L, page.getTotal());
        assertEquals("深海工作室", page.getRecords().get(0).getCreatorName());
        verify(seriesRepository).page(argThat(query ->
                query instanceof SeriesQuery seriesQuery
                        && seriesQuery.getPublishState() == PublishState.PUBLISHED));
    }

    /**
     * 未上架系列封面按不存在处理。
     */
    @Test
    void shouldHideUnpublishedSeriesCover() {
        when(seriesRepository.findById(SERIES_ID)).thenReturn(Optional.of(series(0, PublishState.DRAFT)));
        assertEquals(VideoErrorCode.SERIES_NOT_FOUND,
                assertThrows(BizException.class, () -> seriesAppService.coverFileId(SERIES_ID)).getCode());
    }

    /**
     * 构造系列保存命令。
     *
     * @return 保存命令
     */
    private SeriesSaveCommand command() {
        SeriesSaveCommand command = new SeriesSaveCommand();
        command.setTitle("深海探秘");
        command.setDescription("深海纪录片合集");
        command.setCoverFileId(COVER_ID);
        command.setCategoryId(CATEGORY_ID);
        command.setSortNo(1);
        return command;
    }

    /**
     * 构造分类。
     *
     * @param scope 适用范围
     * @return 分类
     */
    private Category category(String scope) {
        Category category = new Category();
        category.setId(CATEGORY_ID);
        category.setName("纪录片");
        category.setCode("documentary");
        category.setContentTypeScope(scope);
        category.setStatus(1);
        return category;
    }

    /**
     * 构造系列。
     *
     * @param episodeCount 已发布集数
     * @param state 发布状态
     * @return 系列
     */
    private Series series(int episodeCount, PublishState state) {
        Series series = new Series();
        series.setId(SERIES_ID);
        series.setTitle("深海探秘");
        series.setContentType(ContentType.VIDEO);
        series.setPublishState(state);
        series.setEpisodeCount(episodeCount);
        series.setSortNo(1);
        return series;
    }

    /**
     * 构造系列集内容。
     *
     * @param episodeNo 集数
     * @return 内容
     */
    private Content episode(int episodeNo) {
        Content content = new Content();
        content.setId(100L + episodeNo);
        content.setContentType(ContentType.VIDEO);
        content.setTitle("第 " + episodeNo + " 集");
        content.setSeriesId(SERIES_ID);
        content.setEpisodeNo(episodeNo);
        content.setDurationMs(600_000L);
        content.setPublishState(PublishState.PUBLISHED);
        content.setVisibility(ContentVisibility.PUBLIC);
        content.setCopyrightState(CopyrightState.AUTHORIZED);
        return content;
    }
}
