package com.bluewhale.archive.service;

import com.bluewhale.archive.client.FileArchiveClient;
import com.bluewhale.archive.entity.ArchiveFile;
import com.bluewhale.archive.entity.ArchiveItem;
import com.bluewhale.archive.mapper.ArchiveFileMapper;
import com.bluewhale.archive.mapper.ArchiveItemMapper;
import com.bluewhale.archive.remote.RemoteFileMeta;
import com.bluewhale.archive.support.ArchiveConstants;
import com.bluewhale.archive.vo.ArchiveFileLockReconcileVO;
import com.bluewhale.common.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 历史归档文件锁定补扫服务单元测试。
 *
 * <p>覆盖仅补锁未锁定文件、文件缺失计数、单文件失败隔离以及游标返回。</p>
 *
 * @author BlueWhale
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ArchiveFileLockReconcileServiceTest {

    /** 测试租户ID */
    private static final long TENANT_ID = 1L;

    /** 条目ID */
    private static final long ITEM_ID = 10L;

    @Mock
    private ArchiveItemMapper itemMapper;

    @Mock
    private ArchiveFileMapper fileMapper;

    @Mock
    private FileArchiveClient fileClient;

    private ArchiveFileLockReconcileService service;

    @BeforeEach
    void setUp() {
        TenantContext.set(TENANT_ID);
        service = new ArchiveFileLockReconcileService(itemMapper, fileMapper, fileClient);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("补扫：仅锁定未锁定文件，已锁定文件跳过")
    void shouldLockOnlyUnlockedFiles() {
        when(itemMapper.selectList(any())).thenReturn(List.of(archivedItem()));
        when(fileMapper.selectList(any())).thenReturn(List.of(fileRef(101L), fileRef(102L)));
        when(fileClient.detail(101L)).thenReturn(fileMeta(ArchiveConstants.FILE_STATUS_LOCKED));
        when(fileClient.detail(102L)).thenReturn(fileMeta(1));

        ArchiveFileLockReconcileVO result = service.reconcile(200, null);

        verify(fileClient, never()).lock(101L);
        verify(fileClient).lock(102L);
        assertEquals(1, result.getScannedItems());
        assertEquals(1, result.getLockedFiles());
        assertEquals(1, result.getAlreadyLockedFiles());
        assertEquals(0, result.getMissingFiles());
        assertEquals(0, result.getFailedFiles());
        assertFalse(result.getHasMore());
    }

    @Test
    @DisplayName("补扫：文件服务已不存在时计数并继续处理后续文件")
    void shouldCountMissingFileAndContinue() {
        when(itemMapper.selectList(any())).thenReturn(List.of(archivedItem()));
        when(fileMapper.selectList(any())).thenReturn(List.of(fileRef(201L), fileRef(202L)));
        when(fileClient.detail(201L)).thenReturn(null);
        when(fileClient.detail(202L)).thenReturn(fileMeta(1));

        ArchiveFileLockReconcileVO result = service.reconcile(200, null);

        verify(fileClient).lock(202L);
        assertEquals(1, result.getMissingFiles());
        assertEquals(1, result.getLockedFiles());
        assertEquals(0, result.getFailedFiles());
    }

    @Test
    @DisplayName("补扫：单文件锁定失败不阻断整批")
    void shouldIsolateSingleFileFailure() {
        when(itemMapper.selectList(any())).thenReturn(List.of(archivedItem()));
        when(fileMapper.selectList(any())).thenReturn(List.of(fileRef(301L), fileRef(302L)));
        when(fileClient.detail(301L)).thenReturn(fileMeta(1));
        when(fileClient.detail(302L)).thenReturn(fileMeta(1));
        doThrow(new IllegalStateException("文件服务不可用")).when(fileClient).lock(301L);

        ArchiveFileLockReconcileVO result = service.reconcile(200, null);

        verify(fileClient).lock(302L);
        assertEquals(1, result.getFailedFiles());
        assertEquals(1, result.getLockedFiles());
    }

    @Test
    @DisplayName("补扫：无待处理条目时沿用游标且无下一批")
    void shouldKeepCursorWhenNoItems() {
        when(itemMapper.selectList(any())).thenReturn(List.of());

        ArchiveFileLockReconcileVO result = service.reconcile(9999, 50L);

        assertEquals(0, result.getScannedItems());
        assertEquals(0, result.getLockedFiles());
        assertEquals(50L, result.getNextAfterId());
        assertFalse(result.getHasMore());
    }

    @Test
    @DisplayName("补扫：条目数达到批量上限时返回下一批游标")
    void shouldExposeNextCursorWhenBatchFull() {
        ArchiveItem first = archivedItem();
        ArchiveItem second = archivedItem();
        second.setId(ITEM_ID + 1);
        when(itemMapper.selectList(any())).thenReturn(List.of(first, second));
        when(fileMapper.selectList(any())).thenReturn(List.of());

        ArchiveFileLockReconcileVO result = service.reconcile(2, null);

        assertTrue(result.getHasMore());
        assertEquals(ITEM_ID + 1, result.getNextAfterId());
    }

    /**
     * 构造已归档条目。
     *
     * @return 档案条目
     */
    private static ArchiveItem archivedItem() {
        ArchiveItem item = new ArchiveItem();
        item.setId(ITEM_ID);
        item.setItemStatus(ArchiveConstants.ITEM_STATUS_ARCHIVED);
        return item;
    }

    /**
     * 构造文件引用。
     *
     * @param fileId 文件ID
     * @return 文件引用
     */
    private static ArchiveFile fileRef(Long fileId) {
        ArchiveFile file = new ArchiveFile();
        file.setItemId(ITEM_ID);
        file.setFileId(fileId);
        file.setFileRole(ArchiveConstants.FILE_ROLE_ORIGINAL);
        return file;
    }

    /**
     * 构造文件元数据。
     *
     * @param status 文件状态
     * @return 文件元数据
     */
    private static RemoteFileMeta fileMeta(Integer status) {
        RemoteFileMeta meta = new RemoteFileMeta();
        meta.setStatus(status);
        return meta;
    }
}
