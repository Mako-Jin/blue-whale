package com.bluewhale.music.application.service.impl;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.music.application.command.SongQueryCommand;
import com.bluewhale.music.application.command.SongSaveCommand;
import com.bluewhale.music.application.service.CopyrightAgreementAppService;
import com.bluewhale.music.application.service.SongAppService;
import com.bluewhale.music.application.service.UserSongAppService;
import com.bluewhale.music.application.view.PublicSongView;
import com.bluewhale.music.application.view.SongView;
import com.bluewhale.music.application.view.TagView;
import com.bluewhale.music.domain.exception.MusicErrorCode;
import com.bluewhale.music.domain.model.CopyrightSource;
import com.bluewhale.music.domain.model.PublishState;
import com.bluewhale.music.domain.model.Song;
import com.bluewhale.music.domain.model.SongQuery;
import com.bluewhale.music.domain.model.SongVisibility;
import com.bluewhale.music.domain.model.Tag;
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
 * 用户上传与作品管理应用服务实现。
 *
 * <p>创作类操作复用 {@link SongAppService} 的聚合与状态机，避免两套写入口径；
 * 本类只承担协议前置校验、归属校验与用户侧视图转换。</p>
 *
 * @author BlueWhale
 */
@Service
@RequiredArgsConstructor
public class UserSongAppServiceImpl implements UserSongAppService {

    /** 单页最大条数 */
    private static final long MAX_PAGE_SIZE = 200L;

    /** 默认每页条数 */
    private static final long DEFAULT_PAGE_SIZE = 20L;

    private final SongRepository songRepository;

    private final TagRepository tagRepository;

    private final SongAppService songAppService;

    private final CopyrightAgreementAppService agreementAppService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PublicSongView upload(SongSaveCommand command, Long userId) {
        agreementAppService.assertAccepted(userId);
        requireSource(command.getSource());
        SongView created = songAppService.create(command, userId);
        SongView published = songAppService.publish(created.getId());
        return toPublicView(published);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PublicSongView update(Long id, SongSaveCommand command, Long userId) {
        requireOwned(id, userId);
        return toPublicView(songAppService.update(id, command));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id, Long userId) {
        requireOwned(id, userId);
        songAppService.delete(id);
    }

    @Override
    public PageResult<SongView> mySongs(SongQueryCommand command, Long userId) {
        command.setUploaderId(userId);
        return songAppService.page(command);
    }

    @Override
    public PageResult<PublicSongView> publicPage(SongQueryCommand command) {
        SongQuery query = buildPublicQuery(command);
        List<Song> songs = songRepository.page(query);
        Map<Long, List<Long>> tagIdMap = songRepository.findTagIdsBySongIds(
                songs.stream().map(Song::getId).toList());
        Map<Long, TagView> tagCache = loadTags(tagIdMap.values());
        List<PublicSongView> records = songs.stream()
                .map(song -> toPublicView(song,
                        tagIdMap.getOrDefault(song.getId(), List.of()).stream()
                                .map(tagCache::get).filter(Objects::nonNull).toList()))
                .toList();
        return PageResult.of(records, songRepository.count(query), query.getCurrent(), query.getSize());
    }

    @Override
    public PublicSongView publicDetail(Long id, Long viewerId) {
        Song song = songRepository.findById(id)
                .filter(item -> item.visibleTo(viewerId, false))
                .orElseThrow(() -> MusicErrorCode.exception(MusicErrorCode.SONG_NOT_FOUND));
        return toPublicView(song, tagViews(songRepository.findTagIds(id)));
    }

    /**
     * 校验作品来源：用户上传必须声明原创 / 已授权 / 无版权争议素材。
     *
     * @param source 来源文本
     */
    private void requireSource(String source) {
        CopyrightSource.parse(source);
    }

    /**
     * 查询并校验歌曲归属，非本人作品直接拒绝。
     *
     * <p>他人不可见的（自听 / 已下架）作品先按不存在处理，避免泄露存在性；
     * 他人公开作品再返回「不能操作他人作品」。</p>
     *
     * @param id 歌曲 ID
     * @param userId 操作用户 ID
     * @return 歌曲
     */
    private Song requireOwned(Long id, Long userId) {
        Song song = songRepository.findById(id)
                .filter(item -> item.visibleTo(userId, false) || userId.equals(item.getUploaderId()))
                .orElseThrow(() -> MusicErrorCode.exception(MusicErrorCode.SONG_NOT_FOUND));
        song.assertOwnedBy(userId);
        return song;
    }

    /**
     * 构造用户侧公开查询条件：仅上架 + 公开，忽略上传者条件。
     *
     * @param command 查询命令
     * @return 领域查询条件
     */
    private SongQuery buildPublicQuery(SongQueryCommand command) {
        SongQuery query = new SongQuery();
        query.setKeyword(command.getKeyword());
        query.setArtistId(command.getArtistId());
        query.setAlbumId(command.getAlbumId());
        query.setTagId(command.getTagId());
        query.setSortBy(command.getSortBy());
        query.setPublishState(PublishState.PUBLISHED);
        query.setVisibility(SongVisibility.PUBLIC);
        query.setCurrent(Math.max(command.getCurrent(), 1L));
        long size = command.getSize();
        query.setSize(size <= 0 ? DEFAULT_PAGE_SIZE : Math.min(size, MAX_PAGE_SIZE));
        return query;
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
     * 运营侧视图转用户侧视图。
     *
     * @param view 运营侧歌曲视图
     * @return 用户侧歌曲视图
     */
    private PublicSongView toPublicView(SongView view) {
        PublicSongView publicView = new PublicSongView();
        BeanUtils.copyProperties(view, publicView);
        return publicView;
    }

    /**
     * 歌曲领域模型转用户侧视图。
     *
     * @param song 歌曲
     * @param tags 标签视图列表
     * @return 用户侧歌曲视图
     */
    private PublicSongView toPublicView(Song song, List<TagView> tags) {
        PublicSongView view = new PublicSongView();
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
