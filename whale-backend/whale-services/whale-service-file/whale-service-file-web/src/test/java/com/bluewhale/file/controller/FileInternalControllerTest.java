package com.bluewhale.file.controller;

import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.core.handler.GlobalExceptionHandler;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.file.assemble.FileAssembler;
import com.bluewhale.file.config.FileProperties;
import com.bluewhale.file.model.command.UploadFileCommand;
import com.bluewhale.file.model.query.FileChangesQuery;
import com.bluewhale.file.model.result.FileChangeResult;
import com.bluewhale.file.model.result.FileInfoResult;
import com.bluewhale.file.model.result.FileUploadResult;
import com.bluewhale.file.service.FileInfoService;
import com.bluewhale.file.service.support.FileUploadPolicy;
import com.bluewhale.file.service.support.UploadPolicyRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.accept.ApiVersionStrategy;
import org.springframework.web.accept.DefaultApiVersionStrategy;
import org.springframework.web.accept.PathApiVersionResolver;
import org.springframework.web.accept.SemanticApiVersionParser;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@link FileInternalController} 内网接口单元测试（standalone MockMvc）。
 *
 * <p>覆盖内网上传（默认上限）、变更流（含 since 缺省）、变更详情、内容下载（含 416 越界）、
 * 归档锁定/解锁与下载文件名/MIME 类型回落分支。内网来源令牌由全局过滤器统一校验，
 * 不在本控制器单测内覆盖。</p>
 *
 * @author BlueWhale
 */
class FileInternalControllerTest {

    private static final long FILE_ID = 7L;

    private final FileInfoService fileInfoService = mock(FileInfoService.class);

    private final FileProperties fileProperties = new FileProperties();

    private final FileUploadPolicy fileUploadPolicy =
            new FileUploadPolicy(fileProperties, new UploadPolicyRegistry(fileProperties));

    private final FileAssembler fileAssembler = Mappers.getMapper(FileAssembler.class);

    private final FileInternalController controller =
            new FileInternalController(fileInfoService, fileUploadPolicy, fileAssembler);

    private MockMvc mockMvc;

    /**
     * 构建 standalone MockMvc：内网接口不装配权限拦截器
     */
    @BeforeEach
    void setUp() {
        ApiVersionStrategy versionStrategy = new DefaultApiVersionStrategy(
                List.of(new PathApiVersionResolver(1, requestPath -> true)),
                new SemanticApiVersionParser(), false, null, true, null, null);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setApiVersionStrategy(versionStrategy)
                .build();
    }

    /**
     * 构造文件变更结果。
     *
     * @param status 文件状态
     * @return 文件变更结果
     */
    private static FileChangeResult change(int status) {
        return new FileChangeResult(FILE_ID, "stored.pdf", "sample.pdf", "bluewhale",
                "2026/10/19/stored.pdf", 5L, "application/pdf", null, status, 9L, 0,
                LocalDateTime.of(2026, 1, 1, 0, 0), LocalDateTime.of(2026, 1, 2, 3, 4, 5));
    }

    /**
     * 构造文件详情结果。
     *
     * @param originalName 原始文件名
     * @param contentType MIME 类型
     * @return 文件详情结果
     */
    private static FileInfoResult fileInfo(String originalName, String contentType) {
        return new FileInfoResult(FILE_ID, "stored.pdf", originalName, "bluewhale",
                "2026/10/19/stored.pdf", 5L, contentType, null, 9L, null);
    }

