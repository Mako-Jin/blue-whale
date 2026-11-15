package com.bluewhale.file.service.impl;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.file.domain.file.aggregate.FileAssetAggregate;
import com.bluewhale.file.domain.file.repository.FileAssetRepository;
import com.bluewhale.file.domain.model.vo.FileStatus;
import com.bluewhale.file.domain.storage.FileStorage;
import com.bluewhale.file.domain.storage.StoragePluginCatalog;
import com.bluewhale.file.model.command.StorageMigrationCommand;
import com.bluewhale.file.model.result.StorageMigrationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.util.DigestUtils;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link StorageMigrationServiceImpl} 存储迁移单元测试。
 *
 * @author BlueWhale
 */
class StorageMigrationServiceImplTest {

    private static final long FILE_ID = 7L;

    private static final String BUCKET = "bluewhale";

    private static final String OBJECT_KEY = "tenants/1/2026/10/25/a.txt";

    private static final byte[] CONTENT = "migrate-body".getBytes(StandardCharsets.UTF_8);

    private final FileAssetRepository fileAssetRepository = mock(FileAssetRepository.class);

    private final FileStorage fileStorage = mock(FileStorage.class);

    private final StoragePluginCatalog storagePluginCatalog = mock(StoragePluginCatalog.class);

    private final StorageMigrationServiceImpl service =
            new StorageMigrationServiceImpl(fileAssetRepository, fileStorage, storagePluginCatalog);

    /**
     * 默认活动插件为 minio
     */
    @BeforeEach
    void setUp() {
        when(storagePluginCatalog.activeType()).thenReturn("minio");
    }

    /**
     * 构造待迁移文件
     *
     * @param id 主键
     * @param md5 元数据中的 MD5
     * @return 文件资产
     */
    private static FileAssetAggregate asset(long id, String md5) {
        return FileAssetAggregate.reconstitute(id, "a.txt", "a.txt", BUCKET, OBJECT_KEY, CONTENT.length,
                "text/plain", md5, FileStatus.NORMAL, 9L, false, LocalDateTime.of(2026, 10, 25, 9, 0),
                LocalDateTime.of(2026, 10, 25, 9, 0), "local");
    }

    /**
     * 让存储写入消费输入流，模拟真实落盘
     */
    private void stubStoreConsumesStream() {
        doAnswer(invocation -> {
            InputStream in = invocation.getArgument(2);
            in.transferTo(OutputStream.nullOutputStream());
            return null;
        }).when(fileStorage).store(anyString(), anyString(), any(InputStream.class));
    }

    /**
     * 构造迁移命令
     *
     * @param dryRun 是否演练
     * @param deleteSource 是否删除源对象
     * @return 迁移命令
     */
    private static StorageMigrationCommand command(boolean dryRun, boolean deleteSource) {
        return new StorageMigrationCommand("local", BUCKET, 100L, dryRun, deleteSource, true);
    }

    @Test
    @DisplayName("migrate：复制并校验后更新落盘类型，源对象按需删除")
    void shouldMigrateFile() {
        FileAssetAggregate asset = asset(FILE_ID, DigestUtils.md5DigestAsHex(CONTENT));
        when(fileAssetRepository.findByStorageType("local", 100L)).thenReturn(List.of(asset));
        when(fileStorage.load(BUCKET, OBJECT_KEY, "local"))
                .thenReturn(new ByteArrayInputStream(CONTENT));
        stubStoreConsumesStream();

        StorageMigrationResult result = service.migrate(command(false, true));

        assertEquals(1, result.scanned());
        assertEquals(1, result.migrated());
        assertEquals(0, result.failed());
        verify(fileAssetRepository).updateStorageType(FILE_ID, "minio");
        verify(fileStorage).delete(BUCKET, OBJECT_KEY, "local");
    }

