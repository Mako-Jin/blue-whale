package com.bluewhale.video.application.service.impl;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.tenant.TenantContext;
import com.bluewhale.video.application.command.CollectionQueryCommand;
import com.bluewhale.video.application.command.MovieCollectionItemCommand;
import com.bluewhale.video.application.command.MovieCollectionSaveCommand;
import com.bluewhale.video.application.support.PublicContentViewAssembler;
import com.bluewhale.video.application.view.MovieCollectionDetailView;
import com.bluewhale.video.application.view.MovieCollectionView;
import com.bluewhale.video.config.VideoProperties;
import com.bluewhale.video.domain.exception.VideoErrorCode;
import com.bluewhale.video.domain.model.CollectionQuery;
import com.bluewhale.video.domain.model.Content;
import com.bluewhale.video.domain.model.ContentType;
import com.bluewhale.video.domain.model.ContentVisibility;
import com.bluewhale.video.domain.model.CopyrightState;
import com.bluewhale.video.domain.model.FileAsset;
import com.bluewhale.video.domain.model.Movie;
import com.bluewhale.video.domain.model.MovieCollection;
import com.bluewhale.video.domain.model.PublishState;
import com.bluewhale.video.domain.repository.ContentRepository;
import com.bluewhale.video.domain.repository.FileAssetPort;
import com.bluewhale.video.domain.repository.MovieCollectionRepository;
import com.bluewhale.video.domain.repository.MovieRepository;
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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 电影片单应用服务单元测试（M4-4）：封面校验、状态流转、加片幂等与条目数刷新。
 *
 * @author BlueWhale
 */
@ExtendWith(MockitoExtension.class)
class MovieCollectionAppServiceImplTest {

    /** 测试租户 */
    private static final long TENANT_ID = 1L;

    /** 测试片单 */
    private static final long COLLECTION_ID = 31L;

    /** 测试电影内容 */
    private static final long CONTENT_ID = 88L;

    /** 测试封面文件 */
    private static final long COVER_FILE_ID = 66L;

    @Mock
    private MovieCollectionRepository movieCollectionRepository;

    @Mock
    private MovieRepository movieRepository;

    @Mock
    private ContentRepository contentRepository;

    @Mock
    private FileAssetPort fileAssetPort;

    @Mock
    private PublicContentViewAssembler contentViewAssembler;

    @Spy
    private VideoProperties videoProperties = new VideoProperties();

    @InjectMocks
    private MovieCollectionAppServiceImpl movieCollectionAppService;

    /**
     * 绑定租户上下文（封面文件校验需要）。
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
     * 新建片单为草稿、条目数 0，无封面时不生成封面地址。
     */
    @Test
    void shouldCreateDraftCollection() {
        doAnswer(invocation -> {
            MovieCollection collection = invocation.getArgument(0);
            collection.setId(COLLECTION_ID);
            return null;
        }).when(movieCollectionRepository).insert(any());
        MovieCollectionSaveCommand command = new MovieCollectionSaveCommand();
        command.setTitle("经典重映");
        command.setDescription("  ");
        command.setSortNo(2);

        MovieCollectionView view = movieCollectionAppService.create(command);

        assertEquals(COLLECTION_ID, view.getId());
        assertEquals(PublishState.DRAFT.name(), view.getPublishState());
        assertEquals(0, view.getItemCount());
        assertEquals(2, view.getSortNo());
        assertNull(view.getCoverUrl());
    }

    /**
     * 封面不是图片时拒绝新建（40004），且不落库。
     */
    @Test
    void shouldRejectNonImageCover() {
        when(fileAssetPort.find(COVER_FILE_ID, TENANT_ID))
                .thenReturn(Optional.of(new FileAsset(COVER_FILE_ID, "application/pdf", 512L, 0L, true)));
        MovieCollectionSaveCommand command = new MovieCollectionSaveCommand();
        command.setTitle("经典重映");
        command.setCoverFileId(COVER_FILE_ID);

        assertEquals(VideoErrorCode.IMAGE_FORMAT_NOT_ALLOWED,
                assertThrows(BizException.class, () -> movieCollectionAppService.create(command)).getCode());
        verify(movieCollectionRepository, never()).insert(any());
    }

    /**
     * 更新时封面传 0 表示清除封面。
     */
    @Test
    void shouldClearCoverWhenUpdateWithZero() {
        MovieCollection collection = collection(PublishState.PUBLISHED);
        collection.setCoverFileId(COVER_FILE_ID);
        when(movieCollectionRepository.findById(COLLECTION_ID)).thenReturn(Optional.of(collection));
        MovieCollectionSaveCommand command = new MovieCollectionSaveCommand();
        command.setTitle("经典重映");
        command.setCoverFileId(0L);

        MovieCollectionView view = movieCollectionAppService.update(COLLECTION_ID, command);

        assertNull(view.getCoverUrl());
        assertNull(collection.getCoverFileId());
        verify(movieCollectionRepository).update(collection);
    }

