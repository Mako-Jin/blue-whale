package com.bluewhale.forum.service.impl;

import com.bluewhale.common.tenant.TenantContext;
import com.bluewhale.forum.config.ForumProperties;
import com.bluewhale.forum.dto.IndexStat;
import com.bluewhale.forum.dto.SearchStatusVO;
import com.bluewhale.forum.entity.ForumThread;
import com.bluewhale.forum.mapper.ForumThreadMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 检索索引服务单测：分词长度口径、覆盖统计、回填批处理。
 *
 * @author BlueWhale
 */
@ExtendWith(MockitoExtension.class)
class SearchServiceImplTest {

    @Mock
    private ForumThreadMapper threadMapper;

    @Spy
    private ForumProperties properties = new ForumProperties();

    @InjectMocks
    private SearchServiceImpl searchService;

    @BeforeEach
    void setUp() {
        TenantContext.set(1L);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("状态：待重建 = 总数 − 已回填")
    void statusComputesPendingBackfill() {
        IndexStat stat = new IndexStat();
        stat.setTotal(10L);
        stat.setIndexed(7L);
        when(threadMapper.selectIndexStat(1L)).thenReturn(stat);
        when(threadMapper.selectNgramTokenSize()).thenReturn(2);

        SearchStatusVO vo = searchService.status();

        assertThat(vo.getEngine()).isEqualTo("mysql-ngram");
        assertThat(vo.getTotalThreads()).isEqualTo(10);
        assertThat(vo.getIndexedThreads()).isEqualTo(7);
        assertThat(vo.getPendingBackfill()).isEqualTo(3);
        assertThat(vo.getMinKeywordLength()).isEqualTo(2);
        assertThat(vo.getLikeFallback()).isTrue();
    }

    @Test
    @DisplayName("状态：统计行缺失时按 0 处理，不抛空指针")
    void statusToleratesMissingStat() {
        when(threadMapper.selectIndexStat(1L)).thenReturn(null);

        SearchStatusVO vo = searchService.status();

        assertThat(vo.getTotalThreads()).isZero();
        assertThat(vo.getPendingBackfill()).isZero();
    }

    @Test
    @DisplayName("回填：按纯文本写入，标签不去进索引列")
    void reindexWritesPlainText() {
        ForumThread first = new ForumThread();
        first.setId(11L);
        first.setContent("<p>鲸议台  正文</p>");
        when(threadMapper.selectPendingIndexRows(eq(1L), anyInt())).thenReturn(List.of(first), List.of());

        int done = searchService.reindex(10);

        assertThat(done).isEqualTo(1);
        verify(threadMapper).updateContentText(11L, "鲸议台 正文");
    }

    @Test
    @DisplayName("回填：达到本次上限即停，不做全表")
    void reindexStopsAtLimit() {
        ForumThread first = new ForumThread();
        first.setId(11L);
        first.setContent("<p>一</p>");
        ForumThread second = new ForumThread();
        second.setId(12L);
        second.setContent("<p>二</p>");
        when(threadMapper.selectPendingIndexRows(eq(1L), anyInt())).thenReturn(List.of(first, second));

        int done = searchService.reindex(1);

        assertThat(done).isEqualTo(1);
        verify(threadMapper).updateContentText(11L, "一");
        verify(threadMapper, never()).updateContentText(eq(12L), anyString());
    }

    @Test
    @DisplayName("回填：无待处理行时直接返回 0")
    void reindexReturnsZeroWhenNothingPending() {
        when(threadMapper.selectPendingIndexRows(eq(1L), anyInt())).thenReturn(List.of());

        assertThat(searchService.reindex(null)).isZero();
        verify(threadMapper, never()).updateContentText(anyLong(), anyString());
    }

    @Test
    @DisplayName("分词长度：单字不走全文，双字走全文")
    void useFulltextByKeywordLength() {
        when(threadMapper.selectNgramTokenSize()).thenReturn(2);

        assertThat(searchService.useFulltext("鲸")).isFalse();
        assertThat(searchService.useFulltext("鲸议")).isTrue();
        assertThat(searchService.useFulltext(" 鲸 ")).isFalse();
        assertThat(searchService.useFulltext(null)).isFalse();
    }

    @Test
    @DisplayName("分词长度：配置显式指定时不读数据库变量")
    void minKeywordLengthHonorsConfig() {
        properties.setSearchMinKeywordLength(3);

        assertThat(searchService.minKeywordLength()).isEqualTo(3);
        verify(threadMapper, never()).selectNgramTokenSize();
    }

    @Test
    @DisplayName("分词长度：数据库无该变量时按 2 兜底，不让接口 500")
    void minKeywordLengthFallsBackWhenVariableMissing() {
        when(threadMapper.selectNgramTokenSize()).thenThrow(new IllegalStateException("unknown variable"));

        assertThat(searchService.minKeywordLength()).isEqualTo(2);
    }
}
