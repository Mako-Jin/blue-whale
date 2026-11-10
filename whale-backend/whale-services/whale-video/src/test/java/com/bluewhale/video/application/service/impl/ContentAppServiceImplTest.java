package com.bluewhale.video.application.service.impl;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.tenant.TenantContext;
import com.bluewhale.video.application.command.ContentSaveCommand;
import com.bluewhale.video.application.view.ContentView;
import com.bluewhale.video.config.VideoProperties;
import com.bluewhale.video.domain.exception.VideoErrorCode;
import com.bluewhale.video.domain.model.Category;
import com.bluewhale.video.domain.model.Content;
import com.bluewhale.video.domain.model.ContentType;
import com.bluewhale.video.domain.model.CopyrightSource;
import com.bluewhale.video.domain.model.FileAsset;
import com.bluewhale.video.domain.model.PublishState;
import com.bluewhale.video.domain.model.Series;
import com.bluewhale.video.domain.model.UploadDeclaration;
import com.bluewhale.video.domain.repository.CategoryRepository;
import com.bluewhale.video.domain.repository.ContentFavoriteRepository;
import com.bluewhale.video.domain.repository.ContentLikeRepository;
import com.bluewhale.video.domain.repository.ContentRepository;
import com.bluewhale.video.domain.repository.ContentStreamRepository;
import com.bluewhale.video.domain.repository.CreatorRepository;
import com.bluewhale.video.domain.repository.DeclarationRepository;
import com.bluewhale.video.domain.repository.FileAssetPort;
import com.bluewhale.video.domain.repository.FeedCacheRepository;
import com.bluewhale.video.domain.repository.HomeCacheRepository;
import com.bluewhale.video.domain.repository.PlayableCacheRepository;
import com.bluewhale.video.domain.repository.SeriesRepository;
import com.bluewhale.video.domain.repository.TagRepository;
import com.bluewhale.video.domain.repository.WatchHistoryRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 内容应用服务单元测试（M1-1 / M1-2 / M1-7）：声明前置、文件校验、发布与缓存失效。
 *
 * @author BlueWhale
 */
@ExtendWith(MockitoExtension.class)
class ContentAppServiceImplTest {

    /** 测试租户 */
    private static final Long TENANT_ID = 1L;

    /** 测试用户 */
    private static final Long USER_ID = 7L;

    /** 测试内容 */
    private static final Long CONTENT_ID = 9L;

    /** 测试视频文件 */
    private static final Long FILE_ID = 10L;

    /** 测试封面文件 */
    private static final Long COVER_ID = 11L;

    /** 测试分类 */
    private static final Long CATEGORY_ID = 12L;

    /** 测试系列 */
    private static final Long SERIES_ID = 20L;

    /** 文件头读取字节数（与实现保持一致） */
    private static final int HEAD_BYTES = 12;

    /** 合法 MP4 文件头 */
    private static final byte[] MP4_HEAD = {0, 0, 0, 0x18, 'f', 't', 'y', 'p', 'm', 'p', '4', '2'};

    @Mock
    private ContentRepository contentRepository;

    @Mock
    private ContentStreamRepository contentStreamRepository;

    @Mock
    private FileAssetPort fileAssetPort;

    @Mock
    private DeclarationRepository declarationRepository;

    @Mock
    private TagRepository tagRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private CreatorRepository creatorRepository;

    @Mock
    private SeriesRepository seriesRepository;

    @Mock
    private ContentLikeRepository contentLikeRepository;

    @Mock
    private ContentFavoriteRepository contentFavoriteRepository;

    @Mock
    private WatchHistoryRepository watchHistoryRepository;

    @Mock
    private PlayableCacheRepository playableCacheRepository;

    @Mock
    private HomeCacheRepository homeCacheRepository;

    @Mock
    private FeedCacheRepository feedCacheRepository;

    @Spy
    private VideoProperties videoProperties = new VideoProperties();

    @InjectMocks
    private ContentAppServiceImpl contentAppService;

    /**
     * 绑定租户上下文，供文件读取与缓存键使用。
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
     * 未签署当前版本上传声明时禁止创建内容。
     */
    @Test
    void shouldRejectCreateWithoutDeclaration() {
        when(declarationRepository.find(USER_ID, videoProperties.getDeclarationVersion())).thenReturn(Optional.empty());
        BizException exception = assertThrows(BizException.class,
                () -> contentAppService.create(command(), USER_ID));
        assertEquals(VideoErrorCode.DECLARATION_REQUIRED, exception.getCode());
        verify(contentRepository, never()).insert(any());
    }

