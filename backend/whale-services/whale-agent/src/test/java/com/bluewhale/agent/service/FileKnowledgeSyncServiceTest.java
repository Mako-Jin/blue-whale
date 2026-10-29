package com.bluewhale.agent.service;

import com.bluewhale.agent.config.AgentProperties;
import com.bluewhale.agent.entity.AgentKnowledgeFile;
import com.bluewhale.agent.support.index.AgentIndexService;
import com.bluewhale.agent.support.ingest.Chunk;
import com.bluewhale.agent.support.knowledge.ArchiveSecurityClient;
import com.bluewhale.agent.support.knowledge.FileChunker;
import com.bluewhale.agent.support.knowledge.FileDocumentParser;
import com.bluewhale.agent.support.knowledge.FileKnowledgeClient;
import com.bluewhale.agent.support.knowledge.FileParseException;
import com.bluewhale.agent.support.knowledge.KnowledgeConstants;
import com.bluewhale.agent.support.knowledge.RemoteFileChange;
import com.bluewhale.agent.support.knowledge.TenantDirectoryClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 文件知识库同步服务测试：增量去重、删除移除、失败退避与停用。
 *
 * @author BlueWhale
 */
class FileKnowledgeSyncServiceTest {

    private static final long FILE_ID = 100L;

    private static final String PATH = "file:100";

    private AgentProperties properties;

    private FileKnowledgeClient fileClient;

    private TenantDirectoryClient tenantDirectoryClient;

    private ArchiveSecurityClient archiveSecurityClient;

    private FileDocumentParser documentParser;

    private FileChunker fileChunker;

    private AgentKnowledgeFileService fileService;

    private AgentIndexService indexService;

    private FileKnowledgeSyncService syncService;

    @BeforeEach
    void setUp() {
        properties = new AgentProperties();
        fileClient = mock(FileKnowledgeClient.class);
        tenantDirectoryClient = mock(TenantDirectoryClient.class);
        archiveSecurityClient = mock(ArchiveSecurityClient.class);
        documentParser = mock(FileDocumentParser.class);
        fileChunker = mock(FileChunker.class);
        fileService = mock(AgentKnowledgeFileService.class);
        indexService = mock(AgentIndexService.class);
        syncService = new FileKnowledgeSyncService(properties, fileClient, tenantDirectoryClient,
                archiveSecurityClient, documentParser, fileChunker, fileService, indexService);
    }

    @AfterEach
    void tearDown() {
        syncService.shutdown();
    }

    @Test
    @DisplayName("md5 未变化不重建索引")
    void shouldSkipWhenMd5Unchanged() {
        AgentKnowledgeFile existing = indexed("m1");
        when(fileService.find(FILE_ID)).thenReturn(existing);
        when(fileClient.changes(any(), anyLong(), anyLong())).thenReturn(List.of(change("m1")));

        syncService.runOnce();

        verify(documentParser, never()).parse(any(), anyString(), any());
        verify(indexService, never()).addFileChunksWithOwner(anyString(), anyString(), anyList(), any(), any(),
                any());
        assertEquals(KnowledgeConstants.STATUS_INDEXED, saved().getIndexStatus());
    }

    @Test
    @DisplayName("md5 变化触发重建并写入索引")
    void shouldRebuildWhenMd5Changed() {
        when(fileService.find(FILE_ID)).thenReturn(indexed("m1"));
        when(fileClient.changes(any(), anyLong(), anyLong())).thenReturn(List.of(change("m2")));
        when(fileClient.content(FILE_ID)).thenReturn(new byte[]{1, 2, 3});
        when(documentParser.parse(any(), eq("制度.docx"), any())).thenReturn("正文");
        when(fileChunker.chunk(PATH, "bluewhale", "制度.docx", "正文", FileChunker.KIND_FILE))
                .thenReturn(List.of(chunk()));

        syncService.runOnce();

        verify(indexService).deletePaths(List.of(PATH));
        verify(indexService).addFileChunksWithOwner(eq(PATH), eq("m2"), anyList(), eq(FILE_ID), isNull(),
                eq(7L));
        verify(indexService).commitAndRefresh();
        AgentKnowledgeFile latest = saved();
        assertEquals(KnowledgeConstants.STATUS_INDEXED, latest.getIndexStatus());
        assertEquals(1, latest.getChunkCount().intValue());
        assertEquals(0, latest.getAttempts().intValue());
    }

