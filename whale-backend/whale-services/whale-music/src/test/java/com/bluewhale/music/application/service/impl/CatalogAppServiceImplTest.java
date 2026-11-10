package com.bluewhale.music.application.service.impl;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.music.application.command.AlbumSaveCommand;
import com.bluewhale.music.application.command.ArtistSaveCommand;
import com.bluewhale.music.application.command.TagSaveCommand;
import com.bluewhale.music.application.view.AlbumView;
import com.bluewhale.music.application.view.ArtistView;
import com.bluewhale.music.application.view.TagView;
import com.bluewhale.music.domain.exception.MusicErrorCode;
import com.bluewhale.music.domain.model.Album;
import com.bluewhale.music.domain.model.Artist;
import com.bluewhale.music.domain.model.PublishState;
import com.bluewhale.music.domain.model.Tag;
import com.bluewhale.music.domain.model.TagType;
import com.bluewhale.music.domain.repository.AlbumRepository;
import com.bluewhale.music.domain.repository.ArtistRepository;
import com.bluewhale.music.domain.repository.SongRepository;
import com.bluewhale.music.domain.repository.TagRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 曲库运营应用服务单元测试（T-07 覆盖率补齐）。
 *
 * <p>覆盖歌手 / 专辑 / 标签三类聚合的增删改查、唯一性校验、状态流转与分页参数归一化，
 * 仓储与歌曲标签清理全部以 Mock 隔离。</p>
 *
 * @author BlueWhale
 */
@ExtendWith(MockitoExtension.class)
class CatalogAppServiceImplTest {

    /** 内容运营用户 ID */
    private static final Long OPERATOR_ID = 1L;

    @Mock
    private ArtistRepository artistRepository;

    @Mock
    private AlbumRepository albumRepository;

    @Mock
    private TagRepository tagRepository;

    @Mock
    private SongRepository songRepository;

    @InjectMocks
    private CatalogAppServiceImpl catalogAppService;

    /**
     * 同一租户内歌手重名时拒绝创建。
     */
    @Test
    void createArtistShouldRejectDuplicatedName() {
        when(artistRepository.existsByName("周杰伦", null)).thenReturn(true);

        BizException exception = assertThrows(BizException.class,
                () -> catalogAppService.createArtist(artistCommand(" 周杰伦 ", null)));
        assertEquals(MusicErrorCode.ARTIST_NAME_DUPLICATED, exception.getCode());
        verify(artistRepository, never()).insert(any());
    }

    /**
     * 创建歌手时名称去空格、状态缺省为启用。
     */
    @Test
    void createArtistShouldTrimNameAndDefaultStatusEnabled() {
        when(artistRepository.existsByName("周杰伦", null)).thenReturn(false);

        ArtistView view = catalogAppService.createArtist(artistCommand(" 周杰伦 ", null));

        ArgumentCaptor<Artist> captor = ArgumentCaptor.forClass(Artist.class);
        verify(artistRepository).insert(captor.capture());
        assertEquals("周杰伦", captor.getValue().getName());
        assertEquals(1, captor.getValue().getStatus());
        assertEquals("周杰伦", view.getName());
        assertEquals(1, view.getStatus());
    }

    /**
     * 改名后与已有歌手重名时拒绝更新。
     */
    @Test
    void updateArtistShouldRejectDuplicatedRename() {
        when(artistRepository.findById(5L)).thenReturn(Optional.of(artist(5L, "周杰伦", 1)));
        when(artistRepository.existsByName("陶喆", 5L)).thenReturn(true);

        BizException exception = assertThrows(BizException.class,
                () -> catalogAppService.updateArtist(5L, artistCommand("陶喆", 1)));
        assertEquals(MusicErrorCode.ARTIST_NAME_DUPLICATED, exception.getCode());
        verify(artistRepository, never()).update(any());
    }

    /**
     * 名称未变化时跳过重名校验，只更新其余字段。
     */
    @Test
    void updateArtistShouldSkipDuplicateCheckWhenNameUnchanged() {
        Artist existing = artist(5L, "周杰伦", 1);
        when(artistRepository.findById(5L)).thenReturn(Optional.of(existing));

        catalogAppService.updateArtist(5L, artistCommand("周杰伦", 0));

        verify(artistRepository, never()).existsByName(any(), any());
        verify(artistRepository).update(existing);
        assertEquals(0, existing.getStatus());
    }

    /**
     * 更新不存在的歌手抛出 40401。
     */
    @Test
    void updateArtistShouldThrowWhenAbsent() {
        BizException exception = assertThrows(BizException.class,
                () -> catalogAppService.updateArtist(5L, artistCommand("周杰伦", 1)));
        assertEquals(MusicErrorCode.ARTIST_NOT_FOUND, exception.getCode());
    }

