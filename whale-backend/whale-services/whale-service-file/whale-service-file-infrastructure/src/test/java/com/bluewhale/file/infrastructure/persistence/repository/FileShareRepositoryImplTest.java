package com.bluewhale.file.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.file.domain.share.aggregate.FileShareAggregate;
import com.bluewhale.file.domain.share.model.FileSharePage;
import com.bluewhale.file.domain.share.model.FileShareQuery;
import com.bluewhale.file.domain.share.model.vo.ShareStatus;
import com.bluewhale.file.domain.share.model.vo.ShareToken;
import com.bluewhale.file.mapper.FileShareMapper;
import com.bluewhale.file.po.FileSharePo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link FileShareRepositoryImpl} 撤销、下载次数原子占用与分页映射单元测试。
 *
 * @author BlueWhale
 */
class FileShareRepositoryImplTest {

    private final FileShareMapper fileShareMapper = mock(FileShareMapper.class);

    private final FileShareRepositoryImpl repository = new FileShareRepositoryImpl(fileShareMapper);

    @Test
    @DisplayName("revoke：仅有效分享返回 true")
    void shouldRevokeOnlyActiveShare() {
        when(fileShareMapper.revokeById(1L)).thenReturn(1);
        when(fileShareMapper.revokeById(2L)).thenReturn(0);

        assertTrue(repository.revoke(1L));
        assertFalse(repository.revoke(2L));
    }

    @Test
    @DisplayName("consumeDownload：以条件更新影响行数判定是否占用成功")
    void shouldConsumeDownloadByAffectedRows() {
        when(fileShareMapper.consumeDownload(1L)).thenReturn(1);
        when(fileShareMapper.consumeDownload(2L)).thenReturn(0);

        assertTrue(repository.consumeDownload(1L));
        assertFalse(repository.consumeDownload(2L));
        verify(fileShareMapper).consumeDownload(1L);
    }

    @Test
    @DisplayName("page：映射记录字段并按分页参数返回")
    void shouldMapSharePage() {
        LocalDateTime now = LocalDateTime.now();
        FileSharePo po = new FileSharePo();
        po.setId(11L);
        po.setFileId(21L);
        po.setFileName("报表.xlsx");
        po.setToken(ShareToken.generate().value());
        po.setExpireTime(now.plusDays(1));
        po.setMaxDownloads(5);
        po.setDownloadCount(2);
        po.setStatus(ShareStatus.ACTIVE.getCode());
        po.setCreatorId(9L);
        po.setCreateTime(now);
        when(fileShareMapper.selectPage(any(), any())).thenAnswer(invocation -> {
            Page<FileSharePo> page = invocation.getArgument(0);
            page.setRecords(List.of(po));
            page.setTotal(1L);
            return page;
        });

        FileSharePage page = repository.page(new FileShareQuery(9L, 1L, 20L));

        assertEquals(1, page.records().size());
        assertEquals(1L, page.total());
        assertEquals("报表.xlsx", page.records().get(0).fileName());
        assertEquals(2, page.records().get(0).downloadCount());
        assertEquals(ShareStatus.ACTIVE.getCode(), page.records().get(0).status());
    }

    @Test
    @DisplayName("save：插入后回读主键与令牌")
    void shouldInsertShare() {
        LocalDateTime now = LocalDateTime.now();
        FileShareAggregate share = FileShareAggregate.reconstitute(null, 21L, "报表.xlsx",
                ShareToken.generate().value(), null, now.plusDays(1), 3, 0, ShareStatus.ACTIVE,
                9L, now, now);
        when(fileShareMapper.insert(any(FileSharePo.class))).thenAnswer(invocation -> {
            FileSharePo po = invocation.getArgument(0);
            po.setId(31L);
            return 1;
        });

        FileShareAggregate saved = repository.save(share);

        assertEquals(31L, saved.getId());
        assertEquals(share.getToken(), saved.getToken());
    }
}
