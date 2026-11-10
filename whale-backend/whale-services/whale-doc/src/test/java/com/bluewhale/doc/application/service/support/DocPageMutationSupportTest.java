package com.bluewhale.doc.application.service.support;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.doc.infrastructure.persistence.entity.DocPage;
import com.bluewhale.doc.infrastructure.persistence.mapper.DocPageMapper;
import com.bluewhale.doc.support.DocMetrics;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link DocPageMutationSupport} 乐观锁保存行为断言。
 *
 * @author BlueWhale
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DocPageMutationSupportTest {

    @Mock
    private DocPageMapper pageMapper;

    @Mock
    private DocMetrics docMetrics;

    @InjectMocks
    private DocPageMutationSupport support;

    /**
     * 初始化 MyBatis-Plus 表信息，使 Lambda 更新列名解析可在纯单测中工作。
     */
    @BeforeAll
    static void initTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, DocPage.class);
    }

    @Test
    void applyShouldRejectStaleRevisionWithoutWriting() {
        DocPage page = page(9L, 3L);

        assertThatThrownBy(() -> support.apply(page, 2L, "标题", null, null, 7L))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getCode())
                .isEqualTo(40901);
        verify(pageMapper, never()).update(isNull(), org.mockito.ArgumentMatchers.any());
        verify(docMetrics).recordSaveConflict();
    }

    @Test
    @SuppressWarnings("unchecked")
    void applyShouldBumpRevisionAndWriteTitleAndContent() {
        DocPage page = page(9L, 3L);
        DocPage updated = page(9L, 4L);
        updated.setTitle("新标题");
        when(pageMapper.update(isNull(), org.mockito.ArgumentMatchers.any())).thenReturn(1);
        when(pageMapper.selectById(9L)).thenReturn(updated);

        DocPage result = support.apply(page, 3L, "新标题", "{\"type\":\"doc\"}", "纯文本", 7L);

        assertThat(result).isSameAs(updated);
        ArgumentCaptor<LambdaUpdateWrapper<DocPage>> captor =
                ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        verify(pageMapper).update(isNull(), captor.capture());
        assertThat(captor.getValue().getSqlSet()).contains("revision=", "last_editor_id", "content_json");
        verify(pageMapper).selectById(9L);
        verify(docMetrics, never()).recordSaveConflict();
    }

    @Test
    @SuppressWarnings("unchecked")
    void applyShouldOnlyWriteTitleWhenContentOmitted() {
        DocPage page = page(9L, 1L);
        DocPage updated = page(9L, 2L);
        when(pageMapper.update(isNull(), org.mockito.ArgumentMatchers.any())).thenReturn(1);
        when(pageMapper.selectById(9L)).thenReturn(updated);

        support.apply(page, 1L, "仅标题", null, null, 7L);

        ArgumentCaptor<LambdaUpdateWrapper<DocPage>> captor =
                ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        verify(pageMapper).update(isNull(), captor.capture());
        assertThat(captor.getValue().getSqlSet()).contains("title=");
        assertThat(captor.getValue().getSqlSet()).doesNotContain("content_json");
    }

    @Test
    void applyShouldReportPageNotFoundWhenConditionUpdateMissesAndRowGone() {
        DocPage page = page(9L, 1L);
        when(pageMapper.update(isNull(), org.mockito.ArgumentMatchers.any())).thenReturn(0);
        when(pageMapper.selectById(9L)).thenReturn(null);

        assertThatThrownBy(() -> support.apply(page, 1L, null, "{}", "文本", 7L))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getCode())
                .isEqualTo(40401);
        verify(docMetrics, never()).recordSaveConflict();
    }

    @Test
    void applyShouldReportConflictWhenConditionUpdateMissesButRowExists() {
        DocPage page = page(9L, 1L);
        when(pageMapper.update(isNull(), org.mockito.ArgumentMatchers.any())).thenReturn(0);
        when(pageMapper.selectById(9L)).thenReturn(page(9L, 2L));

        assertThatThrownBy(() -> support.apply(page, 1L, null, "{}", "文本", 7L))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getCode())
                .isEqualTo(40901);
        verify(docMetrics).recordSaveConflict();
    }

    /**
     * 构造页面。
     *
     * @param id       页面ID
     * @param revision 版本号
     * @return 页面实体
     */
    private DocPage page(Long id, Long revision) {
        DocPage page = new DocPage();
        page.setId(id);
        page.setRevision(revision);
        return page;
    }
}
