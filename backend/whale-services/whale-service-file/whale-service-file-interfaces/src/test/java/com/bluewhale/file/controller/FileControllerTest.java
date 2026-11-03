package com.bluewhale.file.controller;

import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.web.WebHeaders;
import com.bluewhale.file.config.FileProperties;
import com.bluewhale.file.dto.ChunkUploadInitRequest;
import com.bluewhale.file.dto.FileRapidUploadRequest;
import com.bluewhale.file.service.FileInfoService;
import com.bluewhale.file.service.model.UploadLimits;
import com.bluewhale.file.service.support.FileUploadPolicy;
import com.bluewhale.file.vo.ChunkUploadInitVO;
import com.bluewhale.file.vo.FileInfoVO;
import com.bluewhale.file.vo.FileQuotaVO;
import com.bluewhale.file.vo.FileRapidUploadVO;
import com.bluewhale.file.vo.FileUploadVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@link FileController} 控制器层单元测试（standalone MockMvc）。
 *
 * <p>覆盖在线预览的 {@code Content-Disposition} 分流、秒传接口响应、配额查询响应，
 * 以及 G1 按权限码的大文件上传策略解析（视频 2GB / 电影 4GB / 未命中仍 50MB）。</p>
 *
 * @author BlueWhale
 */
class FileControllerTest {

    private static final long FILE_ID = 7L;
    private static final long UPLOADER_ID = 9L;
    private static final String MD5 = "d41d8cd98f00b204e9800998ecf8427e";

    /** 视频上传权限码：命中 2GB / 8GB 规则 */
    private static final String VIDEO_UPLOAD_PERMISSION = "sys:video:upload";

    /** 视频后台权限码：命中 4GB / 不限配额规则 */
    private static final String MOVIE_MANAGE_PERMISSION = "sys:video:movie:manage";

    private final FileInfoService fileInfoService = mock(FileInfoService.class);

    private final FileProperties fileProperties = new FileProperties();

    private final FileUploadPolicy fileUploadPolicy = new FileUploadPolicy(fileProperties);

    private final FileController controller = new FileController(fileInfoService, fileUploadPolicy);

    private MockMvc mockMvc;

