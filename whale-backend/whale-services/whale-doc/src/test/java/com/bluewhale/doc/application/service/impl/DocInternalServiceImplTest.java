package com.bluewhale.doc.application.service.impl;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.tenant.TenantContext;
import com.bluewhale.doc.infrastructure.persistence.mapper.DocPageMapper;
import com.bluewhale.doc.infrastructure.persistence.query.DocPageChangeRow;
import com.bluewhale.doc.interfaces.vo.DocInternalChangeVO;
import com.bluewhale.doc.interfaces.vo.DocInternalPageVO;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link DocInternalServiceImpl} 知识源同步行为断言。
 *
 * @author BlueWhale
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DocInternalServiceImplTest {

    @Mock
    private DocPageMapper pageMapper;

    @InjectMocks
    private DocInternalServiceImpl service;

    @BeforeEach
    void setUp() {
        TenantContext.set(1L);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void changesShouldReturnEmptyWhenNothingChanged() {
        LocalDateTime since = LocalDateTime.of(2026, 1, 1, 0, 0);
        when(pageMapper.countChanges(1L, since)).thenReturn(0L);

        PageResult<DocInternalChangeVO> result = service.changes(since, 1, 10);

        assertThat(result.getRecords()).isEmpty();
        assertThat(result.getTotal()).isZero();
        assertThat(result.getCurrent()).isEqualTo(1L);
        assertThat(result.getSize()).isEqualTo(10L);
        verify(pageMapper, never()).changes(anyLong(), any(), anyLong(), anyInt());
    }

    @Test
    void changesShouldClampPagingAndMapFlags() {
        LocalDateTime since = LocalDateTime.of(2026, 1, 1, 0, 0);
        DocPageChangeRow active = row(1, 0, 1, 0);
        DocPageChangeRow deleted = row(1, 1, 1, 0);
        DocPageChangeRow inactiveSpace = row(0, 0, 0, 1);
        when(pageMapper.countChanges(1L, since)).thenReturn(3L);
        when(pageMapper.changes(1L, since, 0L, 200)).thenReturn(List.of(active, deleted, inactiveSpace));

        PageResult<DocInternalChangeVO> result = service.changes(since, 0, 500);

        verify(pageMapper).changes(1L, since, 0L, 200);
        assertThat(result.getCurrent()).isEqualTo(1L);
        assertThat(result.getSize()).isEqualTo(200L);
        assertThat(result.getRecords()).hasSize(3);
        assertThat(result.getRecords().get(0).isRestricted()).isTrue();
        assertThat(result.getRecords().get(0).isDeleted()).isFalse();
        assertThat(result.getRecords().get(0).isSpaceActive()).isTrue();
        assertThat(result.getRecords().get(1).isDeleted()).isTrue();
        assertThat(result.getRecords().get(2).isSpaceActive()).isFalse();
        assertThat(result.getRecords().get(0).getSpaceVisibility()).isEqualTo("PUBLIC");
        assertThat(result.getRecords().get(0).getUpdateTime()).isNotNull();
    }

    @Test
    void changesShouldTreatNullFlagsAsFalse() {
        LocalDateTime since = LocalDateTime.of(2026, 2, 2, 0, 0);
        DocPageChangeRow row = row(null, null, null, null);
        when(pageMapper.countChanges(1L, since)).thenReturn(1L);
        when(pageMapper.changes(1L, since, 0L, 10)).thenReturn(List.of(row));

        DocInternalChangeVO vo = service.changes(since, 1, 10).getRecords().get(0);

        assertThat(vo.isRestricted()).isFalse();
        assertThat(vo.isDeleted()).isFalse();
        assertThat(vo.isSpaceActive()).isFalse();
    }

    @Test
    void detailShouldThrowWhenPageMissing() {
        when(pageMapper.selectInternalPage(1L, 9L)).thenReturn(null);

        assertThatThrownBy(() -> service.detail(9L))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getCode())
                .isEqualTo(40401);
    }

    @Test
    void detailShouldReturnInternalSnapshot() {
        DocInternalPageVO page = new DocInternalPageVO();
        page.setPageId(9L);
        page.setTitle("内网页面");
        when(pageMapper.selectInternalPage(1L, 9L)).thenReturn(page);

        assertThat(service.detail(9L)).isSameAs(page);
    }

    /**
     * 构造变更行。
     *
     * @param restricted  受限标记
     * @param deleted     删除标记
     * @param spaceStatus 空间状态
     * @param spaceDeleted 空间删除标记
     * @return 变更行
     */
    private DocPageChangeRow row(Integer restricted, Integer deleted, Integer spaceStatus, Integer spaceDeleted) {
        DocPageChangeRow row = new DocPageChangeRow();
        row.setPageId(9L);
        row.setSpaceId(3L);
        row.setSpaceName("研发空间");
        row.setTitle("页面");
        row.setSlug("page");
        row.setStatus("PUBLISHED");
        row.setRevision(2L);
        row.setIsRestricted(restricted);
        row.setDeleted(deleted);
        row.setVisibility("PUBLIC");
        row.setSpaceStatus(spaceStatus);
        row.setSpaceDeleted(spaceDeleted);
        row.setUpdateTime(LocalDateTime.of(2026, 3, 3, 3, 3));
        return row;
    }
}
