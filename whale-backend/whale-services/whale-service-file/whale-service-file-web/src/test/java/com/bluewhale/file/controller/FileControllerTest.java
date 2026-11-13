package com.bluewhale.file.controller;

import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.core.handler.GlobalExceptionHandler;
import com.bluewhale.common.web.WebHeaders;
import com.bluewhale.file.assemble.FileAssembler;
import com.bluewhale.file.config.FileProperties;
import com.bluewhale.file.model.UploadLimits;
import com.bluewhale.file.model.command.RapidUploadCommand;
import com.bluewhale.file.model.command.UploadFileCommand;
import com.bluewhale.file.model.query.FilePageQuery;
import com.bluewhale.file.model.result.FileInfoResult;
import com.bluewhale.file.model.result.FileQuotaResult;
import com.bluewhale.file.model.result.FileRapidUploadResult;
import com.bluewhale.file.model.result.FileUploadResult;
import com.bluewhale.file.service.FileInfoService;
import com.bluewhale.file.service.support.FileUploadPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.accept.ApiVersionStrategy;
import org.springframework.web.accept.DefaultApiVersionStrategy;
import org.springframework.web.accept.PathApiVersionResolver;
import org.springframework.web.accept.SemanticApiVersionParser;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
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
 * {@link FileController} 控制器层单元测试（standalone MockMvc）。
 *
 * <p>覆盖在线预览的 {@code Content-Disposition} 分流（含 SVG 与 text/html 拒绝内联）、
 * 普通上传全量读入预检、秒传接口响应、配额查询响应、分页查询的 Request→Query 转换，
 * 以及 G1 上传策略的默认上限口径。</p>
 *
 * @author BlueWhale
 */
class FileControllerTest {

    private static final long FILE_ID = 7L;
    private static final long UPLOADER_ID = 9L;
    private static final String MD5 = "d41d8cd98f00b204e9800998ecf8427e";

    private final FileInfoService fileInfoService = mock(FileInfoService.class);

    private final FileProperties fileProperties = new FileProperties();

    private final FileUploadPolicy fileUploadPolicy = new FileUploadPolicy(fileProperties);

    private final FileAssembler fileAssembler = Mappers.getMapper(FileAssembler.class);

    private final FileController controller =
            new FileController(fileInfoService, fileUploadPolicy, fileAssembler);

    private MockMvc mockMvc;

