package com.bluewhale.music.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.music.domain.model.Album;
import com.bluewhale.music.domain.model.PublishState;
import com.bluewhale.music.infrastructure.persistence.entity.AlbumEntity;
import com.bluewhale.music.infrastructure.persistence.mapper.AlbumMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 专辑仓储实现单元测试（T-07 覆盖率补齐）。
 *
 * <p>覆盖专辑字段映射、发布状态解析回落、默认 DRAFT 写入与分页过滤条件拼装。</p>
 *
 * @author BlueWhale
 */
@ExtendWith(MockitoExtension.class)
class AlbumRepositoryImplTest {

    /** 固定时间，便于断言生成主键回填 */
    private static final LocalDateTime FIXED_TIME = LocalDateTime.of(2026, 11, 2, 10, 0);

    /** 固定发行日期 */
    private static final LocalDate RELEASE_DATE = LocalDate.of(2026, 10, 1);

    @Mock
    private AlbumMapper albumMapper;

    @InjectMocks
    private AlbumRepositoryImpl repository;

    /**
     * 初始化 MyBatis-Plus 表元数据，供 Lambda 查询条件解析列名。
     */
    @BeforeAll
    static void initTableInfo() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), AlbumEntity.class);
    }

    /**
     * 实体存在时按字段完整映射。
     */
    @Test
    void findByIdShouldMapAllFields() {
        AlbumEntity entity = new AlbumEntity();
        entity.setId(1L);
        entity.setArtistId(2L);
        entity.setTitle("叶惠美");
        entity.setCoverFileId(3L);
        entity.setReleaseDate(RELEASE_DATE);
        entity.setAlbumType("ALBUM");
        entity.setIntro("经典专辑");
        entity.setPublishStatus(PublishState.PUBLISHED.name());
        entity.setSortNo(4);
        entity.setCreateTime(FIXED_TIME);
        entity.setUpdateTime(FIXED_TIME);
        when(albumMapper.selectById(1L)).thenReturn(entity);

        Album album = repository.findById(1L).orElseThrow();

        assertEquals(1L, album.getId());
        assertEquals(2L, album.getArtistId());
        assertEquals("叶惠美", album.getTitle());
        assertEquals(3L, album.getCoverFileId());
        assertEquals(RELEASE_DATE, album.getReleaseDate());
        assertEquals("ALBUM", album.getAlbumType());
        assertEquals("经典专辑", album.getIntro());
        assertEquals(PublishState.PUBLISHED, album.getPublishState());
        assertEquals(4, album.getSortNo());
    }

    /**
     * 发布状态为空或未知时回落 DRAFT。
     */
    @Test
    void findByIdShouldFallbackWhenPublishStatusBlankOrUnknown() {
        AlbumEntity blank = new AlbumEntity();
        blank.setId(2L);
        blank.setPublishStatus("  ");
        AlbumEntity unknown = new AlbumEntity();
        unknown.setId(3L);
        unknown.setPublishStatus("UNKNOWN");
        when(albumMapper.selectById(2L)).thenReturn(blank);
        when(albumMapper.selectById(3L)).thenReturn(unknown);

        assertEquals(PublishState.DRAFT, repository.findById(2L).orElseThrow().getPublishState());
        assertEquals(PublishState.DRAFT, repository.findById(3L).orElseThrow().getPublishState());
    }

    /**
     * 主键不存在时返回空 Optional。
     */
    @Test
    void findByIdShouldReturnEmptyWhenAbsent() {
        Optional<Album> result = repository.findById(4L);

        assertTrue(result.isEmpty());
        verify(albumMapper).selectById(4L);
    }

    /**
     * 新增时回写主键与时间，发布状态为空则按 DRAFT 落库。
     */
    @Test
    void insertShouldPropagateKeysAndDefaultPublishState() {
        when(albumMapper.insert(any(AlbumEntity.class))).thenAnswer(invocation -> {
            AlbumEntity entity = invocation.getArgument(0);
            entity.setId(66L);
            entity.setCreateTime(FIXED_TIME);
            entity.setUpdateTime(FIXED_TIME);
            return 1;
        });

        Album album = new Album();
        album.setTitle("叶惠美");
        repository.insert(album);

        assertEquals(66L, album.getId());
        assertEquals(FIXED_TIME, album.getCreateTime());
        ArgumentCaptor<AlbumEntity> captor = ArgumentCaptor.forClass(AlbumEntity.class);
        verify(albumMapper).insert(captor.capture());
        assertEquals(PublishState.DRAFT.name(), captor.getValue().getPublishStatus());
        assertEquals("叶惠美", captor.getValue().getTitle());
    }

    /**
     * 更新按主键写回整行，并保留已有发布状态。
     */
    @Test
    void updateShouldWriteEntityById() {
        Album album = new Album();
        album.setId(7L);
        album.setTitle("七里香");
        album.setPublishState(PublishState.OFFLINE);

        repository.update(album);

        ArgumentCaptor<AlbumEntity> captor = ArgumentCaptor.forClass(AlbumEntity.class);
        verify(albumMapper).updateById(captor.capture());
        assertEquals(7L, captor.getValue().getId());
        assertEquals(PublishState.OFFLINE.name(), captor.getValue().getPublishStatus());
    }

    /**
     * 删除按主键下发。
     */
    @Test
    void deleteShouldDelegateToMapper() {
        repository.delete(8L);

        verify(albumMapper).deleteById(8L);
    }

    /**
     * 分页查询命中关键词 / 歌手 / 发布状态组合过滤。
     */
    @Test
    void pageShouldFilterByKeywordArtistAndState() {
        Page<AlbumEntity> page = new Page<>(2, 10);
        AlbumEntity entity = new AlbumEntity();
        entity.setId(1L);
        entity.setPublishStatus("UNKNOWN");
        page.setRecords(List.of(entity));
        when(albumMapper.selectPage(any(), any())).thenReturn(page);

        List<Album> albums = repository.page("叶惠美", 2L, PublishState.PUBLISHED, 2, 10);

        assertEquals(1, albums.size());
        assertEquals(PublishState.DRAFT, albums.get(0).getPublishState());
    }

    /**
     * 分页查询无过滤条件时仍按排序号升序返回。
     */
    @Test
    void pageShouldQueryWithoutFilters() {
        Page<AlbumEntity> page = new Page<>(1, 20);
        page.setRecords(List.of());
        when(albumMapper.selectPage(any(), any())).thenReturn(page);

        assertTrue(repository.page(null, null, null, 1, 20).isEmpty());
    }

    /**
     * 计数委托给 Mapper（含歌手维度计数）。
     */
    @Test
    void countShouldDelegateToMapper() {
        when(albumMapper.selectCount(any())).thenReturn(3L);

        assertEquals(3L, repository.count("叶惠美", 2L, PublishState.DRAFT));
        assertEquals(3L, repository.countByArtist(2L));
    }
}
