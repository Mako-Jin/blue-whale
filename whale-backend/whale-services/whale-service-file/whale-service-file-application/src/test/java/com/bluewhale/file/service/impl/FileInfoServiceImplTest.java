package com.bluewhale.file.service.impl;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.tenant.TenantContext;
import com.bluewhale.file.config.FileProperties;
import com.bluewhale.file.domain.file.aggregate.FileAsset;
import com.bluewhale.file.domain.file.repository.FileAssetPage;
import com.bluewhale.file.domain.file.repository.FileAssetQuery;
import com.bluewhale.file.domain.file.repository.FileAssetRepository;
import com.bluewhale.file.domain.model.vo.FileStatus;
import com.bluewhale.file.domain.storage.ChunkUploadMetadata;
import com.bluewhale.file.domain.storage.ChunkUploadSessionId;
import com.bluewhale.file.domain.storage.ChunkUploadStore;
import com.bluewhale.file.domain.storage.FileStorage;
import com.bluewhale.file.domain.storage.MergedChunkUpload;
import com.bluewhale.file.dto.ChunkUploadCompleteRequest;
import com.bluewhale.file.dto.ChunkUploadInitRequest;
import com.bluewhale.file.dto.FileQueryRequest;
import com.bluewhale.file.dto.FileRapidUploadRequest;
import com.bluewhale.file.service.model.UploadContent;
import com.bluewhale.file.service.model.UploadLimits;
import com.bluewhale.file.vo.ChunkUploadInitVO;
import com.bluewhale.file.vo.FileChangeVO;
import com.bluewhale.file.vo.FileInfoVO;
import com.bluewhale.file.vo.FilePresignedVO;
import com.bluewhale.file.vo.FileQuotaVO;
import com.bluewhale.file.vo.FileRapidUploadVO;
import com.bluewhale.file.vo.FileUploadVO;
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
        when(fileAssetRepository.save(any(FileAsset.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(fileAssetRepository.saveWithQuotaCheck(any(FileAsset.class), anyLong()))
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
     * 构造分片上传初始化请求。
     *
     * @param fileName 原始文件名
     * @param size 文件总字节数
     * @return 初始化请求
     */
    private static ChunkUploadInitRequest initRequest(String fileName, long size) {
        ChunkUploadInitRequest request = new ChunkUploadInitRequest();
        request.setFileName(fileName);
        request.setContentType("video/mp4");
        request.setSize(size);
        return request;
    }

    /**
     * 构造秒传请求。
     *
     * @param fileName 原始文件名
     * @param md5 文件 MD5
     * @param size 文件字节数
     * @param contentType MIME 类型，可为 {@code null}
     * @return 秒传请求
     */
    private static FileRapidUploadRequest rapidRequest(String fileName, String md5, long size, String contentType) {
        FileRapidUploadRequest request = new FileRapidUploadRequest();
        request.setFileName(fileName);
        request.setMd5(md5);
        request.setSize(size);
        request.setContentType(contentType);
        return request;
    }

    /**
     * 构造命中的既有文件聚合（秒传复用来源）。
     *
     * @param size 文件字节数
     * @return 文件资产聚合根
     */
    private static FileAsset existingFile(long size) {
        return FileAsset.reconstitute(11L, "stored.pdf", "origin.pdf", "bluewhale",
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
    private static FileAsset fileWithStatus(long id, FileStatus status) {
        return FileAsset.reconstitute(id, null, "locked.pdf", "bluewhale", "2026/10/24/locked.pdf",
                12L, "application/pdf", null, status, null, false,
                LocalDateTime.of(2026, 10, 24, 9, 0), LocalDateTime.of(2026, 10, 24, 9, 0));
    }

    @Test
    @DisplayName("upload：写入存储并登记元数据（默认桶 + MD5 + 日期目录对象键）")
    void shouldUploadAndPersistMetadata() throws IOException {
        byte[] content = "report-body".getBytes(StandardCharsets.UTF_8);
        UploadContent file = uploadContent(content, "report.pdf");
        TenantContext.set(1L);
        try {
            FileUploadVO vo = fileInfoService.upload(file, null, UPLOADER_ID);

            assertEquals("report.pdf", vo.getOriginalName());
            assertEquals("bluewhale", vo.getBucket());
            assertEquals((long) content.length, vo.getFileSize());
            assertEquals(DigestUtils.md5DigestAsHex(content), vo.getMd5());
            assertTrue(vo.getObjectKey().startsWith("tenants/1/"
                    + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd")) + "/"));
            assertTrue(vo.getObjectKey().endsWith(".pdf"));
            assertTrue(vo.getFileName().endsWith(".pdf"));
            verify(fileStorage).store(eq("bluewhale"), eq(vo.getObjectKey()), any(InputStream.class));
            ArgumentCaptor<FileAsset> captor = ArgumentCaptor.forClass(FileAsset.class);
            verify(fileAssetRepository).saveWithQuotaCheck(captor.capture(), eq(fileProperties.getQuotaBytes()));
            FileAsset saved = captor.getValue();
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

        BizException ex = assertThrows(BizException.class, () -> fileInfoService.upload(file, null, null));

        assertEquals(PARAM_ERROR_CODE, ex.getCode());
        verify(fileStorage, never()).store(any(), any(), any());
        verify(fileAssetRepository, never()).saveWithQuotaCheck(any(FileAsset.class), anyLong());
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
        when(fileAssetRepository.findById(5L)).thenReturn(Optional.of(FileAsset.reconstitute(5L, "a.txt",
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
        FileAsset others = FileAsset.reconstitute(5L, "a.txt", "a.txt", "bluewhale",
                "tenants/1/2026/10/24/a.txt", 3L, "text/plain", null, FileStatus.NORMAL, 8L, false,
                LocalDateTime.now(), LocalDateTime.now());
        when(fileAssetRepository.findById(5L)).thenReturn(Optional.of(others));
        TenantContext.set(1L);
        try {
            BizException ex = assertThrows(BizException.class,
                    () -> fileInfoService.getDetail(5L, UPLOADER_ID, false));
            assertEquals(NOT_FOUND_CODE, ex.getCode());

            FileInfoVO managed = fileInfoService.getDetail(5L, UPLOADER_ID, true);
            assertEquals(Long.valueOf(8L), managed.getUploaderId());
        } finally {
            TenantContext.clear();
        }
    }

    @Test
    @DisplayName("page：持有管理权限时不按上传者过滤，可查看全租户文件")
    void shouldPageAllTenantsFilesWhenManage() {
        when(fileAssetRepository.page(any(FileAssetQuery.class)))
                .thenReturn(new FileAssetPage(List.of(), 0L, 1L, 10L));

        fileInfoService.page(new FileQueryRequest(), UPLOADER_ID, true);

        ArgumentCaptor<FileAssetQuery> captor = ArgumentCaptor.forClass(FileAssetQuery.class);
        verify(fileAssetRepository).page(captor.capture());
        assertNull(captor.getValue().uploaderId());
    }

    @Test
    @DisplayName("page：按条件调用仓储分页并映射结果")
    void shouldPageWithConditions() {
        FileAsset asset = FileAsset.reconstitute(1L, "a.pdf", "报告.pdf", "bluewhale",
                "2026/10/19/a.pdf", 10L, "application/pdf", null, FileStatus.NORMAL, null, false,
                LocalDateTime.of(2026, 10, 19, 10, 0), LocalDateTime.of(2026, 10, 19, 10, 0));
        when(fileAssetRepository.page(any(FileAssetQuery.class)))
                .thenReturn(new FileAssetPage(List.of(asset), 1L, 1L, 10L));

        FileQueryRequest request = new FileQueryRequest();
        request.setOriginalName("报告");
        request.setBucket("bluewhale");
        PageResult<FileInfoVO> result = fileInfoService.page(request, UPLOADER_ID, false);

        assertEquals(1L, result.getTotal());
        assertEquals(1, result.getRecords().size());
        assertEquals("报告.pdf", result.getRecords().get(0).getOriginalName());
        assertNotNull(result.getRecords().get(0).getObjectKey());
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
        FileAsset asset = fileWithStatus(5L, FileStatus.NORMAL);
        when(fileAssetRepository.findById(5L)).thenReturn(Optional.of(asset));
        when(fileStorage.presignedDownloadUrl("bluewhale", "2026/10/24/locked.pdf", 600))
                .thenReturn("http://127.0.0.1:9000/bluewhale/2026/10/24/locked.pdf?sign=x");

        FilePresignedVO vo = fileInfoService.presignedUrl(5L, null, UPLOADER_ID, false);

        assertEquals("http://127.0.0.1:9000/bluewhale/2026/10/24/locked.pdf?sign=x", vo.getUrl());
        assertEquals(Integer.valueOf(600), vo.getExpiresSeconds());
    }

    @Test
    @DisplayName("presignedUrl：有效期超过 3600 秒时截断为 3600")
    void shouldClampPresignExpiry() {
        when(fileAssetRepository.findById(5L)).thenReturn(Optional.of(fileWithStatus(5L, FileStatus.NORMAL)));
        when(fileStorage.presignedDownloadUrl("bluewhale", "2026/10/24/locked.pdf", 3600))
                .thenReturn("http://x");

        FilePresignedVO vo = fileInfoService.presignedUrl(5L, 9999, UPLOADER_ID, false);

        assertEquals(Integer.valueOf(3600), vo.getExpiresSeconds());
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
        ChunkUploadInitVO initVo = fileInfoService.initChunkUpload(initRequest("video.bin", 12L));

        assertNotNull(initVo.getUploadId());
        assertEquals(TEST_CHUNK_SIZE, initVo.getChunkSize());
        assertEquals(List.of(), initVo.getUploadedIndexes());
        assertEquals(List.of(0), fileInfoService.uploadChunkPart(initVo.getUploadId(), 0,
                chunkPart("hello".getBytes(StandardCharsets.UTF_8))));
        assertEquals(List.of(0, 1), fileInfoService.uploadChunkPart(initVo.getUploadId(), 1,
                chunkPart(" whale!".getBytes(StandardCharsets.UTF_8))));
        assertEquals(List.of(0, 1), fileInfoService.chunkUploadStatus(initVo.getUploadId()));

        byte[][] stored = new byte[1][];
        doAnswer(invocation -> {
            stored[0] = invocation.<InputStream>getArgument(2).readAllBytes();
            return null;
        }).when(fileStorage).store(eq("bluewhale"), anyString(), any(InputStream.class));

        ChunkUploadCompleteRequest complete = new ChunkUploadCompleteRequest();
        complete.setUploadId(initVo.getUploadId());
        complete.setFileName("video.bin");
        complete.setSize(12L);
        FileUploadVO vo = fileInfoService.completeChunkUpload(complete, UPLOADER_ID);

        byte[] expected = "hello whale!".getBytes(StandardCharsets.UTF_8);
        assertArrayEquals(expected, stored[0]);
        assertEquals("video.bin", vo.getOriginalName());
        assertEquals("bluewhale", vo.getBucket());
        assertEquals(12L, vo.getFileSize());
        assertEquals("video/mp4", vo.getContentType());
        assertEquals(DigestUtils.md5DigestAsHex(expected), vo.getMd5());
        assertTrue(vo.getObjectKey().endsWith(".bin"));
        ArgumentCaptor<FileAsset> captor = ArgumentCaptor.forClass(FileAsset.class);
        verify(fileAssetRepository).saveWithQuotaCheck(captor.capture(), eq(fileProperties.getQuotaBytes()));
        assertEquals(12L, captor.getValue().getFileSize());
        assertEquals(UPLOADER_ID, captor.getValue().getUploaderId());
        assertFalse(sessionExists(initVo.getUploadId()), "合并成功后应清理暂存会话");
    }

    @Test
    @DisplayName("分片上传：续传 init 返回已上传分片索引")
    void shouldResumeChunkUpload() throws IOException {
        ChunkUploadInitVO initVo = fileInfoService.initChunkUpload(initRequest("video.bin", 12L));
        fileInfoService.uploadChunkPart(initVo.getUploadId(), 0, chunkPart("hello".getBytes(StandardCharsets.UTF_8)));

        ChunkUploadInitRequest resume = initRequest("video.bin", 12L);
        resume.setUploadId(initVo.getUploadId());
        ChunkUploadInitVO resumed = fileInfoService.initChunkUpload(resume);

        assertEquals(initVo.getUploadId(), resumed.getUploadId());
        assertEquals(List.of(0), resumed.getUploadedIndexes());
    }

    @Test
    @DisplayName("分片上传：缺片时 complete 抛参数错误并提示缺失索引，且保留临时目录")
    void shouldRejectIncompleteChunks() throws IOException {
        ChunkUploadInitVO initVo = fileInfoService.initChunkUpload(initRequest("video.bin", 12L));
        fileInfoService.uploadChunkPart(initVo.getUploadId(), 0,
                chunkPart("hello".getBytes(StandardCharsets.UTF_8)));

        ChunkUploadCompleteRequest complete = new ChunkUploadCompleteRequest();
        complete.setUploadId(initVo.getUploadId());
        complete.setFileName("video.bin");
        complete.setSize(12L);
        BizException ex = assertThrows(BizException.class,
                () -> fileInfoService.completeChunkUpload(complete, UPLOADER_ID));

        assertEquals(PARAM_ERROR_CODE, ex.getCode());
        assertTrue(ex.getMessage().contains("缺少分片索引"));
        assertTrue(ex.getMessage().contains("1"));
        verify(fileStorage, never()).store(any(), any(), any());
        verify(fileAssetRepository, never()).saveWithQuotaCheck(any(FileAsset.class), anyLong());
        assertTrue(sessionExists(initVo.getUploadId()), "失败时保留分片供续传");
    }

    @Test
    @DisplayName("分片上传：总大小超过 max-size-bytes 时 complete 抛参数错误")
    void shouldRejectOversizeChunkUpload() throws IOException {
        ChunkUploadInitVO initVo = fileInfoService.initChunkUpload(initRequest("video.bin", 12L));
        fileInfoService.uploadChunkPart(initVo.getUploadId(), 0,
                chunkPart("hello".getBytes(StandardCharsets.UTF_8)));
        fileInfoService.uploadChunkPart(initVo.getUploadId(), 1,
                chunkPart(" whale!".getBytes(StandardCharsets.UTF_8)));
        fileProperties.setMaxSizeBytes(5L);

        ChunkUploadCompleteRequest complete = new ChunkUploadCompleteRequest();
        complete.setUploadId(initVo.getUploadId());
        complete.setFileName("video.bin");
        complete.setSize(12L);
        BizException ex = assertThrows(BizException.class,
                () -> fileInfoService.completeChunkUpload(complete, UPLOADER_ID));

        assertEquals(PARAM_ERROR_CODE, ex.getCode());
        assertTrue(ex.getMessage().contains("超出上限"));
        verify(fileStorage, never()).store(any(), any(), any());
        verify(fileAssetRepository, never()).saveWithQuotaCheck(any(FileAsset.class), anyLong());
    }

    @Test
    @DisplayName("分片上传：分片总大小与声明大小不一致时抛参数错误")
    void shouldRejectChunkSizeMismatch() throws IOException {
        ChunkUploadInitVO initVo = fileInfoService.initChunkUpload(initRequest("video.bin", 20L));
        fileInfoService.uploadChunkPart(initVo.getUploadId(), 0,
                chunkPart("hello".getBytes(StandardCharsets.UTF_8)));
        fileInfoService.uploadChunkPart(initVo.getUploadId(), 1,
                chunkPart(" whale!".getBytes(StandardCharsets.UTF_8)));

        ChunkUploadCompleteRequest complete = new ChunkUploadCompleteRequest();
        complete.setUploadId(initVo.getUploadId());
        complete.setFileName("video.bin");
        complete.setSize(20L);
        BizException ex = assertThrows(BizException.class,
                () -> fileInfoService.completeChunkUpload(complete, UPLOADER_ID));

        assertEquals(PARAM_ERROR_CODE, ex.getCode());
        assertTrue(ex.getMessage().contains("分片总大小与声明大小不一致"));
    }

    @Test
    @DisplayName("rapidUpload：命中已有对象时复用 bucket/objectKey 仅登记新元数据，不写存储")
    void shouldRapidUploadHitWithoutStoring() {
        when(fileAssetRepository.findReusableByDigest("d41d8cd98f00b204e9800998ecf8427e", 123L))
                .thenReturn(Optional.of(existingFile(123L)));

        FileRapidUploadVO vo = fileInfoService.rapidUpload(
                rapidRequest("copy.pdf", "D41D8CD98F00B204E9800998ECF8427E", 123L, null), UPLOADER_ID);

        assertTrue(vo.isHit());
        assertNotNull(vo.getFile());
        assertEquals("bluewhale", vo.getFile().getBucket());
        assertEquals("2026/10/19/stored.pdf", vo.getFile().getObjectKey());
        assertEquals("copy.pdf", vo.getFile().getOriginalName());
        assertEquals("application/pdf", vo.getFile().getContentType());
        assertEquals(Long.valueOf(123L), vo.getFile().getFileSize());
        ArgumentCaptor<FileAsset> captor = ArgumentCaptor.forClass(FileAsset.class);
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

        FileRapidUploadVO vo = fileInfoService.rapidUpload(
                rapidRequest("copy.pdf", "d41d8cd98f00b204e9800998ecf8427e", 123L, "application/pdf"), UPLOADER_ID);

        assertFalse(vo.isHit());
        verify(fileAssetRepository, never()).saveWithQuotaCheck(any(FileAsset.class), anyLong());
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
                rapidRequest("copy.pdf", "d41d8cd98f00b204e9800998ecf8427e", 500L, null), UPLOADER_ID));

        assertEquals(PARAM_ERROR_CODE, ex.getCode());
        assertTrue(ex.getMessage().contains("超出个人存储配额"));
        verify(fileAssetRepository, never()).saveWithQuotaCheck(any(FileAsset.class), anyLong());
    }

    @Test
    @DisplayName("upload：配额不足时抛参数错误且不写存储")
    void shouldRejectUploadWhenQuotaExceeded() throws IOException {
        fileProperties.setQuotaBytes(1000L);
        when(fileAssetRepository.sumNormalSizeByUploader(UPLOADER_ID)).thenReturn(990L);
        UploadContent file = uploadContent(new byte[20], "big.bin");

        BizException ex = assertThrows(BizException.class, () -> fileInfoService.upload(file, null, UPLOADER_ID));

        assertEquals(PARAM_ERROR_CODE, ex.getCode());
        assertTrue(ex.getMessage().contains("超出个人存储配额"));
        verify(fileStorage, never()).store(any(), any(), any());
    }

    @Test
    @DisplayName("upload：配额为 0 表示不限，上传成功且不查询用量")
    void shouldUploadWhenQuotaUnlimited() throws IOException {
        fileProperties.setQuotaBytes(0L);
        UploadContent file = uploadContent("unlimited".getBytes(StandardCharsets.UTF_8), "a.txt");

        FileUploadVO vo = fileInfoService.upload(file, null, UPLOADER_ID);

        assertEquals("a.txt", vo.getOriginalName());
        verify(fileAssetRepository, never()).sumNormalSizeByUploader(anyLong());
    }

    @Test
    @DisplayName("upload：按权限码策略放开的 2GB / 8GB 上限可上传超过全局默认上限的文件")
    void shouldUploadWithPolicyLimits() throws IOException {
        fileProperties.setMaxSizeBytes(10L);
        fileProperties.setQuotaBytes(10L);
        UploadContent file = uploadContent(new byte[100], "movie.mp4");

        FileUploadVO vo = fileInfoService.upload(file, null, UPLOADER_ID,
                new UploadLimits(2147483648L, 8589934592L));

        assertEquals(100L, vo.getFileSize());
        ArgumentCaptor<FileAsset> captor = ArgumentCaptor.forClass(FileAsset.class);
        verify(fileAssetRepository).saveWithQuotaCheck(captor.capture(), eq(8589934592L));
        assertEquals("movie.mp4", captor.getValue().getOriginalName());
    }

    @Test
    @DisplayName("quota：按生效策略返回 8GB 配额与剩余量")
    void shouldReturnPolicyQuota() {
        when(fileAssetRepository.sumNormalSizeByUploader(UPLOADER_ID)).thenReturn(123L);

        FileQuotaVO vo = fileInfoService.quota(UPLOADER_ID, new UploadLimits(2147483648L, 8589934592L));

        assertEquals(8589934592L, vo.getQuotaBytes());
        assertEquals(123L, vo.getUsedBytes());
        assertEquals(8589934592L - 123L, vo.getRemainingBytes());
        assertFalse(vo.isUnlimited());
    }

    @Test
    @DisplayName("分片上传：持有视频策略可初始化 2GB 声明，未命中策略仍按默认 50MB 拒绝")
    void shouldApplyPolicyOnChunkInit() {
        ChunkUploadInitVO video = fileInfoService.initChunkUpload(
                initRequest("movie.mp4", 2147483648L), new UploadLimits(2147483648L, 8589934592L));

        assertNotNull(video.getUploadId());
        ChunkUploadMetadata recorded = chunkUploadStore.metadata(ChunkUploadSessionId.of(video.getUploadId()));
        assertEquals(2147483648L, recorded.maxSizeBytes());
        assertEquals(8589934592L, recorded.quotaBytes());
        assertTrue(recorded.hasRecordedLimits());

        BizException ex = assertThrows(BizException.class,
                () -> fileInfoService.initChunkUpload(initRequest("movie.mp4", 2147483648L)));

        assertEquals(PARAM_ERROR_CODE, ex.getCode());
        assertTrue(ex.getMessage().contains("超出上限"));
    }

    @Test
    @DisplayName("分片上传：init 登记上限后，complete 即使持有更大权限也不能放大上限")
    void shouldNotWidenLimitsBetweenInitAndComplete() throws IOException {
        // init 在较小上限（100 字节）下登记会话，随后客户端上传 12 个分片
        ChunkUploadInitVO initVo = fileInfoService.initChunkUpload(initRequest("video.bin", 40L),
                new UploadLimits(100L, 1000L));
        byte[] part = new byte[10];
        for (int i = 0; i < 12; i++) {
            fileInfoService.uploadChunkPart(initVo.getUploadId(), i, chunkPart(part));
        }

        // complete 以更大权限声明 120 字节：仍按 init 登记的 100 字节上限拒绝
        ChunkUploadCompleteRequest complete = new ChunkUploadCompleteRequest();
        complete.setUploadId(initVo.getUploadId());
        complete.setFileName("video.bin");
        complete.setSize(120L);
        BizException ex = assertThrows(BizException.class, () -> fileInfoService.completeChunkUpload(
                complete, UPLOADER_ID, new UploadLimits(2147483648L, 8589934592L)));

        assertEquals(PARAM_ERROR_CODE, ex.getCode());
        assertTrue(ex.getMessage().contains("超出上限"));
        verify(fileStorage, never()).store(any(), any(), any());
        verify(fileAssetRepository, never()).saveWithQuotaCheck(any(FileAsset.class), anyLong());
    }

    @Test
    @DisplayName("quota：返回已用量与剩余量")
    void shouldReturnQuotaUsage() {
        when(fileAssetRepository.sumNormalSizeByUploader(UPLOADER_ID)).thenReturn(123L);

        FileQuotaVO vo = fileInfoService.quota(UPLOADER_ID);

        assertEquals(123L, vo.getUsedBytes());
        assertEquals(fileProperties.getQuotaBytes(), vo.getQuotaBytes());
        assertEquals(fileProperties.getQuotaBytes() - 123L, vo.getRemainingBytes());
        assertFalse(vo.isUnlimited());
    }

    @Test
    @DisplayName("quota：无用户态时返回不限配额且不查库")
    void shouldReturnUnlimitedQuotaWithoutUser() {
        FileQuotaVO vo = fileInfoService.quota(null);

        assertTrue(vo.isUnlimited());
        assertEquals(0L, vo.getUsedBytes());
        assertEquals(0L, vo.getRemainingBytes());
        verify(fileAssetRepository, never()).sumNormalSizeByUploader(anyLong());
    }

    @Test
    @DisplayName("quota：配额配置为 0 时返回不限且不查库")
    void shouldReturnUnlimitedQuotaWhenConfiguredUnlimited() {
        fileProperties.setQuotaBytes(0L);

        FileQuotaVO vo = fileInfoService.quota(UPLOADER_ID);

        assertTrue(vo.isUnlimited());
        assertEquals(0L, vo.getQuotaBytes());
        verify(fileAssetRepository, never()).sumNormalSizeByUploader(anyLong());
    }

    @Test
    @DisplayName("quota：无记录时已用量返回 0")
    void shouldReturnZeroUsageWhenNoRecords() {
        when(fileAssetRepository.sumNormalSizeByUploader(UPLOADER_ID)).thenReturn(0L);

        FileQuotaVO vo = fileInfoService.quota(UPLOADER_ID);

        assertEquals(0L, vo.getUsedBytes());
        assertEquals(fileProperties.getQuotaBytes(), vo.getRemainingBytes());
        assertFalse(vo.isUnlimited());
    }

    @Test
    @DisplayName("upload：用量恰好达到配额上限时仍允许上传")
    void shouldAllowUploadAtQuotaBoundary() throws IOException {
        fileProperties.setQuotaBytes(1000L);
        when(fileAssetRepository.sumNormalSizeByUploader(UPLOADER_ID)).thenReturn(980L);
        UploadContent file = uploadContent(new byte[20], "edge.bin");

        FileUploadVO vo = fileInfoService.upload(file, null, UPLOADER_ID);

        assertEquals(20L, vo.getFileSize());
        verify(fileStorage).store(eq("bluewhale"), anyString(), any(InputStream.class));
    }

    @Test
    @DisplayName("lock：正常文件写入归档锁定状态")
    void shouldLockFile() {
        when(fileAssetRepository.findById(7L)).thenReturn(Optional.of(fileWithStatus(7L, FileStatus.NORMAL)));

        fileInfoService.lock(7L);

        ArgumentCaptor<FileAsset> captor = ArgumentCaptor.forClass(FileAsset.class);
        verify(fileAssetRepository).save(captor.capture());
        assertEquals(7L, captor.getValue().getId());
        assertEquals(FileStatus.LOCKED, captor.getValue().getStatus());
    }

    @Test
    @DisplayName("lock：已锁定文件幂等，不重复保存")
    void shouldIgnoreRelock() {
        when(fileAssetRepository.findById(7L)).thenReturn(Optional.of(fileWithStatus(7L, FileStatus.LOCKED)));

        fileInfoService.lock(7L);

        verify(fileAssetRepository, never()).save(any(FileAsset.class));
    }

    @Test
    @DisplayName("lock：禁用文件不可锁定")
    void shouldRejectLockDisabledFile() {
        when(fileAssetRepository.findById(7L)).thenReturn(Optional.of(fileWithStatus(7L, FileStatus.DISABLED)));

        BizException ex = assertThrows(BizException.class, () -> fileInfoService.lock(7L));

        assertEquals(PARAM_ERROR_CODE, ex.getCode());
        assertTrue(ex.getMessage().contains("已禁用"));
        verify(fileAssetRepository, never()).save(any(FileAsset.class));
    }

    @Test
    @DisplayName("unlock：锁定文件恢复正常；未锁定文件不产生保存")
    void shouldUnlockOnlyLockedFile() {
        when(fileAssetRepository.findById(7L)).thenReturn(Optional.of(fileWithStatus(7L, FileStatus.LOCKED)));
        fileInfoService.unlock(7L);
        ArgumentCaptor<FileAsset> captor = ArgumentCaptor.forClass(FileAsset.class);
        verify(fileAssetRepository).save(captor.capture());
        assertEquals(FileStatus.NORMAL, captor.getValue().getStatus());

        when(fileAssetRepository.findById(8L)).thenReturn(Optional.of(fileWithStatus(8L, FileStatus.NORMAL)));
        fileInfoService.unlock(8L);
        verify(fileAssetRepository, times(1)).save(any(FileAsset.class));
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
        FileAsset asset = FileAsset.reconstitute(7L, "locked.pdf", "locked.pdf", "bluewhale",
                "2026/10/24/locked.pdf", 12L, "application/pdf", null, FileStatus.LOCKED, null, false,
                LocalDateTime.of(2026, 10, 24, 9, 0), LocalDateTime.of(2026, 10, 24, 10, 0));
        when(fileAssetRepository.findChanges(any(), anyLong(), anyLong()))
                .thenReturn(new FileAssetPage(List.of(asset), 1L, 1L, 200L));

        PageResult<FileChangeVO> result = fileInfoService.changes(LocalDateTime.of(2026, 10, 24, 9, 0), 1L, 500L);

        assertEquals(1L, result.getTotal());
        assertEquals(Integer.valueOf(2), result.getRecords().get(0).getStatus());
        assertEquals(LocalDateTime.of(2026, 10, 24, 10, 0), result.getRecords().get(0).getUpdateTime());
        verify(fileAssetRepository).findChanges(any(), eq(1L), eq(200L));
    }

    @Test
    @DisplayName("getChangeDetail：已逻辑删除文件仍可查询并返回 deleted=1")
    void shouldReturnChangeDetailForDeletedFile() {
        FileAsset deleted = FileAsset.reconstitute(7L, "locked.pdf", "locked.pdf", "bluewhale",
                "2026/10/24/locked.pdf", 12L, "application/pdf", null, FileStatus.NORMAL, null, true,
                LocalDateTime.of(2026, 10, 24, 9, 0), LocalDateTime.of(2026, 10, 24, 10, 0));
        when(fileAssetRepository.findByIdIncludingDeleted(7L)).thenReturn(Optional.of(deleted));

        FileChangeVO vo = fileInfoService.getChangeDetail(7L);

        assertEquals(1, vo.getDeleted());
        assertEquals(Integer.valueOf(1), vo.getStatus());
        assertEquals(LocalDateTime.of(2026, 10, 24, 10, 0), vo.getUpdateTime());
    }

    @Test
    @DisplayName("upload：仓储原子配额校验失败时补偿删除已写入对象，避免孤儿文件")
    void shouldCompensateStoredObjectWhenPersistRejected() throws IOException {
        fileProperties.setQuotaBytes(1000L);
        when(fileAssetRepository.saveWithQuotaCheck(any(FileAsset.class), anyLong()))
                .thenThrow(new BizException(ErrorCode.PARAM_ERROR.getCode(), "超出个人存储配额"));
        UploadContent file = uploadContent("race-lost".getBytes(StandardCharsets.UTF_8), "race.txt");

        BizException ex = assertThrows(BizException.class, () -> fileInfoService.upload(file, null, UPLOADER_ID));

        assertEquals(PARAM_ERROR_CODE, ex.getCode());
        ArgumentCaptor<String> objectKey = ArgumentCaptor.forClass(String.class);
        verify(fileStorage).store(eq("bluewhale"), objectKey.capture(), any(InputStream.class));
        verify(fileStorage).delete("bluewhale", objectKey.getValue());
    }

    @Test
    @DisplayName("upload：补偿删除失败不掩盖原始配额异常")
    void shouldKeepOriginalErrorWhenCompensationFails() throws IOException {
        fileProperties.setQuotaBytes(1000L);
        when(fileAssetRepository.saveWithQuotaCheck(any(FileAsset.class), anyLong()))
                .thenThrow(new BizException(ErrorCode.PARAM_ERROR.getCode(), "超出个人存储配额"));
        doAnswer(invocation -> {
            throw new BizException(ErrorCode.SYSTEM_ERROR);
        }).when(fileStorage).delete(anyString(), anyString());
        UploadContent file = uploadContent("race-lost".getBytes(StandardCharsets.UTF_8), "race.txt");

        BizException ex = assertThrows(BizException.class, () -> fileInfoService.upload(file, null, UPLOADER_ID));

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