    @Test
    @DisplayName("GET /file/v1/internal/changes：按游标返回变更分页")
    void changesShouldReturnPage() throws Exception {
        LocalDateTime since = LocalDateTime.of(2026, 1, 1, 0, 0);
        when(fileInfoService.changes(new FileChangesQuery(since, 2L, 10L)))
                .thenReturn(PageResult.of(List.of(change(1)), 1L, 2L, 10L));

        mockMvc.perform(get("/file/v1/internal/changes")
                        .param("since", "2026-01-01T00:00:00")
                        .param("current", "2")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCode.SUCCESS.getCode()))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].id").value(FILE_ID));

        verify(fileInfoService).changes(new FileChangesQuery(since, 2L, 10L));
    }

    @Test
    @DisplayName("GET /file/v1/internal/{id}：返回变更视图详情")
    void detailShouldReturnChange() throws Exception {
        when(fileInfoService.getChangeDetail(FILE_ID)).thenReturn(change(2));

        mockMvc.perform(get("/file/v1/internal/{id}", FILE_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(FILE_ID))
                .andExpect(jsonPath("$.data.status").value(2))
                .andExpect(jsonPath("$.data.deleted").value(0));
    }

    @Test
    @DisplayName("GET /file/v1/internal/{id}/content：附件下载并带 nosniff")
    void contentShouldBeAttachment() throws Exception {
        when(fileInfoService.getDetail(FILE_ID, null, true)).thenReturn(fileInfo("sample.pdf", "application/pdf"));
        when(fileInfoService.openContent(FILE_ID, null, true))
                .thenReturn(new ByteArrayInputStream("hello".getBytes(StandardCharsets.UTF_8)));

        mockMvc.perform(get("/file/v1/internal/{id}/content", FILE_ID))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("attachment")))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("sample.pdf")))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, containsString("application/pdf")))
                .andExpect(header().longValue(HttpHeaders.CONTENT_LENGTH, 5L));
    }

    @Test
    @DisplayName("GET /file/v1/internal/{id}/content：文件名与 MIME 类型缺失时回落")
    void contentShouldFallbackFilenameAndMediaType() throws Exception {
        when(fileInfoService.getDetail(FILE_ID, null, true)).thenReturn(fileInfo(null, "not-a-media-type"));
        when(fileInfoService.openContent(FILE_ID, null, true))
                .thenReturn(new ByteArrayInputStream("hello".getBytes(StandardCharsets.UTF_8)));

        mockMvc.perform(get("/file/v1/internal/{id}/content", FILE_ID))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("download")))
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, containsString("application/octet-stream")));
    }

    @Test
    @DisplayName("GET /file/v1/internal/{id}/content：Range 命中返回 206 与 Content-Range")
    void contentWithRangeShouldReturnPartialContent() throws Exception {
        when(fileInfoService.getDetail(FILE_ID, null, true)).thenReturn(fileInfo("sample.mp3", "audio/mpeg"));
        when(fileInfoService.openContent(FILE_ID, null, true, 1L, 2L))
                .thenReturn(new ByteArrayInputStream("el".getBytes(StandardCharsets.UTF_8)));

        mockMvc.perform(get("/file/v1/internal/{id}/content", FILE_ID)
                        .header(HttpHeaders.RANGE, "bytes=1-2"))
                .andExpect(status().isPartialContent())
                .andExpect(header().string(HttpHeaders.ACCEPT_RANGES, "bytes"))
                .andExpect(header().string(HttpHeaders.CONTENT_RANGE, "bytes 1-2/5"))
                .andExpect(header().longValue(HttpHeaders.CONTENT_LENGTH, 2L));

        verify(fileInfoService).openContent(FILE_ID, null, true, 1L, 2L);
    }

    @Test
    @DisplayName("GET /file/v1/internal/changes：since 缺省时以 null 游标查询")
    void changesShouldUseNullCursorWhenSinceMissing() throws Exception {
        when(fileInfoService.changes(new FileChangesQuery(null, 1L, 100L)))
                .thenReturn(PageResult.of(List.of(change(1)), 1L, 1L, 100L));

        mockMvc.perform(get("/file/v1/internal/changes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCode.SUCCESS.getCode()))
                .andExpect(jsonPath("$.data.total").value(1));

        verify(fileInfoService).changes(new FileChangesQuery(null, 1L, 100L));
    }

    @Test
    @DisplayName("GET /file/v1/internal/{id}/content：Range 越界返回 416 且不读取内容")
    void contentWithOutOfRangeShouldReturn416() throws Exception {
        when(fileInfoService.getDetail(FILE_ID, null, true)).thenReturn(fileInfo("sample.mp3", "audio/mpeg"));

        mockMvc.perform(get("/file/v1/internal/{id}/content", FILE_ID)
                        .header(HttpHeaders.RANGE, "bytes=5-9"))
                .andExpect(status().isRequestedRangeNotSatisfiable())
                .andExpect(header().string(HttpHeaders.CONTENT_RANGE, "bytes */5"));

        verify(fileInfoService, never()).openContent(FILE_ID, null, true);
        verify(fileInfoService, never()).openContent(eq(FILE_ID), eq(null), eq(true), anyLong(), anyLong());
    }

    @Test
    @DisplayName("POST /file/v1/internal/upload：按全局默认上限登记并返回上传结果")
    void internalUploadShouldUseDefaultLimits() throws Exception {
        when(fileInfoService.upload(any(UploadFileCommand.class)))
                .thenReturn(new FileUploadResult(9L, "stored.txt", "a.txt", "bluewhale",
                        "2026/11/13/stored.txt", 3L, "text/plain", null));

        mockMvc.perform(multipart("/file/v1/internal/upload")
                        .file(new MockMultipartFile("file", "a.txt", "text/plain",
                                "abc".getBytes(StandardCharsets.UTF_8))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCode.SUCCESS.getCode()))
                .andExpect(jsonPath("$.data.originalName").value("a.txt"));

        verify(fileInfoService).upload(any(UploadFileCommand.class));
    }

    @Test
    @DisplayName("POST /file/v1/internal/{id}/lock：调用归档锁定")
    void lockShouldDelegate() throws Exception {
        mockMvc.perform(post("/file/v1/internal/{id}/lock", FILE_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCode.SUCCESS.getCode()));

        verify(fileInfoService).lock(eq(FILE_ID));
    }

    @Test
    @DisplayName("POST /file/v1/internal/{id}/unlock：调用归档解锁")
    void unlockShouldDelegate() throws Exception {
        mockMvc.perform(post("/file/v1/internal/{id}/unlock", FILE_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCode.SUCCESS.getCode()));

        verify(fileInfoService).unlock(eq(FILE_ID));
    }
}
