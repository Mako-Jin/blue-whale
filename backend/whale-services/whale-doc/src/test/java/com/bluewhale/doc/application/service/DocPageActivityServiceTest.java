package com.bluewhale.doc.application.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.doc.infrastructure.persistence.entity.DocPageActivity;
import com.bluewhale.doc.infrastructure.persistence.mapper.DocPageActivityMapper;
import com.bluewhale.doc.interfaces.vo.DocActivityVO;
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

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link DocPageActivityService} 页面动态记录与分页行为断言。
 *
 * @author BlueWhale
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DocPageActivityServiceTest {

    @Mock
    private DocPageActivityMapper activityMapper;

    @InjectMocks
    private DocPageActivityService service;

    /**
     * 初始化 MyBatis-Plus 表信息，使 Lambda 查询列名解析可在纯单测中工作。
     */
    @BeforeAll
    static void initTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, DocPageActivity.class);
    }

    @Test
    void recordShouldSkipWhenPageIdOrActionMissing() {
        service.record(null, "SAVE", 1L, "摘要");
        service.record(7L, null, 1L, "摘要");

        verify(activityMapper, never()).insert(any(DocPageActivity.class));
    }

    @Test
    void recordShouldInsertTrimmedAndTruncatedDetail() {
        service.record(7L, "SAVE", 1L, "  " + "x".repeat(600) + "  ");

        ArgumentCaptor<DocPageActivity> captor = ArgumentCaptor.forClass(DocPageActivity.class);
        verify(activityMapper).insert(captor.capture());
        DocPageActivity activity = captor.getValue();
        assertThat(activity.getPageId()).isEqualTo(7L);
        assertThat(activity.getAction()).isEqualTo("SAVE");
        assertThat(activity.getActorId()).isEqualTo(1L);
        assertThat(activity.getDetail()).hasSize(500);
    }

    @Test
    void recordShouldKeepNullDetail() {
        service.record(7L, "SAVE", 1L, null);

        ArgumentCaptor<DocPageActivity> captor = ArgumentCaptor.forClass(DocPageActivity.class);
        verify(activityMapper).insert(captor.capture());
        assertThat(captor.getValue().getDetail()).isNull();
    }

    @Test
    void recordShouldSwallowPersistenceFailure() {
        when(activityMapper.insert(any(DocPageActivity.class))).thenThrow(new IllegalStateException("db down"));

        assertThatCode(() -> service.record(7L, "SAVE", 1L, "摘要")).doesNotThrowAnyException();
    }

    @Test
    void pageShouldClampPagingAndMapRecords() {
        DocPageActivity activity = new DocPageActivity();
        activity.setId(5L);
        activity.setAction("PUBLISH");
        activity.setActorId(2L);
        activity.setDetail("发布页面 r3");
        activity.setCreateTime(LocalDateTime.of(2026, 6, 7, 8, 9));
        Page<DocPageActivity> page = new Page<>(1L, 100L);
        page.setRecords(List.of(activity));
        page.setTotal(1L);
        when(activityMapper.selectPage(any(), any())).thenReturn(page);

        PageResult<DocActivityVO> result = service.page(7L, 0, 1000);

        assertThat(result.getCurrent()).isEqualTo(1L);
        assertThat(result.getSize()).isEqualTo(100L);
        assertThat(result.getRecords()).singleElement().satisfies(vo -> {
            assertThat(vo.getId()).isEqualTo(5L);
            assertThat(vo.getAction()).isEqualTo("PUBLISH");
            assertThat(vo.getActorId()).isEqualTo(2L);
            assertThat(vo.getDetail()).isEqualTo("发布页面 r3");
            assertThat(vo.getCreateTime()).isEqualTo(LocalDateTime.of(2026, 6, 7, 8, 9));
        });
    }

    @Test
    void pageShouldClampMinimumSizeToOne() {
        Page<DocPageActivity> page = new Page<>(1L, 1L);
        page.setRecords(List.of());
        page.setTotal(0L);
        when(activityMapper.selectPage(any(), any())).thenReturn(page);

        PageResult<DocActivityVO> result = service.page(7L, 1, 0);

        assertThat(result.getSize()).isEqualTo(1L);
    }
}