    /**
     * 删除歌手前先校验存在性。
     */
    @Test
    void deleteArtistShouldThrowWhenAbsentAndDelegateWhenPresent() {
        BizException exception = assertThrows(BizException.class, () -> catalogAppService.deleteArtist(5L));
        assertEquals(MusicErrorCode.ARTIST_NOT_FOUND, exception.getCode());

        when(artistRepository.findById(6L)).thenReturn(Optional.of(artist(6L, "陶喆", 1)));
        catalogAppService.deleteArtist(6L);

        verify(artistRepository).delete(6L);
    }

    /**
     * 歌手详情按 ID 查询并映射为视图。
     */
    @Test
    void artistDetailShouldReturnView() {
        when(artistRepository.findById(7L)).thenReturn(Optional.of(artist(7L, "林俊杰", 1)));

        assertEquals("林俊杰", catalogAppService.artistDetail(7L).getName());
    }

    /**
     * 歌手分页：页码至少为 1、每页条数上限 200。
     */
    @Test
    void artistPageShouldClampPageAndSize() {
        when(artistRepository.page(null, null, 1L, 200L)).thenReturn(List.of(artist(1L, "周杰伦", 1)));
        when(artistRepository.count(null, null)).thenReturn(1L);

        PageResult<ArtistView> result = catalogAppService.artistPage(null, null, 0L, 500L);

        assertEquals(1L, result.getCurrent());
        assertEquals(200L, result.getSize());
        assertEquals(1, result.getRecords().size());
        assertEquals(1L, result.getTotal());
    }

    /**
     * 歌手分页：每页条数非正时回落默认 20。
     */
    @Test
    void artistPageShouldFallbackDefaultSize() {
        when(artistRepository.page("周", 1, 1L, 20L)).thenReturn(List.of());
        when(artistRepository.count("周", 1)).thenReturn(0L);

        PageResult<ArtistView> result = catalogAppService.artistPage("周", 1, 1L, 0L);

        assertEquals(20L, result.getSize());
        assertEquals(0L, result.getTotal());
    }

    /**
     * 专辑关联歌手不存在时拒绝创建。
     */
    @Test
    void createAlbumShouldRejectMissingArtist() {
        when(artistRepository.findById(2L)).thenReturn(Optional.empty());

        BizException exception = assertThrows(BizException.class,
                () -> catalogAppService.createAlbum(albumCommand()));
        assertEquals(MusicErrorCode.ALBUM_ARTIST_INVALID, exception.getCode());
        verify(albumRepository, never()).insert(any());
    }

    /**
     * 专辑关联歌手已停用时拒绝创建。
     */
    @Test
    void createAlbumShouldRejectDisabledArtist() {
        when(artistRepository.findById(2L)).thenReturn(Optional.of(artist(2L, "周杰伦", 0)));

        BizException exception = assertThrows(BizException.class,
                () -> catalogAppService.createAlbum(albumCommand()));
        assertEquals(MusicErrorCode.ALBUM_ARTIST_INVALID, exception.getCode());
    }

    /**
     * 创建专辑默认草稿态、默认类型 ALBUM、默认排序 0。
     */
    @Test
    void createAlbumShouldDefaultDraftTypeAndSort() {
        when(artistRepository.findById(2L)).thenReturn(Optional.of(artist(2L, "周杰伦", 1)));

        AlbumView view = catalogAppService.createAlbum(albumCommand());

        ArgumentCaptor<Album> captor = ArgumentCaptor.forClass(Album.class);
        verify(albumRepository).insert(captor.capture());
        assertEquals(PublishState.DRAFT, captor.getValue().getPublishState());
        assertEquals("ALBUM", captor.getValue().getAlbumType());
        assertEquals(0, captor.getValue().getSortNo());
        assertEquals("叶惠美", captor.getValue().getTitle());
        assertEquals(PublishState.DRAFT, view.getPublishState());
    }

    /**
     * 更新不存在的专辑抛出 40402。
     */
    @Test
    void updateAlbumShouldThrowWhenAbsent() {
        BizException exception = assertThrows(BizException.class,
                () -> catalogAppService.updateAlbum(5L, albumCommand()));
        assertEquals(MusicErrorCode.ALBUM_NOT_FOUND, exception.getCode());
    }

