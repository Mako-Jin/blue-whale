package com.bluewhale.pdm.overview;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.bluewhale.pdm.change.entity.PdmChgOrder;
import com.bluewhale.pdm.change.mapper.PdmChgOrderMapper;
import com.bluewhale.pdm.document.entity.PdmDocDocument;
import com.bluewhale.pdm.document.mapper.PdmDocDocumentMapper;
import com.bluewhale.pdm.entity.PdmProduct;
import com.bluewhale.pdm.mapper.PdmProductMapper;
import com.bluewhale.pdm.part.entity.PdmBom;
import com.bluewhale.pdm.part.mapper.PdmBomMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link PdmOverviewStatsService} 单元测试：四本账的计数透传、
 * "未关闭变更"排除已出结论状态、物料类型分布的顺序。
 *
 * <p>这里不验 SQL 长什么样——SQL 由 MyBatis-Plus 生成、租户条件由插件追加，
 * 断言它的字符串形态只会把测试绑死在框架实现上。真正会漂的是**口径**
 * （哪些状态算欠账、类型分布含不含停用件），所以只钉口径。</p>
 *
 * @author BlueWhale
 */
class PdmOverviewStatsServiceTest {

    /**
     * 初始化 MyBatis-Plus 表元数据。
     *
     * <p>{@code in(...)} / {@code notIn(...)} 需要**立刻**把 lambda 解析成列名
     * （不像 {@code eq} 是渲染期才解析），而单测里没有 MyBatis 扫描 Mapper 的过程，
     * lambda 缓存是空的。不初始化就会抛 "can not find lambda cache for this entity"，
     * 且只有带 {@code in} / {@code notIn} 的那条路径会炸。</p>
     */
    @BeforeAll
    static void initTableInfo() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        TableInfoHelper.initTableInfo(assistant, PdmProduct.class);
        TableInfoHelper.initTableInfo(assistant, PdmBom.class);
        TableInfoHelper.initTableInfo(assistant, PdmChgOrder.class);
        TableInfoHelper.initTableInfo(assistant, PdmDocDocument.class);
    }

    private final PdmProductMapper productMapper = mock(PdmProductMapper.class);

    private final PdmBomMapper bomMapper = mock(PdmBomMapper.class);

    private final PdmChgOrderMapper chgOrderMapper = mock(PdmChgOrderMapper.class);

    private final PdmDocDocumentMapper docDocumentMapper = mock(PdmDocDocumentMapper.class);

    private final PdmOverviewStatsService service =
            new PdmOverviewStatsService(productMapper, bomMapper, chgOrderMapper, docDocumentMapper);

    /**
     * 让四个 Mapper 各按调用顺序返回计数。
     *
     * <p>物料 Mapper 的调用顺序：在册、停用、成品、部件、零件、原材料。</p>
     *
     * @param parts     物料 Mapper 依次返回的 6 个计数
     * @param boms      BOM Mapper 依次返回：生效、草稿、总数
     * @param changes   变更 Mapper 依次返回：未关闭、评估审批、执行中
     * @param documents 文档 Mapper 依次返回：已发布、草稿、借出
     */
    private void stub(long[] parts, long[] boms, long[] changes, long[] documents) {
        when(productMapper.selectCount(any()))
                .thenReturn(parts[0], parts[1], parts[2], parts[3], parts[4], parts[5]);
        when(bomMapper.selectCount(any())).thenReturn(boms[0], boms[1], boms[2]);
        when(chgOrderMapper.selectCount(any())).thenReturn(changes[0], changes[1], changes[2]);
        when(docDocumentMapper.selectCount(any())).thenReturn(documents[0], documents[1], documents[2]);
    }

    @Test
    @DisplayName("四本账：物料、BOM、变更、图纸各按调用顺序透传，不做二次加工")
    void shouldCountFourLedgers() {
        stub(new long[] {120L, 8L, 30L, 20L, 40L, 30L},
                new long[] {12L, 5L, 40L},
                new long[] {7L, 3L, 2L},
                new long[] {25L, 6L, 2L});

        PdmOverviewStats stats = service.snapshot();

        assertThat(stats.partEnabled()).isEqualTo(120L);
        assertThat(stats.partDisabled()).isEqualTo(8L);
        assertThat(stats.bomReleased()).isEqualTo(12L);
        assertThat(stats.bomDraft()).isEqualTo(5L);
        assertThat(stats.bomTotal()).isEqualTo(40L);
        assertThat(stats.chgOpen()).isEqualTo(7L);
        assertThat(stats.chgAssessing()).isEqualTo(3L);
        assertThat(stats.chgExecuting()).isEqualTo(2L);
        assertThat(stats.docReleased()).isEqualTo(25L);
        assertThat(stats.docDraft()).isEqualTo(6L);
        assertThat(stats.docCheckedOut()).isEqualTo(2L);
    }

    @Test
    @DisplayName("未关闭变更：排除 完成/关闭/驳回/取消 四个已出结论状态，只数还在走流程的")
    void shouldExcludeFinishedChangesFromOpenCount() {
        stub(new long[] {0L, 0L, 0L, 0L, 0L, 0L}, new long[] {0L, 0L, 0L},
                new long[] {0L, 0L, 0L}, new long[] {0L, 0L, 0L});

        service.snapshot();

        ArgumentCaptor<Wrapper<PdmChgOrder>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(chgOrderMapper, org.mockito.Mockito.times(3)).selectCount(captor.capture());
        Wrapper<PdmChgOrder> openWrapper = captor.getAllValues().get(0);
        /* notIn 的四个状态以参数形式挂在 wrapper 上。参数在本版 MyBatis-Plus 里是
           渲染时才登记的：先调 getSqlSegment() 触发，再取参数表，否则拿到空表。 */
        String sqlSegment = openWrapper.getSqlSegment();
        assertThat(sqlSegment).contains("NOT IN");
        List<Object> params = List.copyOf(
                ((LambdaQueryWrapper<PdmChgOrder>) openWrapper).getParamNameValuePairs().values());
        assertThat(params).contains("COMPLETED", "CLOSED", "REJECTED", "CANCELLED");
    }

    @Test
    @DisplayName("物料类型分布：成品/部件/零件/原材料按固定顺序，且只数在册件")
    void shouldBuildTypeDistributionInFixedOrder() {
        stub(new long[] {100L, 0L, 30L, 20L, 40L, 10L},
                new long[] {0L, 0L, 0L}, new long[] {0L, 0L, 0L}, new long[] {0L, 0L, 0L});

        List<PdmOverviewStats.TypeCount> types = service.snapshot().partByType();

        assertThat(types).extracting(PdmOverviewStats.TypeCount::label)
                .containsExactly("成品", "部件", "零件", "原材料");
        assertThat(types).extracting(PdmOverviewStats.TypeCount::count)
                .containsExactly(30L, 20L, 40L, 10L);
    }

    @Test
    @DisplayName("计数返回 null（无数据/驱动异常）：一律当 0，不能拆箱 NPE")
    void shouldTreatNullCountAsZero() {
        when(productMapper.selectCount(any())).thenReturn(null);
        when(bomMapper.selectCount(any())).thenReturn(null);
        when(chgOrderMapper.selectCount(any())).thenReturn(null);
        when(docDocumentMapper.selectCount(any())).thenReturn(null);

        PdmOverviewStats stats = service.snapshot();

        assertThat(stats.partEnabled()).isZero();
        assertThat(stats.bomReleased()).isZero();
        assertThat(stats.chgOpen()).isZero();
        assertThat(stats.docCheckedOut()).isZero();
        assertThat(stats.partByType()).extracting(PdmOverviewStats.TypeCount::count).containsOnly(0L);
    }
}
