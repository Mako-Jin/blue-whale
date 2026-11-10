package com.bluewhale.video.application.service.impl;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.tenant.TenantContext;
import com.bluewhale.video.application.command.MovieQueryCommand;
import com.bluewhale.video.application.command.MovieSaveCommand;
import com.bluewhale.video.application.support.PublicContentViewAssembler;
import com.bluewhale.video.application.view.MovieAdminView;
import com.bluewhale.video.application.view.MovieDetailView;
import com.bluewhale.video.application.view.MovieView;
import com.bluewhale.video.application.view.PublicContentView;
import com.bluewhale.video.config.VideoProperties;
import com.bluewhale.video.domain.exception.VideoErrorCode;
import com.bluewhale.video.domain.model.Content;
import com.bluewhale.video.domain.model.ContentType;
import com.bluewhale.video.domain.model.ContentVisibility;
import com.bluewhale.video.domain.model.CopyrightState;
import com.bluewhale.video.domain.model.Movie;
import com.bluewhale.video.domain.model.MovieLicenseSource;
import com.bluewhale.video.domain.model.PublishState;
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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 电影应用服务单元测试（M4-2 / M4-3）：授权校验、状态流转、可见性与图片代理。
 *
 * @author BlueWhale
 */
@ExtendWith(MockitoExtension.class)
class MovieAppServiceImplTest {

    /** 测试租户 */
    private static final long TENANT_ID = 1L;

    /** 测试用户 */
    private static final long USER_ID = 7L;

    /** 测试内容 / 电影 ID */
    private static final long CONTENT_ID = 88L;

    /** 测试视频文件 */
    private static final long VIDEO_FILE_ID = 55L;

    /** 测试海报文件 */
    private static final long POSTER_FILE_ID = 66L;

    /** 电影时长（2 小时，未超上限） */
    private static final long MOVIE_DURATION_MS = 7_200_000L;

    @Mock
    private ContentRepository contentRepository;

    @Mock
    private ContentStreamRepository contentStreamRepository;

    @Mock
    private MovieRepository movieRepository;

    @Mock
    private MovieCollectionRepository movieCollectionRepository;

    @Mock
    private FileAssetPort fileAssetPort;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private CreatorRepository creatorRepository;

    @Mock
    private TagRepository tagRepository;

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

    @Mock
    private PublicContentViewAssembler contentViewAssembler;

    @Spy
    private VideoProperties videoProperties = new VideoProperties();

    @InjectMocks
    private MovieAppServiceImpl movieAppService;

    /**
     * 绑定租户上下文（文件资产读取与缓存失效需要）。
     */
    @BeforeEach
    void bindTenant() {
        TenantContext.set(TENANT_ID);
    }

    /**
     * 清理租户上下文。
     */
    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    /**
     * 授权有效时允许上架：内容切到 PUBLISHED 并失效可播 / 首页 / feed 缓存。
     */
    @Test
    void shouldPublishMovieWithValidLicense() {
        Content content = content(PublishState.DRAFT);
        when(contentRepository.findById(CONTENT_ID)).thenReturn(Optional.of(content));
        when(movieRepository.findByContentId(CONTENT_ID))
                .thenReturn(Optional.of(movie(LocalDateTime.now().plusDays(30))));
        when(contentRepository.findTagIds(CONTENT_ID)).thenReturn(Collections.emptyList());

        MovieAdminView view = movieAppService.publish(CONTENT_ID);

        assertEquals(PublishState.PUBLISHED.name(), view.getPublishState());
        verify(contentRepository).update(content);
        verify(playableCacheRepository).evict(CONTENT_ID);
        verify(homeCacheRepository).evict(TENANT_ID, "HOME");
        verify(feedCacheRepository).evictAll(TENANT_ID);
    }

    /**
     * 授权已到期时拒绝上架（40011），且不写库。
     */
    @Test
    void shouldRejectPublishWithExpiredLicense() {
        Content content = content(PublishState.DRAFT);
        when(contentRepository.findById(CONTENT_ID)).thenReturn(Optional.of(content));
        when(movieRepository.findByContentId(CONTENT_ID))
                .thenReturn(Optional.of(movie(LocalDateTime.now().minusDays(1))));

        assertEquals(VideoErrorCode.MOVIE_LICENSE_INVALID,
                assertThrows(BizException.class, () -> movieAppService.publish(CONTENT_ID)).getCode());
        verify(contentRepository, never()).update(any());
    }