    /**
     * 构建 standalone MockMvc：仅注册控制器、校验器与全局异常处理器，不装配权限拦截器
     */
    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        ApiVersionStrategy versionStrategy = new DefaultApiVersionStrategy(
                List.of(new PathApiVersionResolver(1, requestPath -> true)),
                new SemanticApiVersionParser(), false, null, true, null, null);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setValidator(validator)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setApiVersionStrategy(versionStrategy)
                .build();
    }

    /**
     * 构造文件详情结果
     *
     * @param contentType MIME 类型
     * @return 文件详情结果
     */
    private static FileInfoResult fileInfo(String contentType) {
        return new FileInfoResult(FILE_ID, "stored.pdf", "sample.pdf", "bluewhale",
                "2026/10/19/stored.pdf", 5L, contentType, null, UPLOADER_ID, null);
    }

    @Test
    @DisplayName("GET /file/v1/{id}/preview：PDF 以 inline 呈现并带 nosniff")
    void previewPdfShouldBeInline() throws Exception {
        when(fileInfoService.getDetail(FILE_ID, UPLOADER_ID, false)).thenReturn(fileInfo("application/pdf"));
        when(fileInfoService.openContent(FILE_ID, UPLOADER_ID, false))
                .thenReturn(new ByteArrayInputStream("hello".getBytes(StandardCharsets.UTF_8)));

        mockMvc.perform(get("/file/v1/{id}/preview", FILE_ID)
                        .header(WebHeaders.USER_ID, String.valueOf(UPLOADER_ID)))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("inline")))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, containsString("application/pdf")));
    }

    @Test
    @DisplayName("GET /file/v1/{id}/preview：非安全类型回落 attachment")
    void previewUnsafeTypeShouldFallbackToAttachment() throws Exception {
        when(fileInfoService.getDetail(FILE_ID, UPLOADER_ID, false)).thenReturn(fileInfo("application/zip"));
        when(fileInfoService.openContent(FILE_ID, UPLOADER_ID, false))
                .thenReturn(new ByteArrayInputStream("hello".getBytes(StandardCharsets.UTF_8)));

        mockMvc.perform(get("/file/v1/{id}/preview", FILE_ID)
                        .header(WebHeaders.USER_ID, String.valueOf(UPLOADER_ID)))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("attachment")))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"));
    }

    @Test
    @DisplayName("POST /file/v1/upload/rapid：合法请求返回 hit 与文件元数据")
    void rapidUploadShouldReturnHitResult() throws Exception {
        FileUploadResult file = new FileUploadResult(21L, "stored.pdf", "copy.pdf", "bluewhale",
                "2026/10/19/stored.pdf", 123L, "application/pdf", MD5);
        when(fileInfoService.rapidUpload(any(RapidUploadCommand.class)))
                .thenReturn(new FileRapidUploadResult(true, file));

        mockMvc.perform(post("/file/v1/upload/rapid")
                        .header(WebHeaders.USER_ID, String.valueOf(UPLOADER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fileName\":\"copy.pdf\",\"md5\":\"" + MD5 + "\",\"size\":123,"
                                + "\"contentType\":\"application/pdf\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCode.SUCCESS.getCode()))
                .andExpect(jsonPath("$.data.hit").value(true))
                .andExpect(jsonPath("$.data.file.originalName").value("copy.pdf"))
                .andExpect(jsonPath("$.data.file.objectKey").value("2026/10/19/stored.pdf"));

        ArgumentCaptor<RapidUploadCommand> captor = ArgumentCaptor.forClass(RapidUploadCommand.class);
        verify(fileInfoService).rapidUpload(captor.capture());
        assertEquals(UPLOADER_ID, captor.getValue().uploaderId());
        assertEquals(MD5, captor.getValue().md5());
    }

    @Test
    @DisplayName("GET /file/v1/quota：返回已用、配额与剩余字节数")
    void quotaShouldReturnUsage() throws Exception {
        when(fileInfoService.quota(eq(UPLOADER_ID), any(UploadLimits.class)))
                .thenReturn(new FileQuotaResult(123L, 1000L, 877L, false));

        mockMvc.perform(get("/file/v1/quota").header(WebHeaders.USER_ID, String.valueOf(UPLOADER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCode.SUCCESS.getCode()))
                .andExpect(jsonPath("$.data.usedBytes").value(123L))
                .andExpect(jsonPath("$.data.quotaBytes").value(1000L))
                .andExpect(jsonPath("$.data.remainingBytes").value(877L))
                .andExpect(jsonPath("$.data.unlimited").value(false));

        verify(fileInfoService).quota(eq(UPLOADER_ID), any(UploadLimits.class));
    }

    @Test
    @DisplayName("GET /file/v1/page：Request 转换为 Query 并返回映射后的分页响应")
    void pageShouldConvertRequestToQuery() throws Exception {
        when(fileInfoService.page(any(FilePageQuery.class)))
                .thenReturn(com.bluewhale.common.core.result.PageResult.of(List.of(fileInfo("application/pdf")),
                        1L, 1L, 10L));

        mockMvc.perform(get("/file/v1/page")
                        .header(WebHeaders.USER_ID, String.valueOf(UPLOADER_ID))
                        .param("current", "1")
                        .param("size", "10")
                        .param("originalName", "sample"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].originalName").value("sample.pdf"));

        ArgumentCaptor<FilePageQuery> captor = ArgumentCaptor.forClass(FilePageQuery.class);
        verify(fileInfoService).page(captor.capture());
        assertEquals(1L, captor.getValue().current());
        assertEquals(10L, captor.getValue().size());
        assertEquals("sample", captor.getValue().originalName());
        assertEquals(UPLOADER_ID, captor.getValue().requesterId());
        assertEquals(false, captor.getValue().canManage());
    }

    @Test
    @DisplayName("GET /file/v1/{id}/preview：MIME 类型大小写不敏感，text/plain 仍 inline")
    void previewShouldMatchContentTypeCaseInsensitively() throws Exception {
        when(fileInfoService.getDetail(FILE_ID, UPLOADER_ID, false)).thenReturn(fileInfo("TEXT/PLAIN"));
        when(fileInfoService.openContent(FILE_ID, UPLOADER_ID, false))
                .thenReturn(new ByteArrayInputStream("hello".getBytes(StandardCharsets.UTF_8)));

        mockMvc.perform(get("/file/v1/{id}/preview", FILE_ID)
                        .header(WebHeaders.USER_ID, String.valueOf(UPLOADER_ID)))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("inline")));
    }

    @Test
    @DisplayName("GET /file/v1/{id}/preview：MIME 类型缺失时回落 attachment 与 octet-stream")
    void previewShouldFallbackWhenContentTypeMissing() throws Exception {
        when(fileInfoService.getDetail(FILE_ID, UPLOADER_ID, false)).thenReturn(fileInfo(""));
        when(fileInfoService.openContent(FILE_ID, UPLOADER_ID, false))
                .thenReturn(new ByteArrayInputStream("hello".getBytes(StandardCharsets.UTF_8)));

        mockMvc.perform(get("/file/v1/{id}/preview", FILE_ID)
                        .header(WebHeaders.USER_ID, String.valueOf(UPLOADER_ID)))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("attachment")))
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, containsString("application/octet-stream")));
    }

    @Test
    @DisplayName("GET /file/v1/{id}/preview：原始文件名为空时使用占位名")
    void previewShouldUseFallbackFilename() throws Exception {
        FileInfoResult info = new FileInfoResult(FILE_ID, "stored.pdf", null, "bluewhale",
                "2026/10/19/stored.pdf", 5L, "application/pdf", null, UPLOADER_ID, null);
        when(fileInfoService.getDetail(FILE_ID, UPLOADER_ID, false)).thenReturn(info);
        when(fileInfoService.openContent(FILE_ID, UPLOADER_ID, false))
                .thenReturn(new ByteArrayInputStream("hello".getBytes(StandardCharsets.UTF_8)));

        mockMvc.perform(get("/file/v1/{id}/preview", FILE_ID)
                        .header(WebHeaders.USER_ID, String.valueOf(UPLOADER_ID)))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("download")));
    }

    @Test
    @DisplayName("GET /file/v1/{id}/download：无 Range 返回 200 全量并声明 Accept-Ranges 与 ETag")
    void downloadWithoutRangeShouldReturnFullContent() throws Exception {
        FileInfoResult info = new FileInfoResult(FILE_ID, "sample.mp3", "sample.mp3", "bluewhale",
                "2026/10/19/sample.mp3", 5L, "audio/mpeg", MD5, UPLOADER_ID, null);
        when(fileInfoService.getDetail(FILE_ID, UPLOADER_ID, false)).thenReturn(info);
        when(fileInfoService.openContent(FILE_ID, UPLOADER_ID, false))
                .thenReturn(new ByteArrayInputStream("hello".getBytes(StandardCharsets.UTF_8)));

        mockMvc.perform(get("/file/v1/{id}/download", FILE_ID)
                        .header(WebHeaders.USER_ID, String.valueOf(UPLOADER_ID)))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCEPT_RANGES, "bytes"))
                .andExpect(header().string(HttpHeaders.ETAG, "\"" + MD5 + "\""))
                .andExpect(header().longValue(HttpHeaders.CONTENT_LENGTH, 5L))
                .andExpect(header().doesNotExist(HttpHeaders.CONTENT_RANGE));
    }

    @Test
    @DisplayName("GET /file/v1/{id}/preview：Range 命中返回 206 与 Content-Range")
    void previewWithRangeShouldReturnPartialContent() throws Exception {
        when(fileInfoService.getDetail(FILE_ID, UPLOADER_ID, false)).thenReturn(fileInfo("audio/mpeg"));
        when(fileInfoService.openContent(FILE_ID, UPLOADER_ID, false, 1L, 2L))
                .thenReturn(new ByteArrayInputStream("el".getBytes(StandardCharsets.UTF_8)));

        mockMvc.perform(get("/file/v1/{id}/preview", FILE_ID)
                        .header(WebHeaders.USER_ID, String.valueOf(UPLOADER_ID))
                        .header(HttpHeaders.RANGE, "bytes=1-2"))
                .andExpect(status().isPartialContent())
                .andExpect(header().string(HttpHeaders.ACCEPT_RANGES, "bytes"))
                .andExpect(header().string(HttpHeaders.CONTENT_RANGE, "bytes 1-2/5"))
                .andExpect(header().longValue(HttpHeaders.CONTENT_LENGTH, 2L));

        verify(fileInfoService).openContent(FILE_ID, UPLOADER_ID, false, 1L, 2L);
    }

    @Test
    @DisplayName("GET /file/v1/{id}/preview：后缀 Range 取末尾字节")
    void previewWithSuffixRangeShouldReturnTail() throws Exception {
        when(fileInfoService.getDetail(FILE_ID, UPLOADER_ID, false)).thenReturn(fileInfo("audio/mpeg"));
        when(fileInfoService.openContent(FILE_ID, UPLOADER_ID, false, 3L, 2L))
                .thenReturn(new ByteArrayInputStream("lo".getBytes(StandardCharsets.UTF_8)));

        mockMvc.perform(get("/file/v1/{id}/preview", FILE_ID)
                        .header(WebHeaders.USER_ID, String.valueOf(UPLOADER_ID))
                        .header(HttpHeaders.RANGE, "bytes=-2"))
                .andExpect(status().isPartialContent())
                .andExpect(header().string(HttpHeaders.CONTENT_RANGE, "bytes 3-4/5"));

        verify(fileInfoService).openContent(FILE_ID, UPLOADER_ID, false, 3L, 2L);
    }

    @Test
    @DisplayName("GET /file/v1/{id}/preview：Range 越界返回 416 且不读取内容")
    void previewWithOutOfRangeShouldReturn416() throws Exception {
        when(fileInfoService.getDetail(FILE_ID, UPLOADER_ID, false)).thenReturn(fileInfo("audio/mpeg"));

        mockMvc.perform(get("/file/v1/{id}/preview", FILE_ID)
                        .header(WebHeaders.USER_ID, String.valueOf(UPLOADER_ID))
                        .header(HttpHeaders.RANGE, "bytes=5-9"))
                .andExpect(status().isRequestedRangeNotSatisfiable())
                .andExpect(header().string(HttpHeaders.ACCEPT_RANGES, "bytes"))
                .andExpect(header().string(HttpHeaders.CONTENT_RANGE, "bytes */5"));

        verify(fileInfoService, never()).openContent(FILE_ID, UPLOADER_ID, false);
        verify(fileInfoService, never()).openContent(anyLong(), anyLong(), anyBoolean(), anyLong(), anyLong());
    }

    @Test
    @DisplayName("GET /file/v1/{id}/preview：If-Range 与当前 ETag 不一致时回落 200 全量")
    void previewWithStaleIfRangeShouldReturnFullContent() throws Exception {
        FileInfoResult info = new FileInfoResult(FILE_ID, "sample.mp3", "sample.mp3", "bluewhale",
                "2026/10/19/sample.mp3", 5L, "audio/mpeg", MD5, UPLOADER_ID, null);
        when(fileInfoService.getDetail(FILE_ID, UPLOADER_ID, false)).thenReturn(info);
        when(fileInfoService.openContent(FILE_ID, UPLOADER_ID, false))
                .thenReturn(new ByteArrayInputStream("hello".getBytes(StandardCharsets.UTF_8)));

        mockMvc.perform(get("/file/v1/{id}/preview", FILE_ID)
                        .header(WebHeaders.USER_ID, String.valueOf(UPLOADER_ID))
                        .header(HttpHeaders.RANGE, "bytes=1-2")
                        .header(HttpHeaders.IF_RANGE, "\"stale-etag\""))
                .andExpect(status().isOk())
                .andExpect(header().longValue(HttpHeaders.CONTENT_LENGTH, 5L))
                .andExpect(header().doesNotExist(HttpHeaders.CONTENT_RANGE));
    }

    @Test
    @DisplayName("POST /file/v1/upload/rapid：MD5 格式非法时校验失败且不调用服务")
    void rapidUploadShouldRejectInvalidMd5() throws Exception {
        mockMvc.perform(post("/file/v1/upload/rapid")
                        .header(WebHeaders.USER_ID, String.valueOf(UPLOADER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fileName\":\"copy.pdf\",\"md5\":\"not-a-md5\",\"size\":123}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCode.PARAM_ERROR.getCode()));

        verify(fileInfoService, never()).rapidUpload(any(RapidUploadCommand.class));
    }

    @Test
    @DisplayName("GET /file/v1/{id}/preview：SVG 可内嵌脚本，强制回落 attachment")
    void previewSvgShouldFallbackToAttachment() throws Exception {
        when(fileInfoService.getDetail(FILE_ID, UPLOADER_ID, false)).thenReturn(fileInfo("image/svg+xml"));
        when(fileInfoService.openContent(FILE_ID, UPLOADER_ID, false))
                .thenReturn(new ByteArrayInputStream("<svg/>".getBytes(StandardCharsets.UTF_8)));

        mockMvc.perform(get("/file/v1/{id}/preview", FILE_ID)
                        .header(WebHeaders.USER_ID, String.valueOf(UPLOADER_ID)))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("attachment")));
    }

    @Test
    @DisplayName("GET /file/v1/{id}/preview：text/html 可执行内联脚本，强制回落 attachment")
    void previewTextHtmlShouldFallbackToAttachment() throws Exception {
        when(fileInfoService.getDetail(FILE_ID, UPLOADER_ID, false)).thenReturn(fileInfo("text/html"));
        when(fileInfoService.openContent(FILE_ID, UPLOADER_ID, false))
                .thenReturn(new ByteArrayInputStream("<script/>".getBytes(StandardCharsets.UTF_8)));

        mockMvc.perform(get("/file/v1/{id}/preview", FILE_ID)
                        .header(WebHeaders.USER_ID, String.valueOf(UPLOADER_ID)))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("attachment")));
    }

    @Test
    @DisplayName("GET /file/v1/{id}/preview：带参数的 text/plain 忽略参数后仍 inline")
    void previewShouldIgnoreMediaTypeParameters() throws Exception {
        when(fileInfoService.getDetail(FILE_ID, UPLOADER_ID, false))
                .thenReturn(fileInfo("text/plain; charset=utf-8"));
        when(fileInfoService.openContent(FILE_ID, UPLOADER_ID, false))
                .thenReturn(new ByteArrayInputStream("hello".getBytes(StandardCharsets.UTF_8)));

        mockMvc.perform(get("/file/v1/{id}/preview", FILE_ID)
                        .header(WebHeaders.USER_ID, String.valueOf(UPLOADER_ID)))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("inline")));
    }

    @Test
    @DisplayName("POST /file/v1/upload：超出全局默认上限时拒绝且不读取内容、不调用服务")
    void uploadShouldRejectOversizeFileBeforeReading() throws Exception {
        fileProperties.setMaxSizeBytes(10L);
        MockMultipartFile file = new MockMultipartFile("file", "big.bin",
                "application/octet-stream", new byte[11]);

        mockMvc.perform(multipart("/file/v1/upload").file(file)
                        .header(WebHeaders.USER_ID, String.valueOf(UPLOADER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCode.PARAM_ERROR.getCode()))
                .andExpect(jsonPath("$.message").value("文件大小超出普通上传上限，请使用分片上传接口"));

        verify(fileInfoService, never()).upload(any(UploadFileCommand.class));
    }

    @Test
    @DisplayName("POST /file/v1/upload：未超限时按权限码配额口径委托服务")
    void uploadShouldDelegateWithPermissionLimits() throws Exception {
        fileProperties.setMaxSizeBytes(10L);
        when(fileInfoService.upload(any(UploadFileCommand.class)))
                .thenReturn(new FileUploadResult(31L, "stored.bin", "small.bin", "bluewhale",
                        "2026/10/19/stored.bin", 10L, "application/octet-stream", MD5));
        MockMultipartFile file = new MockMultipartFile("file", "small.bin",
                "application/octet-stream", new byte[10]);

        mockMvc.perform(multipart("/file/v1/upload").file(file)
                        .header(WebHeaders.USER_ID, String.valueOf(UPLOADER_ID))
                        .header(WebHeaders.PERMISSION_CODES, "sys:video:upload"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCode.SUCCESS.getCode()))
                .andExpect(jsonPath("$.data.id").value(31));

        ArgumentCaptor<UploadFileCommand> captor = ArgumentCaptor.forClass(UploadFileCommand.class);
        verify(fileInfoService).upload(captor.capture());
        assertEquals(UPLOADER_ID, captor.getValue().uploaderId());
        assertEquals(new UploadLimits(2147483648L, 8589934592L), captor.getValue().limits());
    }
}
