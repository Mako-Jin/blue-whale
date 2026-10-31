package com.bluewhale.music.application.service.impl;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.music.application.command.SongQueryCommand;
import com.bluewhale.music.application.command.SongSaveCommand;
import com.bluewhale.music.application.service.SongAppService;
import com.bluewhale.music.application.view.SongView;
import com.bluewhale.music.application.view.TagView;
import com.bluewhale.music.domain.exception.MusicErrorCode;
import com.bluewhale.music.domain.model.Artist;
import com.bluewhale.music.domain.model.CopyrightState;
import com.bluewhale.music.domain.model.PublishState;
import com.bluewhale.music.domain.model.Song;
import com.bluewhale.music.domain.model.SongQuery;
import com.bluewhale.music.domain.model.SongVisibility;
import com.bluewhale.music.domain.model.Tag;
import com.bluewhale.music.domain.repository.ArtistRepository;
import com.bluewhale.music.domain.repository.SongRepository;
import com.bluewhale.music.domain.repository.TagRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 歌曲运营应用服务实现。
 *
 * <p>所有写操作在事务内完成歌曲主体与标签关系的同步；发布 / 下架 / 版权下架
 * 统一委托 {@link Song} 聚合根校验状态机，应用层只负责加载、持久化与视图装配。</p>
 *
 * @author BlueWhale
 */
@Service
@RequiredArgsConstructor
public class SongAppServiceImpl implements SongAppService {

    /** 单页最大条数 */
    private static final long MAX_PAGE_SIZE = 200L;

    /** 默认每页条数 */
    private static final long DEFAULT_PAGE_SIZE = 20L;

    /** 单曲的专辑占位值 */
    private static final long NO_ALBUM_ID = 0L;

    /** 默认排序权重 */
    private static final int DEFAULT_SORT_WEIGHT = 0;

    private final SongRepository songRepository;

    private final ArtistRepository artistRepository;