    /**
     * 已上架电影可下架，并保持元数据不变。
     */
    @Test
    void shouldOfflinePublishedMovie() {
        Content content = content(PublishState.PUBLISHED);
        when(contentRepository.findById(CONTENT_ID)).thenReturn(Optional.of(content));
        when(movieRepository.findByContentId(CONTENT_ID))
                .thenReturn(Optional.of(movie(LocalDateTime.now().plusDays(30))));
        when(contentRepository.findTagIds(CONTENT_ID)).thenReturn(Collections.emptyList());

        MovieAdminView view = movieAppService.offline(CONTENT_ID);

        assertEquals(PublishState.OFFLINE.name(), view.getPublishState());
        verify(contentRepository).update(content);
    }

    /**
     * 版权下架原因缺失时拒绝操作（40017）。
     */
    @Test
    void shouldRejectTakedownWithoutReason() {
        Content content = content(PublishState.PUBLISHED);
        when(contentRepository.findById(CONTENT_ID)).thenReturn(Optional.of(content));
        when(movieRepository.findByContentId(CONTENT_ID))
                .thenReturn(Optional.of(movie(LocalDateTime.now().plusDays(30))));

        assertEquals(VideoErrorCode.TAKEDOWN_REASON_REQUIRED,
                assertThrows(BizException.class,
                        () -> movieAppService.takedown(CONTENT_ID, "  ")).getCode());
        verify(contentRepository, never()).update(any());
    }

    /**
     * 未上架电影的用户侧详情按不存在处理（40401）。
     */
    @Test
    void shouldHideUnpublishedMovieDetail() {
        when(contentRepository.findById(CONTENT_ID)).thenReturn(Optional.of(content(PublishState.DRAFT)));
        assertEquals(VideoErrorCode.CONTENT_NOT_FOUND,
                assertThrows(BizException.class, () -> movieAppService.detail(CONTENT_ID, USER_ID)).getCode());
        verifyNoInteractions(movieRepository);
    }

    /**
     * 已上架电影详情返回电影元数据、海报代理与相关片单。
     */
    @Test
    void shouldReturnMovieDetailWithPosterAndCollections() {
        Content content = content(PublishState.PUBLISHED);
        when(contentRepository.findById(CONTENT_ID)).thenReturn(Optional.of(content));
        Movie movie = movie(LocalDateTime.now().plusDays(30));
        movie.setDirector("朱塞佩·托纳多雷");
        when(movieRepository.findByContentId(CONTENT_ID)).thenReturn(Optional.of(movie));
        when(contentRepository.findTagIds(CONTENT_ID)).thenReturn(Collections.emptyList());
        when(contentLikeRepository.exists(USER_ID, CONTENT_ID)).thenReturn(false);
        when(contentFavoriteRepository.exists(USER_ID, CONTENT_ID)).thenReturn(false);
        when(watchHistoryRepository.find(USER_ID, CONTENT_ID)).thenReturn(Optional.empty());
        when(movieCollectionRepository.listPublishedByContentId(CONTENT_ID, 6))
                .thenReturn(Collections.emptyList());

        MovieDetailView view = movieAppService.detail(CONTENT_ID, USER_ID);

        assertEquals("朱塞佩·托纳多雷", view.getDirector());
        assertEquals("/video/movies/88/poster", view.getPosterUrl());
        assertEquals("/video/movies/88/backdrop", view.getBackdropUrl());
        assertEquals(Collections.emptyList(), view.getCollections());
    }

    /**
     * 海报代理读取元数据中的文件 ID。
     */
    @Test
    void shouldReturnPosterFileId() {
        when(contentRepository.findById(CONTENT_ID))
                .thenReturn(Optional.of(content(PublishState.PUBLISHED)));
        when(movieRepository.findByContentId(CONTENT_ID))
                .thenReturn(Optional.of(movie(LocalDateTime.now().plusDays(30))));
        assertEquals(POSTER_FILE_ID, movieAppService.posterFileId(CONTENT_ID, USER_ID));
    }

    /**
     * 未设置海报时返回空，前端不渲染图片。
     */
    @Test
    void shouldReturnNullPosterWhenUnset() {
        Movie movie = movie(LocalDateTime.now().plusDays(30));
        movie.setPosterFileId(null);
        when(contentRepository.findById(CONTENT_ID))
                .thenReturn(Optional.of(content(PublishState.PUBLISHED)));
        when(movieRepository.findByContentId(CONTENT_ID)).thenReturn(Optional.of(movie));
        assertNull(movieAppService.posterFileId(CONTENT_ID, USER_ID));
    }

    /**
     * 视频文件缺失时拒绝入库（40006），不写内容与元数据。
     */
    @Test
    void shouldRejectCreateWhenVideoAssetMissing() {
        when(fileAssetPort.find(VIDEO_FILE_ID, TENANT_ID)).thenReturn(Optional.empty());

        assertEquals(VideoErrorCode.UPLOAD_FILE_INVALID,
                assertThrows(BizException.class,
                        () -> movieAppService.create(command(LocalDateTime.now().plusDays(10)))).getCode());
        verify(contentRepository, never()).insert(any());
        verify(movieRepository, never()).insert(any());
    }

