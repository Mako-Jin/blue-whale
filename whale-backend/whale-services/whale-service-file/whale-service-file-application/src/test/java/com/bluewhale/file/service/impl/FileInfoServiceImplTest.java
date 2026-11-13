package com.bluewhale.file.service.impl;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.tenant.TenantContext;
import com.bluewhale.file.config.FileProperties;
import com.bluewhale.file.domain.file.aggregate.FileAssetAggregate;
import com.bluewhale.file.domain.model.FileAssetPage;
import com.bluewhale.file.domain.model.FileAssetQuery;
import com.bluewhale.file.domain.file.repository.FileAssetRepository;
import com.bluewhale.file.domain.model.vo.FileStatus;
import com.bluewhale.file.domain.storage.ChunkUploadMetadata;
import com.bluewhale.file.domain.storage.ChunkUploadSessionId;
import com.bluewhale.file.domain.storage.ChunkUploadStore;
import com.bluewhale.file.domain.storage.FileStorage;
import com.bluewhale.file.domain.storage.MergedChunkUpload;
import com.bluewhale.file.model.UploadContent;
import com.bluewhale.file.model.UploadLimits;
import com.bluewhale.file.model.command.ChunkUploadCompleteCommand;
import com.bluewhale.file.model.command.ChunkUploadInitCommand;
import com.bluewhale.file.model.command.RapidUploadCommand;
import com.bluewhale.file.model.command.UploadFileCommand;
import com.bluewhale.file.model.query.FileChangesQuery;
import com.bluewhale.file.model.query.FilePageQuery;
import com.bluewhale.file.model.result.ChunkUploadInitResult;
import com.bluewhale.file.model.result.FileChangeResult;
import com.bluewhale.file.model.result.FileInfoResult;
import com.bluewhale.file.model.result.FilePresignedResult;
import com.bluewhale.file.model.result.FileQuotaResult;
import com.bluewhale.file.model.result.FileRapidUploadResult;
import com.bluewhale.file.model.result.FileUploadResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.util.DigestUtils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link FileInfoServiceImpl} 上传落元数据、超限拒绝、下载缺失、分页条件、预签名与分片上传单元测试。
 *
 * @author BlueWhale
 */
class FileInfoServiceImplTest {

    private static final int PARAM_ERROR_CODE = 400;
    private static final int NOT_FOUND_CODE = 404;
    private static final long UPLOADER_ID = 9L;

    /** 分片测试用的分片大小：10 字节 */
    private static final long TEST_CHUNK_SIZE = 10L;

    private final FileAssetRepository fileAssetRepository = mock(FileAssetRepository.class);

    private final FileStorage fileStorage = mock(FileStorage.class);

    private final FileProperties fileProperties = new FileProperties();

    private final ChunkUploadStore chunkUploadStore = new InMemoryChunkUploadStore();

    private final FileInfoServiceImpl fileInfoService =
            new FileInfoServiceImpl(fileAssetRepository, fileStorage, chunkUploadStore, fileProperties);

