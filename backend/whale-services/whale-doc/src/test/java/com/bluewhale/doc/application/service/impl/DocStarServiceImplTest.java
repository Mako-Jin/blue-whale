package com.bluewhale.doc.application.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.tenant.TenantContext;
import com.bluewhale.doc.infrastructure.persistence.entity.DocPage;
import com.bluewhale.doc.infrastructure.persistence.entity.DocPageStar;
import com.bluewhale.doc.infrastructure.persistence.entity.DocSpace;
import com.bluewhale.doc.infrastructure.persistence.mapper.DocPageMapper;
import com.bluewhale.doc.infrastructure.persistence.mapper.DocPageStarMapper;
import com.bluewhale.doc.infrastructure.persistence.mapper.DocSpaceMapper;
import com.bluewhale.doc.interfaces.vo.DocStarItemVO;
import com.bluewhale.doc.interfaces.vo.DocStarToggleVO;
import com.bluewhale.doc.support.DocAccessGuard;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link DocStarServiceImpl} 收藏切换与收藏列表行为断言。
 *
 * @author BlueWhale
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DocStarServiceImplTest {

    @Mock
    private DocPageStarMapper starMapper;

    @Mock
    private DocPageMapper pageMapper;

    @Mock
    private DocSpaceMapper spaceMapper;

    @Mock
    private DocAccessGuard accessGuard;

    @InjectMocks
    private DocStarServiceImpl service;

    /**
     * 初始化 MyBatis-Plus 表信息，使 Lambda 查询列名解析可在纯单测中工作。
     */
    @BeforeAll
    static void initTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, DocPageStar.class);
    }

    @BeforeEach
    void setUp() {
        TenantContext.set(1L);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void toggleShouldCreateStarWhenAbsent() {
        when(starMapper.selectAny(1L, 2L, 9L)).thenReturn(null);

        DocStarToggleVO vo = service.toggle(9L, 2L);

        verify(accessGuard).requirePageRead(9L, 2L);
        verify(starMapper).insert(any(DocPageStar.class));
        assertThat(vo.isStarred()).isTrue();
    }

    @Test
    void toggleShouldReactivateDeletedStar() {
        DocPageStar star = star(5L, 2L, 9L, 1);
        when(starMapper.selectAny(1L, 2L, 9L)).thenReturn(star);

        DocStarToggleVO vo = service.toggle(9L, 2L);

        verify(starMapper).reactivate(1L, 5L);
        verify(starMapper, never()).deactivate(anyLong(), anyLong());
        assertThat(vo.isStarred()).isTrue();
    }

    @Test
    void toggleShouldDeactivateActiveStar() {
        DocPageStar star = star(5L, 2L, 9L, 0);
        when(starMapper.selectAny(1L, 2L, 9L)).thenReturn(star);

        DocStarToggleVO vo = service.toggle(9L, 2L);

        verify(starMapper).deactivate(1L, 5L);
        verify(starMapper, never()).reactivate(anyLong(), anyLong());
        assertThat(vo.isStarred()).isFalse();
    }

    @Test
    void pageShouldReturnEmptyWhenNoStarRecords() {
        Page<DocPageStar> starPage = new Page<>(1L, 10L);
        starPage.setRecords(List.of());
        starPage.setTotal(0L);
        when(starMapper.selectPage(any(), any())).thenReturn(starPage);

        PageResult<DocStarItemVO> result = service.page(1, 10, 2L);

        assertThat(result.getRecords()).isEmpty();
        assertThat(result.getTotal()).isZero();
    }

    @Test
    void pageShouldClampSizeAndCurrent() {
        Page<DocPageStar> starPage = new Page<>(1L, 200L);
        starPage.setRecords(List.of());
        starPage.setTotal(5L);
        when(starMapper.selectPage(any(), any())).thenReturn(starPage);

        PageResult<DocStarItemVO> result = service.page(0, 1000, 2L);

        assertThat(result.getCurrent()).isEqualTo(1L);
        assertThat(result.getSize()).isEqualTo(200L);
        assertThat(result.getTotal()).isEqualTo(5L);
    }

    @Test
    void pageShouldSkipInvisibleOrDeletedPages() {
        DocPageStar first = star(5L, 2L, 9L, 0);
        DocPageStar second = star(6L, 2L, 10L, 0);
        Page<DocPageStar> starPage = new Page<>(1L, 10L);
        starPage.setRecords(List.of(first, second));
        starPage.setTotal(2L);
        when(starMapper.selectPage(any(), any())).thenReturn(starPage);
        when(pageMapper.selectBatchIds(any())).thenReturn(List.of(page(9L, 3L), page(10L, 30L)));
        when(spaceMapper.selectBatchIds(any())).thenReturn(List.of(space(3L, "研发")));
        when(accessGuard.canRead(any(DocPage.class), eq(2L))).thenReturn(false);

        assertThat(service.page(1, 10, 2L).getRecords()).isEmpty();
    }

    @Test
    void pageShouldMapVisiblePagesToItems() {
        DocPageStar star = star(5L, 2L, 9L, 0);
        star.setCreateTime(LocalDateTime.of(2026, 2, 3, 4, 5));
        Page<DocPageStar> starPage = new Page<>(1L, 10L);
        starPage.setRecords(List.of(star));
        starPage.setTotal(1L);
        when(starMapper.selectPage(any(), any())).thenReturn(starPage);
        DocPage page = page(9L, 3L);
        page.setTitle("页面标题");
        page.setSlug("page-slug");
        page.setStatus("PUBLISHED");
        page.setUpdateTime(LocalDateTime.of(2026, 2, 4, 5, 6));
        when(pageMapper.selectBatchIds(any())).thenReturn(List.of(page));
        when(spaceMapper.selectBatchIds(any())).thenReturn(List.of(space(3L, "研发空间")));
        when(accessGuard.canRead(any(DocPage.class), eq(2L))).thenReturn(true);

        List<DocStarItemVO> records = service.page(1, 10, 2L).getRecords();

        assertThat(records).singleElement().satisfies(vo -> {
            assertThat(vo.getPageId()).isEqualTo(9L);
            assertThat(vo.getSpaceId()).isEqualTo(3L);
            assertThat(vo.getSpaceName()).isEqualTo("研发空间");
            assertThat(vo.getTitle()).isEqualTo("页面标题");
            assertThat(vo.getSlug()).isEqualTo("page-slug");
            assertThat(vo.getStatus()).isEqualTo("PUBLISHED");
            assertThat(vo.getStarredAt()).isEqualTo(LocalDateTime.of(2026, 2, 3, 4, 5));
            assertThat(vo.getUpdateTime()).isEqualTo(LocalDateTime.of(2026, 2, 4, 5, 6));
        });
    }

    /**
     * 构造收藏行。
     *
     * @param id      收藏行ID
     * @param userId  用户ID
     * @param pageId  页面ID
     * @param deleted 删除标记
     * @return 收藏行
     */
    private DocPageStar star(Long id, Long userId, Long pageId, Integer deleted) {
        DocPageStar star = new DocPageStar();
        star.setId(id);
        star.setUserId(userId);
        star.setPageId(pageId);
        star.setDeleted(deleted);
        return star;
    }

    /**
     * 构造页面。
     *
     * @param id      页面ID
     * @param spaceId 空间ID
     * @return 页面
     */
    private DocPage page(Long id, Long spaceId) {
        DocPage page = new DocPage();
        page.setId(id);
        page.setSpaceId(spaceId);
        return page;
    }

    /**
     * 构造空间。
     *
     * @param id   空间ID
     * @param name 名称
     * @return 空间
     */
    private DocSpace space(Long id, String name) {
        DocSpace space = new DocSpace();
        space.setId(id);
        space.setName(name);
        return space;
    }
}