    /**
     * 电影仅运营入库，用户侧创建按无权操作处理。
     */
    @Test
    void shouldRejectUserCreatedMovie() {
        ContentSaveCommand command = command();
        command.setContentType("MOVIE");
        assertEquals(VideoErrorCode.CONTENT_NOT_OWNED,
                assertThrows(BizException.class, () -> contentAppService.create(command, USER_ID)).getCode());
    }

    /**
     * 未知内容类型按不存在处理。
     */
    @Test
    void shouldRejectUnknownContentType() {
        ContentSaveCommand command = command();
        command.setContentType("UNKNOWN");
        assertEquals(VideoErrorCode.CONTENT_NOT_FOUND,
                assertThrows(BizException.class, () -> contentAppService.create(command, USER_ID)).getCode());
    }

    /**
     * 合法创建草稿：文件经 MIME + magic bytes 校验，来源回落声明签署值。
     */
    @Test
    void shouldCreateDraftWithValidatedFile() {
        doAnswer(invocation -> {
            Content content = invocation.getArgument(0);
            content.setId(CONTENT_ID);
            return null;
        }).when(contentRepository).insert(any());
        when(declarationRepository.find(USER_ID, videoProperties.getDeclarationVersion()))
                .thenReturn(Optional.of(declaration(CopyrightSource.ORIGINAL)));
        when(fileAssetPort.find(FILE_ID, TENANT_ID))
                .thenReturn(Optional.of(new FileAsset(FILE_ID, "video/mp4", 1024L, USER_ID, true)));
        when(contentStreamRepository.readHead(FILE_ID, TENANT_ID, HEAD_BYTES)).thenReturn(MP4_HEAD);
        when(fileAssetPort.find(COVER_ID, TENANT_ID))
                .thenReturn(Optional.of(new FileAsset(COVER_ID, "image/png", 512L, USER_ID, true)));
        when(categoryRepository.findById(CATEGORY_ID)).thenReturn(Optional.of(category("ALL")));

        ContentView view = contentAppService.create(command(), USER_ID);

        ArgumentCaptor<Content> captor = ArgumentCaptor.forClass(Content.class);
        verify(contentRepository).insert(captor.capture());
        Content content = captor.getValue();
        assertEquals(CONTENT_ID, view.getId());
        assertEquals(PublishState.DRAFT.name(), view.getPublishState());
        assertEquals(ContentType.VIDEO, content.getContentType());
        assertEquals("MP4", content.getFormat());
        assertEquals(FILE_ID, content.getVideoFileId());
        assertEquals(COVER_ID, content.getCoverFileId());
        assertEquals(1024L, content.getSizeBytes());
        assertEquals(USER_ID, content.getUploaderId());
        assertEquals(CATEGORY_ID, content.getCategoryId());
        assertEquals(CopyrightSource.ORIGINAL, content.getSource());
        verify(contentRepository).replaceTags(CONTENT_ID, List.of(5L));
        verify(contentRepository, never()).update(any());
    }

    /**
     * 勾选立即上架时写入已发布状态并立即失效可播与首页缓存。
     */
    @Test
    void shouldPublishImmediatelyAndEvictCache() {
        ContentSaveCommand command = command();
        command.setPublishNow(true);
        command.setCoverFileId(null);
        when(declarationRepository.find(USER_ID, videoProperties.getDeclarationVersion()))
                .thenReturn(Optional.of(declaration(CopyrightSource.ORIGINAL)));
        when(fileAssetPort.find(FILE_ID, TENANT_ID))
                .thenReturn(Optional.of(new FileAsset(FILE_ID, "video/mp4", 1024L, USER_ID, true)));
        when(contentStreamRepository.readHead(FILE_ID, TENANT_ID, HEAD_BYTES)).thenReturn(MP4_HEAD);
        when(categoryRepository.findById(CATEGORY_ID)).thenReturn(Optional.of(category("ALL")));

        ContentView view = contentAppService.create(command, USER_ID);

        assertEquals(PublishState.PUBLISHED.name(), view.getPublishState());
        verify(contentRepository).update(any());
        verify(playableCacheRepository).evict(any());
        verify(homeCacheRepository).evict(TENANT_ID, "HOME");
    }

