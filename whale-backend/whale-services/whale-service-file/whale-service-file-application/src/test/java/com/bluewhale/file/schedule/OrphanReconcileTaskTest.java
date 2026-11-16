package com.bluewhale.file.schedule;

import com.bluewhale.common.audit.annotation.AuditLog;
import com.bluewhale.common.audit.enums.OperateType;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.file.config.FileProperties;
import com.bluewhale.file.domain.file.repository.FileAssetRepository;
import com.bluewhale.file.domain.model.FileObjectRef;
import com.bluewhale.file.domain.storage.FileStorage;
import com.bluewhale.file.domain.storage.StorageObject;
import com.bluewhale.file.domain.storage.StoragePluginCatalog;
import com.bluewhale.file.domain.storage.StoragePluginInfo;
import com.bluewhale.file.metrics.FileMetrics;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@link OrphanReconcileTask} 差异计算、删除开关与执行摘要单元测试。
 *
 * @author BlueWhale
 */
class OrphanReconcileTaskTest {

    private final FileAssetRepository fileAssetRepository = mock(FileAssetRepository.class);

    private final FileStorage fileStorage = mock(FileStorage.class);

    private final StoragePluginCatalog storagePluginCatalog = mock(StoragePluginCatalog.class);

    private final FileMetrics fileMetrics = mock(FileMetrics.class);

    private final FileProperties fileProperties = new FileProperties();

    /**
     * 构造默认开启对账但只报告的任务
     *
     * @return 对账任务
     */
    private OrphanReconcileTask task() {
        fileProperties.getOrphan().setEnabled(true);
        fileProperties.getOrphan().setReportSamples(1);
        when(storagePluginCatalog.activeType()).thenReturn("local");
        when(storagePluginCatalog.plugins()).thenReturn(List.of(
                new StoragePluginInfo("local", "本地文件系统", false)));
        return new OrphanReconcileTask(fileAssetRepository, fileStorage, storagePluginCatalog,
                fileProperties, fileMetrics);
    }

    /**
     * 构造对象引用
     *
     * @param storageType 存储类型，可为 {@code null}
     * @param objectKey 对象键
     * @return 对象引用
     */
    private static FileObjectRef ref(String storageType, String objectKey) {
        return new FileObjectRef(storageType, "bluewhale", objectKey);
    }

    /**
     * 构造存储对象
     *
     * @param objectKey 对象键
     * @param size 大小
     * @return 存储对象
     */
    private static StorageObject object(String objectKey, long size) {
        return new StorageObject("bluewhale", objectKey, size);
    }

    @Test
    @DisplayName("reconcile：标注 @AuditLog，不记录参数，执行摘要留痕")
    void shouldAnnotateReconcileWithAuditLog() throws NoSuchMethodException {
        Method method = OrphanReconcileTask.class.getMethod("reconcile");
        AuditLog annotation = method.getAnnotation(AuditLog.class);

        assertNotNull(annotation, "reconcile 应标注 @AuditLog");
        assertEquals(OperateType.DELETE, annotation.type());
        assertEquals("storage_object", annotation.targetType());
        assertFalse(annotation.recordParams(), "对账任务无请求参数");
        assertTrue(annotation.recordResult(), "执行摘要需留痕");
    }

    @Test
    @DisplayName("默认关闭：返回跳过摘要，不查询元数据、不列举对象")
    void shouldSkipWhenDisabled() {
        OrphanReconcileTask disabled = new OrphanReconcileTask(fileAssetRepository, fileStorage,
                storagePluginCatalog, fileProperties, fileMetrics);

        assertEquals("skipped: orphan reconcile disabled", disabled.reconcile());

        verifyNoInteractions(fileAssetRepository, fileStorage, fileMetrics);
    }

    @Test
    @DisplayName("只报告模式：统计孤儿对象与悬空元数据，摘要含计数且不删除")
    void shouldReportOnlyByDefault() {
        when(fileAssetRepository.findAllObjectRefs()).thenReturn(List.of(
                ref("local", "a.txt"), ref(null, "c.txt")));
        when(fileStorage.listObjects("local")).thenReturn(List.of(
                object("a.txt", 1L), object("b.txt", 2L)));

        assertEquals("orphanObjects=1, danglingMetadata=1, deleted=0", task().reconcile());

        verify(fileStorage, never()).delete(anyString(), anyString(), anyString());
        verify(fileMetrics).recordOrphanReconcile(1L, 1L, true);
    }

    @Test
    @DisplayName("显式开启删除：只删孤儿对象，按落盘介质路由")
    void shouldDeleteOrphanObjectsWhenEnabled() {
        when(fileAssetRepository.findAllObjectRefs()).thenReturn(List.of(ref("local", "a.txt")));
        when(fileStorage.listObjects("local")).thenReturn(List.of(
                object("a.txt", 1L), object("b.txt", 2L)));
        fileProperties.getOrphan().setDeleteOrphanObjects(true);

        assertEquals("orphanObjects=1, danglingMetadata=0, deleted=1", task().reconcile());

        verify(fileStorage).delete("bluewhale", "b.txt", "local");
        verify(fileMetrics).recordOrphanReconcile(1L, 0L, true);
    }

    @Test
    @DisplayName("任一介质列举失败：计失败并上抛，不删除任何对象")
    void shouldAbortWhenListingFails() {
        when(fileAssetRepository.findAllObjectRefs()).thenReturn(List.of());
        when(fileStorage.listObjects("local"))
                .thenThrow(new BizException(ErrorCode.SYSTEM_ERROR.getCode(), "对象存储不可达"));
        fileProperties.getOrphan().setDeleteOrphanObjects(true);

        assertThrows(IllegalStateException.class, () -> task().reconcile());

        verify(fileStorage, never()).delete(anyString(), anyString(), anyString());
        verify(fileMetrics).recordOrphanReconcile(0L, 0L, false);
    }

    @Test
    @DisplayName("历史空存储类型按活动插件归属，不误判为孤儿")
    void shouldTreatBlankStorageTypeAsActive() {
        when(fileAssetRepository.findAllObjectRefs()).thenReturn(List.of(ref(null, "a.txt")));
        when(fileStorage.listObjects("local")).thenReturn(List.of(object("a.txt", 1L)));

        assertEquals("orphanObjects=0, danglingMetadata=0, deleted=0", task().reconcile());

        verify(fileMetrics).recordOrphanReconcile(0L, 0L, true);
        verify(fileStorage, never()).delete(any(), any(), any());
    }
}