    /**
     * 草稿片单可上架，重复上架幂等。
     */
    @Test
    void shouldPublishCollection() {
        MovieCollection collection = collection(PublishState.DRAFT);
        when(movieCollectionRepository.findById(COLLECTION_ID)).thenReturn(Optional.of(collection));

        MovieCollectionView view = movieCollectionAppService.publish(COLLECTION_ID);

        assertEquals(PublishState.PUBLISHED.name(), view.getPublishState());
        verify(movieCollectionRepository).update(collection);
    }

    /**
     * 草稿不可下架（40901）。
     */
    @Test
    void shouldRejectOfflineDraftCollection() {
        MovieCollection collection = collection(PublishState.DRAFT);
        when(movieCollectionRepository.findById(COLLECTION_ID)).thenReturn(Optional.of(collection));

        assertEquals(VideoErrorCode.CONTENT_STATE_CONFLICT,
                assertThrows(BizException.class,
                        () -> movieCollectionAppService.offline(COLLECTION_ID)).getCode());
        verify(movieCollectionRepository, never()).update(any());
    }

    /**
     * 未上架片单的用户侧详情按不存在处理（40405）。
     */
    @Test
    void shouldHideUnpublishedCollectionDetail() {
        when(movieCollectionRepository.findById(COLLECTION_ID))
                .thenReturn(Optional.of(collection(PublishState.DRAFT)));

        assertEquals(VideoErrorCode.COLLECTION_NOT_FOUND,
                assertThrows(BizException.class,
                        () -> movieCollectionAppService.publicDetail(COLLECTION_ID)).getCode());
        verify(movieCollectionRepository, never()).listItemContentIds(anyLong());
    }

    /**
     * 加片目标为电影内容时追加到片单末尾并刷新条目数。
     */
    @Test
    void shouldAddItemAndRefreshItemCount() {
        MovieCollection collection = collection(PublishState.DRAFT);
        when(movieCollectionRepository.findById(COLLECTION_ID)).thenReturn(Optional.of(collection));
        when(contentRepository.findById(CONTENT_ID)).thenReturn(Optional.of(content()));
        Movie movie = new Movie();
        movie.setContentId(CONTENT_ID);
        when(movieRepository.findByContentId(CONTENT_ID)).thenReturn(Optional.of(movie));
        when(movieCollectionRepository.existsItem(COLLECTION_ID, CONTENT_ID)).thenReturn(false);
        when(movieCollectionRepository.countItems(COLLECTION_ID)).thenReturn(2, 3);

        MovieCollectionDetailView view = movieCollectionAppService.addItem(COLLECTION_ID, itemCommand(null));

        assertEquals(3, view.getItemCount());
        verify(movieCollectionRepository).addItem(COLLECTION_ID, CONTENT_ID, 3);
        verify(movieCollectionRepository).update(collection);
    }

    /**
     * 同一电影重复加片幂等：不再写条目、不刷新条目数。
     */
    @Test
    void shouldAddItemIdempotently() {
        MovieCollection collection = collection(PublishState.DRAFT);
        when(movieCollectionRepository.findById(COLLECTION_ID)).thenReturn(Optional.of(collection));
        when(contentRepository.findById(CONTENT_ID)).thenReturn(Optional.of(content()));
        Movie movie = new Movie();
        movie.setContentId(CONTENT_ID);
        when(movieRepository.findByContentId(CONTENT_ID)).thenReturn(Optional.of(movie));
        when(movieCollectionRepository.existsItem(COLLECTION_ID, CONTENT_ID)).thenReturn(true);

        movieCollectionAppService.addItem(COLLECTION_ID, itemCommand(1));

        verify(movieCollectionRepository, never()).addItem(anyLong(), anyLong(), anyInt());
        verify(movieCollectionRepository, never()).countItems(anyLong());
        verify(movieCollectionRepository, never()).update(any());
    }

    /**
     * 加片目标不是电影时按不存在处理（40401）。
     */
    @Test
    void shouldRejectAddNonMovieItem() {
        MovieCollection collection = collection(PublishState.DRAFT);
        when(movieCollectionRepository.findById(COLLECTION_ID)).thenReturn(Optional.of(collection));
        Content content = content();
        content.setContentType(ContentType.VIDEO);
        when(contentRepository.findById(CONTENT_ID)).thenReturn(Optional.of(content));

        assertEquals(VideoErrorCode.CONTENT_NOT_FOUND,
                assertThrows(BizException.class,
                        () -> movieCollectionAppService.addItem(COLLECTION_ID, itemCommand(null))).getCode());
        verify(movieCollectionRepository, never()).addItem(anyLong(), anyLong(), anyInt());
    }