    private final TagRepository tagRepository;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SongView create(SongSaveCommand command, Long operatorId) {
        requireEnabledArtist(command.getArtistId());
        Song song = new Song();
        song.setUploaderId(operatorId);
        song.setPublishState(PublishState.DRAFT);
        song.setCopyrightState(CopyrightState.DECLARED);
        song.setPlayCount(0L);
        song.setLikeCount(0L);
        song.setCommentCount(0L);
        fill(song, command);
        songRepository.insert(song);
        songRepository.replaceTags(song.getId(), command.getTagIds());
        return toView(song);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SongView update(Long id, SongSaveCommand command) {
        Song song = requireSong(id);
        if (song.getCopyrightState() == CopyrightState.TAKEDOWN) {
            throw MusicErrorCode.exception(MusicErrorCode.SONG_STATE_CONFLICT);
        }
        requireEnabledArtist(command.getArtistId());
        fill(song, command);
        songRepository.update(song);
        songRepository.replaceTags(id, command.getTagIds());
        return toView(song);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        requireSong(id);
        songRepository.delete(id);
    }

    @Override
    public SongView detail(Long id) {
        return toView(requireSong(id));
    }

    @Override
    public PageResult<SongView> page(SongQueryCommand command) {
        SongQuery query = toQuery(command);
        query.setCurrent(Math.max(query.getCurrent(), 1L));
        query.setSize(clampSize(query.getSize()));
        List<Song> songs = songRepository.page(query);
        Map<Long, List<Long>> tagIdMap = songRepository.findTagIdsBySongIds(
                songs.stream().map(Song::getId).toList());
        Map<Long, TagView> tagCache = loadTags(tagIdMap.values());
        List<SongView> records = songs.stream()
                .map(song -> toView(song, tagIdMap.getOrDefault(song.getId(), List.of()), tagCache))
                .toList();
        return PageResult.of(records, songRepository.count(query), query.getCurrent(), query.getSize());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SongView publish(Long id) {
        Song song = requireSong(id);
        song.publish();
        songRepository.update(song);
        return toView(song);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SongView offline(Long id) {
        Song song = requireSong(id);
        song.offline();
        songRepository.update(song);
        return toView(song);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SongView takedown(Long id, String reason) {
        Song song = requireSong(id);
        song.takedown(reason);
        songRepository.update(song);
        return toView(song);
    }

    /**
     * 查询歌曲，不存在时抛出业务异常。
     *
     * @param id 歌曲 ID
     * @return 歌曲
     */
    private Song requireSong(Long id) {
        return songRepository.findById(id)
                .orElseThrow(() -> MusicErrorCode.exception(MusicErrorCode.SONG_NOT_FOUND));
    }

    /**
     * 校验歌曲关联的歌手存在且启用。
     *
     * @param artistId 歌手 ID
     */
    private void requireEnabledArtist(Long artistId) {
        Artist artist = artistRepository.findById(artistId)
                .orElseThrow(() -> MusicErrorCode.exception(MusicErrorCode.SONG_NOT_READY));
        if (!artist.enabled()) {
            throw MusicErrorCode.exception(MusicErrorCode.SONG_NOT_READY);
        }
    }

    /**
     * 将保存命令写入歌曲（不含状态与计数）。
     *
     * @param song 歌曲
     * @param command 保存命令
     */
    private void fill(Song song, SongSaveCommand command) {
        song.setAlbumId(command.getAlbumId() == null ? NO_ALBUM_ID : command.getAlbumId());
        song.setArtistId(command.getArtistId());
        song.setTitle(command.getTitle().trim());
        song.setSubtitle(command.getSubtitle());
        song.setAudioFileId(command.getAudioFileId());
        song.setCoverFileId(command.getCoverFileId());
        song.setDurationMs(command.getDurationMs() == null ? 0L : command.getDurationMs());
        song.setLyricFileId(command.getLyricFileId());
        song.setLyricText(command.getLyricText());
        song.setLanguage(command.getLanguage());
        song.setVisibility(command.getVisibility() == null ? SongVisibility.PUBLIC : command.getVisibility());
        song.setSortWeight(command.getSortWeight() == null ? DEFAULT_SORT_WEIGHT : command.getSortWeight());
    }

    /**
     * 将查询命令转换为领域查询条件。
     *
     * @param command 查询命令
     * @return 领域查询条件
     */
    private SongQuery toQuery(SongQueryCommand command) {
        SongQuery query = new SongQuery();
        query.setKeyword(command.getKeyword());
        query.setArtistId(command.getArtistId());
        query.setAlbumId(command.getAlbumId());
        query.setTagId(command.getTagId());
        query.setPublishState(command.getPublishState());
        query.setVisibility(command.getVisibility());
        query.setUploaderId(command.getUploaderId());
        query.setSortBy(command.getSortBy());
        query.setCurrent(command.getCurrent());
        query.setSize(command.getSize());
        return query;
    }

    /**
     * 限制分页大小。
     *
     * @param size 请求条数
     * @return 归一化条数
     */
    private long clampSize(long size) {
        if (size <= 0) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(size, MAX_PAGE_SIZE);
    }

    /**
     * 批量加载标签视图。
     *
     * @param tagIdLists 各歌曲的标签 ID 列表
     * @return 标签 ID → 标签视图
     */
    private Map<Long, TagView> loadTags(Collection<List<Long>> tagIdLists) {
        Set<Long> allTagIds = new HashSet<>();
        tagIdLists.forEach(allTagIds::addAll);
        return tagRepository.findByIds(allTagIds).stream()
                .collect(Collectors.toMap(Tag::getId, this::toTagView));
    }

    /**
     * 查询歌曲标签视图。
     *
     * @param tagIds 标签 ID 集合
     * @return 标签视图列表
     */
    private List<TagView> tagViews(Collection<Long> tagIds) {
        if (tagIds == null || tagIds.isEmpty()) {
            return List.of();
        }
        return tagRepository.findByIds(tagIds).stream().map(this::toTagView).toList();
    }

    /**
     * 单曲转视图（含标签）。
     *
     * @param song 歌曲
     * @return 歌曲视图
     */
    private SongView toView(Song song) {
        return toView(song, tagViews(songRepository.findTagIds(song.getId())));
    }

    /**
     * 单曲转视图（使用已加载的标签 ID 与标签缓存）。
     *
     * @param song 歌曲
     * @param tagIds 标签 ID 列表
     * @param tagCache 标签视图缓存
     * @return 歌曲视图
     */
    private SongView toView(Song song, Collection<Long> tagIds, Map<Long, TagView> tagCache) {
        List<TagView> tags = tagIds.stream().map(tagCache::get).filter(Objects::nonNull).toList();
        return toView(song, tags);
    }

    /**
     * 单曲转视图（使用已加载的标签）。
     *
     * @param song 歌曲
     * @param tags 标签视图列表
     * @return 歌曲视图
     */
    private SongView toView(Song song, List<TagView> tags) {
        SongView view = new SongView();
        BeanUtils.copyProperties(song, view);
        view.setPlayable(song.playable());
        view.setTags(tags);
        return view;
    }

    /**
     * 标签领域模型转视图。
     *
     * @param tag 标签
     * @return 标签视图
     */
    private TagView toTagView(Tag tag) {
        TagView view = new TagView();
        BeanUtils.copyProperties(tag, view);
        return view;
    }
}