    /**
     * 更新专辑时同时校验关联歌手与写回。
     */
    @Test
    void updateAlbumShouldValidateArtistAndUpdate() {
        Album existing = album(5L);
        when(albumRepository.findById(5L)).thenReturn(Optional.of(existing));
        when(artistRepository.findById(2L)).thenReturn(Optional.of(artist(2L, "周杰伦", 1)));

        AlbumView view = catalogAppService.updateAlbum(5L, albumCommand());

        verify(albumRepository).update(existing);
        assertEquals("叶惠美", view.getTitle());
        assertEquals(2L, existing.getArtistId());
    }

    /**
     * 删除不存在的专辑抛出 40402，存在时下发删除。
     */
    @Test
    void deleteAlbumShouldThrowWhenAbsentAndDelegateWhenPresent() {
        BizException exception = assertThrows(BizException.class, () -> catalogAppService.deleteAlbum(5L));
        assertEquals(MusicErrorCode.ALBUM_NOT_FOUND, exception.getCode());

        when(albumRepository.findById(6L)).thenReturn(Optional.of(album(6L)));
        catalogAppService.deleteAlbum(6L);

        verify(albumRepository).delete(6L);
    }

    /**
     * 专辑详情按 ID 查询并映射为视图。
     */
    @Test
    void albumDetailShouldReturnView() {
        when(albumRepository.findById(8L)).thenReturn(Optional.of(album(8L)));

        assertEquals(8L, catalogAppService.albumDetail(8L).getId());
    }

    /**
     * 专辑分页：发布状态大小写不敏感解析，合法值参与过滤。
     */
    @Test
    void albumPageShouldParsePublishStateCaseInsensitively() {
        when(albumRepository.page("叶", 2L, PublishState.DRAFT, 1L, 10L))
                .thenReturn(List.of(album(1L)));
        when(albumRepository.count("叶", 2L, PublishState.DRAFT)).thenReturn(1L);

        PageResult<AlbumView> result = catalogAppService.albumPage("叶", 2L, " draft ", 1L, 10L);

        assertEquals(1, result.getRecords().size());
        assertEquals(10L, result.getSize());
    }

    /**
     * 专辑分页：非法状态视为不过滤，页码与条数归一化。
     */
    @Test
    void albumPageShouldIgnoreInvalidStateAndClampPage() {
        when(albumRepository.page(null, null, null, 1L, 20L)).thenReturn(List.of());
        when(albumRepository.count(null, null, null)).thenReturn(0L);

        PageResult<AlbumView> result = catalogAppService.albumPage(null, null, "BAD_STATE", 0L, 0L);

        assertEquals(1L, result.getCurrent());
        assertEquals(20L, result.getSize());
    }

    /**
     * 上架与下架专辑切换发布状态并写回。
     */
    @Test
    void publishAndOfflineAlbumShouldSwitchState() {
        when(albumRepository.findById(9L)).thenReturn(Optional.of(album(9L)));

        assertEquals(PublishState.PUBLISHED, catalogAppService.publishAlbum(9L).getPublishState());

        Album offline = album(9L);
        offline.setPublishState(PublishState.PUBLISHED);
        when(albumRepository.findById(9L)).thenReturn(Optional.of(offline));

        assertEquals(PublishState.OFFLINE, catalogAppService.offlineAlbum(9L).getPublishState());
        verify(albumRepository, never()).delete(any());
    }

    /**
     * 创建标签默认类型 GENRE、默认排序 0。
     */
    @Test
    void createTagShouldDefaultGenreAndSort() {
        when(tagRepository.exists("摇滚", TagType.GENRE, null)).thenReturn(false);

        TagView view = catalogAppService.createTag(tagCommand(" 摇滚 ", null, null));

        ArgumentCaptor<Tag> captor = ArgumentCaptor.forClass(Tag.class);
        verify(tagRepository).insert(captor.capture());
        assertEquals(TagType.GENRE, captor.getValue().getTagType());
        assertEquals(0, captor.getValue().getSortNo());
        assertEquals("摇滚", view.getName());
    }

    /**
     * 同类型重名标签拒绝创建。
     */
    @Test
    void createTagShouldRejectDuplicatedName() {
        when(tagRepository.exists("摇滚", TagType.GENRE, null)).thenReturn(true);

        BizException exception = assertThrows(BizException.class,
                () -> catalogAppService.createTag(tagCommand("摇滚", null, null)));
        assertEquals(MusicErrorCode.TAG_DUPLICATED, exception.getCode());
    }

    /**
     * 更新标签：命令未指定类型 / 排序时保留原值。
     */
    @Test
    void updateTagShouldKeepTypeAndSortWhenCommandOmits() {
        Tag existing = tag(7L, "摇滚", TagType.GENRE, 3);
        when(tagRepository.findById(7L)).thenReturn(Optional.of(existing));
        when(tagRepository.exists("摇滚", TagType.GENRE, 7L)).thenReturn(false);

        catalogAppService.updateTag(7L, tagCommand("摇滚", null, null));

        verify(tagRepository).update(existing);
        assertEquals(TagType.GENRE, existing.getTagType());
        assertEquals(3, existing.getSortNo());
    }

