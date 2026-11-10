package com.bluewhale.doc.application.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.bluewhale.common.tenant.TenantContext;
import com.bluewhale.doc.infrastructure.persistence.entity.DocPageRecent;
import com.bluewhale.doc.infrastructure.persistence.mapper.DocPageRecentMapper;
import com.bluewhale.doc.infrastructure.persistence.query.DocRecentRow;
import com.bluewhale.doc.interfaces.vo.DocRecentVO;
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

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link DocPageRecentService} 最近访问记录与查询行为断言。
 *
 * @author BlueWhale
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DocPageRecentServiceTest {

    @Mock
    private DocPageRecentMapper recentMapper;

    @InjectMocks
    private DocPageRecentService service;

    /**
     * 初始化 MyBatis-Plus 表信息，使 Lambda 查询列名解析可在纯单测中工作。
     */
    @BeforeAll
    static void initTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, DocPageRecent.class);
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
    void recordVisitShouldSkipWhenArgumentsMissing() {
        service.recordVisit(null, 2L);
        service.recordVisit(7L, null);

        verify(recentMapper, never()).insert(any(DocPageRecent.class));
        verify(recentMapper, never()).updateById(any(DocPageRecent.class));
    }

    @Test
    void recordVisitShouldInsertNewRow() {
        when(recentMapper.selectOne(any())).thenReturn(null);

        service.recordVisit(7L, 2L);

        ArgumentCaptor<DocPageRecent> captor = ArgumentCaptor.forClass(DocPageRecent.class);
        verify(recentMapper).insert(captor.capture());
        assertThat(captor.getValue().getPageId()).isEqualTo(7L);
        assertThat(captor.getValue().getUserId()).isEqualTo(2L);
        assertThat(captor.getValue().getVisitedAt()).isNotNull();
    }

    @Test
    void recordVisitShouldRefreshExistingRow() {
        DocPageRecent existing = new DocPageRecent();
        existing.setId(5L);
        existing.setPageId(7L);
        existing.setUserId(2L);
        when(recentMapper.selectOne(any())).thenReturn(existing);

        service.recordVisit(7L, 2L);

        verify(recentMapper).updateById(existing);
        assertThat(existing.getVisitedAt()).isNotNull();
        assertThat(existing.getUpdateTime()).isNotNull();
        verify(recentMapper, never()).insert(any(DocPageRecent.class));
    }

    @Test
    void recordVisitShouldSwallowFailure() {
        when(recentMapper.selectOne(any())).thenThrow(new IllegalStateException("db down"));

        assertThatCode(() -> service.recordVisit(7L, 2L)).doesNotThrowAnyException();
    }

    @Test
    void recentShouldUseDefaultLimitWhenMissingOrNonPositive() {
        when(recentMapper.selectRecent(1L, 2L, 10)).thenReturn(List.of());
        when(recentMapper.selectRecent(1L, 2L, 20)).thenReturn(List.of());

        service.recent(2L, null);
        service.recent(2L, 0);
        service.recent(2L, -5);
        service.recent(2L, 50);

        verify(recentMapper, org.mockito.Mockito.times(3)).selectRecent(1L, 2L, 10);
        verify(recentMapper).selectRecent(1L, 2L, 20);
    }

    @Test
    void recentShouldMapRows() {
        DocRecentRow row = new DocRecentRow();
        row.setPageId(7L);
        row.setTitle("页面");
        row.setSpaceId(3L);
        row.setSpaceName("研发空间");
        row.setSlug("page-slug");
        row.setVisitedAt(LocalDateTime.of(2026, 7, 8, 9, 10));
        row.setUpdateTime(LocalDateTime.of(2026, 7, 9, 10, 11));
        when(recentMapper.selectRecent(1L, 2L, 5)).thenReturn(List.of(row));

        List<DocRecentVO> result = service.recent(2L, 5);

        assertThat(result).singleElement().satisfies(vo -> {
            assertThat(vo.getPageId()).isEqualTo(7L);
            assertThat(vo.getTitle()).isEqualTo("页面");
            assertThat(vo.getSpaceId()).isEqualTo(3L);
            assertThat(vo.getSpaceName()).isEqualTo("研发空间");
            assertThat(vo.getSlug()).isEqualTo("page-slug");
            assertThat(vo.getVisitedAt()).isEqualTo(LocalDateTime.of(2026, 7, 8, 9, 10));
            assertThat(vo.getUpdateTime()).isEqualTo(LocalDateTime.of(2026, 7, 9, 10, 11));
        });
    }

    @Test
    void recentShouldRequireTenant() {
        TenantContext.clear();

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.recent(2L, 5))
                .isInstanceOf(com.bluewhale.common.core.exception.BizException.class);
        verify(recentMapper, never()).selectRecent(anyLong(), anyLong(), anyInt());
    }
}
