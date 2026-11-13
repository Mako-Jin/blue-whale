package com.bluewhale.file.domain.file.aggregate;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.file.domain.model.vo.FileStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link FileAsset} 聚合不变量与状态迁移单元测试。
 *
 * @author BlueWhale
 */
class FileAssetTest {

    /**
     * 构造正常状态的文件聚合。
     *
     * @param status 文件状态
     * @return 文件资产聚合根
     */
    private static FileAsset asset(FileStatus status) {
        return FileAsset.reconstitute(1L, "a.pdf", "报告.pdf", "bluewhale",
                "tenants/1/2026/10/24/a.pdf", 12L, "application/pdf", "md5",
                status, 9L, false, LocalDateTime.of(2026, 10, 24, 9, 0), LocalDateTime.of(2026, 10, 24, 9, 0));
    }

    @Test
    @DisplayName("register：登记正常状态文件，文件名由对象键尾段推导")
    void shouldRegisterNormalAsset() {
        FileAsset asset = FileAsset.register("bluewhale", "tenants/1/2026/10/24/uuid.pdf",
                "报告.pdf", 12L, "application/pdf", "md5", 9L);

        assertEquals(FileStatus.NORMAL, asset.getStatus());
        assertEquals("uuid.pdf", asset.getFileName());
        assertEquals("报告.pdf", asset.getOriginalName());
        assertEquals("bluewhale", asset.getBucket());
        assertEquals("tenants/1/2026/10/24/uuid.pdf", asset.getObjectKey());
        assertEquals(12L, asset.getFileSize());
        assertEquals("application/pdf", asset.getContentType());
        assertEquals("md5", asset.getMd5());
        assertEquals(9L, asset.getUploaderId());
        assertFalse(asset.isDeleted());
        assertNotNull(asset.getCreateTime());
        assertNotNull(asset.getUpdateTime());
    }

    @Test
    @DisplayName("register：原始文件名为空或大小非正数时拒绝登记")
    void shouldRejectInvalidRegistration() {
        BizException blankName = assertThrows(BizException.class,
                () -> FileAsset.register("bluewhale", "k", " ", 12L, null, null, null));
        assertEquals(ErrorCode.PARAM_ERROR.getCode(), blankName.getCode());

        BizException zeroSize = assertThrows(BizException.class,
                () -> FileAsset.register("bluewhale", "k", "a.txt", 0L, null, null, null));
        assertEquals(ErrorCode.PARAM_ERROR.getCode(), zeroSize.getCode());
    }

    @Test
    @DisplayName("构造不变量：桶名或对象键为空时拒绝")
    void shouldRejectBlankBucketOrObjectKey() {
        BizException noBucket = assertThrows(BizException.class,
                () -> FileAsset.reconstitute(1L, "a.txt", "a.txt", " ", "k", 1L, null, null,
                        FileStatus.NORMAL, null, false, null, null));
        assertEquals(ErrorCode.PARAM_ERROR.getCode(), noBucket.getCode());

        BizException noKey = assertThrows(BizException.class,
                () -> FileAsset.reconstitute(1L, "a.txt", "a.txt", "bluewhale", "", 1L, null, null,
                        FileStatus.NORMAL, null, false, null, null));
        assertEquals(ErrorCode.PARAM_ERROR.getCode(), noKey.getCode());
    }

    @Test
    @DisplayName("reconstitute：缺失状态按正常处理，缺失大小与文件名做归一化")
    void shouldNormalizeReconstitutedValues() {
        FileAsset asset = FileAsset.reconstitute(2L, "", "", "bluewhale",
                "tenants/1/2026/10/24/b.bin", -1L, null, null,
                null, null, false, null, null);

        assertEquals(FileStatus.NORMAL, asset.getStatus());
        assertEquals(0L, asset.getFileSize());
        assertEquals("b.bin", asset.getFileName());
        assertEquals("unknown", asset.getOriginalName());
        assertFalse(asset.isDeleted());
    }

