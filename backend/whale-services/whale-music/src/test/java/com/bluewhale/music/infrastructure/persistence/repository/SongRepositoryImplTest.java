package com.bluewhale.music.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.music.domain.model.CopyrightSource;
import com.bluewhale.music.domain.model.CopyrightState;
import com.bluewhale.music.domain.model.PublishState;
import com.bluewhale.music.domain.model.Song;
import com.bluewhale.music.domain.model.SongQuery;
import com.bluewhale.music.domain.model.SongVisibility;
import com.bluewhale.music.infrastructure.persistence.entity.SongEntity;
import com.bluewhale.music.infrastructure.persistence.mapper.SongMapper;
import com.bluewhale.music.infrastructure.persistence.mapper.SongTagMapper;
import com.bluewhale.music.infrastructure.persistence.query.SongTagRow;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 歌曲仓储实现单元测试（T-07 覆盖率补齐）。
 *
 * <p>Repository 只做「实体 ⇄ 领域模型」转换与查询条件拼装，因此用 Mock 的 Mapper 隔离数据库：
 * 既能验证枚举解析回落、可空字段归一化、标签关系同步等真实逻辑，又不需要 MySQL。
 * {@link TableInfoHelper} 初始化用于让 MyBatis-Plus 的 Lambda 条件能在纯单测环境解析列名。</p>
 *
 * @author BlueWhale
 */
@ExtendWith(MockitoExtension.class)
class SongRepositoryImplTest {

    /** 固定时间，便于断言生成主键回填 */
    private static final LocalDateTime FIXED_TIME = LocalDateTime.of(2026, 11, 2, 10, 0);

    @Mock
    private SongMapper songMapper;

    @Mock
    private SongTagMapper songTagMapper;

    @InjectMocks
    private SongRepositoryImpl repository;

