package com.bluewhale.file.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.file.config.FileProperties;
import com.bluewhale.file.dto.FileQueryRequest;
import com.bluewhale.file.entity.FileInfo;
import com.bluewhale.file.mapper.FileInfoMapper;
import com.bluewhale.file.storage.FileStorage;
import com.bluewhale.file.vo.FileInfoVO;
import com.bluewhale.file.vo.FileUploadVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.springframework.util.DigestUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link FileInfoServiceImpl} 上传落元数据、超限拒绝、下载缺失与分页条件传递单元测试。
 *
 * @author BlueWhale
 */
class FileInfoServiceImplTest {

    private static final int PARAM_ERROR_CODE = 400;
    private static final int NOT_FOUND_CODE = 404;
    private static final long UPLOADER_ID = 9L;

    private final FileInfoMapper fileInfoMapper = mock(FileInfoMapper.class);

    private final FileStorage fileStorage = mock(FileStorage.class);

    private final FileProperties fileProperties = new FileProperties();

    private final FileInfoServiceImpl fileInfoService =
            new FileInfoServiceImpl(fileInfoMapper, fileStorage, fileProperties);

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
}