    @Test
    @DisplayName("档案桶写入 archive 类型与密级权限码")
    void shouldIndexArchiveWithSecurityPermission() {
        RemoteFileChange archive = archiveChange("m3");
        when(fileService.find(FILE_ID)).thenReturn(indexed("m1"));
        when(fileClient.changes(any(), anyLong(), anyLong())).thenReturn(List.of(archive));
        when(fileClient.content(FILE_ID)).thenReturn(new byte[]{'j'});
        when(documentParser.parse(any(), eq("archive-WORKFLOW-1.json"), any())).thenReturn("{}");
        when(archiveSecurityClient.securityLevel(FILE_ID)).thenReturn(3);
        when(fileChunker.chunk(PATH, "whale-archive", "archive-WORKFLOW-1.json", "{}",
                FileChunker.KIND_ARCHIVE)).thenReturn(List.of(chunk()));

        syncService.runOnce();

        verify(indexService).addFileChunksWithOwner(eq(PATH), eq("m3"), anyList(), eq(FILE_ID),
                eq("sys:archive:secret:read"), isNull());
        assertEquals(KnowledgeConstants.SOURCE_ARCHIVE, saved().getSource());
        assertEquals(KnowledgeConstants.STATUS_INDEXED, saved().getIndexStatus());
    }

    @Test
    @DisplayName("档案密级不可用时拒绝入索引")
    void shouldSkipArchiveWhenSecurityUnavailable() {
        RemoteFileChange archive = archiveChange("m3");
        when(fileService.find(FILE_ID)).thenReturn(indexed("m1"));
        when(fileClient.changes(any(), anyLong(), anyLong())).thenReturn(List.of(archive));
        when(fileClient.content(FILE_ID)).thenReturn(new byte[]{'j'});
        when(documentParser.parse(any(), eq("archive-WORKFLOW-1.json"), any())).thenReturn("{}");
        when(archiveSecurityClient.securityLevel(FILE_ID)).thenReturn(null);

        syncService.runOnce();

        verify(indexService).deletePaths(List.of(PATH));
        verify(indexService, never()).addFileChunksWithOwner(anyString(), anyString(), anyList(), any(), any(),
                any());
        assertEquals(KnowledgeConstants.STATUS_SKIPPED, saved().getIndexStatus());
    }

    @Test
    @DisplayName("原件归档锁定后按档案权限索引")
    void shouldIndexLockedOriginalAsArchive() {
        RemoteFileChange locked = change("m3");
        locked.setStatus(2);
        when(fileService.find(FILE_ID)).thenReturn(indexed("m1"));
        when(fileClient.changes(any(), anyLong(), anyLong())).thenReturn(List.of(locked));
        when(fileClient.content(FILE_ID)).thenReturn(new byte[]{'x'});
        when(documentParser.parse(any(), eq("制度.docx"), any())).thenReturn("正文");
        when(archiveSecurityClient.securityLevel(FILE_ID)).thenReturn(2);
        when(fileChunker.chunk(PATH, "bluewhale", "制度.docx", "正文", FileChunker.KIND_ARCHIVE))
                .thenReturn(List.of(chunk()));

        syncService.runOnce();

        verify(indexService).addFileChunksWithOwner(eq(PATH), eq("m3"), anyList(), eq(FILE_ID),
                eq("sys:archive:item:read"), isNull());
        assertEquals(KnowledgeConstants.SOURCE_ARCHIVE, saved().getSource());
    }

    @Test
    @DisplayName("解除归档锁定时降级为普通知识块重建")
    void shouldDowngradeToFileAfterUnlock() {
        AgentKnowledgeFile existing = indexed("m1");
        existing.setSource(KnowledgeConstants.SOURCE_ARCHIVE);
        when(fileService.find(FILE_ID)).thenReturn(existing);
        when(fileClient.changes(any(), anyLong(), anyLong())).thenReturn(List.of(change("m1")));
        when(fileClient.content(FILE_ID)).thenReturn(new byte[]{'x'});
        when(documentParser.parse(any(), eq("制度.docx"), any())).thenReturn("正文");
        when(fileChunker.chunk(PATH, "bluewhale", "制度.docx", "正文", FileChunker.KIND_FILE))
                .thenReturn(List.of(chunk()));

        syncService.runOnce();

        verify(indexService).addFileChunksWithOwner(eq(PATH), eq("m1"), anyList(), eq(FILE_ID), isNull(),
                eq(7L));
        assertEquals(KnowledgeConstants.SOURCE_FILE, saved().getSource());
    }

    @Test
    @DisplayName("删除或停用文件立即移除索引并置 SKIPPED")
    void shouldRemoveIndexWhenDeleted() {
        AgentKnowledgeFile existing = indexed("m1");
        when(fileService.find(FILE_ID)).thenReturn(existing);
        RemoteFileChange deleted = change("m1");
        deleted.setDeleted(1);
        when(fileClient.changes(any(), anyLong(), anyLong())).thenReturn(List.of(deleted));

        syncService.runOnce();

        verify(indexService).deletePaths(List.of(PATH));
        assertEquals(KnowledgeConstants.STATUS_SKIPPED, saved().getIndexStatus());
        assertEquals(0, saved().getChunkCount().intValue());
    }