    /**
     * 设置较小分片大小，并让仓储保存回填入参聚合。
     */
    @BeforeEach
    void setUp() {
        fileProperties.setChunkSizeBytes(TEST_CHUNK_SIZE);
        when(fileAssetRepository.save(any(FileAssetAggregate.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(fileAssetRepository.saveWithQuotaCheck(any(FileAssetAggregate.class), anyLong()))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    /**
     * 构造上传内容：内容、原始文件名与 MIME 类型。
     *
     * @param content 文件内容
     * @param originalName 原始文件名
     * @return 应用层上传内容
     */
    private static UploadContent uploadContent(byte[] content, String originalName) {
        return new UploadContent(originalName, "application/pdf", content);
    }

    /**
     * 构造分片上传内容：仅承载分片字节。
     *
     * @param content 分片内容
     * @return 应用层上传内容
     */
    private static UploadContent chunkPart(byte[] content) {
        return new UploadContent(null, null, content);
    }

    /**
     * 构造上传命令（使用全局默认上限）。
     *
     * @param content 上传内容
     * @param bucket 目标桶，可为 {@code null}
     * @param uploaderId 上传用户 ID，可为 {@code null}
     * @return 上传命令
     */
    private static UploadFileCommand uploadCommand(UploadContent content, String bucket, Long uploaderId) {
        return new UploadFileCommand(content, bucket, uploaderId, null);
    }

    /**
     * 构造上传命令（指定生效上限）。
     *
     * @param content 上传内容
     * @param bucket 目标桶，可为 {@code null}
     * @param uploaderId 上传用户 ID，可为 {@code null}
     * @param limits 生效上限
     * @return 上传命令
     */
    private static UploadFileCommand uploadCommand(UploadContent content, String bucket, Long uploaderId,
                                                   UploadLimits limits) {
        return new UploadFileCommand(content, bucket, uploaderId, limits);
    }

    /**
     * 构造分片上传初始化命令（使用全局默认上限、新建会话）。
     *
     * @param fileName 原始文件名
     * @param size 文件总字节数
     * @return 初始化命令
     */
    private static ChunkUploadInitCommand initCommand(String fileName, long size) {
        return initCommand(fileName, size, null, null);
    }

    /**
     * 构造分片上传初始化命令（指定生效上限、新建会话）。
     *
     * @param fileName 原始文件名
     * @param size 文件总字节数
     * @param limits 生效上限
     * @return 初始化命令
     */
    private static ChunkUploadInitCommand initCommand(String fileName, long size, UploadLimits limits) {
        return initCommand(fileName, size, null, limits);
    }

    /**
     * 构造分片上传初始化命令。
     *
     * @param fileName 原始文件名
     * @param size 文件总字节数
     * @param uploadId 续传会话 ID，可为 {@code null}
     * @param limits 生效上限，可为 {@code null}
     * @return 初始化命令
     */
    private static ChunkUploadInitCommand initCommand(String fileName, long size, String uploadId,
                                                      UploadLimits limits) {
        return new ChunkUploadInitCommand(fileName, "video/mp4", size, null, uploadId, limits);
    }

    /**
     * 构造分片上传完成命令。
     *
     * @param uploadId 上传会话 ID
     * @param fileName 原始文件名
     * @param size 文件总字节数
     * @param uploaderId 上传用户 ID，可为 {@code null}
     * @param limits 生效上限，可为 {@code null}
     * @return 完成命令
     */
    private static ChunkUploadCompleteCommand completeCommand(String uploadId, String fileName, long size,
                                                              Long uploaderId, UploadLimits limits) {
        return new ChunkUploadCompleteCommand(uploadId, fileName, size, null, uploaderId, limits);
    }

    /**
     * 构造秒传命令。
     *
     * @param fileName 原始文件名
     * @param md5 文件 MD5
     * @param size 文件字节数
     * @param contentType MIME 类型，可为 {@code null}
     * @return 秒传命令
     */
    private static RapidUploadCommand rapidCommand(String fileName, String md5, long size, String contentType) {
        return new RapidUploadCommand(fileName, md5, size, contentType, UPLOADER_ID, null);
    }

    /**
     * 构造命中的既有文件聚合（秒传复用来源）。
     *
     * @param size 文件字节数
     * @return 文件资产聚合根
     */
    private static FileAssetAggregate existingFile(long size) {
        return FileAssetAggregate.reconstitute(11L, "stored.pdf", "origin.pdf", "bluewhale",
                "2026/10/19/stored.pdf", size, "application/pdf",
                "d41d8cd98f00b204e9800998ecf8427e", FileStatus.NORMAL, null, false,
                LocalDateTime.of(2026, 10, 19, 10, 0), LocalDateTime.of(2026, 10, 19, 10, 0));
    }

    /**
     * 构造指定状态的文件聚合。
     *
     * @param id 主键
     * @param status 文件状态
     * @return 文件资产聚合根
     */
    private static FileAssetAggregate fileWithStatus(long id, FileStatus status) {
        return FileAssetAggregate.reconstitute(id, null, "locked.pdf", "bluewhale", "2026/10/24/locked.pdf",
                12L, "application/pdf", null, status, UPLOADER_ID, false,
                LocalDateTime.of(2026, 10, 24, 9, 0), LocalDateTime.of(2026, 10, 24, 9, 0));
    }

    @Test
    @DisplayName("upload：写入存储并登记元数据（默认桶 + MD5 + 日期目录对象键）")
    void shouldUploadAndPersistMetadata() throws IOException {
        byte[] content = "report-body".getBytes(StandardCharsets.UTF_8);
        UploadContent file = uploadContent(content, "report.pdf");
        TenantContext.set(1L);
        try {
            FileUploadResult result = fileInfoService.upload(uploadCommand(file, null, UPLOADER_ID));

            assertEquals("report.pdf", result.originalName());
            assertEquals("bluewhale", result.bucket());
            assertEquals((long) content.length, result.fileSize());
            assertEquals(DigestUtils.md5DigestAsHex(content), result.md5());
            assertTrue(result.objectKey().startsWith("tenants/1/"
                    + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd")) + "/"));
            assertTrue(result.objectKey().endsWith(".pdf"));
            assertTrue(result.fileName().endsWith(".pdf"));
            verify(fileStorage).store(eq("bluewhale"), eq(result.objectKey()), any(InputStream.class));
            ArgumentCaptor<FileAssetAggregate> captor = ArgumentCaptor.forClass(FileAssetAggregate.class);
            verify(fileAssetRepository).saveWithQuotaCheck(captor.capture(), eq(fileProperties.getQuotaBytes()));
            FileAssetAggregate saved = captor.getValue();
            assertEquals(UPLOADER_ID, saved.getUploaderId());
            assertEquals(FileStatus.NORMAL, saved.getStatus());
            assertEquals("bluewhale", saved.getBucket());
            assertTrue(saved.getFileName().endsWith(".pdf"));
        } finally {
            TenantContext.clear();
        }
    }

    @Test
    @DisplayName("upload：超过最大字节数抛参数错误且不落库、不写存储")
    void shouldRejectOversizeUpload() throws IOException {
        fileProperties.setMaxSizeBytes(10L);
        UploadContent file = uploadContent(new byte[100], "big.bin");

        BizException ex = assertThrows(BizException.class,
                () -> fileInfoService.upload(uploadCommand(file, null, null)));

        assertEquals(PARAM_ERROR_CODE, ex.getCode());
        verify(fileStorage, never()).store(any(), any(), any());
        verify(fileAssetRepository, never()).saveWithQuotaCheck(any(FileAssetAggregate.class), anyLong());
    }

    @Test
    @DisplayName("openContent：文件不存在抛 NOT_FOUND")
    void shouldThrowWhenDownloadMissing() {
        when(fileAssetRepository.findById(99L)).thenReturn(Optional.empty());

        BizException ex = assertThrows(BizException.class,
                () -> fileInfoService.openContent(99L, UPLOADER_ID, false));

        assertEquals(NOT_FOUND_CODE, ex.getCode());
        verify(fileStorage, never()).load(any(), any());
    }

    @Test
    @DisplayName("getDetail：跨租户按不存在处理，避免泄露文件是否存在")
    void shouldHideCrossTenantFile() {
        when(fileAssetRepository.findById(5L)).thenReturn(Optional.of(FileAssetAggregate.reconstitute(5L, "a.txt",
                "a.txt", "bluewhale", "tenants/2/2026/10/24/a.txt", 3L, "text/plain", null,
                FileStatus.NORMAL, null, false, LocalDateTime.now(), LocalDateTime.now())));
        TenantContext.set(1L);
        try {
            BizException ex = assertThrows(BizException.class,
                    () -> fileInfoService.getDetail(5L, UPLOADER_ID, false));

            assertEquals(NOT_FOUND_CODE, ex.getCode());
        } finally {
            TenantContext.clear();
        }
    }

    @Test
    @DisplayName("getDetail：他人文件按不存在处理，持有管理权限可访问")
    void shouldHideOtherUploaderFileUnlessManage() {
        FileAssetAggregate others = FileAssetAggregate.reconstitute(5L, "a.txt", "a.txt", "bluewhale",
                "tenants/1/2026/10/24/a.txt", 3L, "text/plain", null, FileStatus.NORMAL, 8L, false,
                LocalDateTime.now(), LocalDateTime.now());
        when(fileAssetRepository.findById(5L)).thenReturn(Optional.of(others));
        TenantContext.set(1L);
        try {
            BizException ex = assertThrows(BizException.class,
                    () -> fileInfoService.getDetail(5L, UPLOADER_ID, false));
            assertEquals(NOT_FOUND_CODE, ex.getCode());

            FileInfoResult managed = fileInfoService.getDetail(5L, UPLOADER_ID, true);
            assertEquals(Long.valueOf(8L), managed.uploaderId());
        } finally {
            TenantContext.clear();
        }
    }

    @Test
    @DisplayName("getDetail：服务间上传（上传者为空）仅管理视角可见")
    void shouldHideServiceUploadFromNormalUser() {
        FileAssetAggregate serviceUpload = FileAssetAggregate.reconstitute(6L, "a.txt", "a.txt", "bluewhale",
                "tenants/1/2026/10/24/a.txt", 3L, "text/plain", null, FileStatus.NORMAL, null, false,
                LocalDateTime.now(), LocalDateTime.now());
        when(fileAssetRepository.findById(6L)).thenReturn(Optional.of(serviceUpload));
        TenantContext.set(1L);
        try {
            BizException ex = assertThrows(BizException.class,
                    () -> fileInfoService.getDetail(6L, UPLOADER_ID, false));
            assertEquals(NOT_FOUND_CODE, ex.getCode());

            assertNull(fileInfoService.getDetail(6L, UPLOADER_ID, true).uploaderId());
        } finally {
            TenantContext.clear();
        }
    }

    @Test
    @DisplayName("page：持有管理权限时不按上传者过滤，可查看全租户文件")
    void shouldPageAllTenantsFilesWhenManage() {
        when(fileAssetRepository.page(any(FileAssetQuery.class)))
                .thenReturn(new FileAssetPage(List.of(), 0L, 1L, 10L));

        fileInfoService.page(new FilePageQuery(1L, 10L, null, null, null, null, UPLOADER_ID, true));

        ArgumentCaptor<FileAssetQuery> captor = ArgumentCaptor.forClass(FileAssetQuery.class);
        verify(fileAssetRepository).page(captor.capture());
        assertNull(captor.getValue().uploaderId());
    }

    @Test
    @DisplayName("page：按条件调用仓储分页并映射结果")
    void shouldPageWithConditions() {
        FileAssetAggregate asset = FileAssetAggregate.reconstitute(1L, "a.pdf", "报告.pdf", "bluewhale",
                "2026/10/19/a.pdf", 10L, "application/pdf", null, FileStatus.NORMAL, null, false,
                LocalDateTime.of(2026, 10, 19, 10, 0), LocalDateTime.of(2026, 10, 19, 10, 0));
        when(fileAssetRepository.page(any(FileAssetQuery.class)))
                .thenReturn(new FileAssetPage(List.of(asset), 1L, 1L, 10L));

        FilePageQuery query = new FilePageQuery(1L, 10L, "报告", "bluewhale", null, null, UPLOADER_ID, false);
        PageResult<FileInfoResult> result = fileInfoService.page(query);

        assertEquals(1L, result.getTotal());
        assertEquals(1, result.getRecords().size());
        assertEquals("报告.pdf", result.getRecords().get(0).originalName());
        assertNotNull(result.getRecords().get(0).objectKey());
        ArgumentCaptor<FileAssetQuery> captor = ArgumentCaptor.forClass(FileAssetQuery.class);
        verify(fileAssetRepository).page(captor.capture());
        assertEquals("报告", captor.getValue().originalName());
        assertEquals("bluewhale", captor.getValue().bucket());
        assertEquals(Long.valueOf(UPLOADER_ID), captor.getValue().uploaderId());
        assertEquals(10L, captor.getValue().size());
    }

    @Test
    @DisplayName("presignedUrl：默认 600 秒并回传存储生成的 URL")
    void shouldPresignWithDefaultExpiry() {
        FileAssetAggregate asset = fileWithStatus(5L, FileStatus.NORMAL);
        when(fileAssetRepository.findById(5L)).thenReturn(Optional.of(asset));
        when(fileStorage.presignedDownloadUrl("bluewhale", "2026/10/24/locked.pdf", 600))
                .thenReturn("http://127.0.0.1:9000/bluewhale/2026/10/24/locked.pdf?sign=x");

        FilePresignedResult result = fileInfoService.presignedUrl(5L, null, UPLOADER_ID, false);

        assertEquals("http://127.0.0.1:9000/bluewhale/2026/10/24/locked.pdf?sign=x", result.url());
        assertEquals(Integer.valueOf(600), result.expiresSeconds());
    }

    @Test
    @DisplayName("presignedUrl：有效期超过 3600 秒时截断为 3600")
    void shouldClampPresignExpiry() {
        when(fileAssetRepository.findById(5L)).thenReturn(Optional.of(fileWithStatus(5L, FileStatus.NORMAL)));
        when(fileStorage.presignedDownloadUrl("bluewhale", "2026/10/24/locked.pdf", 3600))
                .thenReturn("http://x");

        FilePresignedResult result = fileInfoService.presignedUrl(5L, 9999, UPLOADER_ID, false);

        assertEquals(Integer.valueOf(3600), result.expiresSeconds());
        verify(fileStorage).presignedDownloadUrl("bluewhale", "2026/10/24/locked.pdf", 3600);
    }

    @Test
    @DisplayName("presignedUrl：local 存储不支持预签名，透传参数错误")
    void shouldRejectPresignWhenStorageUnsupported() {
        when(fileAssetRepository.findById(5L)).thenReturn(Optional.of(fileWithStatus(5L, FileStatus.NORMAL)));
        when(fileStorage.presignedDownloadUrl(anyString(), anyString(), anyInt()))
                .thenThrow(new BizException(ErrorCode.PARAM_ERROR.getCode(), "当前存储插件不支持预签名下载"));

        BizException ex = assertThrows(BizException.class,
                () -> fileInfoService.presignedUrl(5L, 600, UPLOADER_ID, false));

        assertEquals(PARAM_ERROR_CODE, ex.getCode());
        assertTrue(ex.getMessage().contains("当前存储插件不支持预签名下载"));
    }

    @Test
    @DisplayName("分片上传：init → 上传 2 个分片 → complete 合并落存储并写元数据")
    void shouldCompleteChunkUpload() throws IOException {
        ChunkUploadInitResult init = fileInfoService.initChunkUpload(initCommand("video.bin", 12L));

        assertNotNull(init.uploadId());
        assertEquals(TEST_CHUNK_SIZE, init.chunkSize());
        assertEquals(List.of(), init.uploadedIndexes());
        assertEquals(List.of(0), fileInfoService.uploadChunkPart(init.uploadId(), 0,
                chunkPart("hello".getBytes(StandardCharsets.UTF_8))));
        assertEquals(List.of(0, 1), fileInfoService.uploadChunkPart(init.uploadId(), 1,
                chunkPart(" whale!".getBytes(StandardCharsets.UTF_8))));
        assertEquals(List.of(0, 1), fileInfoService.chunkUploadStatus(init.uploadId()));

        byte[][] stored = new byte[1][];
        doAnswer(invocation -> {
            stored[0] = invocation.<InputStream>getArgument(2).readAllBytes();
            return null;
        }).when(fileStorage).store(eq("bluewhale"), anyString(), any(InputStream.class));

        FileUploadResult result = fileInfoService.completeChunkUpload(
                completeCommand(init.uploadId(), "video.bin", 12L, UPLOADER_ID, null));

        byte[] expected = "hello whale!".getBytes(StandardCharsets.UTF_8);
        assertArrayEquals(expected, stored[0]);
        assertEquals("video.bin", result.originalName());
        assertEquals("bluewhale", result.bucket());
        assertEquals(12L, result.fileSize());
        assertEquals("video/mp4", result.contentType());
        assertEquals(DigestUtils.md5DigestAsHex(expected), result.md5());
        assertTrue(result.objectKey().endsWith(".bin"));
        ArgumentCaptor<FileAssetAggregate> captor = ArgumentCaptor.forClass(FileAssetAggregate.class);
        verify(fileAssetRepository).saveWithQuotaCheck(captor.capture(), eq(fileProperties.getQuotaBytes()));
        assertEquals(12L, captor.getValue().getFileSize());
        assertEquals(UPLOADER_ID, captor.getValue().getUploaderId());
        assertFalse(sessionExists(init.uploadId()), "合并成功后应清理暂存会话");
    }

    @Test
    @DisplayName("分片上传：续传 init 返回已上传分片索引")
    void shouldResumeChunkUpload() throws IOException {
        ChunkUploadInitResult init = fileInfoService.initChunkUpload(initCommand("video.bin", 12L));
        fileInfoService.uploadChunkPart(init.uploadId(), 0,
                chunkPart("hello".getBytes(StandardCharsets.UTF_8)));

        ChunkUploadInitResult resumed = fileInfoService.initChunkUpload(
                initCommand("video.bin", 12L, init.uploadId(), null));

        assertEquals(init.uploadId(), resumed.uploadId());
        assertEquals(List.of(0), resumed.uploadedIndexes());
    }

    @Test
    @DisplayName("分片上传：缺片时 complete 抛参数错误并提示缺失索引，且保留临时目录")
    void shouldRejectIncompleteChunks() throws IOException {
        ChunkUploadInitResult init = fileInfoService.initChunkUpload(initCommand("video.bin", 12L));
        fileInfoService.uploadChunkPart(init.uploadId(), 0,
                chunkPart("hello".getBytes(StandardCharsets.UTF_8)));

        ChunkUploadCompleteCommand complete =
                completeCommand(init.uploadId(), "video.bin", 12L, UPLOADER_ID, null);
        BizException ex = assertThrows(BizException.class,
                () -> fileInfoService.completeChunkUpload(complete));

        assertEquals(PARAM_ERROR_CODE, ex.getCode());
        assertTrue(ex.getMessage().contains("缺少分片索引"));
        assertTrue(ex.getMessage().contains("1"));
        verify(fileStorage, never()).store(any(), any(), any());
        verify(fileAssetRepository, never()).saveWithQuotaCheck(any(FileAssetAggregate.class), anyLong());
        assertTrue(sessionExists(init.uploadId()), "失败时保留分片供续传");
    }

    @Test
    @DisplayName("分片上传：总大小超过 max-size-bytes 时 complete 抛参数错误")
    void shouldRejectOversizeChunkUpload() throws IOException {
        ChunkUploadInitResult init = fileInfoService.initChunkUpload(initCommand("video.bin", 12L));
        fileInfoService.uploadChunkPart(init.uploadId(), 0,
                chunkPart("hello".getBytes(StandardCharsets.UTF_8)));
        fileInfoService.uploadChunkPart(init.uploadId(), 1,
                chunkPart(" whale!".getBytes(StandardCharsets.UTF_8)));
        fileProperties.setMaxSizeBytes(5L);

        ChunkUploadCompleteCommand complete =
                completeCommand(init.uploadId(), "video.bin", 12L, UPLOADER_ID, null);
        BizException ex = assertThrows(BizException.class,
                () -> fileInfoService.completeChunkUpload(complete));

        assertEquals(PARAM_ERROR_CODE, ex.getCode());
        assertTrue(ex.getMessage().contains("超出上限"));
        verify(fileStorage, never()).store(any(), any(), any());
        verify(fileAssetRepository, never()).saveWithQuotaCheck(any(FileAssetAggregate.class), anyLong());
    }

    @Test
    @DisplayName("分片上传：分片总大小与声明大小不一致时抛参数错误")
    void shouldRejectChunkSizeMismatch() throws IOException {
        ChunkUploadInitResult init = fileInfoService.initChunkUpload(initCommand("video.bin", 20L));
        fileInfoService.uploadChunkPart(init.uploadId(), 0,
                chunkPart("hello".getBytes(StandardCharsets.UTF_8)));
        fileInfoService.uploadChunkPart(init.uploadId(), 1,
                chunkPart(" whale!".getBytes(StandardCharsets.UTF_8)));

        ChunkUploadCompleteCommand complete =
                completeCommand(init.uploadId(), "video.bin", 20L, UPLOADER_ID, null);
        BizException ex = assertThrows(BizException.class,
                () -> fileInfoService.completeChunkUpload(complete));

        assertEquals(PARAM_ERROR_CODE, ex.getCode());
        assertTrue(ex.getMessage().contains("分片总大小与声明大小不一致"));
    }

    @Test
    @DisplayName("rapidUpload：命中已有对象时复用 bucket/objectKey 仅登记新元数据，不写存储")
    void shouldRapidUploadHitWithoutStoring() {
        when(fileAssetRepository.findReusableByDigest("D41D8CD98F00B204E9800998ECF8427E", 123L))
                .thenReturn(Optional.of(existingFile(123L)));

        FileRapidUploadResult result = fileInfoService.rapidUpload(
                rapidCommand("copy.pdf", "D41D8CD98F00B204E9800998ECF8427E", 123L, null));

        assertTrue(result.hit());
        assertNotNull(result.file());
        assertEquals("bluewhale", result.file().bucket());
        assertEquals("2026/10/19/stored.pdf", result.file().objectKey());
        assertEquals("copy.pdf", result.file().originalName());
        assertEquals("application/pdf", result.file().contentType());
        assertEquals(Long.valueOf(123L), result.file().fileSize());
        ArgumentCaptor<FileAssetAggregate> captor = ArgumentCaptor.forClass(FileAssetAggregate.class);
        verify(fileAssetRepository).saveWithQuotaCheck(captor.capture(), eq(fileProperties.getQuotaBytes()));
        assertEquals("2026/10/19/stored.pdf", captor.getValue().getObjectKey());
        assertEquals("d41d8cd98f00b204e9800998ecf8427e", captor.getValue().getMd5());
        assertEquals(UPLOADER_ID, captor.getValue().getUploaderId());
        verify(fileStorage, never()).store(any(), any(), any());
    }

    @Test
    @DisplayName("rapidUpload：未命中时返回 hit=false 且不落库、不占配额")
    void shouldRapidUploadMissWithoutPersisting() {
        when(fileAssetRepository.findReusableByDigest("d41d8cd98f00b204e9800998ecf8427e", 123L))
                .thenReturn(Optional.empty());

        FileRapidUploadResult result = fileInfoService.rapidUpload(
                rapidCommand("copy.pdf", "d41d8cd98f00b204e9800998ecf8427e", 123L, "application/pdf"));

        assertFalse(result.hit());
        verify(fileAssetRepository, never()).saveWithQuotaCheck(any(FileAssetAggregate.class), anyLong());
        verify(fileAssetRepository, never()).sumNormalSizeByUploader(anyLong());
        verify(fileStorage, never()).store(any(), any(), any());
    }

    @Test
    @DisplayName("rapidUpload：命中但配额不足时抛参数错误且不落库")
    void shouldRejectRapidUploadWhenQuotaExceeded() {
        fileProperties.setQuotaBytes(1000L);
        when(fileAssetRepository.findReusableByDigest("d41d8cd98f00b204e9800998ecf8427e", 500L))
                .thenReturn(Optional.of(existingFile(500L)));
        when(fileAssetRepository.sumNormalSizeByUploader(UPLOADER_ID)).thenReturn(800L);

        BizException ex = assertThrows(BizException.class, () -> fileInfoService.rapidUpload(
                rapidCommand("copy.pdf", "d41d8cd98f00b204e9800998ecf8427e", 500L, null)));

        assertEquals(PARAM_ERROR_CODE, ex.getCode());
        assertTrue(ex.getMessage().contains("超出个人存储配额"));
        verify(fileAssetRepository, never()).saveWithQuotaCheck(any(FileAssetAggregate.class), anyLong());
    }

    @Test
    @DisplayName("upload：配额不足时抛参数错误且不写存储")
    void shouldRejectUploadWhenQuotaExceeded() throws IOException {
        fileProperties.setQuotaBytes(1000L);
        when(fileAssetRepository.sumNormalSizeByUploader(UPLOADER_ID)).thenReturn(990L);
        UploadContent file = uploadContent(new byte[20], "big.bin");

        BizException ex = assertThrows(BizException.class,
                () -> fileInfoService.upload(uploadCommand(file, null, UPLOADER_ID)));

        assertEquals(PARAM_ERROR_CODE, ex.getCode());
        assertTrue(ex.getMessage().contains("超出个人存储配额"));
        verify(fileStorage, never()).store(any(), any(), any());
    }

    @Test
    @DisplayName("upload：配额为 0 表示不限，上传成功且不查询用量")
    void shouldUploadWhenQuotaUnlimited() throws IOException {
        fileProperties.setQuotaBytes(0L);
        UploadContent file = uploadContent("unlimited".getBytes(StandardCharsets.UTF_8), "a.txt");

        FileUploadResult result = fileInfoService.upload(uploadCommand(file, null, UPLOADER_ID));

        assertEquals("a.txt", result.originalName());
        verify(fileAssetRepository, never()).sumNormalSizeByUploader(anyLong());
    }

    @Test
    @DisplayName("upload：按权限码策略放开的 2GB / 8GB 上限可上传超过全局默认上限的文件")
    void shouldUploadWithPolicyLimits() throws IOException {
        fileProperties.setMaxSizeBytes(10L);
        fileProperties.setQuotaBytes(10L);
        UploadContent file = uploadContent(new byte[100], "movie.mp4");

        FileUploadResult result = fileInfoService.upload(uploadCommand(file, null, UPLOADER_ID,
                new UploadLimits(2147483648L, 8589934592L)));

        assertEquals(100L, result.fileSize());
        ArgumentCaptor<FileAssetAggregate> captor = ArgumentCaptor.forClass(FileAssetAggregate.class);
        verify(fileAssetRepository).saveWithQuotaCheck(captor.capture(), eq(8589934592L));
        assertEquals("movie.mp4", captor.getValue().getOriginalName());
    }

    @Test
    @DisplayName("quota：按生效策略返回 8GB 配额与剩余量")
    void shouldReturnPolicyQuota() {
        when(fileAssetRepository.sumNormalSizeByUploader(UPLOADER_ID)).thenReturn(123L);

        FileQuotaResult result = fileInfoService.quota(UPLOADER_ID, new UploadLimits(2147483648L, 8589934592L));

        assertEquals(8589934592L, result.quotaBytes());
        assertEquals(123L, result.usedBytes());
        assertEquals(8589934592L - 123L, result.remainingBytes());
        assertFalse(result.unlimited());
    }

    @Test
    @DisplayName("分片上传：持有视频策略可初始化 2GB 声明，未命中策略仍按默认 50MB 拒绝")
    void shouldApplyPolicyOnChunkInit() {
        ChunkUploadInitResult video = fileInfoService.initChunkUpload(
                initCommand("movie.mp4", 2147483648L, new UploadLimits(2147483648L, 8589934592L)));

        assertNotNull(video.uploadId());
        ChunkUploadMetadata recorded = chunkUploadStore.metadata(ChunkUploadSessionId.of(video.uploadId()));
        assertEquals(2147483648L, recorded.maxSizeBytes());
        assertEquals(8589934592L, recorded.quotaBytes());
        assertTrue(recorded.hasRecordedLimits());

        BizException ex = assertThrows(BizException.class,
                () -> fileInfoService.initChunkUpload(initCommand("movie.mp4", 2147483648L)));

        assertEquals(PARAM_ERROR_CODE, ex.getCode());
        assertTrue(ex.getMessage().contains("超出上限"));
    }

    @Test
    @DisplayName("分片上传：init 登记上限后，complete 即使持有更大权限也不能放大上限")
    void shouldNotWidenLimitsBetweenInitAndComplete() throws IOException {
        // init 在较小上限（100 字节）下登记会话，随后客户端上传 12 个分片
        ChunkUploadInitResult init = fileInfoService.initChunkUpload(
                initCommand("video.bin", 40L, new UploadLimits(100L, 1000L)));
        byte[] part = new byte[10];
        for (int i = 0; i < 12; i++) {
            fileInfoService.uploadChunkPart(init.uploadId(), i, chunkPart(part));
        }

        // complete 以更大权限声明 120 字节：仍按 init 登记的 100 字节上限拒绝
        ChunkUploadCompleteCommand complete = completeCommand(init.uploadId(), "video.bin", 120L,
                UPLOADER_ID, new UploadLimits(2147483648L, 8589934592L));
        BizException ex = assertThrows(BizException.class,
                () -> fileInfoService.completeChunkUpload(complete));

        assertEquals(PARAM_ERROR_CODE, ex.getCode());
        assertTrue(ex.getMessage().contains("超出上限"));
        verify(fileStorage, never()).store(any(), any(), any());
        verify(fileAssetRepository, never()).saveWithQuotaCheck(any(FileAssetAggregate.class), anyLong());
    }

    @Test
    @DisplayName("quota：返回已用量与剩余量")
    void shouldReturnQuotaUsage() {
        when(fileAssetRepository.sumNormalSizeByUploader(UPLOADER_ID)).thenReturn(123L);

        FileQuotaResult result = fileInfoService.quota(UPLOADER_ID, null);

        assertEquals(123L, result.usedBytes());
        assertEquals(fileProperties.getQuotaBytes(), result.quotaBytes());
        assertEquals(fileProperties.getQuotaBytes() - 123L, result.remainingBytes());
        assertFalse(result.unlimited());
    }

    @Test
    @DisplayName("quota：无用户态时返回不限配额且不查库")
    void shouldReturnUnlimitedQuotaWithoutUser() {
        FileQuotaResult result = fileInfoService.quota(null, null);

        assertTrue(result.unlimited());
        assertEquals(0L, result.usedBytes());
        assertEquals(0L, result.remainingBytes());
        verify(fileAssetRepository, never()).sumNormalSizeByUploader(anyLong());
    }

    @Test
    @DisplayName("quota：配额配置为 0 时返回不限且不查库")
    void shouldReturnUnlimitedQuotaWhenConfiguredUnlimited() {
        fileProperties.setQuotaBytes(0L);

        FileQuotaResult result = fileInfoService.quota(UPLOADER_ID, null);

        assertTrue(result.unlimited());
        assertEquals(0L, result.quotaBytes());
        verify(fileAssetRepository, never()).sumNormalSizeByUploader(anyLong());
    }

    @Test
    @DisplayName("quota：无记录时已用量返回 0")
    void shouldReturnZeroUsageWhenNoRecords() {
        when(fileAssetRepository.sumNormalSizeByUploader(UPLOADER_ID)).thenReturn(0L);

        FileQuotaResult result = fileInfoService.quota(UPLOADER_ID, null);

        assertEquals(0L, result.usedBytes());
        assertEquals(fileProperties.getQuotaBytes(), result.remainingBytes());
        assertFalse(result.unlimited());
    }

    @Test
    @DisplayName("upload：用量恰好达到配额上限时仍允许上传")
    void shouldAllowUploadAtQuotaBoundary() throws IOException {
        fileProperties.setQuotaBytes(1000L);
        when(fileAssetRepository.sumNormalSizeByUploader(UPLOADER_ID)).thenReturn(980L);
        UploadContent file = uploadContent(new byte[20], "edge.bin");

        FileUploadResult result = fileInfoService.upload(uploadCommand(file, null, UPLOADER_ID));

        assertEquals(20L, result.fileSize());
        verify(fileStorage).store(eq("bluewhale"), anyString(), any(InputStream.class));
    }

    @Test
    @DisplayName("lock：正常文件写入归档锁定状态")
    void shouldLockFile() {
        when(fileAssetRepository.findById(7L)).thenReturn(Optional.of(fileWithStatus(7L, FileStatus.NORMAL)));

        fileInfoService.lock(7L);

        ArgumentCaptor<FileAssetAggregate> captor = ArgumentCaptor.forClass(FileAssetAggregate.class);
        verify(fileAssetRepository).save(captor.capture());
        assertEquals(7L, captor.getValue().getId());
        assertEquals(FileStatus.LOCKED, captor.getValue().getStatus());
    }

    @Test
    @DisplayName("lock：已锁定文件幂等，不重复保存")
    void shouldIgnoreRelock() {
        when(fileAssetRepository.findById(7L)).thenReturn(Optional.of(fileWithStatus(7L, FileStatus.LOCKED)));

        fileInfoService.lock(7L);

        verify(fileAssetRepository, never()).save(any(FileAssetAggregate.class));
    }

    @Test
    @DisplayName("lock：禁用文件不可锁定")
    void shouldRejectLockDisabledFile() {
        when(fileAssetRepository.findById(7L)).thenReturn(Optional.of(fileWithStatus(7L, FileStatus.DISABLED)));

        BizException ex = assertThrows(BizException.class, () -> fileInfoService.lock(7L));

        assertEquals(PARAM_ERROR_CODE, ex.getCode());
        assertTrue(ex.getMessage().contains("已禁用"));
        verify(fileAssetRepository, never()).save(any(FileAssetAggregate.class));
    }

    @Test
    @DisplayName("unlock：锁定文件恢复正常；未锁定文件不产生保存")
    void shouldUnlockOnlyLockedFile() {
        when(fileAssetRepository.findById(7L)).thenReturn(Optional.of(fileWithStatus(7L, FileStatus.LOCKED)));
        fileInfoService.unlock(7L);
        ArgumentCaptor<FileAssetAggregate> captor = ArgumentCaptor.forClass(FileAssetAggregate.class);
        verify(fileAssetRepository).save(captor.capture());
        assertEquals(FileStatus.NORMAL, captor.getValue().getStatus());

        when(fileAssetRepository.findById(8L)).thenReturn(Optional.of(fileWithStatus(8L, FileStatus.NORMAL)));
        fileInfoService.unlock(8L);
        verify(fileAssetRepository, times(1)).save(any(FileAssetAggregate.class));
    }

    @Test
    @DisplayName("delete：归档锁定文件禁止删除")
    void shouldRejectDeletingLockedFile() {
        when(fileAssetRepository.findById(7L)).thenReturn(Optional.of(fileWithStatus(7L, FileStatus.LOCKED)));

        BizException ex = assertThrows(BizException.class,
                () -> fileInfoService.delete(7L, UPLOADER_ID, false));

        assertEquals(PARAM_ERROR_CODE, ex.getCode());
        assertTrue(ex.getMessage().contains("禁止删除"));
        verify(fileAssetRepository, never()).deleteById(anyLong());
    }

    @Test
    @DisplayName("changes：按更新时间游标分页并回传状态与更新时间")
    void shouldListChangesWithCursor() {
        FileAssetAggregate asset = FileAssetAggregate.reconstitute(7L, "locked.pdf", "locked.pdf", "bluewhale",
                "2026/10/24/locked.pdf", 12L, "application/pdf", null, FileStatus.LOCKED, null, false,
                LocalDateTime.of(2026, 10, 24, 9, 0), LocalDateTime.of(2026, 10, 24, 10, 0));
        when(fileAssetRepository.findChanges(any(), anyLong(), anyLong()))
                .thenReturn(new FileAssetPage(List.of(asset), 1L, 1L, 200L));

        PageResult<FileChangeResult> result = fileInfoService.changes(
                new FileChangesQuery(LocalDateTime.of(2026, 10, 24, 9, 0), 1L, 500L));

        assertEquals(1L, result.getTotal());
        assertEquals(Integer.valueOf(2), result.getRecords().get(0).status());
        assertEquals(LocalDateTime.of(2026, 10, 24, 10, 0), result.getRecords().get(0).updateTime());
        verify(fileAssetRepository).findChanges(any(), eq(1L), eq(200L));
    }

    @Test
    @DisplayName("getChangeDetail：已逻辑删除文件仍可查询并返回 deleted=1")
    void shouldReturnChangeDetailForDeletedFile() {
        FileAssetAggregate deleted = FileAssetAggregate.reconstitute(7L, "locked.pdf", "locked.pdf", "bluewhale",
                "2026/10/24/locked.pdf", 12L, "application/pdf", null, FileStatus.NORMAL, null, true,
                LocalDateTime.of(2026, 10, 24, 9, 0), LocalDateTime.of(2026, 10, 24, 10, 0));
        when(fileAssetRepository.findByIdIncludingDeleted(7L)).thenReturn(Optional.of(deleted));

        FileChangeResult result = fileInfoService.getChangeDetail(7L);

        assertEquals(Integer.valueOf(1), result.deleted());
        assertEquals(Integer.valueOf(1), result.status());
        assertEquals(LocalDateTime.of(2026, 10, 24, 10, 0), result.updateTime());
    }

    @Test
    @DisplayName("upload：仓储原子配额校验失败时补偿删除已写入对象，避免孤儿文件")
    void shouldCompensateStoredObjectWhenPersistRejected() throws IOException {
        fileProperties.setQuotaBytes(1000L);
        when(fileAssetRepository.saveWithQuotaCheck(any(FileAssetAggregate.class), anyLong()))
                .thenThrow(new BizException(ErrorCode.PARAM_ERROR.getCode(), "超出个人存储配额"));
        UploadContent file = uploadContent("race-lost".getBytes(StandardCharsets.UTF_8), "race.txt");

        BizException ex = assertThrows(BizException.class,
                () -> fileInfoService.upload(uploadCommand(file, null, UPLOADER_ID)));

        assertEquals(PARAM_ERROR_CODE, ex.getCode());
        ArgumentCaptor<String> objectKey = ArgumentCaptor.forClass(String.class);
        verify(fileStorage).store(eq("bluewhale"), objectKey.capture(), any(InputStream.class));
        verify(fileStorage).delete("bluewhale", objectKey.getValue());
    }

    @Test
    @DisplayName("upload：补偿删除失败不掩盖原始配额异常")
    void shouldKeepOriginalErrorWhenCompensationFails() throws IOException {
        fileProperties.setQuotaBytes(1000L);
        when(fileAssetRepository.saveWithQuotaCheck(any(FileAssetAggregate.class), anyLong()))
                .thenThrow(new BizException(ErrorCode.PARAM_ERROR.getCode(), "超出个人存储配额"));
        doAnswer(invocation -> {
            throw new BizException(ErrorCode.SYSTEM_ERROR);
        }).when(fileStorage).delete(anyString(), anyString());
        UploadContent file = uploadContent("race-lost".getBytes(StandardCharsets.UTF_8), "race.txt");

        BizException ex = assertThrows(BizException.class,
                () -> fileInfoService.upload(uploadCommand(file, null, UPLOADER_ID)));

        assertEquals(PARAM_ERROR_CODE, ex.getCode());
    }

    @Test
    @DisplayName("分片上传：uploadId 非法或会话不存在时抛参数错误")
    void shouldRejectUnknownChunkSession() {
        BizException illegal = assertThrows(BizException.class,
                () -> fileInfoService.chunkUploadStatus("../../etc/passwd"));
        assertEquals(PARAM_ERROR_CODE, illegal.getCode());

        BizException missing = assertThrows(BizException.class,
                () -> fileInfoService.chunkUploadStatus(ChunkUploadSessionId.random().value()));
        assertEquals(PARAM_ERROR_CODE, missing.getCode());
        assertTrue(missing.getMessage().contains("上传会话不存在或已失效"));
    }

    /**
     * 判断分片暂存会话是否仍存在
     *
     * @param uploadId 上传会话 ID
     * @return 会话存在时返回 {@code true}
     */
    private boolean sessionExists(String uploadId) {
        return chunkUploadStore.exists(ChunkUploadSessionId.of(uploadId));
    }

    /**
     * 应用层测试替身：内存分片暂存，避免单测依赖基础设施实现（保持分层依赖方向）。
     */
    private static final class InMemoryChunkUploadStore implements ChunkUploadStore {

        /** 会话 ID → 暂存内容 */
        private final Map<String, Session> sessions = new HashMap<>();

        @Override
        public void initialize(ChunkUploadSessionId sessionId, ChunkUploadMetadata metadata) {
            Session session = sessions.computeIfAbsent(sessionId.value(), key -> new Session());
            session.contentType = metadata.contentType();
            session.metadata = metadata;
        }

        @Override
        public boolean exists(ChunkUploadSessionId sessionId) {
            return sessions.containsKey(sessionId.value());
        }

        @Override
        public ChunkUploadMetadata metadata(ChunkUploadSessionId sessionId) {
            Session session = sessions.get(sessionId.value());
            return session == null ? null : session.metadata;
        }

        @Override
        public void storePart(ChunkUploadSessionId sessionId, int index, InputStream content, long size) {
            Session session = require(sessionId);
            try {
                session.parts.put(index, content.readAllBytes());
            } catch (IOException e) {
                throw new BizException(ErrorCode.SYSTEM_ERROR);
            }
        }

        @Override
        public List<Integer> uploadedPartIndexes(ChunkUploadSessionId sessionId) {
            Session session = sessions.get(sessionId.value());
            if (session == null) {
                return List.of();
            }
            return List.copyOf(session.parts.keySet());
        }

        @Override
        public MergedChunkUpload merge(ChunkUploadSessionId sessionId, int partCount) {
            Session session = require(sessionId);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            for (int i = 0; i < partCount; i++) {
                byte[] part = session.parts.get(i);
                if (part == null) {
                    throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "缺少分片索引: " + i);
                }
                out.writeBytes(part);
            }
            byte[] merged = out.toByteArray();
            return new MergedChunkUpload(new ByteArrayInputStream(merged), merged.length,
                    session.contentType, DigestUtils.md5DigestAsHex(merged));
        }

        @Override
        public void discard(ChunkUploadSessionId sessionId) {
            sessions.remove(sessionId.value());
        }

        /**
         * 获取会话暂存内容，不存在时抛出参数错误
         *
         * @param sessionId 会话标识
         * @return 会话暂存内容
         */
        private Session require(ChunkUploadSessionId sessionId) {
            Session session = sessions.get(sessionId.value());
            if (session == null) {
                throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "上传会话不存在或已失效");
            }
            return session;
        }
    }

    /**
     * 单个上传会话的暂存内容。
     */
    private static final class Session {

        /** 分片索引 → 分片内容（按索引升序） */
        private final Map<Integer, byte[]> parts = new TreeMap<>();

        /** 会话登记的 MIME 类型 */
        private String contentType;

        /** 会话登记的元数据（含初始化时生效的上传上限） */
        private ChunkUploadMetadata metadata;
    }
}
