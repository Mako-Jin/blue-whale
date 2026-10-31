package com.bluewhale.music.application.service.impl;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.music.application.view.PublicSongView;
import com.bluewhale.music.domain.model.Song;
import com.bluewhale.music.domain.model.SongPlayRank;
import com.bluewhale.music.domain.model.SongVisibility;
import com.bluewhale.music.domain.repository.AlbumRepository;
import com.bluewhale.music.domain.repository.ArtistRepository;
import com.bluewhale.music.domain.repository.HomeCacheRepository;
import com.bluewhale.music.domain.repository.PlayHistoryRepository;
import com.bluewhale.music.domain.repository.PlaylistRepository;
import com.bluewhale.music.domain.repository.SongRepository;
import com.bluewhale.music.domain.repository.TagRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 发现与榜单应用服务单元测试：近 7 天热榜聚合、空窗口回落与榜单顺序保持。
 *
 * @author BlueWhale
 */
@ExtendWith(MockitoExtension.class)
class DiscoverAppServiceImplTest {

    /** 歌曲 A */
    private static final Long SONG_A = 11L;

    /** 歌曲 B */
    private static final Long SONG_B = 22L;

    @Mock
    private SongRepository songRepository;

    @Mock
    private ArtistRepository artistRepository;

    @Mock
    private AlbumRepository albumRepository;

    @Mock
    private PlaylistRepository playlistRepository;

    @Mock
    private TagRepository tagRepository;

    @Mock
    private HomeCacheRepository homeCacheRepository;

    @Mock
    private PlayHistoryRepository playHistoryRepository;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private DiscoverAppServiceImpl discoverAppService;

    /**
     * 近 7 天有播放记录：按聚合结果顺序装配视图，不回表排序。
     */
    @Test
    void shouldRankHotChartByRecentPlayHistory() {
        when(playHistoryRepository.countRankedSince(any())).thenReturn(2L);
        when(playHistoryRepository.rankSince(any(), anyLong(), anyLong()))
                .thenReturn(List.of(rank(SONG_A, 5L), rank(SONG_B, 3L)));
        when(songRepository.findByIds(any())).thenReturn(List.of(song(SONG_B), song(SONG_A)));

        PageResult<PublicSongView> page = discoverAppService.hotChart(1L, 20L);

        assertEquals(2L, page.getTotal());
        assertEquals(1L, page.getCurrent());
        assertEquals(20L, page.getSize());
        assertEquals(List.of(SONG_A, SONG_B),
                page.getRecords().stream().map(PublicSongView::getId).toList());
        verify(songRepository, never()).page(any());
    }

    /**
     * 窗口内无播放记录：回落累计播放榜，保证新环境榜单仍有内容。
     */
    @Test
    void shouldFallbackToCumulativeChartWhenWindowEmpty() {
        when(playHistoryRepository.countRankedSince(any())).thenReturn(0L);
        when(songRepository.page(any())).thenReturn(List.of(song(SONG_A)));
        when(songRepository.count(any())).thenReturn(1L);

        PageResult<PublicSongView> page = discoverAppService.hotChart(1L, 10L);

        assertEquals(1L, page.getTotal());
        assertEquals(1, page.getRecords().size());
        assertEquals(SONG_A, page.getRecords().get(0).getId());
        verify(playHistoryRepository, never()).rankSince(any(), anyLong(), anyLong());
    }

    /**
     * 窗口内有数据但当前页已越界：返回空记录且不回落到累计榜。
     */
    @Test
    void shouldReturnEmptyPageWithoutFallbackWhenRankEmpty() {
        when(playHistoryRepository.countRankedSince(any())).thenReturn(2L);
        when(playHistoryRepository.rankSince(any(), anyLong(), anyLong())).thenReturn(List.of());

        PageResult<PublicSongView> page = discoverAppService.hotChart(3L, 1L);

        assertEquals(2L, page.getTotal());
        assertEquals(0, page.getRecords().size());
        verify(songRepository, never()).page(any());
    }

    /**
     * 构造排行项。
     *
     * @param songId 歌曲 ID
     * @param playCount 窗口内播放次数
     * @return 排行项
     */
    private SongPlayRank rank(Long songId, Long playCount) {
        SongPlayRank rank = new SongPlayRank();
        rank.setSongId(songId);
        rank.setPlayCount(playCount);
        return rank;
    }

    /**
     * 构造上架公开歌曲。
     *
     * @param id 歌曲 ID
     * @return 歌曲
     */
    private Song song(Long id) {
        Song song = new Song();
        song.setId(id);
        song.setUploaderId(1L);
        song.setArtistId(2L);
        song.setTitle("歌曲-" + id);
        song.setVisibility(SongVisibility.PUBLIC);
        song.setPlayCount(0L);
        return song;
    }
}
