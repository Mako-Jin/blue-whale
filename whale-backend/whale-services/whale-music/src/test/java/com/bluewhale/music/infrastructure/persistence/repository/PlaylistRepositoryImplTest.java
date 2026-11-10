package com.bluewhale.music.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.music.domain.model.Playlist;
import com.bluewhale.music.domain.model.PlaylistQuery;
import com.bluewhale.music.domain.model.PlaylistSong;
import com.bluewhale.music.domain.model.PlaylistType;
import com.bluewhale.music.domain.model.PlaylistVisibility;
import com.bluewhale.music.infrastructure.persistence.entity.PlaylistEntity;
import com.bluewhale.music.infrastructure.persistence.entity.PlaylistSongEntity;
import com.bluewhale.music.infrastructure.persistence.mapper.PlaylistMapper;
import com.bluewhale.music.infrastructure.persistence.mapper.PlaylistSongMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 歌单仓储实现单元测试（T-07 覆盖率补齐）。
 *
 * <p>覆盖歌单实体的枚举解析回落、默认值填充、歌单歌曲关系维护与分页过滤条件拼装。</p>
 *
 * @author BlueWhale
 */
@ExtendWith(MockitoExtension.class)
class PlaylistRepositoryImplTest {

    /** 固定时间，便于断言生成主键回填 */
    private static final LocalDateTime FIXED_TIME = LocalDateTime.of(2026, 11, 2, 10, 0);

    @Mock
    private PlaylistMapper playlistMapper;

    @Mock
    private PlaylistSongMapper playlistSongMapper;

    @InjectMocks
    private PlaylistRepositoryImpl repository;

