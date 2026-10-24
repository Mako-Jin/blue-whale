package com.bluewhale.file.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.file.config.FileProperties;
import com.bluewhale.file.dto.ChunkUploadCompleteRequest;
import com.bluewhale.file.dto.ChunkUploadInitRequest;
import com.bluewhale.file.dto.FileQueryRequest;
import com.bluewhale.file.dto.FileRapidUploadRequest;
import com.bluewhale.file.entity.FileInfo;
import com.bluewhale.file.mapper.FileInfoMapper;
import com.bluewhale.file.storage.FileStorage;
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
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.springframework.util.DigestUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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

    @TempDir
    Path tempDir;

    private final FileInfoMapper fileInfoMapper = mock(FileInfoMapper.class);

    private final FileStorage fileStorage = mock(FileStorage.class);

    private final FileProperties fileProperties = new FileProperties();

    private final FileInfoServiceImpl fileInfoService =
            new FileInfoServiceImpl(fileInfoMapper, fileStorage, fileProperties);

    /**
     * 以临时目录作为存储根目录，并设置较小的分片大小便于构造多分片场景。
     */
    @BeforeEach
    void setUp() {
        fileProperties.getStorage().setRoot(tempDir.toString());
        fileProperties.setChunkSizeBytes(TEST_CHUNK_SIZE);
    }

    /**
     * 构造上传文件桩对象：内容、大小、原始名与 MIME 类型。
     *
     * @param content 文件内容
     * @param originalName 原始文件名
     * @return MultipartFile 桩对象
     * @throws IOException 桩设定期不抛出，仅为对齐 {@code getBytes} 声明
     */
    private static MultipartFile multipartFile(byte[] content, String originalName) throws IOException {
        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getSize()).thenReturn((long) content.length);
        when(file.getBytes()).thenReturn(content);
        when(file.getOriginalFilename()).thenReturn(originalName);
        when(file.getContentType()).thenReturn("application/pdf");
        return file;
    }

    /**
     * 构造分片上传桩对象：仅提供流式读取能力。
     *
     * @param content 分片内容
     * @return MultipartFile 桩对象
     * @throws IOException 桩设定期不抛出，仅为对齐 {@code getInputStream} 声明
     */
    private static MultipartFile chunkPart(byte[] content) throws IOException {
        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(content.length == 0);
        when(file.getSize()).thenReturn((long) content.length);
        when(file.getInputStream()).thenReturn(new ByteArrayInputStream(content));
        return file;
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
     * 构造命中的既有文件记录。
     *
     * @param size 文件字节数
     * @return 文件实体
     */
    private static FileInfo existingFile(long size) {
        FileInfo existing = new FileInfo();
        existing.setId(11L);
        existing.setFileName("stored.pdf");
        existing.setOriginalName("origin.pdf");
        existing.setBucket("bluewhale");
        existing.setObjectKey("2026/10/19/stored.pdf");
        existing.setFileSize(size);
        existing.setContentType("application/pdf");
        existing.setMd5("d41d8cd98f00b204e9800998ecf8427e");
        existing.setStatus(1);
        return existing;
    }

    @Test
    @DisplayName("upload：写入存储并登记元数据（默认桶 + MD5 + 日期目录对象键）")
    void shouldUploadAndPersistMetadata() throws IOException {
        byte[] content = "report-body".getBytes(StandardCharsets.UTF_8);
        MultipartFile file = multipartFile(content, "report.pdf");

        FileUploadVO vo = fileInfoService.upload(file, null, UPLOADER_ID);

        assertEquals("report.pdf", vo.getOriginalName());
        assertEquals("bluewhale", vo.getBucket());
        assertEquals((long) content.length, vo.getFileSize());
        assertEquals(DigestUtils.md5DigestAsHex(content), vo.getMd5());
        assertTrue(vo.getObjectKey().startsWith(LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd")) + "/"));
        assertTrue(vo.getObjectKey().endsWith(".pdf"));
        assertTrue(vo.getFileName().endsWith(".pdf"));
        verify(fileStorage).store(eq("bluewhale"), eq(vo.getObjectKey()), any(InputStream.class));
        ArgumentCaptor<FileInfo> captor = ArgumentCaptor.forClass(FileInfo.class);
        verify(fileInfoMapper).insert(captor.capture());
        assertEquals(UPLOADER_ID, captor.getValue().getUploaderId());
        assertEquals(1, captor.getValue().getStatus().intValue());
        assertEquals("bluewhale", captor.getValue().getBucket());
    }

    @Test
    @DisplayName("upload：超过最大字节数抛参数错误且不落库、不写存储")
    void shouldRejectOversizeUpload() throws IOException {
        fileProperties.setMaxSizeBytes(10L);
        MultipartFile file = multipartFile(new byte[100], "big.bin");

        BizException ex = assertThrows(BizException.class, () -> fileInfoService.upload(file, null, null));

        assertEquals(PARAM_ERROR_CODE, ex.getCode());
        verify(fileStorage, never()).store(any(), any(), any());
        verify(fileInfoMapper, never()).insert(any(FileInfo.class));
    }

    @Test
    @DisplayName("loadResource：文件不存在抛 NOT_FOUND")
    void shouldThrowWhenDownloadMissing() {
        when(fileInfoMapper.selectById(99L)).thenReturn(null);

        BizException ex = assertThrows(BizException.class, () -> fileInfoService.loadResource(99L));

        assertEquals(NOT_FOUND_CODE, ex.getCode());
        verify(fileStorage, never()).load(any(), any());
    }

    @Test
    @DisplayName("page：按条件调用分页查询并映射结果")
    void shouldPageWithConditions() {
        FileInfo entity = new FileInfo();
        entity.setId(1L);
        entity.setOriginalName("报告.pdf");
        entity.setBucket("bluewhale");
        entity.setObjectKey("2026/10/19/a.pdf");
        Page<FileInfo> page = new Page<>(1L, 10L);
        page.setRecords(List.of(entity));
        page.setTotal(1L);
        when(fileInfoMapper.selectPage(ArgumentMatchers.<Page<FileInfo>>any(),
                ArgumentMatchers.<Wrapper<FileInfo>>any())).thenReturn(page);

        FileQueryRequest request = new FileQueryRequest();
        request.setOriginalName("报告");
        request.setBucket("bluewhale");
        PageResult<FileInfoVO> result = fileInfoService.page(request);

        assertEquals(1L, result.getTotal());
        assertEquals(1, result.getRecords().size());
        assertEquals("报告.pdf", result.getRecords().get(0).getOriginalName());
        assertNotNull(result.getRecords().get(0).getObjectKey());
        verify(fileInfoMapper).selectPage(ArgumentMatchers.<Page<FileInfo>>any(),
                ArgumentMatchers.<Wrapper<FileInfo>>any());
    }

    @Test
    @DisplayName("presignedUrl：默认 600 秒并回传存储生成的 URL")
    void shouldPresignWithDefaultExpiry() {
        FileInfo entity = new FileInfo();
        entity.setId(5L);
        entity.setBucket("bluewhale");
        entity.setObjectKey("2026/10/19/a.txt");
        when(fileInfoMapper.selectById(5L)).thenReturn(entity);
        when(fileStorage.presignedDownloadUrl("bluewhale", "2026/10/19/a.txt", 600))
                .thenReturn("http://127.0.0.1:9000/bluewhale/2026/10/19/a.txt?sign=x");

        FilePresignedVO vo = fileInfoService.presignedUrl(5L, null);

        assertEquals("http://127.0.0.1:9000/bluewhale/2026/10/19/a.txt?sign=x", vo.getUrl());
        assertEquals(Integer.valueOf(600), vo.getExpiresSeconds());
    }

    @Test
    @DisplayName("presignedUrl：有效期超过 3600 秒时截断为 3600")
    void shouldClampPresignExpiry() {
        FileInfo entity = new FileInfo();
        entity.setId(5L);
        entity.setBucket("bluewhale");
        entity.setObjectKey("2026/10/19/a.txt");
        when(fileInfoMapper.selectById(5L)).thenReturn(entity);
        when(fileStorage.presignedDownloadUrl("bluewhale", "2026/10/19/a.txt", 3600))
                .thenReturn("http://x");

        FilePresignedVO vo = fileInfoService.presignedUrl(5L, 9999);

        assertEquals(Integer.valueOf(3600), vo.getExpiresSeconds());
        verify(fileStorage).presignedDownloadUrl("bluewhale", "2026/10/19/a.txt", 3600);
    }

    @Test
    @DisplayName("presignedUrl：local 存储不支持预签名，透传参数错误")
    void shouldRejectPresignWhenStorageUnsupported() {
        FileInfo entity = new FileInfo();
        entity.setId(5L);
        entity.setBucket("bluewhale");
        entity.setObjectKey("2026/10/19/a.txt");
        when(fileInfoMapper.selectById(5L)).thenReturn(entity);
        when(fileStorage.presignedDownloadUrl(anyString(), anyString(), anyInt()))
                .thenThrow(new BizException(ErrorCode.PARAM_ERROR.getCode(), "当前存储插件不支持预签名下载"));

        BizException ex = assertThrows(BizException.class, () -> fileInfoService.presignedUrl(5L, 600));

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
        ArgumentCaptor<FileInfo> captor = ArgumentCaptor.forClass(FileInfo.class);
        verify(fileInfoMapper).insert(captor.capture());
        assertEquals(Long.valueOf(12L), captor.getValue().getFileSize());
        assertEquals(UPLOADER_ID, captor.getValue().getUploaderId());
        assertFalse(Files.exists(tempDir.resolve("tmp").resolve(initVo.getUploadId())),
                "合并成功后应清理临时目录");
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
        verify(fileInfoMapper, never()).insert(any(FileInfo.class));
        assertTrue(Files.exists(tempDir.resolve("tmp").resolve(initVo.getUploadId())),
                "失败时保留分片供续传");
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
        verify(fileInfoMapper, never()).insert(any(FileInfo.class));
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
        when(fileInfoMapper.selectOne(ArgumentMatchers.<Wrapper<FileInfo>>any())).thenReturn(existingFile(123L));

        FileRapidUploadVO vo = fileInfoService.rapidUpload(
                rapidRequest("copy.pdf", "D41D8CD98F00B204E9800998ECF8427E", 123L, null), UPLOADER_ID);

        assertTrue(vo.isHit());
        assertNotNull(vo.getFile());
        assertEquals("bluewhale", vo.getFile().getBucket());
        assertEquals("2026/10/19/stored.pdf", vo.getFile().getObjectKey());
        assertEquals("copy.pdf", vo.getFile().getOriginalName());
        assertEquals("application/pdf", vo.getFile().getContentType());
        assertEquals(Long.valueOf(123L), vo.getFile().getFileSize());
        ArgumentCaptor<FileInfo> captor = ArgumentCaptor.forClass(FileInfo.class);
        verify(fileInfoMapper).insert(captor.capture());
        assertEquals("2026/10/19/stored.pdf", captor.getValue().getObjectKey());
        assertEquals("d41d8cd98f00b204e9800998ecf8427e", captor.getValue().getMd5());
        assertEquals(UPLOADER_ID, captor.getValue().getUploaderId());
        verify(fileStorage, never()).store(any(), any(), any());
    }

    @Test
    @DisplayName("rapidUpload：未命中时返回 hit=false 且不落库、不占配额")
    void shouldRapidUploadMissWithoutPersisting() {
        when(fileInfoMapper.selectOne(ArgumentMatchers.<Wrapper<FileInfo>>any())).thenReturn(null);

        FileRapidUploadVO vo = fileInfoService.rapidUpload(
                rapidRequest("copy.pdf", "d41d8cd98f00b204e9800998ecf8427e", 123L, "application/pdf"), UPLOADER_ID);

        assertFalse(vo.isHit());
        verify(fileInfoMapper, never()).insert(any(FileInfo.class));
        verify(fileInfoMapper, never()).selectObjs(ArgumentMatchers.<Wrapper<FileInfo>>any());
        verify(fileStorage, never()).store(any(), any(), any());
    }

    @Test
    @DisplayName("rapidUpload：命中但配额不足时抛参数错误且不落库")
    void shouldRejectRapidUploadWhenQuotaExceeded() {
        fileProperties.setQuotaBytes(1000L);
        when(fileInfoMapper.selectOne(ArgumentMatchers.<Wrapper<FileInfo>>any())).thenReturn(existingFile(500L));
        when(fileInfoMapper.selectObjs(ArgumentMatchers.<Wrapper<FileInfo>>any())).thenReturn(List.of(800L));

        BizException ex = assertThrows(BizException.class, () -> fileInfoService.rapidUpload(
                rapidRequest("copy.pdf", "d41d8cd98f00b204e9800998ecf8427e", 500L, null), UPLOADER_ID));

        assertEquals(PARAM_ERROR_CODE, ex.getCode());
        assertTrue(ex.getMessage().contains("超出个人存储配额"));
        verify(fileInfoMapper, never()).insert(any(FileInfo.class));
    }

    @Test
    @DisplayName("upload：配额不足时抛参数错误且不写存储")
    void shouldRejectUploadWhenQuotaExceeded() throws IOException {
        fileProperties.setQuotaBytes(1000L);
        when(fileInfoMapper.selectObjs(ArgumentMatchers.<Wrapper<FileInfo>>any())).thenReturn(List.of(990L));
        MultipartFile file = multipartFile(new byte[20], "big.bin");

        BizException ex = assertThrows(BizException.class, () -> fileInfoService.upload(file, null, UPLOADER_ID));

        assertEquals(PARAM_ERROR_CODE, ex.getCode());
        assertTrue(ex.getMessage().contains("超出个人存储配额"));
        verify(fileStorage, never()).store(any(), any(), any());
    }

    @Test
    @DisplayName("upload：配额为 0 表示不限，上传成功且不查询用量")
    void shouldUploadWhenQuotaUnlimited() throws IOException {
        fileProperties.setQuotaBytes(0L);
        MultipartFile file = multipartFile("unlimited".getBytes(StandardCharsets.UTF_8), "a.txt");

        FileUploadVO vo = fileInfoService.upload(file, null, UPLOADER_ID);

        assertEquals("a.txt", vo.getOriginalName());
        verify(fileInfoMapper, never()).selectObjs(ArgumentMatchers.<Wrapper<FileInfo>>any());
    }

    @Test
    @DisplayName("quota：返回已用量与剩余量")
    void shouldReturnQuotaUsage() {
        when(fileInfoMapper.selectObjs(ArgumentMatchers.<Wrapper<FileInfo>>any())).thenReturn(List.of(123L));

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
        verify(fileInfoMapper, never()).selectObjs(ArgumentMatchers.<Wrapper<FileInfo>>any());
    }

    @Test
    @DisplayName("quota：配额配置为 0 时返回不限且不查库")
    void shouldReturnUnlimitedQuotaWhenConfiguredUnlimited() {
        fileProperties.setQuotaBytes(0L);

        FileQuotaVO vo = fileInfoService.quota(UPLOADER_ID);

        assertTrue(vo.isUnlimited());
        assertEquals(0L, vo.getQuotaBytes());
        verify(fileInfoMapper, never()).selectObjs(ArgumentMatchers.<Wrapper<FileInfo>>any());
    }

    @Test
    @DisplayName("quota：聚合结果为空时已用量返回 0")
    void shouldReturnZeroUsageWhenNoRecords() {
        when(fileInfoMapper.selectObjs(ArgumentMatchers.<Wrapper<FileInfo>>any())).thenReturn(List.of());

        FileQuotaVO vo = fileInfoService.quota(UPLOADER_ID);

        assertEquals(0L, vo.getUsedBytes());
        assertEquals(fileProperties.getQuotaBytes(), vo.getRemainingBytes());
        assertFalse(vo.isUnlimited());
    }

    @Test
    @DisplayName("quota：聚合结果兼容字符串类型")
    void shouldParseStringUsage() {
        when(fileInfoMapper.selectObjs(ArgumentMatchers.<Wrapper<FileInfo>>any())).thenReturn(List.of("456"));

        FileQuotaVO vo = fileInfoService.quota(UPLOADER_ID);

        assertEquals(456L, vo.getUsedBytes());
        assertEquals(fileProperties.getQuotaBytes() - 456L, vo.getRemainingBytes());
    }

    @Test
    @DisplayName("quota：聚合查询异常时抛系统错误")
    void shouldThrowSystemErrorWhenUsageQueryFails() {
        when(fileInfoMapper.selectObjs(ArgumentMatchers.<Wrapper<FileInfo>>any()))
                .thenThrow(new IllegalStateException("db down"));

        BizException ex = assertThrows(BizException.class, () -> fileInfoService.quota(UPLOADER_ID));

        assertEquals(ErrorCode.SYSTEM_ERROR.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("upload：用量恰好达到配额上限时仍允许上传")
    void shouldAllowUploadAtQuotaBoundary() throws IOException {
        fileProperties.setQuotaBytes(1000L);
        when(fileInfoMapper.selectObjs(ArgumentMatchers.<Wrapper<FileInfo>>any())).thenReturn(List.of(980L));
        MultipartFile file = multipartFile(new byte[20], "edge.bin");

        FileUploadVO vo = fileInfoService.upload(file, null, UPLOADER_ID);

        assertEquals(20L, vo.getFileSize());
        verify(fileStorage).store(eq("bluewhale"), anyString(), any(InputStream.class));
    }

    /**
     * 构造指定状态的文件实体。
     *
     * @param id 主键
     * @param status 文件状态
     * @return 文件实体
     */
    private static FileInfo fileWithStatus(long id, int status) {
        FileInfo entity = new FileInfo();
        entity.setId(id);
        entity.setOriginalName("locked.pdf");
        entity.setBucket("bluewhale");
        entity.setObjectKey("2026/10/24/locked.pdf");
        entity.setFileSize(12L);
        entity.setStatus(status);
        return entity;
    }

    @Test
    @DisplayName("lock：正常文件写入 status=2（归档锁定）")
    void shouldLockFile() {
        when(fileInfoMapper.selectById(7L)).thenReturn(fileWithStatus(7L, 1));

        fileInfoService.lock(7L);

        ArgumentCaptor<FileInfo> captor = ArgumentCaptor.forClass(FileInfo.class);
        verify(fileInfoMapper).updateById(captor.capture());
        assertEquals(7L, captor.getValue().getId());
        assertEquals(Integer.valueOf(2), captor.getValue().getStatus());
    }

    @Test
    @DisplayName("lock：已锁定文件幂等，不重复更新")
    void shouldIgnoreRelock() {
        when(fileInfoMapper.selectById(7L)).thenReturn(fileWithStatus(7L, 2));

        fileInfoService.lock(7L);

        verify(fileInfoMapper, never()).updateById(any(FileInfo.class));
    }

    @Test
    @DisplayName("lock：禁用文件不可锁定")
    void shouldRejectLockDisabledFile() {
        when(fileInfoMapper.selectById(7L)).thenReturn(fileWithStatus(7L, 0));

        BizException ex = assertThrows(BizException.class, () -> fileInfoService.lock(7L));

        assertEquals(PARAM_ERROR_CODE, ex.getCode());
        assertTrue(ex.getMessage().contains("已禁用"));
        verify(fileInfoMapper, never()).updateById(any(FileInfo.class));
    }

    @Test
    @DisplayName("unlock：锁定文件恢复 status=1；未锁定文件不产生更新")
    void shouldUnlockOnlyLockedFile() {
        when(fileInfoMapper.selectById(7L)).thenReturn(fileWithStatus(7L, 2));
        fileInfoService.unlock(7L);
        ArgumentCaptor<FileInfo> captor = ArgumentCaptor.forClass(FileInfo.class);
        verify(fileInfoMapper).updateById(captor.capture());
        assertEquals(Integer.valueOf(1), captor.getValue().getStatus());

        when(fileInfoMapper.selectById(8L)).thenReturn(fileWithStatus(8L, 1));
        fileInfoService.unlock(8L);
        verify(fileInfoMapper, org.mockito.Mockito.times(1)).updateById(any(FileInfo.class));
    }

    @Test
    @DisplayName("delete：归档锁定文件禁止删除")
    void shouldRejectDeletingLockedFile() {
        when(fileInfoMapper.selectById(7L)).thenReturn(fileWithStatus(7L, 2));

        BizException ex = assertThrows(BizException.class, () -> fileInfoService.delete(7L));

        assertEquals(PARAM_ERROR_CODE, ex.getCode());
        assertTrue(ex.getMessage().contains("禁止删除"));
        verify(fileInfoMapper, never()).deleteById(any(Long.class));
    }

    @Test
    @DisplayName("changes：按更新时间游标分页并回传状态与更新时间")
    void shouldListChangesWithCursor() {
        FileInfo entity = fileWithStatus(7L, 2);
        entity.setUpdateTime(LocalDateTime.of(2026, 10, 24, 10, 0));
        Page<FileInfo> page = new Page<>(1L, 100L);
        page.setRecords(List.of(entity));
        page.setTotal(1L);
        when(fileInfoMapper.selectPage(ArgumentMatchers.<Page<FileInfo>>any(),
                ArgumentMatchers.<Wrapper<FileInfo>>any())).thenReturn(page);

        PageResult<FileChangeVO> result = fileInfoService.changes(LocalDateTime.of(2026, 10, 24, 9, 0), 1L, 500L);

        assertEquals(1L, result.getTotal());
        assertEquals(Integer.valueOf(2), result.getRecords().get(0).getStatus());
        assertEquals(LocalDateTime.of(2026, 10, 24, 10, 0), result.getRecords().get(0).getUpdateTime());
    }
}
