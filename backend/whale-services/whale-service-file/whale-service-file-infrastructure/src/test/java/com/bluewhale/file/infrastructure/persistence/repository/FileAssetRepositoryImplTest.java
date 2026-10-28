package com.bluewhale.file.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.file.domain.file.aggregate.FileAsset;
import com.bluewhale.file.domain.file.repository.FileAssetPage;
import com.bluewhale.file.domain.file.repository.FileAssetQuery;
import com.bluewhale.file.domain.model.vo.FileStatus;
import com.bluewhale.file.entity.FileInfo;
import com.bluewhale.file.mapper.FileInfoMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link FileAssetRepositoryImpl} 仓储适配与 PO ↔ 聚合转换单元测试。
 *
 * @author BlueWhale
 */
class FileAssetRepositoryImplTest {

    private final FileInfoMapper fileInfoMapper = mock(FileInfoMapper.class);

    private final FileAssetRepositoryImpl repository = new FileAssetRepositoryImpl(fileInfoMapper);

    /**
     * 构造指定主键与逻辑删除标记的文件持久化对象。
     *
     * @param id 主键
     * @param deleted 逻辑删除标记
     * @return 文件持久化对象
     */
    private static FileInfo po(Long id, Integer deleted) {
        FileInfo po = new FileInfo();
        po.setId(id);
        po.setFileName("a.pdf");
        po.setOriginalName("报告.pdf");
        po.setBucket("bluewhale");
        po.setObjectKey("tenants/1/2026/10/24/a.pdf");
        po.setFileSize(12L);
        po.setContentType("application/pdf");
        po.setMd5("md5");
        po.setStatus(FileStatus.NORMAL.getCode());
        po.setUploaderId(9L);
        po.setDeleted(deleted);
        po.setCreateTime(LocalDateTime.of(2026, 10, 24, 9, 0));
        po.setUpdateTime(LocalDateTime.of(2026, 10, 24, 10, 0));
        return po;
    }

    @Test
    @DisplayName("save：无主键走新增，有主键走更新")
    void shouldInsertOrUpdate() {
        FileAsset created = FileAsset.register("bluewhale", "tenants/1/2026/10/24/a.pdf",
                "报告.pdf", 12L, "application/pdf", "md5", 9L);

        FileAsset saved = repository.save(created);

        verify(fileInfoMapper).insert(any(FileInfo.class));
        assertEquals("a.pdf", saved.getFileName());
        assertEquals(FileStatus.NORMAL, saved.getStatus());

        FileAsset existing = FileAsset.reconstitute(3L, "a.pdf", "报告.pdf", "bluewhale",
                "tenants/1/2026/10/24/a.pdf", 12L, "application/pdf", "md5", FileStatus.LOCKED,
                9L, false, LocalDateTime.of(2026, 10, 24, 9, 0), LocalDateTime.of(2026, 10, 24, 10, 0));
        repository.save(existing);

        verify(fileInfoMapper, times(1)).updateById(any(FileInfo.class));
        verify(fileInfoMapper, times(1)).insert(ArgumentMatchers.any(FileInfo.class));
    }

    @Test
    @DisplayName("findById/findByIdIncludingDeleted：命中返回聚合，未命中返回空")
    void shouldFindById() {
        when(fileInfoMapper.selectById(1L)).thenReturn(po(1L, 0));
        when(fileInfoMapper.selectById(2L)).thenReturn(null);
        when(fileInfoMapper.selectByIdIncludingDeleted(3L)).thenReturn(po(3L, 1));
        when(fileInfoMapper.selectByIdIncludingDeleted(4L)).thenReturn(null);

        Optional<FileAsset> found = repository.findById(1L);
        assertTrue(found.isPresent());
        assertEquals("a.pdf", found.get().getFileName());
        assertTrue(repository.findById(2L).isEmpty());

        Optional<FileAsset> deleted = repository.findByIdIncludingDeleted(3L);
        assertTrue(deleted.isPresent());
        assertTrue(deleted.get().isDeleted());
        assertTrue(repository.findByIdIncludingDeleted(4L).isEmpty());
    }

    @Test
    @DisplayName("findById：空字段归一化（大小为空、状态为空、删除标记为空）")
    void shouldNormalizeNullColumns() {
        FileInfo po = po(5L, null);
        po.setFileSize(null);
        po.setStatus(null);
        when(fileInfoMapper.selectById(5L)).thenReturn(po);

        FileAsset asset = repository.findById(5L).orElseThrow();

        assertEquals(0L, asset.getFileSize());
        assertEquals(FileStatus.NORMAL, asset.getStatus());
        assertFalse(asset.isDeleted());
    }

