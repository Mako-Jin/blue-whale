package com.bluewhale.archive.service;

import com.bluewhale.archive.client.FileArchiveClient;
import com.bluewhale.archive.config.ArchiveProperties;
import com.bluewhale.archive.dto.ArchiveIngestRequest;
import com.bluewhale.archive.entity.ArchiveFile;
import com.bluewhale.archive.entity.ArchiveItem;
import com.bluewhale.archive.entity.ArchiveSourceRecord;
import com.bluewhale.archive.mapper.ArchiveFileMapper;
import com.bluewhale.archive.mapper.ArchiveItemMapper;
import com.bluewhale.archive.support.ArchiveConstants;
import com.bluewhale.archive.support.ArchiveSnapshotBuilder;
import com.bluewhale.archive.vo.ArchiveIngestResultVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 业务快照入档服务单元测试。
 *
 * <p>重点覆盖归档不可变：快照与原件必须在落库前锁定；锁定失败不得产生档案条目；
 * 重复推送时补锁既有条目文件，用于修复历史未锁定数据。</p>
 *
 * @author BlueWhale
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ArchiveIngestServiceTest {

    /** 档案条目ID */
    private static final long ITEM_ID = 100L;

    /** 快照文件ID */
    private static final long SNAPSHOT_FILE_ID = 55L;

    /** 原件文件ID */
    private static final long ORIGINAL_FILE_ID = 88L;

    /** 快照摘要 */
    private static final String SNAPSHOT_HASH = "9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08";

    @Mock
    private ArchiveItemMapper itemMapper;

    @Mock
    private ArchiveFileMapper fileMapper;

    @Mock
    private ArchiveItemStore itemStore;

    @Mock
    private FileArchiveClient fileClient;

    @Mock
    private ArchiveSnapshotBuilder snapshotBuilder;

    private ArchiveIngestService service;

    @BeforeEach
    void setUp() {
        service = new ArchiveIngestService(itemMapper, fileMapper, itemStore, fileClient,
                snapshotBuilder, new ArchiveProperties());
    }

    @Test
    @DisplayName("入档：先锁定快照与原件，再落库")
    void shouldLockSnapshotAndOriginalBeforePersist() {
        when(snapshotBuilder.sha256(any(byte[].class))).thenReturn(SNAPSHOT_HASH);
        when(fileClient.upload(any(byte[].class), anyString(), anyString())).thenReturn(SNAPSHOT_FILE_ID);

        service.ingest(request(true));

        InOrder inOrder = inOrder(fileClient, itemStore);
        inOrder.verify(fileClient).lock(SNAPSHOT_FILE_ID);
        inOrder.verify(fileClient).lock(ORIGINAL_FILE_ID);
        inOrder.verify(itemStore).persist(any(ArchiveItem.class), anyList(), any(ArchiveSourceRecord.class));
    }

    @Test
    @DisplayName("入档：无原件时仅锁定快照")
    void shouldLockSnapshotOnlyWhenOriginalAbsent() {
        when(snapshotBuilder.sha256(any(byte[].class))).thenReturn(SNAPSHOT_HASH);
        when(fileClient.upload(any(byte[].class), anyString(), anyString())).thenReturn(SNAPSHOT_FILE_ID);

        service.ingest(request(false));

        verify(fileClient).lock(SNAPSHOT_FILE_ID);
        verify(fileClient, never()).lock(ORIGINAL_FILE_ID);
        verify(itemStore).persist(any(ArchiveItem.class), anyList(), any(ArchiveSourceRecord.class));
    }

    @Test
    @DisplayName("入档：锁定失败不落库，允许业务重试")
    void shouldAbortPersistWhenLockFails() {
        when(snapshotBuilder.sha256(any(byte[].class))).thenReturn(SNAPSHOT_HASH);
        when(fileClient.upload(any(byte[].class), anyString(), anyString())).thenReturn(SNAPSHOT_FILE_ID);
        doThrow(new IllegalStateException("文件服务不可用")).when(fileClient).lock(SNAPSHOT_FILE_ID);

        assertThrows(IllegalStateException.class, () -> service.ingest(request(true)));

        verify(itemStore, never()).persist(any(ArchiveItem.class), anyList(), any(ArchiveSourceRecord.class));
    }

    @Test
    @DisplayName("重复推送：幂等返回既有条目并补锁其文件")
    void shouldRelockExistingItemOnDuplicatePush() {
        ArchiveItem existing = existingItem();
        ArchiveFile original = new ArchiveFile();
        original.setItemId(ITEM_ID);
        original.setFileId(ORIGINAL_FILE_ID);
        original.setFileRole(ArchiveConstants.FILE_ROLE_ORIGINAL);
        when(itemMapper.selectOne(any())).thenReturn(existing);
        when(fileMapper.selectList(any())).thenReturn(List.of(original));

        ArchiveIngestResultVO result = service.ingest(request(true));

        assertFalse(result.getCreated());
        assertEquals(String.valueOf(ITEM_ID), result.getItemId());
        verify(fileClient).lock(ORIGINAL_FILE_ID);
        verify(itemStore, never()).persist(any(ArchiveItem.class), anyList(), any(ArchiveSourceRecord.class));
    }

    /**
     * 构造入档请求。
     *
     * @param withOriginal 是否携带原件引用
     * @return 入档请求
     */
    private static ArchiveIngestRequest request(boolean withOriginal) {
        ArchiveIngestRequest request = new ArchiveIngestRequest();
        request.setSourceType("contract");
        request.setSourceId("2001");
        request.setTitle("采购合同");
        request.setSnapshotJson("{\"contractId\":\"2001\"}");
        request.setFinishedAt(LocalDateTime.of(2026, 10, 27, 10, 0));
        if (withOriginal) {
            request.setOriginalFileId(ORIGINAL_FILE_ID);
            request.setOriginalFileName("HT-2026-001.pdf");
            request.setOriginalContentType("application/pdf");
        }
        return request;
    }

    /**
     * 构造既有档案条目。
     *
     * @return 档案条目
     */
    private static ArchiveItem existingItem() {
        ArchiveItem item = new ArchiveItem();
        item.setId(ITEM_ID);
        item.setSourceType(ArchiveConstants.SOURCE_CONTRACT);
        item.setSourceId("2001");
        item.setItemStatus(ArchiveConstants.ITEM_STATUS_PENDING);
        item.setSnapshotFileId(SNAPSHOT_FILE_ID);
        item.setSnapshotHash(SNAPSHOT_HASH);
        return item;
    }
}
