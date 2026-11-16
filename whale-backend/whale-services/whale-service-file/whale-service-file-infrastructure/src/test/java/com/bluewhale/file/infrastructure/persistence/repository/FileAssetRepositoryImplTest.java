package com.bluewhale.file.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.tenant.TenantContext;
import com.bluewhale.file.domain.file.aggregate.FileAssetAggregate;
import com.bluewhale.file.domain.model.FileAssetPage;
import com.bluewhale.file.domain.model.FileAssetQuery;
import com.bluewhale.file.domain.model.FileRecycleItem;
import com.bluewhale.file.domain.model.FileRecyclePage;
import com.bluewhale.file.domain.model.FileRecycleQuery;
import com.bluewhale.file.domain.model.vo.FileStatus;
import com.bluewhale.file.domain.model.vo.StorageQuota;
import com.bluewhale.file.po.FileInfoPo;
import com.bluewhale.file.mapper.FileInfoMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.InOrder;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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

    private final FileAssetRepositoryImpl repository =
            new FileAssetRepositoryImpl(fileInfoMapper, new SimpleMeterRegistry());

    /**
     * 构造指定主键与逻辑删除标记的文件持久化对象。
     *
     * @param id 主键
     * @param deleted 逻辑删除标记
     * @return 文件持久化对象
     */
    private static FileInfoPo po(Long id, Integer deleted) {
        FileInfoPo po = new FileInfoPo();
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
        FileAssetAggregate created = FileAssetAggregate.register("bluewhale", "tenants/1/2026/10/24/a.pdf",
                "报告.pdf", 12L, "application/pdf", "md5", 9L, "local");

        FileAssetAggregate saved = repository.save(created);

        verify(fileInfoMapper).insert(any(FileInfoPo.class));
        assertEquals("a.pdf", saved.getFileName());
        assertEquals(FileStatus.NORMAL, saved.getStatus());

        FileAssetAggregate existing = FileAssetAggregate.reconstitute(3L, "a.pdf", "报告.pdf", "bluewhale",
                "tenants/1/2026/10/24/a.pdf", 12L, "application/pdf", "md5", FileStatus.LOCKED,
                9L, false, LocalDateTime.of(2026, 10, 24, 9, 0), LocalDateTime.of(2026, 10, 24, 10, 0),
                "local");
        repository.save(existing);

        verify(fileInfoMapper, times(1)).updateById(any(FileInfoPo.class));
        verify(fileInfoMapper, times(1)).insert(ArgumentMatchers.any(FileInfoPo.class));
    }

    @Test
    @DisplayName("saveWithQuotaCheck：无上传用户或不限配额时退化为普通保存")
    void shouldSaveWithoutQuotaGuard() {
        FileAssetAggregate noUploader = FileAssetAggregate.register("bluewhale", "tenants/1/2026/10/24/a.pdf",
                "报告.pdf", 12L, "application/pdf", "md5", null, "local");
        FileAssetAggregate unlimited = FileAssetAggregate.register("bluewhale", "tenants/1/2026/10/24/b.pdf",
                "报告.pdf", 12L, "application/pdf", "md5", 9L, "local");

        repository.saveWithQuotaCheck(noUploader, userQuota(1000L));
        repository.saveWithQuotaCheck(unlimited, unlimitedQuota());

        verify(fileInfoMapper, times(2)).insert(any(FileInfoPo.class));
        verify(fileInfoMapper, never()).acquireNamedLock(anyString(), anyInt());
    }

    @Test
    @DisplayName("saveWithQuotaCheck：锁内统计用量后放行，无事务上下文时立即释放锁")
    void shouldSaveWithinQuotaGuard() {
        FileAssetAggregate asset = quotaAsset(100L);
        when(fileInfoMapper.acquireNamedLock("whale:file:quota:9", 5)).thenReturn(1);
        when(fileInfoMapper.selectObjs(ArgumentMatchers.<Wrapper<FileInfoPo>>any()))
                .thenReturn(List.<Object>of(100L));

        FileAssetAggregate saved = repository.saveWithQuotaCheck(asset, userQuota(1000L));

        assertEquals("a.pdf", saved.getFileName());
        verify(fileInfoMapper).insert(any(FileInfoPo.class));
        verify(fileInfoMapper).releaseNamedLock("whale:file:quota:9");
    }

    @Test
    @DisplayName("saveWithQuotaCheck：超出配额时不落库并释放锁")
    void shouldRejectExceedingQuota() {
        when(fileInfoMapper.acquireNamedLock("whale:file:quota:9", 5)).thenReturn(1);
        when(fileInfoMapper.selectObjs(ArgumentMatchers.<Wrapper<FileInfoPo>>any()))
                .thenReturn(List.<Object>of(950L));

        BizException ex = assertThrows(BizException.class,
                () -> repository.saveWithQuotaCheck(quotaAsset(100L), userQuota(1000L)));

        assertEquals(ErrorCode.PARAM_ERROR.getCode(), ex.getCode());
        verify(fileInfoMapper, never()).insert(any(FileInfoPo.class));
        verify(fileInfoMapper).releaseNamedLock("whale:file:quota:9");
    }

    @Test
    @DisplayName("saveWithQuotaCheck：获取命名锁超时或异常时抛系统错误，不查用量")
    void shouldFailWhenQuotaLockUnavailable() {
        when(fileInfoMapper.acquireNamedLock("whale:file:quota:9", 5)).thenReturn(0).thenReturn(null);

        BizException timeout = assertThrows(BizException.class,
                () -> repository.saveWithQuotaCheck(quotaAsset(100L), userQuota(1000L)));
        assertEquals(ErrorCode.SYSTEM_ERROR.getCode(), timeout.getCode());

        BizException failed = assertThrows(BizException.class,
                () -> repository.saveWithQuotaCheck(quotaAsset(100L), userQuota(1000L)));
        assertEquals(ErrorCode.SYSTEM_ERROR.getCode(), failed.getCode());

        verify(fileInfoMapper, never()).selectObjs(ArgumentMatchers.<Wrapper<FileInfoPo>>any());
        verify(fileInfoMapper, never()).insert(any(FileInfoPo.class));
        verify(fileInfoMapper, never()).releaseNamedLock(anyString());
    }

    @Test
    @DisplayName("saveWithQuotaCheck：事务同步激活时延迟到事务完成后释放锁")
    void shouldReleaseQuotaLockAfterTransactionCompletion() {
        when(fileInfoMapper.acquireNamedLock("whale:file:quota:9", 5)).thenReturn(1);
        when(fileInfoMapper.selectObjs(ArgumentMatchers.<Wrapper<FileInfoPo>>any()))
                .thenReturn(List.<Object>of(0L));
        TransactionSynchronizationManager.initSynchronization();
        try {
            repository.saveWithQuotaCheck(quotaAsset(100L), userQuota(1000L));
            verify(fileInfoMapper, never()).releaseNamedLock(anyString());

            TransactionSynchronizationManager.getSynchronizations()
                    .forEach(synchronization -> synchronization.afterCompletion(
                            TransactionSynchronization.STATUS_COMMITTED));
            verify(fileInfoMapper).releaseNamedLock("whale:file:quota:9");
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    @DisplayName("saveWithQuotaCheck：释放锁失败仅告警，不影响保存结果")
    void shouldIgnoreReleaseFailure() {
        when(fileInfoMapper.acquireNamedLock("whale:file:quota:9", 5)).thenReturn(1);
        when(fileInfoMapper.selectObjs(ArgumentMatchers.<Wrapper<FileInfoPo>>any()))
                .thenReturn(List.<Object>of(0L));
        when(fileInfoMapper.releaseNamedLock("whale:file:quota:9"))
                .thenThrow(new IllegalStateException("connection closed"));

        FileAssetAggregate saved = repository.saveWithQuotaCheck(quotaAsset(100L), userQuota(1000L));

        assertEquals("a.pdf", saved.getFileName());
        verify(fileInfoMapper).insert(any(FileInfoPo.class));
    }

    @Test
    @DisplayName("saveWithQuotaCheck：租户维度超限时拒绝并释放租户锁")
    void shouldRejectWhenTenantQuotaExceeded() {
        when(fileInfoMapper.acquireNamedLock("whale:file:quota:tenant:1", 5)).thenReturn(1);
        when(fileInfoMapper.selectObjs(ArgumentMatchers.<Wrapper<FileInfoPo>>any()))
                .thenReturn(List.<Object>of(950L));
        TenantContext.set(1L);
        try {
            BizException ex = assertThrows(BizException.class,
                    () -> repository.saveWithQuotaCheck(quotaAsset(100L), new StorageQuota(0L, 1000L, 0L)));

            assertEquals(ErrorCode.PARAM_ERROR.getCode(), ex.getCode());
            assertTrue(ex.getMessage().contains("超出租户存储配额"));
        } finally {
            TenantContext.clear();
        }
        verify(fileInfoMapper, never()).insert(any(FileInfoPo.class));
        verify(fileInfoMapper).releaseNamedLock("whale:file:quota:tenant:1");
    }

    @Test
    @DisplayName("saveWithQuotaCheck：桶维度超限时拒绝并释放桶锁")
    void shouldRejectWhenBucketQuotaExceeded() {
        when(fileInfoMapper.acquireNamedLock("whale:file:quota:bucket:bluewhale", 5)).thenReturn(1);
        when(fileInfoMapper.selectObjs(ArgumentMatchers.<Wrapper<FileInfoPo>>any()))
                .thenReturn(List.<Object>of(990L));
        TenantContext.set(1L);
        try {
            BizException ex = assertThrows(BizException.class,
                    () -> repository.saveWithQuotaCheck(quotaAsset(100L), new StorageQuota(0L, 0L, 1000L)));

            assertEquals(ErrorCode.PARAM_ERROR.getCode(), ex.getCode());
            assertTrue(ex.getMessage().contains("超出桶存储配额"));
        } finally {
            TenantContext.clear();
        }
        verify(fileInfoMapper, never()).insert(any(FileInfoPo.class));
        verify(fileInfoMapper).releaseNamedLock("whale:file:quota:bucket:bluewhale");
    }

    @Test
    @DisplayName("saveWithQuotaCheck：三个维度按个人 → 租户 → 桶的固定顺序取锁")
    void shouldAcquireQuotaLocksInFixedOrder() {
        when(fileInfoMapper.acquireNamedLock(anyString(), anyInt())).thenReturn(1);
        when(fileInfoMapper.selectObjs(ArgumentMatchers.<Wrapper<FileInfoPo>>any()))
                .thenReturn(List.<Object>of(0L));
        TenantContext.set(1L);
        try {
            repository.saveWithQuotaCheck(quotaAsset(100L), new StorageQuota(1000L, 1000L, 1000L));
        } finally {
            TenantContext.clear();
        }

        InOrder order = inOrder(fileInfoMapper);
        order.verify(fileInfoMapper).acquireNamedLock("whale:file:quota:9", 5);
        order.verify(fileInfoMapper).acquireNamedLock("whale:file:quota:tenant:1", 5);
        order.verify(fileInfoMapper).acquireNamedLock("whale:file:quota:bucket:bluewhale", 5);
    }

    /**
     * 构造仅启用个人维度的配额
     *
     * @param userBytes 个人配额字节数
     * @return 配额值对象
     */
    private static StorageQuota userQuota(long userBytes) {
        return new StorageQuota(userBytes, 0L, 0L);
    }

    /**
     * 构造三个维度均不限的配额
     *
     * @return 配额值对象
     */
    private static StorageQuota unlimitedQuota() {
        return new StorageQuota(0L, 0L, 0L);
    }

    /**
     * 构造配额校验用文件资产聚合（上传用户 9，大小可控）
     *
     * @param size 文件字节数
     * @return 文件资产聚合根
     */
    private static FileAssetAggregate quotaAsset(long size) {
        return FileAssetAggregate.register("bluewhale", "tenants/1/2026/10/24/a.pdf",
                "报告.pdf", size, "application/pdf", "md5", 9L, "local");
    }

    @Test
    @DisplayName("findById/findByIdIncludingDeleted：命中返回聚合，未命中返回空")
    void shouldFindById() {
        when(fileInfoMapper.selectById(1L)).thenReturn(po(1L, 0));
        when(fileInfoMapper.selectById(2L)).thenReturn(null);
        when(fileInfoMapper.selectByIdIncludingDeleted(3L)).thenReturn(po(3L, 1));
        when(fileInfoMapper.selectByIdIncludingDeleted(4L)).thenReturn(null);

        Optional<FileAssetAggregate> found = repository.findById(1L);
        assertTrue(found.isPresent());
        assertEquals("a.pdf", found.get().getFileName());
        assertTrue(repository.findById(2L).isEmpty());

        Optional<FileAssetAggregate> deleted = repository.findByIdIncludingDeleted(3L);
        assertTrue(deleted.isPresent());
        assertTrue(deleted.get().isDeleted());
        assertTrue(repository.findByIdIncludingDeleted(4L).isEmpty());
    }

    @Test
    @DisplayName("findById：空字段归一化（大小为空、状态为空、删除标记为空）")
    void shouldNormalizeNullColumns() {
        FileInfoPo po = po(5L, null);
        po.setFileSize(null);
        po.setStatus(null);
        when(fileInfoMapper.selectById(5L)).thenReturn(po);

        FileAssetAggregate asset = repository.findById(5L).orElseThrow();

        assertEquals(0L, asset.getFileSize());
        assertEquals(FileStatus.NORMAL, asset.getStatus());
        assertFalse(asset.isDeleted());
    }

    @Test
    @DisplayName("findReusableByDigest：命中返回可复用聚合，未命中返回空")
    void shouldFindReusableByDigest() {
        when(fileInfoMapper.selectOne(ArgumentMatchers.<Wrapper<FileInfoPo>>any())).thenReturn(po(1L, 0));

        Optional<FileAssetAggregate> hit = repository.findReusableByDigest("md5", 12L);

        assertTrue(hit.isPresent());
        assertEquals("tenants/1/2026/10/24/a.pdf", hit.get().getObjectKey());

        when(fileInfoMapper.selectOne(ArgumentMatchers.<Wrapper<FileInfoPo>>any())).thenReturn(null);
        assertTrue(repository.findReusableByDigest("md5", 12L).isEmpty());
    }

    @Test
    @DisplayName("deleteById：逻辑删除目标行并级联删除版本行")
    void shouldDeleteById() {
        repository.deleteById(7L);

        InOrder order = inOrder(fileInfoMapper);
        order.verify(fileInfoMapper).markDeletedById(7L);
        order.verify(fileInfoMapper).markDeletedByRootId(7L);
    }

    @Test
    @DisplayName("updateOriginalName / updateBucket：委派元数据更新")
    void shouldUpdateMetadata() {
        repository.updateOriginalName(7L, "新名称.pdf");
        repository.updateBucket(7L, "archive");

        verify(fileInfoMapper).updateOriginalName(7L, "新名称.pdf");
        verify(fileInfoMapper).updateBucket(7L, "archive");
    }

    @Test
    @DisplayName("pageRecycle：仅查逻辑删除行，透传筛选条件并回填分页元数据")
    void shouldPageRecycle() {
        when(fileInfoMapper.selectRecyclePage("报告", "bluewhale", 9L, 10L, 10L))
                .thenReturn(List.of(po(1L, 1), po(2L, 1)));
        when(fileInfoMapper.countRecycle("报告", "bluewhale", 9L)).thenReturn(22L);

        FileRecyclePage page = repository.pageRecycle(new FileRecycleQuery("报告", "bluewhale", 9L, 2L, 10L));

        assertEquals(22L, page.total());
        assertEquals(2L, page.current());
        assertEquals(10L, page.size());
        assertEquals(2, page.records().size());
        assertEquals("a.pdf", page.records().get(0).fileName());
    }

    @Test
    @DisplayName("findExpiredRecycle：按截止时间与批大小查询，返回含存储定位的投影")
    void shouldFindExpiredRecycle() {
        LocalDateTime cutoff = LocalDateTime.of(2026, 10, 1, 0, 0);
        when(fileInfoMapper.selectExpiredRecycle(cutoff, 20L)).thenReturn(List.of(po(3L, 1)));

        List<FileRecycleItem> items = repository.findExpiredRecycle(cutoff, 20L);

        assertEquals(1, items.size());
        assertEquals(3L, items.get(0).id());
    }

    @Test
    @DisplayName("purgeVersionById：物理删除命中返回 true，未命中返回 false")
    void shouldPurgeVersionById() {
        when(fileInfoMapper.purgeVersionById(3L)).thenReturn(1);
        when(fileInfoMapper.purgeVersionById(4L)).thenReturn(0);

        assertTrue(repository.purgeVersionById(3L));
        assertFalse(repository.purgeVersionById(4L));
    }

    @Test
    @DisplayName("findVersionRootIdsWithExcess：游标扫描超限根文件并规整批次大小")
    void shouldFindVersionRootIdsWithExcess() {
        when(fileInfoMapper.selectVersionRootIdsWithExcess(1, 10L, 2L)).thenReturn(List.of(11L, 12L));
        when(fileInfoMapper.selectVersionRootIdsWithExcess(1, 10L, 1L)).thenReturn(List.of());

        assertEquals(List.of(11L, 12L), repository.findVersionRootIdsWithExcess(1, 10L, 2L));
        assertTrue(repository.findVersionRootIdsWithExcess(1, 10L, 0L).isEmpty());
    }

    @Test
    @DisplayName("purgeById：物理删除命中时返回 true，未命中返回 false")
    void shouldPurgeById() {
        when(fileInfoMapper.purgeById(3L)).thenReturn(1);
        when(fileInfoMapper.purgeById(4L)).thenReturn(0);

        assertTrue(repository.purgeById(3L));
        assertFalse(repository.purgeById(4L));
    }

    @Test
    @DisplayName("restoreWithQuotaCheck：目标不存在或未删除时幂等返回 false，不获取锁")
    void shouldSkipRestoreWhenNotDeleted() {
        when(fileInfoMapper.selectByIdIncludingDeleted(1L)).thenReturn(null);
        when(fileInfoMapper.selectByIdIncludingDeleted(2L)).thenReturn(po(2L, 0));

        assertFalse(repository.restoreWithQuotaCheck(1L, userQuota(1000L)));
        assertFalse(repository.restoreWithQuotaCheck(2L, userQuota(1000L)));

        verify(fileInfoMapper, never()).acquireNamedLock(anyString(), anyInt());
        verify(fileInfoMapper, never()).restoreById(anyLong());
    }

    @Test
    @DisplayName("restoreWithQuotaCheck：配额内恢复，按组内待恢复字节统计并恢复版本")
    void shouldRestoreWithinQuota() {
        when(fileInfoMapper.selectByIdIncludingDeleted(1L)).thenReturn(po(1L, 1));
        when(fileInfoMapper.acquireNamedLock("whale:file:quota:9", 5)).thenReturn(1);
        when(fileInfoMapper.sumDeletedSizeOfGroup(1L)).thenReturn(120L);
        when(fileInfoMapper.selectObjs(ArgumentMatchers.<Wrapper<FileInfoPo>>any()))
                .thenReturn(List.<Object>of(100L));
        when(fileInfoMapper.restoreById(1L)).thenReturn(1);

        assertTrue(repository.restoreWithQuotaCheck(1L, userQuota(1000L)));

        verify(fileInfoMapper).restoreById(1L);
        verify(fileInfoMapper).restoreVersionsByRootId(1L);
        verify(fileInfoMapper).releaseNamedLock("whale:file:quota:9");
    }

    @Test
    @DisplayName("restoreWithQuotaCheck：超出配额时不恢复并释放锁")
    void shouldRejectRestoreExceedingQuota() {
        when(fileInfoMapper.selectByIdIncludingDeleted(1L)).thenReturn(po(1L, 1));
        when(fileInfoMapper.acquireNamedLock("whale:file:quota:9", 5)).thenReturn(1);
        when(fileInfoMapper.sumDeletedSizeOfGroup(1L)).thenReturn(999L);
        when(fileInfoMapper.selectObjs(ArgumentMatchers.<Wrapper<FileInfoPo>>any()))
                .thenReturn(List.<Object>of(999L));

        BizException ex = assertThrows(BizException.class, () -> repository.restoreWithQuotaCheck(1L, userQuota(1000L)));

        assertEquals(ErrorCode.PARAM_ERROR.getCode(), ex.getCode());
        verify(fileInfoMapper, never()).restoreById(anyLong());
        verify(fileInfoMapper, never()).restoreVersionsByRootId(anyLong());
        verify(fileInfoMapper).releaseNamedLock("whale:file:quota:9");
    }

    @Test
    @DisplayName("findDeletedVersions：映射已删除版本归属")
    void shouldFindDeletedVersions() {
        FileInfoPo version = po(3L, 1);
        version.setParentId(1L);
        version.setVersion(3);
        when(fileInfoMapper.selectDeletedVersions(1L)).thenReturn(List.of(version));

        List<FileAssetAggregate> versions = repository.findDeletedVersions(1L);

        assertEquals(1, versions.size());
        assertEquals(1L, versions.get(0).getParentId());
        assertEquals(3, versions.get(0).getVersion());
        assertTrue(versions.get(0).isDeleted());
    }

    @Test
    @DisplayName("findByStorageType/updateStorageType：按源类型查询并更新落盘归属")
    void shouldFindAndUpdateStorageType() {
        FileInfoPo po = po(5L, 0);
        po.setStorageType("local");
        when(fileInfoMapper.selectByStorageType("local", 10L)).thenReturn(List.of(po));

        List<FileAssetAggregate> assets = repository.findByStorageType("local", 10L);

        assertEquals(1, assets.size());
        assertEquals("local", assets.get(0).getStorageType());

        repository.updateStorageType(5L, "minio");
        verify(fileInfoMapper).updateStorageType(5L, "minio");

        repository.findByStorageType("local", 0L);
        verify(fileInfoMapper).selectByStorageType("local", 1L);
    }

    @Test
    @DisplayName("sumNormalSizeByUploader：兼容数值、字符串、空结果")
    void shouldSumUsedBytes() {
        when(fileInfoMapper.selectObjs(ArgumentMatchers.<Wrapper<FileInfoPo>>any()))
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
    @DisplayName("sumNormalSizeByTenant/ByBucket：返回聚合结果")
    void shouldSumByTenantAndBucket() {
        when(fileInfoMapper.selectObjs(ArgumentMatchers.<Wrapper<FileInfoPo>>any()))
                .thenReturn(List.<Object>of(321L));

        assertEquals(321L, repository.sumNormalSizeByTenant());
        assertEquals(321L, repository.sumNormalSizeByBucket("bluewhale"));
    }

    @Test
    @DisplayName("sumNormalSizeByUploader：未知类型或查询异常抛系统错误")
    void shouldFailWhenSumUnreadable() {
        when(fileInfoMapper.selectObjs(ArgumentMatchers.<Wrapper<FileInfoPo>>any()))
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
        Page<FileInfoPo> page = new Page<>(1L, 10L);
        page.setRecords(List.of(po(1L, 0)));
        page.setTotal(1L);
        when(fileInfoMapper.selectPage(ArgumentMatchers.<Page<FileInfoPo>>any(),
                ArgumentMatchers.<Wrapper<FileInfoPo>>any())).thenReturn(page);
        LocalDateTime start = LocalDateTime.of(2026, 10, 1, 0, 0);
        LocalDateTime end = LocalDateTime.of(2026, 10, 31, 0, 0);

        FileAssetPage result = repository.page(
                new FileAssetQuery("报告", "bluewhale", start, end, null, 1L, 10L));

        assertEquals(1L, result.total());
        assertEquals(1L, result.current());
        assertEquals(10L, result.size());
        assertEquals("报告.pdf", result.records().get(0).getOriginalName());
    }

    @Test
    @DisplayName("page：无查询条件时仍返回空页")
    void shouldPageWithoutConditions() {
        Page<FileInfoPo> page = new Page<>(1L, 10L);
        when(fileInfoMapper.selectPage(ArgumentMatchers.<Page<FileInfoPo>>any(),
                ArgumentMatchers.<Wrapper<FileInfoPo>>any())).thenReturn(page);

        FileAssetPage result = repository.page(new FileAssetQuery(null, null, null, null, null, 1L, 10L));

        assertTrue(result.records().isEmpty());
        assertEquals(0L, result.total());
    }

    @Test
    @DisplayName("findLatestVersion/findVersions：映射版本归属并透传根文件ID")
    void shouldFindVersions() {
        FileInfoPo root = po(1L, 0);
        FileInfoPo version2 = po(2L, 0);
        version2.setParentId(1L);
        version2.setVersion(2);
        when(fileInfoMapper.selectLatestVersion(1L)).thenReturn(version2);
        when(fileInfoMapper.selectVersions(1L)).thenReturn(List.of(version2, root));

        FileAssetAggregate latest = repository.findLatestVersion(1L).orElseThrow();
        assertEquals(2, latest.getVersion());
        assertEquals(1L, latest.getParentId());

        List<FileAssetAggregate> versions = repository.findVersions(1L);
        assertEquals(2, versions.size());
        assertEquals(2, versions.get(0).getVersion());
        assertEquals(1, versions.get(1).getVersion());

        when(fileInfoMapper.selectLatestVersion(2L)).thenReturn(null);
        assertTrue(repository.findLatestVersion(2L).isEmpty());
    }

    @Test
    @DisplayName("page：只查询版本组根文件，版本行不进入文件列表")
    void shouldPageOnlyRootFiles() {
        Page<FileInfoPo> page = new Page<>(1L, 10L);
        when(fileInfoMapper.selectPage(ArgumentMatchers.<Page<FileInfoPo>>any(),
                ArgumentMatchers.<Wrapper<FileInfoPo>>any())).thenReturn(page);

        repository.page(new FileAssetQuery(null, null, null, null, null, 1L, 10L));

        // Lambda 列解析依赖 MyBatis-Plus 实体元数据，单测无 Spring 容器时需手动初始化
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), FileInfoPo.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Wrapper<FileInfoPo>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(fileInfoMapper).selectPage(ArgumentMatchers.<Page<FileInfoPo>>any(), captor.capture());
        assertTrue(captor.getValue().getSqlSegment().contains("parent_id IS NULL"),
                "文件列表应过滤版本行：" + captor.getValue().getSqlSegment());
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
