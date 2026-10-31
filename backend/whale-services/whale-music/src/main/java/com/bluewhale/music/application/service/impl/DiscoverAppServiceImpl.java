package com.bluewhale.music.application.service.impl;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.tenant.TenantContext;
import com.bluewhale.music.application.command.SearchCommand;
import com.bluewhale.music.application.service.DiscoverAppService;
import com.bluewhale.music.application.view.AlbumView;
import com.bluewhale.music.application.view.ArtistView;
import com.bluewhale.music.application.view.HomeView;
import com.bluewhale.music.application.view.PlaylistView;
import com.bluewhale.music.application.view.PublicSongView;
import com.bluewhale.music.application.view.SearchResultView;
import com.bluewhale.music.application.view.TagView;
import com.bluewhale.music.domain.model.Album;
import com.bluewhale.music.domain.model.Artist;
import com.bluewhale.music.domain.model.Playlist;
import com.bluewhale.music.domain.model.PlaylistQuery;
import com.bluewhale.music.domain.model.PlaylistType;
import com.bluewhale.music.domain.model.PublishState;
import com.bluewhale.music.domain.model.Song;
import com.bluewhale.music.domain.model.SongQuery;
import com.bluewhale.music.domain.model.SongVisibility;
import com.bluewhale.music.domain.model.Tag;
import com.bluewhale.music.domain.repository.AlbumRepository;
import com.bluewhale.music.domain.repository.ArtistRepository;
import com.bluewhale.music.domain.repository.HomeCacheRepository;
import com.bluewhale.music.domain.repository.PlaylistRepository;
import com.bluewhale.music.domain.repository.SongRepository;
import com.bluewhale.music.domain.repository.TagRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 发现与榜单应用服务实现。
 *
 * <p>首页聚合以租户为粒度写入 Redis 缓存（TTL 60 秒 + 0–10 秒随机抖动），
 * 歌曲发布 / 下架 / 版权下架与官方歌单变更时由写操作主动失效；
 * 榜单与搜索只返回上架且公开的歌曲。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DiscoverAppServiceImpl implements DiscoverAppService {

    /** 首页热门 / 新歌条数 */
    private static final long HOME_SONG_TOP = 10L;

    /** 首页官方歌单条数 */
    private static final long OFFICIAL_PLAYLIST_TOP = 6L;

    /** 首页标签条数 */
    private static final int TAG_TOP = 20;

    /** 搜索结果各组条数 */
    private static final long SEARCH_TOP = 10L;

    /** 首页缓存基础 TTL（秒） */
    private static final int HOME_CACHE_TTL_SECONDS = 60;

    /** 首页缓存随机抖动上限（秒） */
    private static final int HOME_CACHE_JITTER_SECONDS = 10;

    /** 单页最大条数 */
    private static final long MAX_PAGE_SIZE = 200L;

    /** 默认每页条数 */
    private static final long DEFAULT_PAGE_SIZE = 20L;

    /** 排序：热门 */
    private static final String SORT_HOT = "HOT";

    /** 排序：最新 */
    private static final String SORT_NEW = "NEW";

    /** 搜索类型：全部 */
    private static final String TYPE_ALL = "ALL";

    /** 搜索类型：歌曲 */
    private static final String TYPE_SONG = "SONG";

    /** 搜索类型：歌手 */
    private static final String TYPE_ARTIST = "ARTIST";

    /** 搜索类型：专辑 */
    private static final String TYPE_ALBUM = "ALBUM";

    /** 搜索类型：歌单 */
    private static final String TYPE_PLAYLIST = "PLAYLIST";

    /** 启用状态 */
    private static final Integer STATUS_ENABLED = 1;

    private final SongRepository songRepository;

    private final ArtistRepository artistRepository;

    private final AlbumRepository albumRepository;

    private final PlaylistRepository playlistRepository;

    private final TagRepository tagRepository;

    private final HomeCacheRepository homeCacheRepository;

    private final ObjectMapper objectMapper;

    @Override
    public HomeView home(Long userId) {
        Long tenantId = TenantContext.requireId();
        Optional<String> cached = homeCacheRepository.find(tenantId);
        if (cached.isPresent()) {
            try {
                return objectMapper.readValue(cached.get(), HomeView.class);
            } catch (Exception ex) {
                log.warn("首页缓存反序列化失败，回源重建: tenantId={}, error={}", tenantId, ex.getMessage());
            }
        }
        HomeView view = buildHome();
        saveHome(tenantId, view);
        return view;
    }

    @Override
    public PageResult<PublicSongView> hotChart(long current, long size) {
        return chart(SORT_HOT, current, size);
    }

    @Override
    public PageResult<PublicSongView> newChart(long current, long size) {
        return chart(SORT_NEW, current, size);
    }

    @Override
    public SearchResultView search(SearchCommand command, Long viewerId) {
        SearchResultView result = emptyResult(command);
        String keyword = result.getKeyword();
        if (keyword.isEmpty()) {
            return result;
        }
        String type = result.getType();
        if (TYPE_ALL.equals(type) || TYPE_SONG.equals(type)) {
            fillSongs(result, keyword);
        }
        if (TYPE_ALL.equals(type) || TYPE_ARTIST.equals(type)) {
            fillArtists(result, keyword);
        }
        if (TYPE_ALL.equals(type) || TYPE_ALBUM.equals(type)) {
            fillAlbums(result, keyword);
        }
        if (TYPE_ALL.equals(type) || TYPE_PLAYLIST.equals(type)) {
            fillPlaylists(result, keyword);
        }
        result.setTotal(result.getSongTotal() + result.getArtistTotal()
                + result.getAlbumTotal() + result.getPlaylistTotal());
        return result;
    }

    /**
     * 构造首页聚合数据（热门歌曲、新歌、官方歌单与热门标签）。
     *
     * @return 首页聚合视图
     */
    private HomeView buildHome() {
        HomeView view = new HomeView();
        view.setHotSongs(toPublicSongs(songRepository.page(songQuery(SORT_HOT, HOME_SONG_TOP))));
        view.setNewSongs(toPublicSongs(songRepository.page(songQuery(SORT_NEW, HOME_SONG_TOP))));
        view.setOfficialPlaylists(playlistRepository.page(officialQuery(OFFICIAL_PLAYLIST_TOP)).stream()
                .map(this::toPlaylistView).toList());
        view.setTags(tagRepository.list().stream().limit(TAG_TOP).map(this::toTagView).toList());
        return view;
    }

    /**
     * 写入首页缓存，失败仅告警不影响主流程。
     *
     * @param tenantId 租户 ID
     * @param view 首页聚合视图
     */
    private void saveHome(Long tenantId, HomeView view) {
        try {
            int ttl = HOME_CACHE_TTL_SECONDS + ThreadLocalRandom.current().nextInt(HOME_CACHE_JITTER_SECONDS + 1);
            homeCacheRepository.save(tenantId, objectMapper.writeValueAsString(view), Duration.ofSeconds(ttl));
        } catch (Exception ex) {
            log.warn("首页缓存写入失败，忽略: tenantId={}, error={}", tenantId, ex.getMessage());
        }
    }

    /**
     * 构造榜单分页结果。
     *
     * @param sortBy 排序方式
     * @param current 页码
     * @param size 每页条数
     * @return 歌曲分页结果
     */
    private PageResult<PublicSongView> chart(String sortBy, long current, long size) {
        SongQuery query = songQuery(sortBy, clampSize(size));
        query.setCurrent(Math.max(current, 1L));
        List<Song> songs = songRepository.page(query);
        return PageResult.of(toPublicSongs(songs), songRepository.count(query), query.getCurrent(), query.getSize());
    }

    /**
     * 构造仅上架且公开的歌曲查询。
     *
     * @param sortBy 排序方式
     * @param size 每页条数
     * @return 歌曲查询条件
     */
    private SongQuery songQuery(String sortBy, long size) {
        SongQuery query = new SongQuery();
        query.setPublishState(PublishState.PUBLISHED);
        query.setVisibility(SongVisibility.PUBLIC);
        query.setSortBy(sortBy);
        query.setCurrent(1L);
        query.setSize(size);
        return query;
    }

    /**
     * 构造官方歌单查询。
     *
     * @param size 每页条数
     * @return 歌单查询条件
     */
    private PlaylistQuery officialQuery(long size) {
        PlaylistQuery query = new PlaylistQuery();
        query.setPlaylistType(PlaylistType.OFFICIAL);
        query.setPublicOnly(true);
        query.setCurrent(1L);
        query.setSize(size);
        return query;
    }

    /**
     * 构造稳定空搜索结果（关键词为空时不报错）。
     *
     * @param command 搜索命令
     * @return 空搜索结果
     */
    private SearchResultView emptyResult(SearchCommand command) {
        SearchResultView result = new SearchResultView();
        String keyword = command.getKeyword() == null ? "" : command.getKeyword().trim();
        result.setKeyword(keyword);
        result.setType(normalizeType(command.getType()));
        result.setSongs(List.of());
        result.setArtists(List.of());
        result.setAlbums(List.of());
        result.setPlaylists(List.of());
        return result;
    }

    /**
     * 填充歌曲搜索结果。
     *
     * @param result 搜索结果
     * @param keyword 关键词
     */
    private void fillSongs(SearchResultView result, String keyword) {
        SongQuery query = songQuery(SORT_NEW, SEARCH_TOP);
        query.setKeyword(keyword);
        result.setSongs(toPublicSongs(songRepository.page(query)));
        result.setSongTotal(songRepository.count(query));
    }

    /**
     * 填充歌手搜索结果。
     *
     * @param result 搜索结果
     * @param keyword 关键词
     */
    private void fillArtists(SearchResultView result, String keyword) {
        result.setArtists(artistRepository.page(keyword, STATUS_ENABLED, 1L, SEARCH_TOP).stream()
                .map(this::toArtistView).toList());
        result.setArtistTotal(artistRepository.count(keyword, STATUS_ENABLED));
    }

    /**
     * 填充专辑搜索结果（仅已上架专辑）。
     *
     * @param result 搜索结果
     * @param keyword 关键词
     */
    private void fillAlbums(SearchResultView result, String keyword) {
        result.setAlbums(albumRepository.page(keyword, null, PublishState.PUBLISHED, 1L, SEARCH_TOP).stream()
                .map(this::toAlbumView).toList());
        result.setAlbumTotal(albumRepository.count(keyword, null, PublishState.PUBLISHED));
    }

    /**
     * 填充歌单搜索结果（仅公开歌单）。
     *
     * @param result 搜索结果
     * @param keyword 关键词
     */
    private void fillPlaylists(SearchResultView result, String keyword) {
        PlaylistQuery query = new PlaylistQuery();
        query.setKeyword(keyword);
        query.setPublicOnly(true);
        query.setCurrent(1L);
        query.setSize(SEARCH_TOP);
        result.setPlaylists(playlistRepository.page(query).stream().map(this::toPlaylistView).toList());
        result.setPlaylistTotal(playlistRepository.count(query));
    }

    /**
     * 归一化搜索类型，未知或为空时回落 {@code ALL}。
     *
     * @param type 搜索类型文本
     * @return 归一化后的类型
     */
    private String normalizeType(String type) {
        if (type == null || type.isBlank()) {
            return TYPE_ALL;
        }
        String value = type.trim().toUpperCase(Locale.ROOT);
        if (TYPE_SONG.equals(value) || TYPE_ARTIST.equals(value) || TYPE_ALBUM.equals(value)
                || TYPE_PLAYLIST.equals(value)) {
            return value;
        }
        return TYPE_ALL;
    }

    /**
     * 批量转换用户侧歌曲视图。
     *
     * @param songs 歌曲列表
     * @return 用户侧歌曲视图列表
     */
    private List<PublicSongView> toPublicSongs(List<Song> songs) {
        return songs.stream().map(this::toPublicSongView).toList();
    }

    /**
     * 歌曲领域模型转用户侧视图（不暴露文件 ID）。
     *
     * @param song 歌曲
     * @return 用户侧歌曲视图
     */
    private PublicSongView toPublicSongView(Song song) {
        PublicSongView view = new PublicSongView();
        BeanUtils.copyProperties(song, view);
        view.setPlayable(song.playable());
        view.setTags(List.of());
        return view;
    }

    /**
     * 歌单领域模型转视图（列表场景不填充歌曲）。
     *
     * @param playlist 歌单
     * @return 歌单视图
     */
    private PlaylistView toPlaylistView(Playlist playlist) {
        PlaylistView view = new PlaylistView();
        BeanUtils.copyProperties(playlist, view);
        view.setSongs(null);
        return view;
    }

    /**
     * 歌手领域模型转视图。
     *
     * @param artist 歌手
     * @return 歌手视图
     */
    private ArtistView toArtistView(Artist artist) {
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
    private AlbumView toAlbumView(Album album) {
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
    private TagView toTagView(Tag tag) {
        TagView view = new TagView();
        BeanUtils.copyProperties(tag, view);
        return view;
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