    @Test
    @DisplayName("migrate：dry-run 只统计，不触达存储")
    void shouldNotTouchStorageInDryRun() {
        when(fileAssetRepository.findByStorageType("local", 100L)).thenReturn(List.of(asset(FILE_ID, null)));

        StorageMigrationResult result = service.migrate(command(true, false));

        assertEquals(1, result.scanned());
        assertEquals(1, result.migrated());
        assertTrue(result.dryRun());
        verify(fileStorage, never()).load(anyString(), anyString(), anyString());
        verify(fileStorage, never()).store(anyString(), anyString(), any(InputStream.class));
        verify(fileAssetRepository, never()).updateStorageType(anyLong(), anyString());
    }

    @Test
    @DisplayName("migrate：源类型等于活动类型或未确认时拒绝")
    void shouldRejectInvalidCommand() {
        BizException sameType = assertThrows(BizException.class, () -> service.migrate(
                new StorageMigrationCommand("minio", null, 100L, false, false, true)));
        assertEquals(ErrorCode.PARAM_ERROR.getCode(), sameType.getCode());

        BizException notConfirmed = assertThrows(BizException.class, () -> service.migrate(
                new StorageMigrationCommand("local", null, 100L, false, false, false)));
        assertEquals(ErrorCode.PARAM_ERROR.getCode(), notConfirmed.getCode());

        verify(fileAssetRepository, never()).findByStorageType(anyString(), anyLong());
    }

    @Test
    @DisplayName("migrate：校验不一致时清理目标对象且不更新元数据")
    void shouldAbortOnChecksumMismatch() {
        when(fileAssetRepository.findByStorageType("local", 100L))
                .thenReturn(List.of(asset(FILE_ID, "deadbeef")));
        when(fileStorage.load(BUCKET, OBJECT_KEY, "local"))
                .thenReturn(new ByteArrayInputStream(CONTENT));
        stubStoreConsumesStream();

        StorageMigrationResult result = service.migrate(command(false, false));

        assertEquals(0, result.migrated());
        assertEquals(1, result.failed());
        assertEquals(1, result.failures().size());
        verify(fileAssetRepository, never()).updateStorageType(anyLong(), anyString());
        verify(fileStorage).delete(BUCKET, OBJECT_KEY, "minio");
    }

    @Test
    @DisplayName("migrate：桶过滤不匹配时跳过且不触达存储")
    void shouldSkipBucketMismatch() {
        when(fileAssetRepository.findByStorageType("local", 100L)).thenReturn(List.of(asset(FILE_ID, null)));

        StorageMigrationResult result = service.migrate(
                new StorageMigrationCommand("local", "archive", 100L, false, false, true));

        assertEquals(1, result.scanned());
        assertEquals(0, result.migrated());
        assertEquals(1, result.skipped());
        verify(fileStorage, never()).load(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("migrate：单个文件失败不影响后续文件")
    void shouldContinueAfterSingleFailure() {
        FileAssetAggregate first = asset(1L, DigestUtils.md5DigestAsHex(CONTENT));
        FileAssetAggregate second = asset(2L, DigestUtils.md5DigestAsHex(CONTENT));
        when(fileAssetRepository.findByStorageType("local", 100L)).thenReturn(List.of(first, second));
        when(fileStorage.load(BUCKET, OBJECT_KEY, "local"))
                .thenReturn(new ByteArrayInputStream(CONTENT), new ByteArrayInputStream(CONTENT));
        doAnswer(invocation -> {
            InputStream in = invocation.getArgument(2);
            if (in == null) {
                return null;
            }
            in.transferTo(OutputStream.nullOutputStream());
            return null;
        }).doAnswer(invocation -> {
            throw new BizException(ErrorCode.SYSTEM_ERROR);
        }).doAnswer(invocation -> {
            InputStream in = invocation.getArgument(2);
            in.transferTo(OutputStream.nullOutputStream());
            return null;
        }).when(fileStorage).store(anyString(), anyString(), any(InputStream.class));

        StorageMigrationResult result = service.migrate(command(false, false));

        assertEquals(2, result.scanned());
        assertEquals(1, result.migrated());
        assertEquals(1, result.failed());
        verify(fileAssetRepository).updateStorageType(eq(1L), eq("minio"));
        verify(fileAssetRepository, never()).updateStorageType(eq(2L), anyString());
    }
}
