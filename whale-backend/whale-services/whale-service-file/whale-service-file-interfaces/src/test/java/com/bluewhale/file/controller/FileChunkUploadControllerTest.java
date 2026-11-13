package com.bluewhale.file.controller;

import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.core.handler.GlobalExceptionHandler;
import com.bluewhale.common.web.WebHeaders;
import com.bluewhale.file.config.FileProperties;
import com.bluewhale.file.dto.ChunkUploadInitRequest;
import com.bluewhale.file.service.FileInfoService;
import com.bluewhale.file.service.model.UploadContent;
import com.bluewhale.file.service.model.UploadLimits;
import com.bluewhale.file.service.support.FileUploadPolicy;
import com.bluewhale.file.vo.ChunkUploadInitVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@link FileChunkUploadController} 控制器层单元测试（standalone MockMvc）。
 *
 * <p>覆盖 G1 按权限码的大文件上传策略解析（视频 2GB / 电影 4GB / 未命中仍 50MB）
 * 与单片全量读入预检（超限单片拒绝且不读取内容）。</p>
 *
 * @author BlueWhale
 */
class FileChunkUploadControllerTest {

    private static final long UPLOADER_ID = 9L;

    /** 视频上传权限码：命中 2GB / 8GB 规则 */
    private static final String VIDEO_UPLOAD_PERMISSION = "sys:video:upload";

    /** 视频后台权限码：命中 4GB / 不限配额规则 */
    private static final String MOVIE_MANAGE_PERMISSION = "sys:video:movie:manage";

    private final FileInfoService fileInfoService = mock(FileInfoService.class);

    private final FileProperties fileProperties = new FileProperties();

    private final FileUploadPolicy fileUploadPolicy = new FileUploadPolicy(fileProperties);

    private final FileChunkUploadController controller =
            new FileChunkUploadController(fileInfoService, fileUploadPolicy);

    private MockMvc mockMvc;

    /**
     * 构建 standalone MockMvc：仅注册控制器、校验器与全局异常处理器，不装配权限拦截器
     */
    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setValidator(validator)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
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

    @Test
    @DisplayName("POST /file/upload/chunk/part：单片超出生效分片大小时拒绝且不读取内容")
    void uploadChunkPartShouldRejectOversizePartBeforeReading() throws Exception {
        when(fileInfoService.chunkSizeBytes()).thenReturn(4L);
        MockMultipartFile part = new MockMultipartFile("file", "part.bin",
                "application/octet-stream", new byte[5]);

        mockMvc.perform(multipart("/file/upload/chunk/part")
                        .file(part)
                        .param("uploadId", "upload-1")
                        .param("index", "0")
                        .header(WebHeaders.USER_ID, String.valueOf(UPLOADER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCode.PARAM_ERROR.getCode()))
                .andExpect(jsonPath("$.message").value("分片大小超出上限"));

        verify(fileInfoService, never()).uploadChunkPart(any(), anyInt(), any(UploadContent.class));
    }

    @Test
    @DisplayName("POST /file/upload/chunk/part：单片未超限时委托服务")
    void uploadChunkPartShouldDelegateWithinLimit() throws Exception {
        when(fileInfoService.chunkSizeBytes()).thenReturn(8L);
        when(fileInfoService.uploadChunkPart(eq("upload-1"), eq(0), any(UploadContent.class)))
                .thenReturn(List.of(0));
        MockMultipartFile part = new MockMultipartFile("file", "part.bin",
                "application/octet-stream", new byte[5]);

        mockMvc.perform(multipart("/file/upload/chunk/part")
                        .file(part)
                        .param("uploadId", "upload-1")
                        .param("index", "0")
                        .header(WebHeaders.USER_ID, String.valueOf(UPLOADER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCode.SUCCESS.getCode()));

        verify(fileInfoService).uploadChunkPart(eq("upload-1"), eq(0), any(UploadContent.class));
    }
}
