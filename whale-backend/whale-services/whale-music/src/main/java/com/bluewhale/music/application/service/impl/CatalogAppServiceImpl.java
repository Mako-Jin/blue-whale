package com.bluewhale.music.application.service.impl;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.music.application.command.AlbumSaveCommand;
import com.bluewhale.music.application.command.ArtistSaveCommand;
import com.bluewhale.music.application.command.TagSaveCommand;
import com.bluewhale.music.application.service.CatalogAppService;
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
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 曲库运营应用服务实现（歌手 / 专辑 / 标签）。
 *
 * @author BlueWhale
 */
@Service
@RequiredArgsConstructor
public class CatalogAppServiceImpl implements CatalogAppService {

    /** 启用状态 */
    private static final int STATUS_ENABLED = 1;

    /** 单页最大条数 */
    private static final long MAX_PAGE_SIZE = 200L;

    /** 默认专辑类型 */
    private static final String DEFAULT_ALBUM_TYPE = "ALBUM";

    /** 默认排序 */
    private static final int DEFAULT_SORT = 0;

    private final ArtistRepository artistRepository;

    private final AlbumRepository albumRepository;

    private final TagRepository tagRepository;

    private final SongRepository songRepository;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ArtistView createArtist(ArtistSaveCommand command) {
        String name = command.getName().trim();
        if (artistRepository.existsByName(name, null)) {
            throw MusicErrorCode.exception(MusicErrorCode.ARTIST_NAME_DUPLICATED);
        }
        Artist artist = new Artist();
        artist.setName(name);
        artist.setAvatarFileId(command.getAvatarFileId());
        artist.setRegion(command.getRegion());
        artist.setBio(command.getBio());
        artist.setStatus(command.getStatus() == null ? STATUS_ENABLED : command.getStatus());
        artistRepository.insert(artist);
        return toView(artist);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ArtistView updateArtist(Long id, ArtistSaveCommand command) {
        Artist artist = requireArtist(id);
        String name = command.getName().trim();
        if (!name.equals(artist.getName()) && artistRepository.existsByName(name, id)) {
            throw MusicErrorCode.exception(MusicErrorCode.ARTIST_NAME_DUPLICATED);
        }
        artist.setName(name);
        artist.setAvatarFileId(command.getAvatarFileId());
        artist.setRegion(command.getRegion());
        artist.setBio(command.getBio());
        if (command.getStatus() != null) {
            artist.setStatus(command.getStatus());
        }
        artistRepository.update(artist);
        return toView(artist);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteArtist(Long id) {
        requireArtist(id);
        artistRepository.delete(id);
    }

    @Override
    public ArtistView artistDetail(Long id) {
        return toView(requireArtist(id));
    }

    @Override
    public PageResult<ArtistView> artistPage(String keyword, Integer status, long current, long size) {
        long pageSize = clampSize(size);
        long pageNo = Math.max(current, 1L);
        List<ArtistView> records = artistRepository.page(keyword, status, pageNo, pageSize)
                .stream().map(this::toView).toList();
        return PageResult.of(records, artistRepository.count(keyword, status), pageNo, pageSize);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AlbumView createAlbum(AlbumSaveCommand command) {
        requireEnabledArtist(command.getArtistId());
        Album album = new Album();
        album.setArtistId(command.getArtistId());
        album.setPublishState(PublishState.DRAFT);
        fillAlbum(album, command);
        albumRepository.insert(album);
        return toView(album);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AlbumView updateAlbum(Long id, AlbumSaveCommand command) {
        Album album = requireAlbum(id);
        requireEnabledArtist(command.getArtistId());
        album.setArtistId(command.getArtistId());
        fillAlbum(album, command);
        albumRepository.update(album);
        return toView(album);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteAlbum(Long id) {
        requireAlbum(id);
        albumRepository.delete(id);
    }

    @Override
    public AlbumView albumDetail(Long id) {
        return toView(requireAlbum(id));
    }

    @Override
    public PageResult<AlbumView> albumPage(String keyword, Long artistId, String publishState,
                                           long current, long size) {
        long pageSize = clampSize(size);
        long pageNo = Math.max(current, 1L);
        PublishState state = parsePublishState(publishState);
        List<AlbumView> records = albumRepository.page(keyword, artistId, state, pageNo, pageSize)
                .stream().map(this::toView).toList();
        return PageResult.of(records, albumRepository.count(keyword, artistId, state), pageNo, pageSize);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AlbumView publishAlbum(Long id) {
        Album album = requireAlbum(id);
        album.publish();
        albumRepository.update(album);
        return toView(album);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AlbumView offlineAlbum(Long id) {
        Album album = requireAlbum(id);
        album.offline();
        albumRepository.update(album);
        return toView(album);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TagView createTag(TagSaveCommand command) {
        String name = command.getName().trim();
        TagType type = command.getTagType() == null ? TagType.GENRE : command.getTagType();
        if (tagRepository.exists(name, type, null)) {
            throw MusicErrorCode.exception(MusicErrorCode.TAG_DUPLICATED);
        }
        Tag tag = new Tag();
        tag.setName(name);
        tag.setTagType(type);
        tag.setSortNo(command.getSortNo() == null ? DEFAULT_SORT : command.getSortNo());
        tagRepository.insert(tag);
        return toView(tag);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TagView updateTag(Long id, TagSaveCommand command) {
        Tag tag = requireTag(id);
        String name = command.getName().trim();
        TagType type = command.getTagType() == null ? tag.getTagType() : command.getTagType();
        if (tagRepository.exists(name, type, id)) {
            throw MusicErrorCode.exception(MusicErrorCode.TAG_DUPLICATED);
        }
        tag.setName(name);
        tag.setTagType(type);
        if (command.getSortNo() != null) {
            tag.setSortNo(command.getSortNo());
        }
        tagRepository.update(tag);
        return toView(tag);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteTag(Long id) {
        requireTag(id);
        tagRepository.delete(id);
        songRepository.removeTag(id);
    }

    @Override
    public List<TagView> listTags() {
        return tagRepository.list().stream().map(this::toView).toList();
    }

    /**
     * 查询歌手，不存在时抛出业务异常。
     *
     * @param id 歌手 ID
     * @return 歌手
     */
    private Artist requireArtist(Long id) {
        return artistRepository.findById(id)
                .orElseThrow(() -> MusicErrorCode.exception(MusicErrorCode.ARTIST_NOT_FOUND));
    }

    /**
     * 校验歌手存在且启用。
     *
     * @param artistId 歌手 ID
     */
    private void requireEnabledArtist(Long artistId) {
        Artist artist = artistRepository.findById(artistId)
                .orElseThrow(() -> MusicErrorCode.exception(MusicErrorCode.ALBUM_ARTIST_INVALID));
        if (!artist.enabled()) {
            throw MusicErrorCode.exception(MusicErrorCode.ALBUM_ARTIST_INVALID);
        }
    }

    /**
     * 查询专辑，不存在时抛出业务异常。
     *
     * @param id 专辑 ID
     * @return 专辑
     */
    private Album requireAlbum(Long id) {
        return albumRepository.findById(id)
                .orElseThrow(() -> MusicErrorCode.exception(MusicErrorCode.ALBUM_NOT_FOUND));
    }

    /**
     * 查询标签，不存在时抛出业务异常。
     *
     * @param id 标签 ID
     * @return 标签
     */
    private Tag requireTag(Long id) {
        return tagRepository.findById(id)
                .orElseThrow(() -> MusicErrorCode.exception(MusicErrorCode.TAG_NOT_FOUND));
    }

    /**
     * 将命令字段写入专辑。
     *
     * @param album 专辑
     * @param command 保存命令
     */
    private void fillAlbum(Album album, AlbumSaveCommand command) {
        album.setTitle(command.getTitle().trim());
        album.setCoverFileId(command.getCoverFileId());
        album.setReleaseDate(command.getReleaseDate());
        album.setAlbumType(command.getAlbumType() == null ? DEFAULT_ALBUM_TYPE : command.getAlbumType());
        album.setIntro(command.getIntro());
        album.setSortNo(command.getSortNo() == null ? DEFAULT_SORT : command.getSortNo());
    }

    /**
     * 限制分页大小。
     *
     * @param size 请求条数
     * @return 归一化条数
     */
    private long clampSize(long size) {
        if (size <= 0) {
            return 20L;
        }
        return Math.min(size, MAX_PAGE_SIZE);
    }

    /**
     * 解析发布状态文本，非法或为空时视为不筛选。
     *
     * @param value 发布状态文本
     * @return 发布状态；无有效值时返回 {@code null}
     */
    private PublishState parsePublishState(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        for (PublishState item : PublishState.values()) {
            if (item.name().equalsIgnoreCase(value.trim())) {
                return item;
            }
        }
        return null;
    }

    /**
     * 歌手领域模型转视图。
     *
     * @param artist 歌手
     * @return 歌手视图
     */
    private ArtistView toView(Artist artist) {
        ArtistView view = new ArtistView();
        BeanUtils.copyProperties(artist, view);
        return view;
    }

    /**
     * 专辑领域模型转视图。
     *
     * @param album 专辑
     * @return 专辑视图
     */
    private AlbumView toView(Album album) {
        AlbumView view = new AlbumView();
        BeanUtils.copyProperties(album, view);
        return view;
    }

    /**
     * 标签领域模型转视图。
     *
     * @param tag 标签
     * @return 标签视图
     */
    private TagView toView(Tag tag) {
        TagView view = new TagView();
        BeanUtils.copyProperties(tag, view);
        return view;
    }
}
