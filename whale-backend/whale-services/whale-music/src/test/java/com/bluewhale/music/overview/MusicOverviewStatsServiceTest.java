package com.bluewhale.music.overview;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.bluewhale.music.infrastructure.persistence.entity.SongAppealEntity;
import com.bluewhale.music.infrastructure.persistence.entity.SongCommentEntity;
import com.bluewhale.music.infrastructure.persistence.entity.SongEntity;
import com.bluewhale.music.infrastructure.persistence.mapper.SongAppealMapper;
import com.bluewhale.music.infrastructure.persistence.mapper.SongCommentMapper;
import com.bluewhale.music.infrastructure.persistence.mapper.SongMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link MusicOverviewStatsService} 单元测试：曲库/审核/版权三本账的
 * 计数透传、状态口径与待办只数未结段。
 *
 * <p>这里不验 SQL 长什么样——SQL 由 MyBatis-Plus 生成、租户条件由插件追加，
 * 断言它的字符串形态只会把测试绑死在框架实现上。真正会漂的是**口径**
 * （待办只数 PENDING、版权下架不进待办），所以只钉口径。</p>
 *
 * @author BlueWhale
 */
class MusicOverviewStatsServiceTest {

    /**
     * 初始化 MyBatis-Plus 表元数据。
     *
     * <p>单测里没有 MyBatis 扫描 Mapper 的过程，lambda 缓存是空的；
     * 不初始化就会抛 "can not find lambda cache for this entity"。</p>
     */
    @BeforeAll
    static void initTableInfo() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        TableInfoHelper.initTableInfo(assistant, SongEntity.class);
        TableInfoHelper.initTableInfo(assistant, SongCommentEntity.class);
        TableInfoHelper.initTableInfo(assistant, SongAppealEntity.class);
    }

    private final SongMapper songMapper = mock(SongMapper.class);

    private final SongCommentMapper songCommentMapper = mock(SongCommentMapper.class);

    private final SongAppealMapper songAppealMapper = mock(SongAppealMapper.class);

    private final MusicOverviewStatsService service =
            new MusicOverviewStatsService(songMapper, songCommentMapper, songAppealMapper);

    /**
     * 让三个 Mapper 各按调用顺序返回计数。
     *
     * @param song    歌曲 Mapper 依次返回：草稿、已上架、已下架、版权待复核、版权有效、版权下架
     * @param comment 评论 Mapper 依次返回：待审
     * @param appeal  申诉 Mapper 依次返回：待复核
     */
    private void stub(long[] song, long comment, long appeal) {
        when(songMapper.selectCount(any())).thenReturn(song[0], song[1], song[2],
                song[3], song[4], song[5]);
        when(songCommentMapper.selectCount(any())).thenReturn(comment);
        when(songAppealMapper.selectCount(any())).thenReturn(appeal);
    }

    @Test
    @DisplayName("缺租户 ID 直接抛：总控会显式传租户，缺租户说明调用方配错了，宁可降级不可给假 0")
    void shouldRejectMissingTenant() {
        assertThatThrownBy(() -> service.snapshot(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tenantId");
    }

    @Test
    @DisplayName("三本账：歌曲三态、版权三态、评论与申诉待办各按调用顺序透传，不做二次加工")
    void shouldCountThreeLedgers() {
        stub(new long[] {12L, 40L, 6L, 3L, 33L, 4L}, 7L, 2L);

        MusicOverviewStats stats = service.snapshot(9001L);

        assertThat(stats.songDraft()).isEqualTo(12L);
        assertThat(stats.songPublished()).isEqualTo(40L);
        assertThat(stats.songOffline()).isEqualTo(6L);
        assertThat(stats.copyrightDeclared()).isEqualTo(3L);
        assertThat(stats.copyrightValid()).isEqualTo(33L);
        assertThat(stats.copyrightTakedown()).isEqualTo(4L);
        assertThat(stats.commentPending()).isEqualTo(7L);
        assertThat(stats.appealPending()).isEqualTo(2L);
    }

    @Test
    @DisplayName("歌曲与版权各走独立 wrapper：六次查询每次一条 eq 条件，发布与版权两个维度不混")
    void shouldCountSongAndCopyrightSeparately() {
        stub(new long[] {0L, 0L, 0L, 0L, 0L, 0L}, 0L, 0L);

        service.snapshot(9001L);

        ArgumentCaptor<Wrapper<SongEntity>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(songMapper, times(6)).selectCount(captor.capture());
        List<String> segments = captor.getAllValues().stream()
                .map(wrapper -> ((LambdaQueryWrapper<SongEntity>) wrapper).getSqlSegment())
                .toList();
        assertThat(segments).hasSize(6);
        assertThat(segments.subList(0, 3)).allMatch(segment -> segment.contains("publish_status = "));
        assertThat(segments.subList(3, 6)).allMatch(segment -> segment.contains("copyright_status = "));
    }

    @Test
    @DisplayName("评论与申诉只数 PENDING 一段：出了结论的不进待办")
    void shouldCountOnlyPendingTodos() {
        stub(new long[] {0L, 0L, 0L, 0L, 0L, 0L}, 0L, 0L);

        service.snapshot(9001L);

        ArgumentCaptor<Wrapper<SongCommentEntity>> commentCaptor = ArgumentCaptor.forClass(Wrapper.class);
        verify(songCommentMapper, times(1)).selectCount(commentCaptor.capture());
        assertThat(((LambdaQueryWrapper<SongCommentEntity>) commentCaptor.getValue()).getSqlSegment())
                .contains("status = ");

        ArgumentCaptor<Wrapper<SongAppealEntity>> appealCaptor = ArgumentCaptor.forClass(Wrapper.class);
        verify(songAppealMapper, times(1)).selectCount(appealCaptor.capture());
        assertThat(((LambdaQueryWrapper<SongAppealEntity>) appealCaptor.getValue()).getSqlSegment())
                .contains("status = ");
    }

    @Test
    @DisplayName("计数返回 null（无数据/驱动异常）：一律当 0，不能拆箱 NPE")
    void shouldTreatNullCountAsZero() {
        when(songMapper.selectCount(any())).thenReturn(null);
        when(songCommentMapper.selectCount(any())).thenReturn(null);
        when(songAppealMapper.selectCount(any())).thenReturn(null);

        MusicOverviewStats stats = service.snapshot(9001L);

        assertThat(stats.songPublished()).isZero();
        assertThat(stats.copyrightDeclared()).isZero();
        assertThat(stats.commentPending()).isZero();
        assertThat(stats.appealPending()).isZero();
    }
}
