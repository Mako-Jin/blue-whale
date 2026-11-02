package com.bluewhale.doc.application.service.impl;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.tenant.TenantContext;
import com.bluewhale.doc.infrastructure.persistence.mapper.DocPageMapper;
import com.bluewhale.doc.infrastructure.persistence.query.DocSearchRow;
import com.bluewhale.doc.interfaces.dto.DocSearchQueryRequest;
import com.bluewhale.doc.interfaces.vo.DocSearchItemVO;
import com.bluewhale.doc.support.DocMetrics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link DocSearchServiceImpl} 搜索分页与摘要生成行为断言。
 *
 * @author BlueWhale
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DocSearchServiceImplTest {

    @Mock
    private DocPageMapper pageMapper;

    @Mock
    private DocMetrics docMetrics;

    @InjectMocks
    private DocSearchServiceImpl service;

    @BeforeEach
    void setUp() {
        TenantContext.set(1L);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void searchShouldReturnEmptyPageWhenNothingMatched() {
        when(pageMapper.countSearch(1L, 2L, "abc", 3L, 4L, true)).thenReturn(0L);

        PageResult<DocSearchItemVO> result = service.search(request("abc", 3L, 4L, 1, 10), 2L);

        assertThat(result.getRecords()).isEmpty();
        assertThat(result.getTotal()).isZero();
        assertThat(result.getCurrent()).isEqualTo(1L);
        assertThat(result.getSize()).isEqualTo(10L);
        verify(pageMapper, never()).search(anyLong(), anyLong(), eq("abc"), eq(3L), eq(4L),
                eq(true), anyLong(), org.mockito.ArgumentMatchers.anyInt());
        verify(docMetrics).recordSearch(eq(true), anyLong());
    }

    @Test
    void searchShouldUseLikeModeForSingleCharacterKeyword() {
        when(pageMapper.countSearch(1L, 2L, "a", null, null, false)).thenReturn(0L);

        service.search(request("a", null, null, 1, 10), 2L);

        verify(docMetrics).recordSearch(eq(false), anyLong());
    }

    @Test
    void searchShouldClampSizeAndCurrentAndMapRows() {
        DocSearchRow row = new DocSearchRow();
        row.setPageId(7L);
        row.setSpaceId(3L);
        row.setSpaceName("研发空间");
        row.setTitle("设计稿");
        row.setSlug("design");
        row.setStatus("PUBLISHED");
        row.setRevision(5L);
        row.setContentText("前置内容 关键字 后置内容");
        row.setUpdateTime(LocalDateTime.of(2026, 5, 6, 7, 8));
        when(pageMapper.countSearch(1L, 2L, "关键字", null, null, true)).thenReturn(4L);
        when(pageMapper.search(1L, 2L, "关键字", null, null, true, 0L, 100)).thenReturn(List.of(row));

        PageResult<DocSearchItemVO> result = service.search(request("关键字", null, null, 0, 1000), 2L);

        verify(pageMapper).search(1L, 2L, "关键字", null, null, true, 0L, 100);
        assertThat(result.getTotal()).isEqualTo(4L);
        assertThat(result.getCurrent()).isEqualTo(1L);
        assertThat(result.getSize()).isEqualTo(100L);
        assertThat(result.getRecords()).singleElement().satisfies(vo -> {
            assertThat(vo.getPageId()).isEqualTo(7L);
            assertThat(vo.getSpaceName()).isEqualTo("研发空间");
            assertThat(vo.getTitle()).isEqualTo("设计稿");
            assertThat(vo.getRevision()).isEqualTo(5L);
            assertThat(vo.getSnippet()).isEqualTo("前置内容 关键字 后置内容");
            assertThat(vo.getUpdateTime()).isEqualTo(LocalDateTime.of(2026, 5, 6, 7, 8));
        });
    }

    @Test
    void searchShouldComputeOffsetFromRequestedPage() {
        when(pageMapper.countSearch(1L, 2L, "关键字", null, null, true)).thenReturn(20L);
        when(pageMapper.search(1L, 2L, "关键字", null, null, true, 10L, 5)).thenReturn(List.of());

        service.search(request("关键字", null, null, 3, 5), 2L);

        verify(pageMapper).search(1L, 2L, "关键字", null, null, true, 10L, 5);
    }

    @Test
    void searchShouldTrimKeywordAndAddEllipsisAroundHit() {
        DocSearchRow row = new DocSearchRow();
        row.setContentText("a".repeat(60) + " target " + "b".repeat(100));
        when(pageMapper.countSearch(1L, 2L, "target", null, null, true)).thenReturn(1L);
        when(pageMapper.search(1L, 2L, "target", null, null, true, 0L, 10)).thenReturn(List.of(row));

        PageResult<DocSearchItemVO> result = service.search(request("  target  ", null, null, 1, 10), 2L);

        String snippet = result.getRecords().get(0).getSnippet();
        assertThat(snippet).startsWith("…");
        assertThat(snippet).contains("target");
        assertThat(snippet).endsWith("…");
    }

    @Test
    void searchShouldReturnEmptySnippetForBlankContentAndTruncateWithoutHit() {
        DocSearchRow blank = new DocSearchRow();
        blank.setContentText("   ");
        DocSearchRow shortText = new DocSearchRow();
        shortText.setContentText("短文本");
        DocSearchRow longText = new DocSearchRow();
        longText.setContentText("x".repeat(200));
        when(pageMapper.countSearch(1L, 2L, "keyword", null, null, true)).thenReturn(3L);
        when(pageMapper.search(1L, 2L, "keyword", null, null, true, 0L, 10))
                .thenReturn(List.of(blank, shortText, longText));

        PageResult<DocSearchItemVO> result = service.search(request("keyword", null, null, 1, 10), 2L);

        assertThat(result.getRecords()).extracting(DocSearchItemVO::getSnippet)
                .containsExactly("", "短文本", "x".repeat(120) + "…");
    }

    /**
     * 构造搜索请求。
     *
     * @param keyword 关键词
     * @param spaceId 空间ID
     * @param tagId   标签ID
     * @param current 当前页
     * @param size    每页条数
     * @return 搜索请求
     */
    private DocSearchQueryRequest request(String keyword, Long spaceId, Long tagId, long current, long size) {
        DocSearchQueryRequest request = new DocSearchQueryRequest();
        request.setKeyword(keyword);
        request.setSpaceId(spaceId);
        request.setTagId(tagId);
        request.setCurrent(current);
        request.setSize(size);
        return request;
    }
}