    /**
     * 授权已过期时拒绝入库（40011），不读取文件资产。
     */
    @Test
    void shouldRejectCreateWhenLicenseExpired() {
        assertEquals(VideoErrorCode.MOVIE_LICENSE_INVALID,
                assertThrows(BizException.class,
                        () -> movieAppService.create(command(LocalDateTime.now().minusMinutes(1)))).getCode());
        verifyNoInteractions(fileAssetPort, contentRepository, movieRepository);
    }

    /**
     * 用户侧电影分页只返回可见内容并补充电影元数据。
     */
    @Test
    void shouldPageVisibleMovies() {
        Content content = content(PublishState.PUBLISHED);
        when(contentRepository.page(any())).thenReturn(List.of(content));
        when(contentRepository.count(any())).thenReturn(1L);
        when(movieRepository.findByContentIds(List.of(CONTENT_ID)))
                .thenReturn(Map.of(CONTENT_ID, movie(LocalDateTime.now().plusDays(30))));
        when(contentViewAssembler.assemble(List.of(content))).thenReturn(List.of(publicView()));

        PageResult<MovieView> page = movieAppService.publicPage(new MovieQueryCommand(), USER_ID);

        assertEquals(1L, page.getTotal());
        assertEquals(1, page.getRecords().size());
        assertEquals("/video/movies/88/poster", page.getRecords().get(0).getPosterUrl());
    }

    /**
     * 运营侧分页只返回存在电影元数据的记录。
     */
    @Test
    void shouldPageAdminMoviesWithMetadata() {
        Content content = content(PublishState.DRAFT);
        when(contentRepository.page(any())).thenReturn(List.of(content));
        when(contentRepository.count(any())).thenReturn(1L);
        when(movieRepository.findByContentIds(List.of(CONTENT_ID)))
                .thenReturn(Map.of(CONTENT_ID, movie(LocalDateTime.now().plusDays(30))));
        when(contentRepository.findTagIdsByContentIds(List.of(CONTENT_ID)))
                .thenReturn(Collections.emptyMap());

        PageResult<MovieAdminView> page = movieAppService.adminPage(new MovieQueryCommand());

        assertEquals(1L, page.getTotal());
        assertEquals("LICENSED", page.getRecords().get(0).getLicenseSource());
        assertEquals(POSTER_FILE_ID, page.getRecords().get(0).getPosterFileId());
    }

    /**
     * 构造内容。
     *
     * @param state 发布状态
     * @return 内容
     */
    private Content content(PublishState state) {
        Content content = new Content();
        content.setId(CONTENT_ID);
        content.setContentType(ContentType.MOVIE);
        content.setTitle("海上钢琴师");
        content.setVideoFileId(VIDEO_FILE_ID);
        content.setDurationMs(MOVIE_DURATION_MS);
        content.setPublishState(state);
        content.setCopyrightState(CopyrightState.AUTHORIZED);
        content.setVisibility(ContentVisibility.PUBLIC);
        content.setUploaderId(0L);
        content.setCreatorId(0L);
        content.setCategoryId(0L);
        return content;
    }

    /**
     * 构造电影元数据。
     *
     * @param expireAt 授权到期时间
     * @return 电影元数据
     */
    private Movie movie(LocalDateTime expireAt) {
        Movie movie = new Movie();
        movie.setContentId(CONTENT_ID);
        movie.setOriginalTitle("The Legend of 1900");
        movie.setLicenseSource(MovieLicenseSource.LICENSED);
        movie.setLicenseNo("L-2026-001");
        movie.setLicenseExpireAt(expireAt);
        movie.setPosterFileId(POSTER_FILE_ID);
        movie.setBackdropFileId(POSTER_FILE_ID + 1);
        return movie;
    }

    /**
     * 构造电影入库命令（仅填充校验所需字段）。
     *
     * @param expireAt 授权到期时间
     * @return 命令
     */
    private MovieSaveCommand command(LocalDateTime expireAt) {
        MovieSaveCommand command = new MovieSaveCommand();
        command.setVideoFileId(VIDEO_FILE_ID);
        command.setTitle("海上钢琴师");
        command.setDurationMs(MOVIE_DURATION_MS);
        command.setLicenseSource("LICENSED");
        command.setLicenseExpireAt(expireAt);
        return command;
    }

    /**
     * 构造用户侧内容视图。
     *
     * @return 视图
     */
    private PublicContentView publicView() {
        PublicContentView view = new PublicContentView();
        view.setId(CONTENT_ID);
        view.setTitle("海上钢琴师");
        return view;
    }
}