    /**
     * 初始化 MyBatis-Plus 表元数据，供 Lambda 查询条件解析列名。
     */
    @BeforeAll
    static void initTableInfo() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), SongEntity.class);
    }

    /**
     * 实体存在时按字段完整映射，包括时间与外键。
     */
    @Test
    void findByIdShouldMapAllFields() {
        SongEntity entity = new SongEntity();
        entity.setId(1L);
        entity.setUploaderId(2L);
        entity.setVisibility(SongVisibility.PRIVATE.name());
        entity.setAlbumId(3L);
        entity.setArtistId(4L);
        entity.setTitle("晴天");
        entity.setSubtitle("叶惠美");
        entity.setAudioFileId(5L);
        entity.setCoverFileId(6L);
        entity.setDurationMs(269_000L);
        entity.setLyricFileId(7L);
        entity.setLyricText("故事的小黄花");
        entity.setLanguage("ZH");
        entity.setPublishStatus(PublishState.PUBLISHED.name());
        entity.setCopyrightStatus(CopyrightState.VALID.name());
        entity.setSource(CopyrightSource.ORIGINAL.name());
        entity.setPlayCount(11L);
        entity.setLikeCount(12L);
        entity.setCommentCount(13L);
        entity.setSortWeight(14);
        entity.setPublishedAt(FIXED_TIME);
        entity.setTakedownReason("侵权");
        entity.setCreateTime(FIXED_TIME);
        entity.setUpdateTime(FIXED_TIME);
        when(songMapper.selectById(1L)).thenReturn(entity);

        Song song = repository.findById(1L).orElseThrow();

        assertEquals(1L, song.getId());
        assertEquals(2L, song.getUploaderId());
        assertEquals(SongVisibility.PRIVATE, song.getVisibility());
        assertEquals(3L, song.getAlbumId());
        assertEquals(4L, song.getArtistId());
        assertEquals("晴天", song.getTitle());
        assertEquals("叶惠美", song.getSubtitle());
        assertEquals(5L, song.getAudioFileId());
        assertEquals(6L, song.getCoverFileId());
        assertEquals(269_000L, song.getDurationMs());
        assertEquals(7L, song.getLyricFileId());
        assertEquals("故事的小黄花", song.getLyricText());
        assertEquals("ZH", song.getLanguage());
        assertEquals(PublishState.PUBLISHED, song.getPublishState());
        assertEquals(CopyrightState.VALID, song.getCopyrightState());
        assertEquals(CopyrightSource.ORIGINAL, song.getSource());
        assertEquals(11L, song.getPlayCount());
        assertEquals(12L, song.getLikeCount());
        assertEquals(13L, song.getCommentCount());
        assertEquals(14, song.getSortWeight());
        assertEquals(FIXED_TIME, song.getPublishedAt());
        assertEquals("侵权", song.getTakedownReason());
        assertEquals(FIXED_TIME, song.getCreateTime());
        assertEquals(FIXED_TIME, song.getUpdateTime());
    }

    /**
     * 历史脏数据（枚举串为空或未知、数值列为空）按默认值回落。
     */
    @Test
    void findByIdShouldFallbackWhenStoredValuesAreBlankOrUnknown() {
        SongEntity entity = new SongEntity();
        entity.setId(2L);
        entity.setVisibility(null);
        entity.setAlbumId(null);
        entity.setDurationMs(null);
        entity.setPublishStatus("UNKNOWN_STATE");
        entity.setCopyrightStatus(null);
        entity.setSource(null);
        when(songMapper.selectById(2L)).thenReturn(entity);

        Song song = repository.findById(2L).orElseThrow();

        assertEquals(SongVisibility.PUBLIC, song.getVisibility());
        assertEquals(0L, song.getAlbumId());
        assertEquals(0L, song.getDurationMs());
        assertEquals(PublishState.DRAFT, song.getPublishState());
        assertEquals(CopyrightState.DECLARED, song.getCopyrightState());
        assertNull(song.getSource());
    }

    /**
     * 主键不存在时返回空 Optional。
     */
    @Test
    void findByIdShouldReturnEmptyWhenAbsent() {
        Optional<Song> result = repository.findById(3L);

        assertTrue(result.isEmpty());
        verify(songMapper).selectById(3L);
    }

    /**
     * 批量查询在入参为空时短路，不访问数据库。
     */
    @Test
    void findByIdsShouldShortCircuitWhenIdsNullOrEmpty() {
        assertTrue(repository.findByIds(null).isEmpty());
        assertTrue(repository.findByIds(List.of()).isEmpty());

        verify(songMapper, never()).selectBatchIds(any());
    }

    /**
     * 批量查询逐条映射为领域模型。
     */
    @Test
    void findByIdsShouldMapAllEntities() {
        SongEntity first = new SongEntity();
        first.setId(1L);
        first.setVisibility(SongVisibility.PUBLIC.name());
        SongEntity second = new SongEntity();
        second.setId(2L);
        second.setVisibility(SongVisibility.PRIVATE.name());
        when(songMapper.selectBatchIds(List.of(1L, 2L))).thenReturn(List.of(first, second));

        List<Song> songs = repository.findByIds(List.of(1L, 2L));

        assertEquals(2, songs.size());
        assertEquals(SongVisibility.PUBLIC, songs.get(0).getVisibility());
        assertEquals(SongVisibility.PRIVATE, songs.get(1).getVisibility());
    }

    /**
     * 播放、点赞、评论计数自增直接下发到 Mapper。
     */
    @Test
    void incrementalCountersShouldDelegateToMapper() {
        repository.addPlayCount(1L, 2L);
        repository.addLikeCount(1L, -1L);
        repository.addCommentCount(1L, 3L);

        verify(songMapper).addPlayCount(1L, 2L);
        verify(songMapper).addLikeCount(1L, -1L);
        verify(songMapper).addCommentCount(1L, 3L);
    }

    /**
     * 新增时填充实体默认值，并把数据库生成的主键与时间回写到聚合根。
     */
    @Test
    void insertShouldFillEntityAndPropagateGeneratedKeys() {
        when(songMapper.insert(any(SongEntity.class))).thenAnswer(invocation -> {
            SongEntity entity = invocation.getArgument(0);
            entity.setId(99L);
            entity.setCreateTime(FIXED_TIME);
            entity.setUpdateTime(FIXED_TIME);
            return 1;
        });

        Song song = new Song();
        repository.insert(song);

        assertEquals(99L, song.getId());
        assertEquals(FIXED_TIME, song.getCreateTime());
        assertEquals(FIXED_TIME, song.getUpdateTime());

        ArgumentCaptor<SongEntity> captor = ArgumentCaptor.forClass(SongEntity.class);
        verify(songMapper).insert(captor.capture());
        SongEntity saved = captor.getValue();
        assertEquals(SongVisibility.PUBLIC.name(), saved.getVisibility());
        assertEquals(0L, saved.getAlbumId());
        assertEquals(0L, saved.getDurationMs());
        assertEquals(PublishState.DRAFT.name(), saved.getPublishStatus());
        assertEquals(CopyrightState.DECLARED.name(), saved.getCopyrightStatus());
        assertNull(saved.getSource());
    }

    /**
     * 更新时以显式 SET 写入可空的版权下架原因，避免 updateById 忽略 null。
     */
    @Test
    void updateShouldWriteTakedownReasonExplicitly() {
        Song song = new Song();
        song.setId(5L);
        song.setTitle("晴天");
        song.setTakedownReason("侵权");

        repository.update(song);

        ArgumentCaptor<SongEntity> captor = ArgumentCaptor.forClass(SongEntity.class);
        verify(songMapper).update(captor.capture(), any());
        assertEquals(5L, captor.getValue().getId());
        assertEquals("侵权", captor.getValue().getTakedownReason());
    }

    /**
     * 删除歌曲时同步清理歌曲-标签关系。
     */
    @Test
    void deleteShouldRemoveSongAndTagRelations() {
        repository.delete(6L);

        verify(songMapper).deleteById(6L);
        verify(songTagMapper).deleteBySongId(6L);
    }

    /**
     * 标签筛选命中空集合时直接返回空页，不再查询歌曲表。
     */
    @Test
    void pageShouldReturnEmptyWhenTagHasNoSongs() {
        SongQuery query = new SongQuery();
        query.setTagId(7L);
        when(songTagMapper.selectSongIdsByTagId(7L)).thenReturn(List.of());

        assertTrue(repository.page(query).isEmpty());
        verify(songMapper, never()).selectPage(any(), any());
    }

    /**
     * 热门排序：先按标签过滤，再按播放量 / 点赞数 / 主键倒序。
     */
    @Test
    void pageShouldQueryWithTagFilterAndHotSort() {
        SongQuery query = new SongQuery();
        query.setTagId(7L);
        query.setSortBy("HOT");
        when(songTagMapper.selectSongIdsByTagId(7L)).thenReturn(List.of(1L, 2L));
        Page<SongEntity> page = new Page<>(1, 20);
        SongEntity entity = new SongEntity();
        entity.setId(1L);
        entity.setVisibility(SongVisibility.PUBLIC.name());
        page.setRecords(List.of(entity));
        when(songMapper.selectPage(any(), any())).thenReturn(page);

        List<Song> songs = repository.page(query);

        assertEquals(1, songs.size());
        assertEquals(1L, songs.get(0).getId());
    }

    /**
     * 默认排序：命中全部过滤条件（歌手 / 专辑 / 上传者 / 状态 / 可见性 / 关键词）。
     */
    @Test
    void pageShouldQueryWithAllFiltersAndDefaultSort() {
        SongQuery query = new SongQuery();
        query.setArtistId(1L);
        query.setAlbumId(2L);
        query.setUploaderId(3L);
        query.setPublishState(PublishState.PUBLISHED);
        query.setVisibility(SongVisibility.PRIVATE);
        query.setKeyword("晴天");
        Page<SongEntity> page = new Page<>(2, 10);
        page.setRecords(List.of(new SongEntity()));
        when(songMapper.selectPage(any(), any())).thenReturn(page);

        assertEquals(1, repository.page(query).size());
        verify(songTagMapper, never()).selectSongIdsByTagId(anyLong());
    }

    /**
     * 标签筛选命中空集合时计数直接返回 0。
     */
    @Test
    void countShouldReturnZeroWhenTagHasNoSongs() {
        SongQuery query = new SongQuery();
        query.setTagId(7L);
        when(songTagMapper.selectSongIdsByTagId(7L)).thenReturn(List.of());

        assertEquals(0L, repository.count(query));
        verify(songMapper, never()).selectCount(any());
    }

    /**
     * 常规计数委托给 Mapper。
     */
    @Test
    void countShouldDelegateToMapper() {
        when(songMapper.selectCount(any())).thenReturn(3L);

        assertEquals(3L, repository.count(new SongQuery()));
    }

    /**
     * 重设标签时过滤 null 与非正数标签，只写入合法关系。
     */
    @Test
    void replaceTagsShouldSkipNullAndNonPositiveTagIds() {
        Collection<Long> tags = Arrays.asList(null, 0L, -1L, 5L);

        repository.replaceTags(9L, tags);

        verify(songTagMapper).deleteBySongId(9L);
        verify(songTagMapper, times(1)).insertOne(anyLong(), anyLong());
        verify(songTagMapper).insertOne(9L, 5L);
    }

    /**
     * 标签集合为 null 时仅清理旧关系。
     */
    @Test
    void replaceTagsShouldOnlyClearWhenTagsNull() {
        repository.replaceTags(9L, null);

        verify(songTagMapper).deleteBySongId(9L);
        verify(songTagMapper, never()).insertOne(anyLong(), anyLong());
    }

    /**
     * 查询与移除标签关系直接委托给 Mapper。
     */
    @Test
    void findTagIdsAndRemoveTagShouldDelegate() {
        when(songTagMapper.selectTagIdsBySongId(9L)).thenReturn(List.of(1L, 2L));

        assertEquals(List.of(1L, 2L), repository.findTagIds(9L));
        repository.removeTag(2L);

        verify(songTagMapper).deleteByTagId(2L);
    }

    /**
     * 批量标签查询在入参为空时返回空映射，不访问数据库。
     */
    @Test
    void findTagIdsBySongIdsShouldReturnEmptyForMissingInput() {
        assertTrue(repository.findTagIdsBySongIds(null).isEmpty());
        assertTrue(repository.findTagIdsBySongIds(List.of()).isEmpty());

        verify(songTagMapper, never()).selectRowsBySongIds(any());
    }

    /**
     * 批量标签查询按歌曲 ID 归组。
     */
    @Test
    void findTagIdsBySongIdsShouldGroupRowsBySong() {
        when(songTagMapper.selectRowsBySongIds(List.of(1L, 2L)))
                .thenReturn(List.of(row(1L, 10L), row(1L, 11L), row(2L, 20L)));

        Map<Long, List<Long>> result = repository.findTagIdsBySongIds(List.of(1L, 2L));

        assertEquals(2, result.size());
        assertEquals(List.of(10L, 11L), result.get(1L));
        assertEquals(List.of(20L), result.get(2L));
    }

    /**
     * 构造歌曲-标签关系行。
     *
     * @param songId 歌曲 ID
     * @param tagId 标签 ID
     * @return 关系行
     */
    private SongTagRow row(Long songId, Long tagId) {
        SongTagRow row = new SongTagRow();
        row.setSongId(songId);
        row.setTagId(tagId);
        return row;
    }
}