    /**
     * 视频文件不存在时按上传文件非法处理，不写入内容。
     */
    @Test
    void shouldRejectUnusableVideoFile() {
        when(declarationRepository.find(USER_ID, videoProperties.getDeclarationVersion()))
                .thenReturn(Optional.of(declaration(CopyrightSource.ORIGINAL)));
        when(fileAssetPort.find(FILE_ID, TENANT_ID)).thenReturn(Optional.empty());
        BizException exception = assertThrows(BizException.class,
                () -> contentAppService.create(command(), USER_ID));
        assertEquals(VideoErrorCode.UPLOAD_FILE_INVALID, exception.getCode());
        verify(contentRepository, never()).insert(any());
    }

    /**
     * 封面未通过图片白名单时拒绝创建（40004）。
     */
    @Test
    void shouldRejectNonImageCover() {
        when(declarationRepository.find(USER_ID, videoProperties.getDeclarationVersion()))
                .thenReturn(Optional.of(declaration(CopyrightSource.ORIGINAL)));
        when(fileAssetPort.find(FILE_ID, TENANT_ID))
                .thenReturn(Optional.of(new FileAsset(FILE_ID, "video/mp4", 1024L, USER_ID, true)));
        when(fileAssetPort.find(COVER_ID, TENANT_ID))
                .thenReturn(Optional.of(new FileAsset(COVER_ID, "application/pdf", 512L, USER_ID, true)));
        when(contentStreamRepository.readHead(FILE_ID, TENANT_ID, HEAD_BYTES)).thenReturn(MP4_HEAD);

        assertEquals(VideoErrorCode.IMAGE_FORMAT_NOT_ALLOWED,
                assertThrows(BizException.class, () -> contentAppService.create(command(), USER_ID)).getCode());
    }

    /**
     * 分类不存在按 40402 返回。
     */
    @Test
    void shouldRejectMissingCategory() {
        when(declarationRepository.find(USER_ID, videoProperties.getDeclarationVersion()))
                .thenReturn(Optional.of(declaration(CopyrightSource.ORIGINAL)));
        when(fileAssetPort.find(FILE_ID, TENANT_ID))
                .thenReturn(Optional.of(new FileAsset(FILE_ID, "video/mp4", 1024L, USER_ID, true)));
        when(contentStreamRepository.readHead(FILE_ID, TENANT_ID, HEAD_BYTES)).thenReturn(MP4_HEAD);
        when(categoryRepository.findById(CATEGORY_ID)).thenReturn(Optional.empty());

        assertEquals(VideoErrorCode.CATEGORY_NOT_FOUND,
                assertThrows(BizException.class, () -> contentAppService.create(commandWithoutCover(), USER_ID))
                        .getCode());
    }

    /**
     * 分类范围与内容类型不匹配时按分类非法返回（40012）。
     */
    @Test
    void shouldRejectCategoryOutOfScope() {
        when(declarationRepository.find(USER_ID, videoProperties.getDeclarationVersion()))
                .thenReturn(Optional.of(declaration(CopyrightSource.ORIGINAL)));
        when(fileAssetPort.find(FILE_ID, TENANT_ID))
                .thenReturn(Optional.of(new FileAsset(FILE_ID, "video/mp4", 1024L, USER_ID, true)));
        when(contentStreamRepository.readHead(FILE_ID, TENANT_ID, HEAD_BYTES)).thenReturn(MP4_HEAD);
        when(categoryRepository.findById(CATEGORY_ID)).thenReturn(Optional.of(category("MOVIE")));

        assertEquals(VideoErrorCode.CATEGORY_INVALID,
                assertThrows(BizException.class, () -> contentAppService.create(commandWithoutCover(), USER_ID))
                        .getCode());
    }

    /**
     * 命令与声明均无内容来源时拒绝入库（40009）。
     */
    @Test
    void shouldRequireContentSource() {
        when(declarationRepository.find(USER_ID, videoProperties.getDeclarationVersion()))
                .thenReturn(Optional.of(declaration(null)));
        when(fileAssetPort.find(FILE_ID, TENANT_ID))
                .thenReturn(Optional.of(new FileAsset(FILE_ID, "video/mp4", 1024L, USER_ID, true)));
        when(contentStreamRepository.readHead(FILE_ID, TENANT_ID, HEAD_BYTES)).thenReturn(MP4_HEAD);
        when(categoryRepository.findById(CATEGORY_ID)).thenReturn(Optional.of(category("ALL")));

        assertEquals(VideoErrorCode.CONTENT_SOURCE_REQUIRED,
                assertThrows(BizException.class, () -> contentAppService.create(commandWithoutCover(), USER_ID))
                        .getCode());
        verify(contentRepository, never()).insert(any());
    }

