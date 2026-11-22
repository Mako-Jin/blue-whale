package com.bluewhale.doc.overview;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.bluewhale.doc.infrastructure.persistence.entity.DocPage;
import com.bluewhale.doc.infrastructure.persistence.entity.DocSpace;
import com.bluewhale.doc.infrastructure.persistence.mapper.DocPageMapper;
import com.bluewhale.doc.infrastructure.persistence.mapper.DocSpaceMapper;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link DocOverviewStatsService} 单元测试：空间/页面两本账的计数透传、
 * 页面状态口径、回收站按棵数与租户条件。
 *
 * <p>这里不验 SQL 长什么样——SQL 由 MyBatis-Plus 生成、租户条件由插件追加，
 * 断言它的字符串形态只会把测试绑死在框架实现上。真正会漂的是**口径**
 * （正式页面与回收站分两条通道、回收站按棵不按页），所以只钉口径。</p>
 *
 * @author BlueWhale
 */
class DocOverviewStatsServiceTest {

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
        TableInfoHelper.initTableInfo(assistant, DocPage.class);
        TableInfoHelper.initTableInfo(assistant, DocSpace.class);
    }

    private final DocPageMapper pageMapper = mock(DocPageMapper.class);

    private final DocSpaceMapper spaceMapper = mock(DocSpaceMapper.class);

    private final DocOverviewStatsService service = new DocOverviewStatsService(pageMapper, spaceMapper);

    /**
     * 让两个 Mapper 各按调用顺序返回计数。
     *
     * @param spaces 空间 Mapper 依次返回：正常、归档
     * @param pages  页面 Mapper 依次返回：总数、草稿、已发布、待审批、已归档
     * @param trash  回收站根节点数
     */
    private void stub(long[] spaces, long[] pages, long trash) {
        when(spaceMapper.selectCount(any())).thenReturn(spaces[0], spaces[1]);
        when(pageMapper.selectCount(any())).thenReturn(pages[0], pages[1], pages[2], pages[3], pages[4]);
        when(pageMapper.countTrashRootsByTenant(9001L)).thenReturn(trash);
    }

    @Test
    @DisplayName("缺租户 ID 直接抛：总控会显式传租户，缺租户说明调用方配错了，宁可降级不可给假 0")
    void shouldRejectMissingTenant() {
        assertThatThrownBy(() -> service.snapshot(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tenantId");
    }

    @Test
    @DisplayName("四本账：空间、页面、审批、回收站各按调用顺序透传，不做二次加工")
    void shouldCountFourLedgers() {
        stub(new long[] {12L, 3L},
                new long[] {200L, 70L, 100L, 5L, 25L}, 4L);

        DocOverviewStats stats = service.snapshot(9001L);

        assertThat(stats.spaceActive()).isEqualTo(12L);
        assertThat(stats.spaceArchived()).isEqualTo(3L);
        assertThat(stats.pageTotal()).isEqualTo(200L);
        assertThat(stats.pageDraft()).isEqualTo(70L);
        assertThat(stats.pagePublished()).isEqualTo(100L);
        assertThat(stats.pagePendingApproval()).isEqualTo(5L);
        assertThat(stats.pageArchived()).isEqualTo(25L);
        assertThat(stats.trashRoots()).isEqualTo(4L);
    }

    @Test
    @DisplayName("回收站按棵数：走手工 SQL 并把租户 ID 显式带进去")
    void shouldCountTrashRootsWithTenant() {
        stub(new long[] {0L, 0L}, new long[] {0L, 0L, 0L, 0L, 0L}, 7L);

        long trash = service.snapshot(9001L).trashRoots();

        assertThat(trash).isEqualTo(7L);
        verify(pageMapper).countTrashRootsByTenant(9001L);
    }

    @Test
    @DisplayName("页面状态计数走独立 wrapper：每个状态一条 eq 条件，不混用同一本字典")
    void shouldCountPageStatusSeparately() {
        stub(new long[] {0L, 0L}, new long[] {0L, 0L, 0L, 0L, 0L}, 0L);

        service.snapshot(9001L);

        /* 第 1 次是总数（selectCount(null)），第 2-5 次各是一个状态 wrapper */
        ArgumentCaptor<Wrapper<DocPage>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(pageMapper, org.mockito.Mockito.times(5)).selectCount(captor.capture());
        assertThat(captor.getAllValues().get(0)).isNull();
        List<String> segments = captor.getAllValues().stream().skip(1)
                .map(wrapper -> ((LambdaQueryWrapper<DocPage>) wrapper).getSqlSegment())
                .toList();
        assertThat(segments).hasSize(4).allMatch(segment -> segment.contains("status = "));
    }

    @Test
    @DisplayName("计数返回 null（无数据/驱动异常）：一律当 0，不能拆箱 NPE")
    void shouldTreatNullCountAsZero() {
        when(spaceMapper.selectCount(any())).thenReturn(null);
        when(pageMapper.selectCount(any())).thenReturn(null);
        when(pageMapper.countTrashRootsByTenant(any())).thenReturn(0L);

        DocOverviewStats stats = service.snapshot(9001L);

        assertThat(stats.spaceActive()).isZero();
        assertThat(stats.pageTotal()).isZero();
        assertThat(stats.pagePublished()).isZero();
        assertThat(stats.trashRoots()).isZero();
    }
}