    /**
     * 更新标签改名撞上同类型已有标签时拒绝。
     */
    @Test
    void updateTagShouldRejectDuplicatedName() {
        when(tagRepository.findById(7L)).thenReturn(Optional.of(tag(7L, "摇滚", TagType.GENRE, 3)));
        when(tagRepository.exists("流行", TagType.LANGUAGE, 7L)).thenReturn(true);

        BizException exception = assertThrows(BizException.class,
                () -> catalogAppService.updateTag(7L, tagCommand("流行", TagType.LANGUAGE, 8)));
        assertEquals(MusicErrorCode.TAG_DUPLICATED, exception.getCode());
        verify(tagRepository, never()).update(any());
    }

    /**
     * 更新不存在的标签抛出 40404。
     */
    @Test
    void updateTagShouldThrowWhenAbsent() {
        BizException exception = assertThrows(BizException.class,
                () -> catalogAppService.updateTag(7L, tagCommand("摇滚", null, null)));
        assertEquals(MusicErrorCode.TAG_NOT_FOUND, exception.getCode());
    }

    /**
     * 删除标签时同步清理歌曲-标签关系。
     */
    @Test
    void deleteTagShouldRemoveTagAndSongRelations() {
        BizException exception = assertThrows(BizException.class, () -> catalogAppService.deleteTag(7L));
        assertEquals(MusicErrorCode.TAG_NOT_FOUND, exception.getCode());

        when(tagRepository.findById(8L)).thenReturn(Optional.of(tag(8L, "摇滚", TagType.GENRE, 0)));
        catalogAppService.deleteTag(8L);

        verify(tagRepository).delete(8L);
        verify(songRepository).removeTag(8L);
    }

    /**
     * 标签列表全量映射为视图。
     */
    @Test
    void listTagsShouldMapAll() {
        when(tagRepository.list()).thenReturn(List.of(
                tag(1L, "摇滚", TagType.GENRE, 0),
                tag(2L, "国语", TagType.LANGUAGE, 1)));

        List<TagView> views = catalogAppService.listTags();

        assertEquals(2, views.size());
        assertEquals("国语", views.get(1).getName());
    }

    /**
     * 构造歌手保存命令。
     *
     * @param name 名称
     * @param status 状态
     * @return 保存命令
     */
    private ArtistSaveCommand artistCommand(String name, Integer status) {
        ArtistSaveCommand command = new ArtistSaveCommand();
        command.setName(name);
        command.setStatus(status);
        command.setRegion("中国台湾");
        return command;
    }

    /**
     * 构造专辑保存命令。
     *
     * @return 保存命令
     */
    private AlbumSaveCommand albumCommand() {
        AlbumSaveCommand command = new AlbumSaveCommand();
        command.setArtistId(2L);
        command.setTitle(" 叶惠美 ");
        command.setCoverFileId(3L);
        command.setReleaseDate(LocalDate.of(2003, 7, 31));
        return command;
    }

    /**
     * 构造标签保存命令。
     *
     * @param name 名称
     * @param type 类型
     * @param sortNo 排序
     * @return 保存命令
     */
    private TagSaveCommand tagCommand(String name, TagType type, Integer sortNo) {
        TagSaveCommand command = new TagSaveCommand();
        command.setName(name);
        command.setTagType(type);
        command.setSortNo(sortNo);
        return command;
    }

    /**
     * 构造歌手。
     *
     * @param id 主键
     * @param name 名称
     * @param status 状态
     * @return 歌手
     */
    private Artist artist(Long id, String name, Integer status) {
        Artist artist = new Artist();
        artist.setId(id);
        artist.setName(name);
        artist.setStatus(status);
        return artist;
    }

    /**
     * 构造草稿专辑。
     *
     * @param id 主键
     * @return 专辑
     */
    private Album album(Long id) {
        Album album = new Album();
        album.setId(id);
        album.setArtistId(2L);
        album.setTitle("叶惠美");
        album.setPublishState(PublishState.DRAFT);
        return album;
    }

    /**
     * 构造标签。
     *
     * @param id 主键
     * @param name 名称
     * @param type 类型
     * @param sortNo 排序
     * @return 标签
     */
    private Tag tag(Long id, String name, TagType type, Integer sortNo) {
        Tag tag = new Tag();
        tag.setId(id);
        tag.setName(name);
        tag.setTagType(type);
        tag.setSortNo(sortNo);
        return tag;
    }
}
