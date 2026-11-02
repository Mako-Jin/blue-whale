package com.bluewhale.doc.application.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.tenant.TenantContext;
import com.bluewhale.doc.domain.model.DocMemberRole;
import com.bluewhale.doc.infrastructure.persistence.entity.DocPage;
import com.bluewhale.doc.infrastructure.persistence.entity.DocPageTag;
import com.bluewhale.doc.infrastructure.persistence.entity.DocTag;
import com.bluewhale.doc.infrastructure.persistence.mapper.DocPageMapper;
import com.bluewhale.doc.infrastructure.persistence.mapper.DocPageTagMapper;
import com.bluewhale.doc.infrastructure.persistence.mapper.DocTagMapper;
import com.bluewhale.doc.infrastructure.persistence.query.DocTagRow;
import com.bluewhale.doc.interfaces.dto.DocTagRequest;
import com.bluewhale.doc.interfaces.vo.DocTagVO;
import com.bluewhale.doc.support.DocAccessGuard;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DuplicateKeyException;

import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link DocTagService} 标签管理与页面标签行为断言。
 *
 * @author BlueWhale
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DocTagServiceTest {

    @Mock
    private DocTagMapper tagMapper;

    @Mock
    private DocPageTagMapper pageTagMapper;

    @Mock
    private DocPageMapper pageMapper;

    @Mock
    private DocAccessGuard accessGuard;

    @Mock
    private DocPageActivityService activityService;

    @InjectMocks
    private DocTagService service;

    /**
     * 初始化 MyBatis-Plus 表信息，使 Lambda 查询列名解析可在纯单测中工作。
     */
    @BeforeAll
    static void initTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, DocTag.class);
        TableInfoHelper.initTableInfo(assistant, DocPageTag.class);
        TableInfoHelper.initTableInfo(assistant, DocPage.class);
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
    void listShouldRequireReadAndReturnPageCounts() {
        DocTagRow row = new DocTagRow();
        row.setTagId(5L);
        row.setName("规范");
        row.setColor("#fff");
        row.setPageCount(3L);
        when(tagMapper.selectWithPageCount(1L, 2L, 9L)).thenReturn(List.of(row));

        List<DocTagVO> result = service.list(9L, 2L);

        verify(accessGuard).requireSpaceRead(9L, 2L);
        assertThat(result).singleElement().satisfies(vo -> {
            assertThat(vo.getId()).isEqualTo(5L);
            assertThat(vo.getName()).isEqualTo("规范");
            assertThat(vo.getColor()).isEqualTo("#fff");
            assertThat(vo.getPageCount()).isEqualTo(3L);
        });
    }

    @Test
    void createShouldRejectBlankName() {
        DocTagRequest request = request("   ", "#1677ff");

        assertThatThrownBy(() -> service.create(9L, request, 2L))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("标签名称不能为空");
        verify(accessGuard).requireMember(9L, 2L, DocMemberRole.EDITOR);
        verify(tagMapper, never()).insert(any(DocTag.class));
    }

    @Test
    void createShouldRejectDuplicatedName() {
        when(tagMapper.selectCount(any())).thenReturn(1L);

        assertThatThrownBy(() -> service.create(9L, request("规范", "#fff"), 2L))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("标签已存在");
        verify(tagMapper, never()).insert(any(DocTag.class));
    }

    @Test
    void createShouldNormalizeNameAndColor() {
        when(tagMapper.selectCount(any())).thenReturn(0L);
        when(tagMapper.insert(any(DocTag.class))).thenReturn(1);

        DocTagVO vo = service.create(9L, request("  研发   规范  ", "  #1677ff  "), 2L);

        ArgumentCaptor<DocTag> captor = ArgumentCaptor.forClass(DocTag.class);
        verify(tagMapper).insert(captor.capture());
        assertThat(captor.getValue().getSpaceId()).isEqualTo(9L);
        assertThat(captor.getValue().getName()).isEqualTo("研发 规范");
        assertThat(captor.getValue().getColor()).isEqualTo("#1677ff");
        assertThat(vo.getName()).isEqualTo("研发 规范");
    }

    @Test
    void createShouldTreatBlankColorAsNullAndTruncateLongName() {
        when(tagMapper.selectCount(any())).thenReturn(0L);
        String longName = "x".repeat(80);

        DocTagVO vo = service.create(9L, request(longName, "   "), 2L);

        assertThat(vo.getName()).hasSize(64);
        assertThat(vo.getColor()).isNull();
    }

    @Test
    void createShouldTranslateDuplicateKeyException() {
        when(tagMapper.selectCount(any())).thenReturn(0L);
        when(tagMapper.insert(any(DocTag.class))).thenThrow(new DuplicateKeyException("dup"));

        assertThatThrownBy(() -> service.create(9L, request("规范", null), 2L))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("标签已存在");
    }

    @Test
    void updateShouldRejectMissingTag() {
        when(tagMapper.selectById(5L)).thenReturn(null);

        assertThatThrownBy(() -> service.update(5L, request("规范", null), 2L))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("标签不存在");
        verify(accessGuard, never()).requireMember(anyLong(), anyLong(), any());
    }

    @Test
    void updateShouldRejectDuplicatedName() {
        when(tagMapper.selectById(5L)).thenReturn(tag(5L, 9L, "旧名", null));
        when(tagMapper.selectCount(any())).thenReturn(1L);

        assertThatThrownBy(() -> service.update(5L, request("规范", null), 2L))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("标签已存在");
        verify(tagMapper, never()).updateById(any(DocTag.class));
    }

    @Test
    void updateShouldPersistNormalizedNameAndColor() {
        when(tagMapper.selectById(5L)).thenReturn(tag(5L, 9L, "旧名", null));
        when(tagMapper.selectCount(any())).thenReturn(0L);

        DocTagVO vo = service.update(5L, request("新名", " #000 "), 2L);

        verify(accessGuard).requireMember(9L, 2L, DocMemberRole.EDITOR);
        ArgumentCaptor<DocTag> captor = ArgumentCaptor.forClass(DocTag.class);
        verify(tagMapper).updateById(captor.capture());
        assertThat(captor.getValue().getName()).isEqualTo("新名");
        assertThat(captor.getValue().getColor()).isEqualTo("#000");
        assertThat(vo.getName()).isEqualTo("新名");
    }

    @Test
    void deleteShouldRemoveRelationsAndTagRow() {
        when(tagMapper.selectById(5L)).thenReturn(tag(5L, 9L, "规范", null));

        service.delete(5L, 2L);

        verify(accessGuard).requireMember(9L, 2L, DocMemberRole.EDITOR);
        verify(pageTagMapper).deleteByTag(1L, 5L);
        verify(tagMapper).deletePhysical(1L, 5L);
    }

    @Test
    void pageTagsShouldRejectUnreadablePage() {
        when(accessGuard.requirePage(7L)).thenReturn(page(7L, 9L));
        when(accessGuard.canRead(any(DocPage.class), eq(2L))).thenReturn(false);

        assertThatThrownBy(() -> service.pageTags(7L, 2L))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getCode())
                .isEqualTo(40401);
    }

    @Test
    void pageTagsShouldReturnEmptyWhenNoRelation() {
        when(accessGuard.requirePage(7L)).thenReturn(page(7L, 9L));
        when(accessGuard.canRead(any(DocPage.class), eq(2L))).thenReturn(true);
        when(pageTagMapper.selectList(any())).thenReturn(List.of());

        assertThat(service.pageTags(7L, 2L)).isEmpty();
        verify(tagMapper, never()).selectBatchIds(any());
    }

    @Test
    void pageTagsShouldMapRelationsInOrder() {
        DocPageTag first = relation(7L, 5L);
        DocPageTag second = relation(7L, 6L);
        when(accessGuard.requirePage(7L)).thenReturn(page(7L, 9L));
        when(accessGuard.canRead(any(DocPage.class), eq(2L))).thenReturn(true);
        when(pageTagMapper.selectList(any())).thenReturn(List.of(first, second));
        when(tagMapper.selectBatchIds(any())).thenReturn(List.of(tag(6L, 9L, "乙", null), tag(5L, 9L, "甲", "#fff")));

        List<DocTagVO> result = service.pageTags(7L, 2L);

        assertThat(result).extracting(DocTagVO::getName).containsExactly("甲", "乙");
    }

    @Test
    void updatePageTagsShouldRejectMoreThanTwentyDistinctTags() {
        when(accessGuard.requirePage(7L)).thenReturn(page(7L, 9L));
        List<Long> ids = IntStream.rangeClosed(1, 21).mapToObj(Long::valueOf).toList();

        assertThatThrownBy(() -> service.updatePageTags(7L, ids, 2L))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("单页标签不能超过 20 个");
        verify(pageTagMapper, never()).deleteByPage(anyLong(), anyLong());
    }

    @Test
    void updatePageTagsShouldRejectTagFromOtherSpace() {
        when(accessGuard.requirePage(7L)).thenReturn(page(7L, 9L));
        when(tagMapper.selectBatchIds(any())).thenReturn(List.of(tag(5L, 8L, "跨空间", null)));

        assertThatThrownBy(() -> service.updatePageTags(7L, List.of(5L), 2L))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("标签不存在或不属于当前空间");
    }

    @Test
    void updatePageTagsShouldRejectMissingTag() {
        when(accessGuard.requirePage(7L)).thenReturn(page(7L, 9L));
        when(tagMapper.selectBatchIds(any())).thenReturn(List.of());

        assertThatThrownBy(() -> service.updatePageTags(7L, List.of(5L, 6L), 2L))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("标签不存在或不属于当前空间");
    }

    @Test
    void updatePageTagsShouldReplaceRelationsAndRecordActivity() {
        when(accessGuard.requirePage(7L)).thenReturn(page(7L, 9L));
        when(tagMapper.selectBatchIds(any())).thenReturn(List.of(tag(5L, 9L, "甲", null), tag(6L, 9L, "乙", null)));

        List<DocTagVO> result = service.updatePageTags(7L, java.util.Arrays.asList(5L, 6L, 5L, 0L, null), 2L);

        verify(accessGuard).requirePageEdit(any(DocPage.class), eq(2L));
        verify(pageTagMapper).deleteByPage(1L, 7L);
        ArgumentCaptor<DocPageTag> captor = ArgumentCaptor.forClass(DocPageTag.class);
        verify(pageTagMapper, times(2)).insert(captor.capture());
        assertThat(captor.getAllValues()).extracting(DocPageTag::getTagId).containsExactly(5L, 6L);
        verify(activityService).record(eq(7L), eq("TAG"), eq(2L), any());
        assertThat(result).extracting(DocTagVO::getId).containsExactly(5L, 6L);
    }

    @Test
    void updatePageTagsShouldClearAllWhenIdsEmpty() {
        when(accessGuard.requirePage(7L)).thenReturn(page(7L, 9L));

        assertThat(service.updatePageTags(7L, List.of(), 2L)).isEmpty();
        verify(pageTagMapper).deleteByPage(1L, 7L);
        verify(pageTagMapper, never()).insert(any(DocPageTag.class));
        verify(activityService).record(eq(7L), eq("TAG"), eq(2L), any());
    }

    /**
     * 构造标签请求。
     *
     * @param name  名称
     * @param color 颜色
     * @return 请求
     */
    private DocTagRequest request(String name, String color) {
        DocTagRequest request = new DocTagRequest();
        request.setName(name);
        request.setColor(color);
        return request;
    }

    /**
     * 构造标签实体。
     *
     * @param id      标签ID
     * @param spaceId 空间ID
     * @param name    名称
     * @param color   颜色
     * @return 标签
     */
    private DocTag tag(Long id, Long spaceId, String name, String color) {
        DocTag tag = new DocTag();
        tag.setId(id);
        tag.setSpaceId(spaceId);
        tag.setName(name);
        tag.setColor(color);
        return tag;
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
     * 构造页面标签关联。
     *
     * @param pageId 页面ID
     * @param tagId  标签ID
     * @return 关联
     */
    private DocPageTag relation(Long pageId, Long tagId) {
        DocPageTag relation = new DocPageTag();
        relation.setPageId(pageId);
        relation.setTagId(tagId);
        return relation;
    }
}
