package com.bluewhale.file.controller;

import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.file.service.FileInfoService;
import com.bluewhale.file.vo.FileChangeVO;
import com.bluewhale.file.vo.FileInfoVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@link FileInternalController} 内网接口单元测试（standalone MockMvc）。
 *
 * <p>覆盖变更流、变更详情、内容下载、归档锁定/解锁及下载文件名与 MIME 类型回落分支。</p>
 *
 * @author BlueWhale
 */
class FileInternalControllerTest {

    private static final long FILE_ID = 7L;

    private final FileInfoService fileInfoService = mock(FileInfoService.class);

    private final FileInternalController controller = new FileInternalController(fileInfoService);

    private MockMvc mockMvc;

    /**
     * 构建 standalone MockMvc：内网接口不装配权限拦截器
     */
    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    /**
     * 构造文件变更视图对象。
     *
     * @param status 文件状态
     * @return 文件变更视图对象
     */
    private static FileChangeVO change(int status) {
        FileChangeVO change = new FileChangeVO();
        change.setId(FILE_ID);
        change.setOriginalName("sample.pdf");
        change.setStatus(status);
        change.setDeleted(0);
        change.setUpdateTime(LocalDateTime.of(2026, 1, 2, 3, 4, 5));
        return change;
    }

    /**
     * 构造文件详情视图对象。
     *
     * @param originalName 原始文件名
     * @param contentType MIME 类型
     * @return 文件详情视图对象
     */
    private static FileInfoVO fileInfo(String originalName, String contentType) {
        FileInfoVO info = new FileInfoVO();
        info.setId(FILE_ID);
        info.setOriginalName(originalName);
        info.setContentType(contentType);
        info.setFileSize(5L);
        return info;
    }

    @Test
    @DisplayName("GET /file/internal/changes：按游标返回变更分页")
    void changesShouldReturnPage() throws Exception {
        LocalDateTime since = LocalDateTime.of(2026, 1, 1, 0, 0);
        when(fileInfoService.changes(since, 2L, 10L))
                .thenReturn(PageResult.of(List.of(change(1)), 1L, 2L, 10L));

        mockMvc.perform(get("/file/internal/changes")
                        .param("since", "2026-01-01T00:00:00")
                        .param("current", "2")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCode.SUCCESS.getCode()))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].id").value(FILE_ID));

        verify(fileInfoService).changes(since, 2L, 10L);
    }

    @Test
    @DisplayName("GET /file/internal/{id}：返回变更视图详情")
    void detailShouldReturnChange() throws Exception {
        when(fileInfoService.getChangeDetail(FILE_ID)).thenReturn(change(2));

        mockMvc.perform(get("/file/internal/{id}", FILE_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(FILE_ID))
                .andExpect(jsonPath("$.data.status").value(2))
                .andExpect(jsonPath("$.data.deleted").value(0));
    }

    @Test
    @DisplayName("GET /file/internal/{id}/content：附件下载并带 nosniff")
    void contentShouldBeAttachment() throws Exception {
        when(fileInfoService.getDetail(FILE_ID, null, true)).thenReturn(fileInfo("sample.pdf", "application/pdf"));
        when(fileInfoService.openContent(FILE_ID, null, true))
                .thenReturn(new ByteArrayInputStream("hello".getBytes(StandardCharsets.UTF_8)));

        mockMvc.perform(get("/file/internal/{id}/content", FILE_ID))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("attachment")))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("sample.pdf")))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, containsString("application/pdf")))
                .andExpect(header().longValue(HttpHeaders.CONTENT_LENGTH, 5L));
    }

    @Test
    @DisplayName("GET /file/internal/{id}/content：文件名与 MIME 类型缺失时回落")
    void contentShouldFallbackFilenameAndMediaType() throws Exception {
        when(fileInfoService.getDetail(FILE_ID, null, true)).thenReturn(fileInfo(null, "not-a-media-type"));
        when(fileInfoService.openContent(FILE_ID, null, true))
                .thenReturn(new ByteArrayInputStream("hello".getBytes(StandardCharsets.UTF_8)));

        mockMvc.perform(get("/file/internal/{id}/content", FILE_ID))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("download")))
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, containsString("application/octet-stream")));
    }

    @Test
    @DisplayName("GET /file/internal/{id}/content：Range 命中返回 206 与 Content-Range")
    void contentWithRangeShouldReturnPartialContent() throws Exception {
        when(fileInfoService.getDetail(FILE_ID, null, true)).thenReturn(fileInfo("sample.mp3", "audio/mpeg"));
        when(fileInfoService.openContent(FILE_ID, null, true, 1L, 2L))
                .thenReturn(new ByteArrayInputStream("el".getBytes(StandardCharsets.UTF_8)));

        mockMvc.perform(get("/file/internal/{id}/content", FILE_ID)
                        .header(HttpHeaders.RANGE, "bytes=1-2"))
                .andExpect(status().isPartialContent())
                .andExpect(header().string(HttpHeaders.ACCEPT_RANGES, "bytes"))
                .andExpect(header().string(HttpHeaders.CONTENT_RANGE, "bytes 1-2/5"))
                .andExpect(header().longValue(HttpHeaders.CONTENT_LENGTH, 2L));

        verify(fileInfoService).openContent(FILE_ID, null, true, 1L, 2L);
    }

    @Test
    @DisplayName("POST /file/internal/{id}/lock：调用归档锁定")
    void lockShouldDelegate() throws Exception {
        mockMvc.perform(post("/file/internal/{id}/lock", FILE_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCode.SUCCESS.getCode()));

        verify(fileInfoService).lock(eq(FILE_ID));
    }

    @Test
    @DisplayName("POST /file/internal/{id}/unlock：调用归档解锁")
    void unlockShouldDelegate() throws Exception {
        mockMvc.perform(post("/file/internal/{id}/unlock", FILE_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCode.SUCCESS.getCode()));

        verify(fileInfoService).unlock(eq(FILE_ID));
    }
}