    @Test
    @DisplayName("不支持或损坏文件置 SKIPPED 且不重试")
    void shouldSkipNonRetryableParseFailure() {
        when(fileService.find(FILE_ID)).thenReturn(indexed("m1"));
        when(fileClient.changes(any(), anyLong(), anyLong())).thenReturn(List.of(change("m2")));
        when(fileClient.content(FILE_ID)).thenReturn(new byte[]{1, 2, 3});
        when(documentParser.parse(any(), anyString(), any()))
                .thenThrow(new FileParseException(false, "不支持的文件类型"));

        syncService.runOnce();

        AgentKnowledgeFile latest = saved();
        assertEquals(KnowledgeConstants.STATUS_SKIPPED, latest.getIndexStatus());
        assertEquals(0, latest.getAttempts().intValue());
    }

    @Test
    @DisplayName("可重试失败按指数退避记录")
    void shouldScheduleRetryWithBackoff() {
        when(fileService.find(FILE_ID)).thenReturn(indexed("m1"));
        when(fileClient.changes(any(), anyLong(), anyLong())).thenReturn(List.of(change("m2")));
        when(fileClient.content(FILE_ID)).thenReturn(new byte[]{1, 2, 3});
        when(documentParser.parse(any(), anyString(), any()))
                .thenThrow(new FileParseException(true, "解析超时"));

        syncService.runOnce();

        AgentKnowledgeFile latest = saved();
        assertEquals(KnowledgeConstants.STATUS_FAILED, latest.getIndexStatus());
        assertEquals(1, latest.getAttempts().intValue());
        assertNotNull(latest.getNextRetryAt());
    }

    @Test
    @DisplayName("重试次数耗尽后不再自动重建")
    void shouldStopAfterMaxAttempts() {
        AgentKnowledgeFile existing = indexed("m1");
        existing.setIndexStatus(KnowledgeConstants.STATUS_FAILED);
        existing.setAttempts(3);
        when(fileService.find(FILE_ID)).thenReturn(existing);
        when(fileClient.changes(any(), anyLong(), anyLong())).thenReturn(List.of(change("m2")));

        syncService.runOnce();

        verify(fileClient, never()).content(any());
        verify(documentParser, never()).parse(any(), anyString(), any());
    }

    @Test
    @DisplayName("停用文件清除索引")
    void shouldRemoveIndexWhenDisabled() {
        when(fileService.find(FILE_ID)).thenReturn(indexed("m1"));

        syncService.setEnabled(FILE_ID, false);

        verify(indexService).deletePaths(List.of(PATH));
        verify(indexService).commitAndRefresh();
        AgentKnowledgeFile latest = saved();
        assertEquals(KnowledgeConstants.ENABLED_NO, latest.getEnabled().intValue());
        assertEquals(KnowledgeConstants.STATUS_SKIPPED, latest.getIndexStatus());
    }

    @Test
    @DisplayName("白名单外的桶不采集且不下发解析")
    void shouldSkipBucketOutsideWhitelist() {
        when(fileService.find(FILE_ID)).thenReturn(indexed("m1"));
        RemoteFileChange outside = change("m2");
        outside.setBucket("oa-private");
        when(fileClient.changes(any(), anyLong(), anyLong())).thenReturn(List.of(outside));

        syncService.runOnce();

        verify(fileClient, never()).content(any());
        verify(documentParser, never()).parse(any(), anyString(), any());
        verify(indexService, never()).addFileChunksWithOwner(anyString(), anyString(), anyList(), any(), any(),
                any());
        verify(indexService).deletePaths(List.of(PATH));
        AgentKnowledgeFile latest = saved();
        assertEquals(KnowledgeConstants.STATUS_SKIPPED, latest.getIndexStatus());
        assertEquals("文件桶未纳入知识库白名单", latest.getLastError());
    }

    @Test
    @DisplayName("白名单为空时 default-deny 不采集任何桶")
    void shouldSkipEverythingWhenWhitelistEmpty() {
        properties.getKnowledge().setIncludedBuckets(List.of());
        when(fileService.find(FILE_ID)).thenReturn(indexed("m1"));
        when(fileClient.changes(any(), anyLong(), anyLong())).thenReturn(List.of(change("m2")));

        syncService.runOnce();

        verify(fileClient, never()).content(any());
        verify(indexService, never()).addFileChunksWithOwner(anyString(), anyString(), anyList(), any(), any(),
                any());
        assertEquals("文件桶未纳入知识库白名单", saved().getLastError());
    }