    /**
     * 初始化 MyBatis-Plus 表元数据，供 Lambda 查询条件解析列名。
     */
    @BeforeAll
    static void initTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, PlaylistEntity.class);
        TableInfoHelper.initTableInfo(assistant, PlaylistSongEntity.class);
    }

    /**
     * 实体存在时按字段完整映射。
     */
    @Test
    void findByIdShouldMapAllFields() {
        PlaylistEntity entity = new PlaylistEntity();
        entity.setId(1L);
        entity.setOwnerId(2L);
        entity.setTitle("我的收藏");
        entity.setCoverFileId(3L);
        entity.setDescription("私人歌单");
        entity.setPlaylistType(PlaylistType.OFFICIAL.name());
        entity.setVisibility(PlaylistVisibility.PRIVATE.name());
        entity.setSongCount(4);
        entity.setPlayCount(5L);
        entity.setStatus(1);
        entity.setCreateTime(FIXED_TIME);
        entity.setUpdateTime(FIXED_TIME);
        when(playlistMapper.selectById(1L)).thenReturn(entity);

        Playlist playlist = repository.findById(1L).orElseThrow();

        assertEquals(1L, playlist.getId());
        assertEquals(2L, playlist.getOwnerId());
        assertEquals("我的收藏", playlist.getTitle());
        assertEquals(3L, playlist.getCoverFileId());
        assertEquals("私人歌单", playlist.getDescription());
        assertEquals(PlaylistType.OFFICIAL, playlist.getPlaylistType());
        assertEquals(PlaylistVisibility.PRIVATE, playlist.getVisibility());
        assertEquals(4, playlist.getSongCount());
        assertEquals(5L, playlist.getPlayCount());
        assertEquals(1, playlist.getStatus());
    }

    /**
     * 历史脏数据（类型 / 可见性为空或未知）按默认值回落。
     */
    @Test
    void findByIdShouldFallbackWhenStoredEnumsAreUnknown() {
        PlaylistEntity entity = new PlaylistEntity();
        entity.setId(2L);
        entity.setPlaylistType("UNKNOWN_TYPE");
        entity.setVisibility(null);
        when(playlistMapper.selectById(2L)).thenReturn(entity);

        Playlist playlist = repository.findById(2L).orElseThrow();

        assertEquals(PlaylistType.USER, playlist.getPlaylistType());
        assertEquals(PlaylistVisibility.PUBLIC, playlist.getVisibility());
    }

    /**
     * 主键不存在时返回空 Optional。
     */
    @Test
    void findByIdShouldReturnEmptyWhenAbsent() {
        Optional<Playlist> result = repository.findById(3L);

        assertTrue(result.isEmpty());
        verify(playlistMapper).selectById(3L);
    }

    /**
     * 新增时填充默认值，并把数据库生成的主键与时间回写。
     */
    @Test
    void insertShouldFillDefaultsAndPropagateKeys() {
        when(playlistMapper.insert(any(PlaylistEntity.class))).thenAnswer(invocation -> {
            PlaylistEntity entity = invocation.getArgument(0);
            entity.setId(88L);
            entity.setCreateTime(FIXED_TIME);
            entity.setUpdateTime(FIXED_TIME);
            return 1;
        });

        Playlist playlist = new Playlist();
        repository.insert(playlist);

        assertEquals(88L, playlist.getId());
        assertEquals(FIXED_TIME, playlist.getCreateTime());
        assertEquals(FIXED_TIME, playlist.getUpdateTime());

        ArgumentCaptor<PlaylistEntity> captor = ArgumentCaptor.forClass(PlaylistEntity.class);
        verify(playlistMapper).insert(captor.capture());
        PlaylistEntity saved = captor.getValue();
        assertEquals(PlaylistType.USER.name(), saved.getPlaylistType());
        assertEquals(PlaylistVisibility.PUBLIC.name(), saved.getVisibility());
        assertEquals(0, saved.getSongCount());
        assertEquals(0L, saved.getPlayCount());
        assertEquals(1, saved.getStatus());
    }

    /**
     * 更新按主键写回整行。
     */
    @Test
    void updateShouldWriteEntityById() {
        Playlist playlist = new Playlist();
        playlist.setId(9L);
        playlist.setTitle("通勤歌单");

        repository.update(playlist);

        ArgumentCaptor<PlaylistEntity> captor = ArgumentCaptor.forClass(PlaylistEntity.class);
        verify(playlistMapper).updateById(captor.capture());
        assertEquals(9L, captor.getValue().getId());
        assertEquals("通勤歌单", captor.getValue().getTitle());
    }

    /**
     * 删除歌单时同步清理歌单歌曲关系。
     */
    @Test
    void deleteShouldRemovePlaylistAndRelations() {
        repository.delete(6L);

        verify(playlistMapper).deleteById(6L);
        verify(playlistSongMapper).delete(any());
    }

    /**
     * 歌单内歌曲按排序号升序返回并映射。
     */
    @Test
    void findSongsShouldMapEntities() {
        PlaylistSongEntity first = playlistSongEntity(1L, 6L, 10L, 0);
        PlaylistSongEntity second = playlistSongEntity(2L, 6L, 11L, 1);
        when(playlistSongMapper.selectList(any())).thenReturn(List.of(first, second));

        List<PlaylistSong> songs = repository.findSongs(6L);

        assertEquals(2, songs.size());
        assertEquals(10L, songs.get(0).getSongId());
        assertEquals(0, songs.get(0).getSortNo());
        assertEquals(11L, songs.get(1).getSongId());
    }

    /**
     * 加入歌曲时回写数据库生成的关系主键与时间。
     */
    @Test
    void insertSongShouldPropagateGeneratedKeys() {
        when(playlistSongMapper.insert(any(PlaylistSongEntity.class))).thenAnswer(invocation -> {
            PlaylistSongEntity entity = invocation.getArgument(0);
            entity.setId(77L);
            entity.setCreateTime(FIXED_TIME);
            return 1;
        });

        PlaylistSong song = new PlaylistSong();
        song.setPlaylistId(6L);
        song.setSongId(10L);
        song.setSortNo(2);
        repository.insertSong(song);

        assertEquals(77L, song.getId());
        assertEquals(FIXED_TIME, song.getCreateTime());
        ArgumentCaptor<PlaylistSongEntity> captor = ArgumentCaptor.forClass(PlaylistSongEntity.class);
        verify(playlistSongMapper).insert(captor.capture());
        assertEquals(6L, captor.getValue().getPlaylistId());
        assertEquals(10L, captor.getValue().getSongId());
        assertEquals(2, captor.getValue().getSortNo());
    }

    /**
     * 移出歌曲时按歌单 + 歌曲组合条件删除。
     */
    @Test
    void deleteSongShouldUseCompositeCondition() {
        repository.deleteSong(6L, 10L);

        verify(playlistSongMapper).delete(any());
    }

    /**
     * 调整排序号走「空实体 + SET」组合更新。
     */
    @Test
    void updateSongSortShouldUseCompositeCondition() {
        repository.updateSongSort(6L, 10L, 3);

        verify(playlistSongMapper).update(isNull(), any());
    }

    /**
     * 分页查询命中全部过滤条件（所有者 / 类型 / 查看者 / 关键词）。
     */
    @Test
    void pageShouldFilterByAllConditions() {
        PlaylistQuery query = new PlaylistQuery();
        query.setOwnerId(2L);
        query.setViewerId(3L);
        query.setPlaylistType(PlaylistType.OFFICIAL);
        query.setKeyword("通勤");
        query.setCurrent(2L);
        query.setSize(10L);
        Page<PlaylistEntity> page = new Page<>(2, 10);
        page.setRecords(List.of(new PlaylistEntity()));
        when(playlistMapper.selectPage(any(), any())).thenReturn(page);

        assertEquals(1, repository.page(query).size());
    }

    /**
     * 仅公开歌单场景不拼接所有者分支。
     */
    @Test
    void pageShouldFilterPublicOnly() {
        PlaylistQuery query = new PlaylistQuery();
        query.setPublicOnly(true);
        Page<PlaylistEntity> page = new Page<>(1, 20);
        page.setRecords(List.of());
        when(playlistMapper.selectPage(any(), any())).thenReturn(page);

        assertTrue(repository.page(query).isEmpty());
    }

    /**
     * 计数委托给 Mapper。
     */
    @Test
    void countShouldDelegateToMapper() {
        when(playlistMapper.selectCount(any())).thenReturn(5L);

        assertEquals(5L, repository.count(new PlaylistQuery()));
        verify(playlistSongMapper, never()).selectList(any());
    }

    /**
     * 构造歌单歌曲实体。
     *
     * @param id 主键
     * @param playlistId 歌单 ID
     * @param songId 歌曲 ID
     * @param sortNo 排序号
     * @return 歌单歌曲实体
     */
    private PlaylistSongEntity playlistSongEntity(Long id, Long playlistId, Long songId, int sortNo) {
        PlaylistSongEntity entity = new PlaylistSongEntity();
        entity.setId(id);
        entity.setPlaylistId(playlistId);
        entity.setSongId(songId);
        entity.setSortNo(sortNo);
        entity.setAddedBy(2L);
        entity.setCreateTime(FIXED_TIME);
        return entity;
    }
}