    @Test
    @DisplayName("reuse：复用物理对象并覆盖原始文件名与 MIME 类型")
    void shouldReusePhysicalObject() {
        FileAsset source = asset(FileStatus.LOCKED);

        FileAsset reused = FileAsset.reuse(source, "copy.pdf", "application/octet-stream", 7L);

        assertEquals("a.pdf", reused.getFileName());
        assertEquals("copy.pdf", reused.getOriginalName());
        assertEquals("tenants/1/2026/10/24/a.pdf", reused.getObjectKey());
        assertEquals("application/octet-stream", reused.getContentType());
        assertEquals(FileStatus.NORMAL, reused.getStatus());
        assertEquals(7L, reused.getUploaderId());

        FileAsset fallback = FileAsset.reuse(source, "copy.pdf", " ", 7L);
        assertEquals("application/pdf", fallback.getContentType());

        BizException ex = assertThrows(BizException.class, () -> FileAsset.reuse(null, "a", null, null));
        assertEquals(ErrorCode.SYSTEM_ERROR.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("lock：正常文件锁定，已锁定幂等，禁用文件拒绝")
    void shouldLockOnlyNormalAsset() {
        FileAsset normal = asset(FileStatus.NORMAL);
        assertTrue(normal.lock());
        assertEquals(FileStatus.LOCKED, normal.getStatus());
        assertFalse(normal.lock());

        BizException ex = assertThrows(BizException.class, () -> asset(FileStatus.DISABLED).lock());
        assertEquals(ErrorCode.PARAM_ERROR.getCode(), ex.getCode());
        assertTrue(ex.getMessage().contains("已禁用"));
    }

    @Test
    @DisplayName("unlock：已锁定恢复为正常，未锁定幂等")
    void shouldUnlockOnlyLockedAsset() {
        FileAsset locked = asset(FileStatus.LOCKED);
        assertTrue(locked.unlock());
        assertEquals(FileStatus.NORMAL, locked.getStatus());
        assertFalse(locked.unlock());
    }

    @Test
    @DisplayName("assertDeletable：锁定文件禁止删除，其他状态允许")
    void shouldRejectDeletingLockedAsset() {
        BizException ex = assertThrows(BizException.class, () -> asset(FileStatus.LOCKED).assertDeletable());
        assertEquals(ErrorCode.PARAM_ERROR.getCode(), ex.getCode());
        assertTrue(ex.getMessage().contains("禁止删除"));

        asset(FileStatus.NORMAL).assertDeletable();
        asset(FileStatus.DISABLED).assertDeletable();
    }

    @Test
    @DisplayName("isVisibleToTenant：无租户上下文可见，租户前缀匹配才可见")
    void shouldCheckTenantVisibility() {
        FileAsset asset = asset(FileStatus.NORMAL);

        assertTrue(asset.isVisibleToTenant(null));
        assertTrue(asset.isVisibleToTenant(1L));
        assertFalse(asset.isVisibleToTenant(2L));
        assertFalse(asset.isVisibleToTenant(12L));
    }

    @Test
    @DisplayName("assertVisibleTo：管理权限或上传者本人可见，跨租户与服务间上传对普通用户不可见")
    void shouldAssertRequesterVisibility() {
        FileAsset asset = asset(FileStatus.NORMAL);

        asset.assertVisibleTo(9L, false, 1L);
        asset.assertVisibleTo(7L, true, 1L);
        assertNotFound(() -> asset.assertVisibleTo(7L, false, 1L));
        assertNotFound(() -> asset.assertVisibleTo(9L, false, 2L));

        FileAsset serviceUpload = FileAsset.reconstitute(2L, "b.txt", "b.txt", "bluewhale",
                "tenants/1/2026/10/24/b.txt", 1L, null, null, FileStatus.NORMAL, null, false, null, null);
        assertNotFound(() -> serviceUpload.assertVisibleTo(9L, false, 1L));
        serviceUpload.assertVisibleTo(null, false, 1L);
        serviceUpload.assertVisibleTo(9L, true, 1L);
    }

    @Test
    @DisplayName("构造：MD5 统一归一化为小写，空白摘要归一化为 null")
    void shouldNormalizeMd5() {
        FileAsset upper = FileAsset.register("bluewhale", "k", "a.txt", 1L, null,
                " D41D8CD98F00B204E9800998ECF8427E ", 9L);
        assertEquals("d41d8cd98f00b204e9800998ecf8427e", upper.getMd5());

        FileAsset blank = FileAsset.register("bluewhale", "k", "a.txt", 1L, null, "  ", 9L);
        assertNull(blank.getMd5());
    }

    /**
     * 断言操作抛出 {@code NOT_FOUND}
     *
     * @param executable 待执行操作
     */
    private static void assertNotFound(Executable executable) {
        BizException ex = assertThrows(BizException.class, executable);
        assertEquals(ErrorCode.NOT_FOUND.getCode(), ex.getCode());
    }
}