    @Test
    @DisplayName("排除列表优先于白名单")
    void shouldLetExcludedOverrideIncluded() {
        properties.getKnowledge().setIncludedBuckets(List.of("whale-doc"));
        properties.getKnowledge().setExcludedBuckets(List.of("whale-doc"));
        when(fileService.find(FILE_ID)).thenReturn(indexed("m1"));
        RemoteFileChange docBucket = change("m2");
        docBucket.setBucket("whale-doc");
        when(fileClient.changes(any(), anyLong(), anyLong())).thenReturn(List.of(docBucket));

        syncService.runOnce();

        verify(fileClient, never()).content(any());
        verify(indexService, never()).addFileChunksWithOwner(anyString(), anyString(), anyList(), any(), any(),
                any());
        assertEquals("文件已删除、禁用或桶被排除", saved().getLastError());
    }

    @Test
    @DisplayName("白名单按前缀匹配且可通过配置扩展")
    void shouldSupportWhitelistPrefixOverride() {
        properties.getKnowledge().setIncludedBuckets(List.of("oa-"));
        when(fileService.find(FILE_ID)).thenReturn(indexed("m1"));
        RemoteFileChange oa = change("m2");
        oa.setBucket("oa-public");
        when(fileClient.changes(any(), anyLong(), anyLong())).thenReturn(List.of(oa));
        when(fileClient.content(FILE_ID)).thenReturn(new byte[]{'x'});
        when(documentParser.parse(any(), eq("制度.docx"), any())).thenReturn("正文");
        when(fileChunker.chunk(PATH, "oa-public", "制度.docx", "正文", FileChunker.KIND_FILE))
                .thenReturn(List.of(chunk()));

        syncService.runOnce();

        verify(indexService).addFileChunksWithOwner(eq(PATH), eq("m2"), anyList(), eq(FILE_ID), isNull(),
                eq(7L));
        assertEquals(KnowledgeConstants.STATUS_INDEXED, saved().getIndexStatus());
    }

    @Test
    @DisplayName("退避秒数按 2 的幂增长")
    void shouldComputeBackoff() {
        assertEquals(60L, FileKnowledgeSyncService.retryDelaySeconds(1, 60));
        assertEquals(120L, FileKnowledgeSyncService.retryDelaySeconds(2, 60));
        assertEquals(240L, FileKnowledgeSyncService.retryDelaySeconds(3, 60));
        assertTrue(FileKnowledgeSyncService.retryDelaySeconds(20, 60) > 0);
    }

    /**
     * 构造已索引台账。
     *
     * @param md5 文件摘要
     * @return 台账
     */
    private AgentKnowledgeFile indexed(String md5) {
        AgentKnowledgeFile record = new AgentKnowledgeFile();
        record.setId(9L);
        record.setFileId(FILE_ID);
        record.setMd5(md5);
        record.setEnabled(KnowledgeConstants.ENABLED_YES);
        record.setIndexStatus(KnowledgeConstants.STATUS_INDEXED);
        record.setAttempts(0);
        record.setChunkCount(2);
        record.setSourceUpdateTime(LocalDateTime.of(2026, 1, 1, 10, 0));
        return record;
    }

    /**
     * 构造变更记录。
     *
     * @param md5 文件摘要
     * @return 变更记录
     */
    private RemoteFileChange change(String md5) {
        RemoteFileChange change = new RemoteFileChange();
        change.setId(FILE_ID);
        change.setOriginalName("制度.docx");
        change.setBucket("bluewhale");
        change.setContentType("text/plain");
        change.setFileSize(3L);
        change.setMd5(md5);
        change.setStatus(1);
        change.setDeleted(0);
        change.setUploaderId(7L);
        change.setUpdateTime(LocalDateTime.of(2026, 2, 1, 9, 0));
        return change;
    }

    /**
     * 构造档案变更记录。
     *
     * @param md5 文件摘要
     * @return 变更记录
     */
    private RemoteFileChange archiveChange(String md5) {
        RemoteFileChange change = change(md5);
        change.setBucket("whale-archive");
        change.setOriginalName("archive-WORKFLOW-1.json");
        change.setContentType("application/json");
        return change;
    }

    /**
     * 构造知识块。
     *
     * @return 知识块
     */
    private Chunk chunk() {
        return Chunk.builder()
                .kind(FileChunker.KIND_FILE)
                .module("whale-file")
                .path(PATH)
                .title("制度.docx")
                .content("正文")
                .startLine(0)
                .endLine(0)
                .wave("")
                .build();
    }

    /**
     * 取最近一次保存的台账。
     *
     * @return 台账
     */
    private AgentKnowledgeFile saved() {
        ArgumentCaptor<AgentKnowledgeFile> captor = ArgumentCaptor.forClass(AgentKnowledgeFile.class);
        verify(fileService, atLeastOnce()).save(captor.capture());
        List<AgentKnowledgeFile> values = captor.getAllValues();
        return values.get(values.size() - 1);
    }
}