    /**
     * 时长超过类型上限时拒绝创建（40002）。
     */
    @Test
    void shouldRejectDurationBeyondLimit() {
        ContentSaveCommand command = command();
        command.setDurationMs(7_200_001L);
        command.setCoverFileId(null);
        when(declarationRepository.find(USER_ID, videoProperties.getDeclarationVersion()))
                .thenReturn(Optional.of(declaration(CopyrightSource.ORIGINAL)));
        when(fileAssetPort.find(FILE_ID, TENANT_ID))
                .thenReturn(Optional.of(new FileAsset(FILE_ID, "video/mp4", 1024L, USER_ID, true)));
        when(contentStreamRepository.readHead(FILE_ID, TENANT_ID, HEAD_BYTES)).thenReturn(MP4_HEAD);
        when(categoryRepository.findById(CATEGORY_ID)).thenReturn(Optional.of(category("ALL")));

        assertEquals(VideoErrorCode.CONTENT_DURATION_INVALID,
                assertThrows(BizException.class, () -> contentAppService.create(command, USER_ID)).getCode());
    }

    /**
     * 非本人内容不允许编辑（40301）。
     */
    @Test
    void shouldRejectUpdateByNonOwner() {
        Content content = publishedContent();
        content.setUploaderId(999L);
        when(contentRepository.findById(CONTENT_ID)).thenReturn(Optional.of(content));
        assertEquals(VideoErrorCode.CONTENT_NOT_OWNED,
                assertThrows(BizException.class, () -> contentAppService.update(CONTENT_ID, command(), USER_ID)).getCode());
        verify(contentRepository, never()).update(any());
    }

    /**
     * 他人私密 / 未发布内容按不存在处理（40401，不泄露存在性）。
     */
    @Test
    void shouldHidePrivateContentOfOthers() {
        Content content = publishedContent();
        content.setUploaderId(999L);
        content.setVisibility(com.bluewhale.video.domain.model.ContentVisibility.PRIVATE);
        when(contentRepository.findById(CONTENT_ID)).thenReturn(Optional.of(content));
        assertEquals(VideoErrorCode.CONTENT_NOT_FOUND,
                assertThrows(BizException.class, () -> contentAppService.ownerView(CONTENT_ID, USER_ID)).getCode());
    }

    /**
     * 版权下架强制下线、记录原因并失效缓存。
     */
    @Test
    void shouldTakedownAndEvictCache() {
        when(contentRepository.findById(CONTENT_ID)).thenReturn(Optional.of(publishedContent()));
        ContentView view = contentAppService.adminTakedown(CONTENT_ID, "  侵权投诉  ");
        assertEquals(PublishState.OFFLINE.name(), view.getPublishState());
        assertEquals("TAKEDOWN", view.getCopyrightState());
        assertEquals("侵权投诉", view.getTakedownReason());
        verify(contentRepository).update(any());
        verify(playableCacheRepository).evict(CONTENT_ID);
        verify(homeCacheRepository).evict(TENANT_ID, "HOME");
    }

    /**
     * 系列不存在时拒绝入库（40404）。
     */
    @Test
    void shouldRejectMissingSeries() {
        ContentSaveCommand command = seriesCommand(1);
        stubValidUpload();
        when(seriesRepository.findById(SERIES_ID)).thenReturn(Optional.empty());

        assertEquals(VideoErrorCode.SERIES_NOT_FOUND,
                assertThrows(BizException.class, () -> contentAppService.create(command, USER_ID)).getCode());
        verify(contentRepository, never()).insert(any());
    }

    /**
     * 系列内容未填集数时按集数非法返回（40013）。
     */
    @Test
    void shouldRejectEpisodeNoMissing() {
        ContentSaveCommand command = seriesCommand(null);
        stubValidUpload();
        when(seriesRepository.findById(SERIES_ID)).thenReturn(Optional.of(series()));

        assertEquals(VideoErrorCode.EPISODE_NO_INVALID,
                assertThrows(BizException.class, () -> contentAppService.create(command, USER_ID)).getCode());
        verify(contentRepository, never()).insert(any());
    }

    /**
     * 同系列集数已存在时拒绝入库（40902）。
     */
    @Test
    void shouldRejectDuplicateEpisode() {
        ContentSaveCommand command = seriesCommand(3);
        stubValidUpload();
        when(seriesRepository.findById(SERIES_ID)).thenReturn(Optional.of(series()));
        when(contentRepository.existsSeriesEpisode(SERIES_ID, 3, null)).thenReturn(true);

        assertEquals(VideoErrorCode.EPISODE_CONFLICT,
                assertThrows(BizException.class, () -> contentAppService.create(command, USER_ID)).getCode());
        verify(contentRepository, never()).insert(any());
    }