    @Test
    @DisplayName("findReusableByDigest：命中返回可复用聚合，未命中返回空")
    void shouldFindReusableByDigest() {
        when(fileInfoMapper.selectOne(ArgumentMatchers.<Wrapper<FileInfo>>any())).thenReturn(po(1L, 0));

        Optional<FileAsset> hit = repository.findReusableByDigest("md5", 12L);

        assertTrue(hit.isPresent());
        assertEquals("tenants/1/2026/10/24/a.pdf", hit.get().getObjectKey());

        when(fileInfoMapper.selectOne(ArgumentMatchers.<Wrapper<FileInfo>>any())).thenReturn(null);
        assertTrue(repository.findReusableByDigest("md5", 12L).isEmpty());
    }

    @Test
    @DisplayName("deleteById：委派逻辑删除")
    void shouldDeleteById() {
        repository.deleteById(7L);

        verify(fileInfoMapper).deleteById(7L);
    }

    @Test
    @DisplayName("sumNormalSizeByUploader：兼容数值、字符串、空结果")
    void shouldSumUsedBytes() {
        when(fileInfoMapper.selectObjs(ArgumentMatchers.<Wrapper<FileInfo>>any()))
                .thenReturn(List.<Object>of(123L))
                .thenReturn(List.<Object>of("456"))
                .thenReturn(List.<Object>of())
                .thenReturn(null);

        assertEquals(123L, repository.sumNormalSizeByUploader(9L));
        assertEquals(456L, repository.sumNormalSizeByUploader(9L));
        assertEquals(0L, repository.sumNormalSizeByUploader(9L));
        assertEquals(0L, repository.sumNormalSizeByUploader(9L));
    }

    @Test
    @DisplayName("sumNormalSizeByUploader：未知类型或查询异常抛系统错误")
    void shouldFailWhenSumUnreadable() {
        when(fileInfoMapper.selectObjs(ArgumentMatchers.<Wrapper<FileInfo>>any()))
                .thenReturn(List.<Object>of("abc"))
                .thenReturn(List.<Object>of(new Object()))
                .thenThrow(new IllegalStateException("db down"));

        BizException parseError = assertThrows(BizException.class, () -> repository.sumNormalSizeByUploader(9L));
        assertEquals(ErrorCode.SYSTEM_ERROR.getCode(), parseError.getCode());

        BizException typeError = assertThrows(BizException.class, () -> repository.sumNormalSizeByUploader(9L));
        assertEquals(ErrorCode.SYSTEM_ERROR.getCode(), typeError.getCode());

        BizException queryError = assertThrows(BizException.class, () -> repository.sumNormalSizeByUploader(9L));
        assertEquals(ErrorCode.SYSTEM_ERROR.getCode(), queryError.getCode());
    }

    @Test
    @DisplayName("page：分页转换聚合结果并回传分页元数据")
    void shouldPage() {
        Page<FileInfo> page = new Page<>(1L, 10L);
        page.setRecords(List.of(po(1L, 0)));
        page.setTotal(1L);
        when(fileInfoMapper.selectPage(ArgumentMatchers.<Page<FileInfo>>any(),
                ArgumentMatchers.<Wrapper<FileInfo>>any())).thenReturn(page);
        LocalDateTime start = LocalDateTime.of(2026, 10, 1, 0, 0);
        LocalDateTime end = LocalDateTime.of(2026, 10, 31, 0, 0);

        FileAssetPage result = repository.page(
                new FileAssetQuery("报告", "bluewhale", start, end, 1L, 10L));

        assertEquals(1L, result.total());
        assertEquals(1L, result.current());
        assertEquals(10L, result.size());
        assertEquals("报告.pdf", result.records().get(0).getOriginalName());
    }

    @Test
    @DisplayName("page：无查询条件时仍返回空页")
    void shouldPageWithoutConditions() {
        Page<FileInfo> page = new Page<>(1L, 10L);
        when(fileInfoMapper.selectPage(ArgumentMatchers.<Page<FileInfo>>any(),
                ArgumentMatchers.<Wrapper<FileInfo>>any())).thenReturn(page);

        FileAssetPage result = repository.page(new FileAssetQuery(null, null, null, null, 1L, 10L));

        assertTrue(result.records().isEmpty());
        assertEquals(0L, result.total());
    }

    @Test
    @DisplayName("findChanges：变更流包含逻辑删除行，并按更新时间升序分页")
    void shouldFindChanges() {
        when(fileInfoMapper.selectChanges(any(), any(long.class), any(long.class)))
                .thenReturn(List.of(po(1L, 1)));
        when(fileInfoMapper.countChanges(any())).thenReturn(1L);
        LocalDateTime since = LocalDateTime.of(2026, 10, 24, 9, 0);

        FileAssetPage result = repository.findChanges(since, 2L, 10L);

        assertEquals(1L, result.total());
        assertEquals(2L, result.current());
        assertEquals(10L, result.size());
        assertTrue(result.records().get(0).isDeleted());
        verify(fileInfoMapper).selectChanges(since, 10L, 10L);
    }
}