    /**
     * 移除片单内电影后刷新条目数。
     */
    @Test
    void shouldRemoveItemAndRefreshItemCount() {
        MovieCollection collection = collection(PublishState.DRAFT);
        collection.setItemCount(2);
        when(movieCollectionRepository.findById(COLLECTION_ID)).thenReturn(Optional.of(collection));
        when(movieCollectionRepository.existsItem(COLLECTION_ID, CONTENT_ID)).thenReturn(true);
        when(movieCollectionRepository.countItems(COLLECTION_ID)).thenReturn(1);

        MovieCollectionDetailView view = movieCollectionAppService.removeItem(COLLECTION_ID, CONTENT_ID);

        assertEquals(1, view.getItemCount());
        verify(movieCollectionRepository).removeItem(COLLECTION_ID, CONTENT_ID);
        verify(movieCollectionRepository).update(collection);
    }

    /**
     * 移除不在片单内的电影不写库。
     */
    @Test
    void shouldSkipRemoveWhenItemMissing() {
        MovieCollection collection = collection(PublishState.DRAFT);
        when(movieCollectionRepository.findById(COLLECTION_ID)).thenReturn(Optional.of(collection));
        when(movieCollectionRepository.existsItem(COLLECTION_ID, CONTENT_ID)).thenReturn(false);

        movieCollectionAppService.removeItem(COLLECTION_ID, CONTENT_ID);

        verify(movieCollectionRepository, never()).removeItem(anyLong(), anyLong());
        verify(movieCollectionRepository, never()).update(any());
    }

    /**
     * 用户侧片单分页强制只查已上架。
     */
    @Test
    void shouldForcePublishedOnPublicPage() {
        when(movieCollectionRepository.page(any())).thenReturn(List.of(collection(PublishState.PUBLISHED)));
        when(movieCollectionRepository.count(any())).thenReturn(1L);

        PageResult<MovieCollectionView> page = movieCollectionAppService.publicPage(new CollectionQueryCommand());

        assertEquals(1L, page.getTotal());
        assertEquals(1, page.getRecords().size());
        ArgumentCaptor<CollectionQuery> captor = ArgumentCaptor.forClass(CollectionQuery.class);
        verify(movieCollectionRepository).page(captor.capture());
        assertEquals(PublishState.PUBLISHED, captor.getValue().getPublishState());
    }

    /**
     * 运营侧片单分页的状态筛选非法时返回参数错误。
     */
    @Test
    void shouldRejectInvalidStateFilter() {
        CollectionQueryCommand command = new CollectionQueryCommand();
        command.setPublishState("UNKNOWN");

        assertEquals(ErrorCode.PARAM_ERROR.getCode(),
                assertThrows(BizException.class, () -> movieCollectionAppService.adminPage(command)).getCode());
        verify(movieCollectionRepository, never()).page(any());
    }

    /**
     * 未设置封面的片单封面代理返回空。
     */
    @Test
    void shouldReturnNullCoverWhenUnset() {
        when(movieCollectionRepository.findById(COLLECTION_ID))
                .thenReturn(Optional.of(collection(PublishState.DRAFT)));
        assertNull(movieCollectionAppService.coverFileId(COLLECTION_ID));
    }

    /**
     * 构造片单。
     *
     * @param state 发布状态
     * @return 片单
     */
    private MovieCollection collection(PublishState state) {
        MovieCollection collection = new MovieCollection();
        collection.setId(COLLECTION_ID);
        collection.setTitle("经典重映");
        collection.setDescription("胶卷时代的回响");
        collection.setPublishState(state);
        collection.setItemCount(0);
        collection.setSortNo(1);
        return collection;
    }

    /**
     * 构造电影内容。
     *
     * @return 内容
     */
    private Content content() {
        Content content = new Content();
        content.setId(CONTENT_ID);
        content.setContentType(ContentType.MOVIE);
        content.setTitle("海上钢琴师");
        content.setPublishState(PublishState.PUBLISHED);
        content.setCopyrightState(CopyrightState.AUTHORIZED);
        content.setVisibility(ContentVisibility.PUBLIC);
        return content;
    }

    /**
     * 构造加片命令。
     *
     * @param sortNo 片单内排序
     * @return 命令
     */
    private MovieCollectionItemCommand itemCommand(Integer sortNo) {
        MovieCollectionItemCommand command = new MovieCollectionItemCommand();
        command.setContentId(CONTENT_ID);
        command.setSortNo(sortNo);
        return command;
    }
}
