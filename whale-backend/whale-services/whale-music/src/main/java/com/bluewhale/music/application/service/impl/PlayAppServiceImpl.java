package com.bluewhale.music.application.service.impl;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.music.application.command.PlayProgressCommand;
import com.bluewhale.music.application.service.PlayAppService;
import com.bluewhale.music.application.view.AudioStreamView;
import com.bluewhale.music.application.view.PlayHistoryView;
import com.bluewhale.music.application.view.PlayTicketView;
import com.bluewhale.music.application.view.PublicSongView;
import com.bluewhale.music.config.MusicProperties;
import com.bluewhale.music.domain.exception.MusicErrorCode;
import com.bluewhale.music.domain.model.AudioStream;
import com.bluewhale.music.domain.model.PlayHistory;
import com.bluewhale.music.domain.model.PlayTicket;
import com.bluewhale.music.domain.model.Song;
import com.bluewhale.music.domain.repository.AudioContentRepository;
import com.bluewhale.music.domain.repository.PlayCountRepository;
import com.bluewhale.music.domain.repository.PlayHistoryRepository;
import com.bluewhale.music.domain.repository.PlayTicketRepository;
import com.bluewhale.music.domain.repository.SongLikeRepository;
import com.bluewhale.music.domain.repository.SongRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 播放与音频流应用服务实现。
 *
 * <p>票据签发与每次流式读取都校验可见性、发布状态与版权状态，版权下架后
 * 已签发票据无法继续播放；播放历史与收藏按「用户 + 歌曲」幂等写入，
 * 租户隔离由统一租户插件保证。</p>
 *
 * @author BlueWhale
 */
@Service
@RequiredArgsConstructor
public class PlayAppServiceImpl implements PlayAppService {

    /** 流式地址前缀 */
    private static final String STREAM_PATH = "/music/stream/";

    /** 单页最大条数 */
    private static final long MAX_PAGE_SIZE = 200L;

    /** 默认每页条数 */
    private static final long DEFAULT_PAGE_SIZE = 20L;

    /** 默认播放来源 */
    private static final String DEFAULT_SOURCE = "MY";

    private final SongRepository songRepository;

    private final PlayTicketRepository playTicketRepository;

    private final PlayHistoryRepository playHistoryRepository;

    private final SongLikeRepository songLikeRepository;

    private final AudioContentRepository audioContentRepository;

    private final PlayCountRepository playCountRepository;

    private final MusicProperties musicProperties;

    @Override
    public PlayTicketView issueTicket(Long songId, Long userId, Long tenantId) {
        Song song = requirePlayableSong(songId, userId);
        PlayTicket ticket = new PlayTicket();
        ticket.setTicket(UUID.randomUUID().toString().replace("-", ""));
        ticket.setTenantId(tenantId);
        ticket.setUserId(userId);
        ticket.setSongId(songId);
        playTicketRepository.issue(ticket, musicProperties.getPlayTicketTtlSeconds());
        PlayTicketView view = new PlayTicketView();
        view.setTicket(ticket.getTicket());
        view.setExpiresIn(musicProperties.getPlayTicketTtlSeconds());
        view.setSongId(songId);
        view.setTitle(song.getTitle());
        view.setArtistId(song.getArtistId());
        view.setCoverFileId(song.getCoverFileId());
        view.setDurationMs(song.getDurationMs());
        view.setLyricText(song.getLyricText());
        view.setStreamUrl(STREAM_PATH + songId + "?ticket=" + ticket.getTicket());
        return view;
    }