    /**
     * 合法入系列：写入系列与集数，立即上架后刷新系列集数为 1。
     */
    @Test
    void shouldCreateSeriesEpisodeAndRefreshCount() {
        ContentSaveCommand command = seriesCommand(2);
        command.setPublishNow(true);
        stubValidUpload();
        when(seriesRepository.findById(SERIES_ID)).thenReturn(Optional.of(series()));
        when(contentRepository.existsSeriesEpisode(SERIES_ID, 2, null)).thenReturn(false);
        doAnswer(invocation -> {
            Content content = invocation.getArgument(0);
            content.setId(CONTENT_ID);
            return null;
        }).when(contentRepository).insert(any());
        when(contentRepository.countPublishedEpisodes(SERIES_ID)).thenReturn(1);

        ContentView view = contentAppService.create(command, USER_ID);

        assertEquals(SERIES_ID, view.getSeriesId());
        assertEquals(2, view.getEpisodeNo());
        assertEquals(PublishState.PUBLISHED.name(), view.getPublishState());
        verify(seriesRepository).updateEpisodeCount(SERIES_ID, 1);
    }

    /**
     * 构造系列集的保存命令。
     *
     * @param episodeNo 集数
     * @return 保存命令
     */
    private ContentSaveCommand seriesCommand(Integer episodeNo) {
        ContentSaveCommand command = commandWithoutCover();
        command.setSeriesId(SERIES_ID);
        command.setEpisodeNo(episodeNo);
        return command;
    }

    /**
     * 构造已上架系列。
     *
     * @return 系列
     */
    private Series series() {
        Series series = new Series();
        series.setId(SERIES_ID);
        series.setTitle("深海探秘");
        series.setContentType(ContentType.VIDEO);
        series.setPublishState(PublishState.PUBLISHED);
        series.setEpisodeCount(0);
        return series;
    }

    /**
     * 声明、视频文件与分类的合法桩（不含封面）。
     */
    private void stubValidUpload() {
        when(declarationRepository.find(USER_ID, videoProperties.getDeclarationVersion()))
                .thenReturn(Optional.of(declaration(CopyrightSource.ORIGINAL)));
        when(fileAssetPort.find(FILE_ID, TENANT_ID))
                .thenReturn(Optional.of(new FileAsset(FILE_ID, "video/mp4", 1024L, USER_ID, true)));
        when(contentStreamRepository.readHead(FILE_ID, TENANT_ID, HEAD_BYTES)).thenReturn(MP4_HEAD);
        when(categoryRepository.findById(CATEGORY_ID)).thenReturn(Optional.of(category("ALL")));
    }

    /**
     * 构造内容保存命令。
     *
     * @return 保存命令
     */
    private ContentSaveCommand command() {
        ContentSaveCommand command = new ContentSaveCommand();
        command.setContentType("VIDEO");
        command.setTitle("深海纪录片");
        command.setVideoFileId(FILE_ID);
        command.setCoverFileId(COVER_ID);
        command.setDurationMs(60_000L);
        command.setWidth(1920);
        command.setHeight(1080);
        command.setCategoryId(CATEGORY_ID);
        command.setTagIds(List.of(5L));
        return command;
    }

    /**
     * 构造不含封面的内容保存命令（用于隔离封面校验）。
     *
     * @return 保存命令
     */
    private ContentSaveCommand commandWithoutCover() {
        ContentSaveCommand command = command();
        command.setCoverFileId(null);
        return command;
    }

    /**
     * 构造已签署声明。
     *
     * @param source 声明中的内容来源
     * @return 声明
     */
    private UploadDeclaration declaration(CopyrightSource source) {
        UploadDeclaration declaration = new UploadDeclaration();
        declaration.setUserId(USER_ID);
        declaration.setDeclarationVersion("V1");
        declaration.setContentSource(source);
        return declaration;
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
     * 构造已发布内容。
     *
     * @return 内容
     */
    private Content publishedContent() {
        Content content = new Content();
        content.setId(CONTENT_ID);
        content.setContentType(ContentType.VIDEO);
        content.setTitle("深海纪录片");
        content.setUploaderId(USER_ID);
        content.setVideoFileId(FILE_ID);
        content.setDurationMs(3_600_000L);
        content.setPublishState(PublishState.PUBLISHED);
        content.setCopyrightState(com.bluewhale.video.domain.model.CopyrightState.AUTHORIZED);
        content.setVisibility(com.bluewhale.video.domain.model.ContentVisibility.PUBLIC);
        return content;
    }
}