    /**
     * 构建 standalone MockMvc：仅注册控制器与校验器，不装配权限拦截器
     */
    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setValidator(validator)
                .build();
    }

    /**
     * 构造文件详情视图对象
     *
     * @param contentType MIME 类型
     * @return 文件详情视图对象
     */
    private static FileInfoVO fileInfo(String contentType) {
        FileInfoVO info = new FileInfoVO();
        info.setId(FILE_ID);
        info.setOriginalName("sample.pdf");
        info.setContentType(contentType);
        info.setFileSize(5L);
        return info;
    }

    @Test
    @DisplayName("GET /file/{id}/preview：PDF 以 inline 呈现并带 nosniff")
    void previewPdfShouldBeInline() throws Exception {
        when(fileInfoService.getDetail(FILE_ID, UPLOADER_ID, false)).thenReturn(fileInfo("application/pdf"));
        when(fileInfoService.openContent(FILE_ID, UPLOADER_ID, false))
                .thenReturn(new ByteArrayInputStream("hello".getBytes(StandardCharsets.UTF_8)));

        mockMvc.perform(get("/file/{id}/preview", FILE_ID)
                        .header(WebHeaders.USER_ID, String.valueOf(UPLOADER_ID)))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("inline")))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, containsString("application/pdf")));
    }

    @Test
    @DisplayName("GET /file/{id}/preview：非安全类型回落 attachment")
    void previewUnsafeTypeShouldFallbackToAttachment() throws Exception {
        when(fileInfoService.getDetail(FILE_ID, UPLOADER_ID, false)).thenReturn(fileInfo("application/zip"));
        when(fileInfoService.openContent(FILE_ID, UPLOADER_ID, false))
                .thenReturn(new ByteArrayInputStream("hello".getBytes(StandardCharsets.UTF_8)));

        mockMvc.perform(get("/file/{id}/preview", FILE_ID)
                        .header(WebHeaders.USER_ID, String.valueOf(UPLOADER_ID)))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("attachment")))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"));
    }

    @Test
    @DisplayName("POST /file/upload/rapid：合法请求返回 hit 与文件元数据")
    void rapidUploadShouldReturnHitResult() throws Exception {
        FileUploadVO file = new FileUploadVO();
        file.setId(21L);
        file.setOriginalName("copy.pdf");
        file.setBucket("bluewhale");
        file.setObjectKey("2026/10/19/stored.pdf");
        file.setFileSize(123L);
        file.setMd5(MD5);
        FileRapidUploadVO result = new FileRapidUploadVO();
        result.setHit(true);
        result.setFile(file);
        when(fileInfoService.rapidUpload(any(FileRapidUploadRequest.class), eq(UPLOADER_ID),
                any(UploadLimits.class))).thenReturn(result);

        mockMvc.perform(post("/file/upload/rapid")
                        .header(WebHeaders.USER_ID, String.valueOf(UPLOADER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fileName\":\"copy.pdf\",\"md5\":\"" + MD5 + "\",\"size\":123,"
                                + "\"contentType\":\"application/pdf\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCode.SUCCESS.getCode()))
                .andExpect(jsonPath("$.data.hit").value(true))
                .andExpect(jsonPath("$.data.file.originalName").value("copy.pdf"))
                .andExpect(jsonPath("$.data.file.objectKey").value("2026/10/19/stored.pdf"));

        verify(fileInfoService).rapidUpload(any(FileRapidUploadRequest.class), eq(UPLOADER_ID),
                any(UploadLimits.class));
    }

    @Test
    @DisplayName("GET /file/quota：返回已用、配额与剩余字节数")
    void quotaShouldReturnUsage() throws Exception {
        FileQuotaVO quota = new FileQuotaVO();
        quota.setUsedBytes(123L);
        quota.setQuotaBytes(1000L);
        quota.setRemainingBytes(877L);
        quota.setUnlimited(false);
        when(fileInfoService.quota(eq(UPLOADER_ID), any(UploadLimits.class))).thenReturn(quota);

        mockMvc.perform(get("/file/quota").header(WebHeaders.USER_ID, String.valueOf(UPLOADER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCode.SUCCESS.getCode()))
                .andExpect(jsonPath("$.data.usedBytes").value(123L))
                .andExpect(jsonPath("$.data.quotaBytes").value(1000L))
                .andExpect(jsonPath("$.data.remainingBytes").value(877L))
                .andExpect(jsonPath("$.data.unlimited").value(false));

        verify(fileInfoService).quota(eq(UPLOADER_ID), any(UploadLimits.class));
    }

    @Test
    @DisplayName("GET /file/{id}/preview：MIME 类型大小写不敏感，text/plain 仍 inline")
    void previewShouldMatchContentTypeCaseInsensitively() throws Exception {
        when(fileInfoService.getDetail(FILE_ID, UPLOADER_ID, false)).thenReturn(fileInfo("TEXT/PLAIN"));
        when(fileInfoService.openContent(FILE_ID, UPLOADER_ID, false))
                .thenReturn(new ByteArrayInputStream("hello".getBytes(StandardCharsets.UTF_8)));

        mockMvc.perform(get("/file/{id}/preview", FILE_ID)
                        .header(WebHeaders.USER_ID, String.valueOf(UPLOADER_ID)))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("inline")));
    }

    @Test
    @DisplayName("GET /file/{id}/preview：MIME 类型缺失时回落 attachment 与 octet-stream")
    void previewShouldFallbackWhenContentTypeMissing() throws Exception {
        when(fileInfoService.getDetail(FILE_ID, UPLOADER_ID, false)).thenReturn(fileInfo(""));
        when(fileInfoService.openContent(FILE_ID, UPLOADER_ID, false))
                .thenReturn(new ByteArrayInputStream("hello".getBytes(StandardCharsets.UTF_8)));

        mockMvc.perform(get("/file/{id}/preview", FILE_ID)
                        .header(WebHeaders.USER_ID, String.valueOf(UPLOADER_ID)))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("attachment")))
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, containsString("application/octet-stream")));
    }

    @Test
    @DisplayName("GET /file/{id}/preview：原始文件名为空时使用占位名")
    void previewShouldUseFallbackFilename() throws Exception {
        FileInfoVO info = fileInfo("application/pdf");
        info.setOriginalName(null);
        when(fileInfoService.getDetail(FILE_ID, UPLOADER_ID, false)).thenReturn(info);
        when(fileInfoService.openContent(FILE_ID, UPLOADER_ID, false))
                .thenReturn(new ByteArrayInputStream("hello".getBytes(StandardCharsets.UTF_8)));

        mockMvc.perform(get("/file/{id}/preview", FILE_ID)
                        .header(WebHeaders.USER_ID, String.valueOf(UPLOADER_ID)))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("download")));
    }

    @Test
    @DisplayName("GET /file/{id}/download：无 Range 返回 200 全量并声明 Accept-Ranges 与 ETag")
    void downloadWithoutRangeShouldReturnFullContent() throws Exception {
        FileInfoVO info = fileInfo("audio/mpeg");
        info.setMd5(MD5);
        when(fileInfoService.getDetail(FILE_ID, UPLOADER_ID, false)).thenReturn(info);
        when(fileInfoService.openContent(FILE_ID, UPLOADER_ID, false))
                .thenReturn(new ByteArrayInputStream("hello".getBytes(StandardCharsets.UTF_8)));

        mockMvc.perform(get("/file/{id}/download", FILE_ID)
                        .header(WebHeaders.USER_ID, String.valueOf(UPLOADER_ID)))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCEPT_RANGES, "bytes"))
                .andExpect(header().string(HttpHeaders.ETAG, "\"" + MD5 + "\""))
                .andExpect(header().longValue(HttpHeaders.CONTENT_LENGTH, 5L))
                .andExpect(header().doesNotExist(HttpHeaders.CONTENT_RANGE));
    }

    @Test
    @DisplayName("GET /file/{id}/preview：Range 命中返回 206 与 Content-Range")
    void previewWithRangeShouldReturnPartialContent() throws Exception {
        when(fileInfoService.getDetail(FILE_ID, UPLOADER_ID, false)).thenReturn(fileInfo("audio/mpeg"));
        when(fileInfoService.openContent(FILE_ID, UPLOADER_ID, false, 1L, 2L))
                .thenReturn(new ByteArrayInputStream("el".getBytes(StandardCharsets.UTF_8)));

        mockMvc.perform(get("/file/{id}/preview", FILE_ID)
                        .header(WebHeaders.USER_ID, String.valueOf(UPLOADER_ID))
                        .header(HttpHeaders.RANGE, "bytes=1-2"))
                .andExpect(status().isPartialContent())
                .andExpect(header().string(HttpHeaders.ACCEPT_RANGES, "bytes"))
                .andExpect(header().string(HttpHeaders.CONTENT_RANGE, "bytes 1-2/5"))
                .andExpect(header().longValue(HttpHeaders.CONTENT_LENGTH, 2L));

        verify(fileInfoService).openContent(FILE_ID, UPLOADER_ID, false, 1L, 2L);
    }

    @Test
    @DisplayName("GET /file/{id}/preview：后缀 Range 取末尾字节")
    void previewWithSuffixRangeShouldReturnTail() throws Exception {
        when(fileInfoService.getDetail(FILE_ID, UPLOADER_ID, false)).thenReturn(fileInfo("audio/mpeg"));
        when(fileInfoService.openContent(FILE_ID, UPLOADER_ID, false, 3L, 2L))
                .thenReturn(new ByteArrayInputStream("lo".getBytes(StandardCharsets.UTF_8)));

        mockMvc.perform(get("/file/{id}/preview", FILE_ID)
                        .header(WebHeaders.USER_ID, String.valueOf(UPLOADER_ID))
                        .header(HttpHeaders.RANGE, "bytes=-2"))
                .andExpect(status().isPartialContent())
                .andExpect(header().string(HttpHeaders.CONTENT_RANGE, "bytes 3-4/5"));

        verify(fileInfoService).openContent(FILE_ID, UPLOADER_ID, false, 3L, 2L);
    }

    @Test
    @DisplayName("GET /file/{id}/preview：Range 越界返回 416 且不读取内容")
    void previewWithOutOfRangeShouldReturn416() throws Exception {
        when(fileInfoService.getDetail(FILE_ID, UPLOADER_ID, false)).thenReturn(fileInfo("audio/mpeg"));

        mockMvc.perform(get("/file/{id}/preview", FILE_ID)
                        .header(WebHeaders.USER_ID, String.valueOf(UPLOADER_ID))
                        .header(HttpHeaders.RANGE, "bytes=5-9"))
                .andExpect(status().isRequestedRangeNotSatisfiable())
                .andExpect(header().string(HttpHeaders.ACCEPT_RANGES, "bytes"))
                .andExpect(header().string(HttpHeaders.CONTENT_RANGE, "bytes */5"));

        verify(fileInfoService, never()).openContent(FILE_ID, UPLOADER_ID, false);
        verify(fileInfoService, never()).openContent(anyLong(), anyLong(), anyBoolean(), anyLong(), anyLong());
    }

    @Test
    @DisplayName("GET /file/{id}/preview：If-Range 与当前 ETag 不一致时回落 200 全量")
    void previewWithStaleIfRangeShouldReturnFullContent() throws Exception {
        FileInfoVO info = fileInfo("audio/mpeg");
        info.setMd5(MD5);
        when(fileInfoService.getDetail(FILE_ID, UPLOADER_ID, false)).thenReturn(info);
        when(fileInfoService.openContent(FILE_ID, UPLOADER_ID, false))
                .thenReturn(new ByteArrayInputStream("hello".getBytes(StandardCharsets.UTF_8)));

        mockMvc.perform(get("/file/{id}/preview", FILE_ID)
                        .header(WebHeaders.USER_ID, String.valueOf(UPLOADER_ID))
                        .header(HttpHeaders.RANGE, "bytes=1-2")
                        .header(HttpHeaders.IF_RANGE, "\"stale-etag\""))
                .andExpect(status().isOk())
                .andExpect(header().longValue(HttpHeaders.CONTENT_LENGTH, 5L))
                .andExpect(header().doesNotExist(HttpHeaders.CONTENT_RANGE));
    }

    @Test
    @DisplayName("POST /file/upload/rapid：MD5 格式非法时校验失败且不调用服务")
    void rapidUploadShouldRejectInvalidMd5() throws Exception {
        mockMvc.perform(post("/file/upload/rapid")
                        .header(WebHeaders.USER_ID, String.valueOf(UPLOADER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fileName\":\"copy.pdf\",\"md5\":\"not-a-md5\",\"size\":123}"))
                .andExpect(status().isBadRequest());

        verify(fileInfoService, never()).rapidUpload(any(FileRapidUploadRequest.class), eq(UPLOADER_ID),
                any(UploadLimits.class));
    }

    @Test
    @DisplayName("POST /file/upload/chunk/init：持有 sys:video:upload 时按 2GB / 8GB 初始化分片上传")
    void initChunkUploadShouldApplyVideoPolicy() throws Exception {
        ChunkUploadInitVO vo = new ChunkUploadInitVO();
        vo.setUploadId("upload-video-1");
        vo.setChunkSize(5242880L);
        vo.setUploadedIndexes(List.of());
        when(fileInfoService.initChunkUpload(any(ChunkUploadInitRequest.class), any(UploadLimits.class)))
                .thenReturn(vo);

        mockMvc.perform(post("/file/upload/chunk/init")
                        .header(WebHeaders.USER_ID, String.valueOf(UPLOADER_ID))
                        .header(WebHeaders.PERMISSION_CODES, VIDEO_UPLOAD_PERMISSION)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fileName\":\"video.mp4\",\"size\":2147483648,\"contentType\":\"video/mp4\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCode.SUCCESS.getCode()))
                .andExpect(jsonPath("$.data.uploadId").value("upload-video-1"));

        verify(fileInfoService).initChunkUpload(any(ChunkUploadInitRequest.class),
                eq(new UploadLimits(2147483648L, 8589934592L)));
    }

    @Test
    @DisplayName("POST /file/upload/chunk/init：无视频权限时仍按默认 50MB / 1GB 校验")
    void initChunkUploadShouldFallbackToDefaults() throws Exception {
        ChunkUploadInitVO vo = new ChunkUploadInitVO();
        vo.setUploadId("upload-plain-1");
        vo.setChunkSize(5242880L);
        vo.setUploadedIndexes(List.of());
        when(fileInfoService.initChunkUpload(any(ChunkUploadInitRequest.class), any(UploadLimits.class)))
                .thenReturn(vo);

        mockMvc.perform(post("/file/upload/chunk/init")
                        .header(WebHeaders.USER_ID, String.valueOf(UPLOADER_ID))
                        .header(WebHeaders.PERMISSION_CODES, "sys:file:upload")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fileName\":\"report.pdf\",\"size\":5242880,\"contentType\":\"application/pdf\"}"))
                .andExpect(status().isOk());

        verify(fileInfoService).initChunkUpload(any(ChunkUploadInitRequest.class),
                eq(fileUploadPolicy.defaults()));
    }

    @Test
    @DisplayName("POST /file/upload/chunk/init：同时持有视频与电影权限时取 4GB / 不限配额")
    void initChunkUploadShouldPickLargestRule() throws Exception {
        ChunkUploadInitVO vo = new ChunkUploadInitVO();
        vo.setUploadId("upload-movie-1");
        vo.setChunkSize(5242880L);
        vo.setUploadedIndexes(List.of());
        when(fileInfoService.initChunkUpload(any(ChunkUploadInitRequest.class), any(UploadLimits.class)))
                .thenReturn(vo);

        mockMvc.perform(post("/file/upload/chunk/init")
                        .header(WebHeaders.USER_ID, String.valueOf(UPLOADER_ID))
                        .header(WebHeaders.PERMISSION_CODES,
                                VIDEO_UPLOAD_PERMISSION + "," + MOVIE_MANAGE_PERMISSION)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fileName\":\"movie.mp4\",\"size\":4294967296,\"contentType\":\"video/mp4\"}"))
                .andExpect(status().isOk());

        verify(fileInfoService).initChunkUpload(any(ChunkUploadInitRequest.class),
                eq(new UploadLimits(4294967296L, 0L)));
    }
}