    @Override
    public AudioStreamView openStream(Long songId, String ticket, Long userId, Long tenantId,
                                      String rangeHeader, String ifRangeHeader) {
        if (ticket == null || ticket.isBlank()) {
            throw MusicErrorCode.exception(MusicErrorCode.PLAY_TICKET_INVALID);
        }
        PlayTicket bound = playTicketRepository.find(ticket)
                .orElseThrow(() -> MusicErrorCode.exception(MusicErrorCode.PLAY_TICKET_INVALID));
        if (!bound.matches(tenantId, userId, songId)) {
            throw MusicErrorCode.exception(MusicErrorCode.PLAY_TICKET_INVALID);
        }
        // 每次流式请求复核歌曲状态：下架 / 版权下架后立即阻断，不等待票据过期
        Song song = requirePlayableSong(songId, userId);
        AudioStream stream = audioContentRepository.open(
                song.getAudioFileId(), tenantId, rangeHeader, ifRangeHeader);
        AudioStreamView view = new AudioStreamView();
        view.setStatus(stream.status());
        view.setContentLength(stream.contentLength());
        view.setContentRange(stream.contentRange());
        view.setContentRangeTotal(stream.contentRangeTotal());
        view.setContentType(stream.contentType());
        view.setEtag(stream.etag());
        view.setBody(stream.body());
        // 真正开流时才累计播放次数；Redis 累加，定时任务回刷 mu_song.play_count
        playCountRepository.increment(songId);
        return view;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reportProgress(PlayProgressCommand command, Long userId) {
        Song song = requirePlayableSong(command.getSongId(), userId);
        PlayHistory history = new PlayHistory();
        history.setUserId(userId);
        history.setSongId(song.getId());
        history.setProgressMs(command.getProgressMs() == null ? 0L : command.getProgressMs());
        history.setSource(command.getSource() == null || command.getSource().isBlank()
                ? DEFAULT_SOURCE : command.getSource());
        history.setDevice(command.getDevice());
        history.setPlayedAt(LocalDateTime.now());
        playHistoryRepository.saveProgress(history);
    }

    @Override
    public PageResult<PlayHistoryView> recentHistory(Long userId, long current, long size) {
        long page = Math.max(current, 1L);
        long pageSize = clampSize(size);
        long total = playHistoryRepository.countByUser(userId);
        List<PlayHistory> histories = playHistoryRepository.page(userId, (page - 1) * pageSize, pageSize);
        Map<Long, Song> songMap = songRepository.findByIds(
                        histories.stream().map(PlayHistory::getSongId).toList())
                .stream().collect(Collectors.toMap(Song::getId, Function.identity()));
        List<PlayHistoryView> records = histories.stream()
                .map(history -> toHistoryView(history, songMap.get(history.getSongId())))
                .toList();
        return PageResult.of(records, total, page, pageSize);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void like(Long songId, Long userId) {
        requireVisibleSong(songId, userId);
        if (songLikeRepository.add(userId, songId)) {
            songRepository.addLikeCount(songId, 1L);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unlike(Long songId, Long userId) {
        if (songLikeRepository.remove(userId, songId)) {
            songRepository.addLikeCount(songId, -1L);
        }
    }

    @Override
    public PageResult<PublicSongView> myLikes(Long userId, long current, long size) {
        long page = Math.max(current, 1L);
        long pageSize = clampSize(size);
        long total = songLikeRepository.countByUser(userId);
        List<Long> songIds = songLikeRepository.pageSongIds(userId, (page - 1) * pageSize, pageSize);
        Map<Long, Song> songMap = songRepository.findByIds(songIds)
                .stream().collect(Collectors.toMap(Song::getId, Function.identity()));
        List<PublicSongView> records = songIds.stream()
                .map(songMap::get)
                .filter(song -> song != null && song.visibleTo(userId, false))
                .map(this::toPublicView)
                .toList();
        return PageResult.of(records, total, page, pageSize);
    }

    /**
     * 加载当前用户可见且可播放的歌曲，否则按不存在处理（跨租户 / 自听他人作品 / 下架）。
     *
     * @param songId 歌曲 ID
     * @param userId 用户 ID
     * @return 歌曲
     */
    private Song requirePlayableSong(Long songId, Long userId) {
        Song song = requireVisibleSong(songId, userId);
        if (!song.playable() || song.getAudioFileId() == null || song.getAudioFileId() <= 0) {
            throw MusicErrorCode.exception(MusicErrorCode.SONG_NOT_FOUND);
        }
        return song;
    }

    /**
     * 加载当前用户可见的歌曲，不存在或不可见时统一按不存在处理。
     *
     * @param songId 歌曲 ID
     * @param userId 用户 ID
     * @return 歌曲
     */
    private Song requireVisibleSong(Long songId, Long userId) {
        return songRepository.findById(songId)
                .filter(song -> song.visibleTo(userId, false))
                .orElseThrow(() -> MusicErrorCode.exception(MusicErrorCode.SONG_NOT_FOUND));
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

    /**
     * 播放历史转视图（歌曲已删除时保留历史，标题缺失）。
     *
     * @param history 播放历史
     * @param song 歌曲，可能为 {@code null}
     * @return 历史视图
     */
    private PlayHistoryView toHistoryView(PlayHistory history, Song song) {
        PlayHistoryView view = new PlayHistoryView();
        view.setSongId(history.getSongId());
        view.setProgressMs(history.getProgressMs());
        view.setSource(history.getSource());
        view.setPlayedAt(history.getPlayedAt());
        if (song != null) {
            view.setTitle(song.getTitle());
            view.setArtistId(song.getArtistId());
            view.setCoverFileId(song.getCoverFileId());
            view.setDurationMs(song.getDurationMs());
            view.setPlayable(song.playable());
        }
        return view;
    }

    /**
     * 歌曲转用户侧视图（不暴露文件 ID）。
     *
     * @param song 歌曲
     * @return 用户侧视图
     */
    private PublicSongView toPublicView(Song song) {
        PublicSongView view = new PublicSongView();
        BeanUtils.copyProperties(song, view);
        view.setPlayable(song.playable());
        view.setTags(List.of());
        return view;
    }
}
