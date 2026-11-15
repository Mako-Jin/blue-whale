package com.bluewhale.file.controller;

import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.core.handler.GlobalExceptionHandler;
import com.bluewhale.common.web.WebHeaders;
import com.bluewhale.file.assemble.FileAssembler;
import com.bluewhale.file.config.FileProperties;
import com.bluewhale.file.model.UploadContent;
import com.bluewhale.file.model.UploadLimits;
import com.bluewhale.file.model.command.ChunkUploadInitCommand;
import com.bluewhale.file.model.result.ChunkUploadInitResult;
import com.bluewhale.file.service.FileInfoService;
import com.bluewhale.file.service.support.FileUploadPolicy;
import com.bluewhale.file.service.support.UploadPolicyRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.accept.ApiVersionStrategy;
import org.springframework.web.accept.DefaultApiVersionStrategy;
import org.springframework.web.accept.PathApiVersionResolver;
import org.springframework.web.accept.SemanticApiVersionParser;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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

    private final FileUploadPolicy fileUploadPolicy =
            new FileUploadPolicy(fileProperties, new UploadPolicyRegistry(fileProperties));

    private final FileAssembler fileAssembler = Mappers.getMapper(FileAssembler.class);

    private final FileChunkUploadController controller =
            new FileChunkUploadController(fileInfoService, fileUploadPolicy, fileAssembler);

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

    @Test
    @DisplayName("POST /file/v1/upload/chunk/init：持有 sys:video:upload 时按 2GB / 8GB 初始化分片上传")
    void initChunkUploadShouldApplyVideoPolicy() throws Exception {
        when(fileInfoService.initChunkUpload(any(ChunkUploadInitCommand.class)))
                .thenReturn(new ChunkUploadInitResult("upload-video-1", 5242880L, List.of()));

        mockMvc.perform(post("/file/v1/upload/chunk/init")
                        .header(WebHeaders.USER_ID, String.valueOf(UPLOADER_ID))
                        .header(WebHeaders.PERMISSION_CODES, VIDEO_UPLOAD_PERMISSION)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fileName\":\"video.mp4\",\"size\":2147483648,\"contentType\":\"video/mp4\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCode.SUCCESS.getCode()))
                .andExpect(jsonPath("$.data.uploadId").value("upload-video-1"));

        ArgumentCaptor<ChunkUploadInitCommand> captor = ArgumentCaptor.forClass(ChunkUploadInitCommand.class);
        verify(fileInfoService).initChunkUpload(captor.capture());
        assertEquals(new UploadLimits(2147483648L, 8589934592L), captor.getValue().limits());
        assertEquals("video.mp4", captor.getValue().fileName());
    }

    @Test
    @DisplayName("POST /file/v1/upload/chunk/init：无视频权限时仍按默认 50MB / 1GB 校验")
    void initChunkUploadShouldFallbackToDefaults() throws Exception {
        when(fileInfoService.initChunkUpload(any(ChunkUploadInitCommand.class)))
                .thenReturn(new ChunkUploadInitResult("upload-plain-1", 5242880L, List.of()));

        mockMvc.perform(post("/file/v1/upload/chunk/init")
                        .header(WebHeaders.USER_ID, String.valueOf(UPLOADER_ID))
                        .header(WebHeaders.PERMISSION_CODES, "sys:file:upload")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fileName\":\"report.pdf\",\"size\":5242880,\"contentType\":\"application/pdf\"}"))
                .andExpect(status().isOk());

        ArgumentCaptor<ChunkUploadInitCommand> captor = ArgumentCaptor.forClass(ChunkUploadInitCommand.class);
        verify(fileInfoService).initChunkUpload(captor.capture());
        assertEquals(fileUploadPolicy.defaults(), captor.getValue().limits());
    }

    @Test
    @DisplayName("POST /file/v1/upload/chunk/init：同时持有视频与电影权限时取 4GB / 不限配额")
    void initChunkUploadShouldPickLargestRule() throws Exception {
        when(fileInfoService.initChunkUpload(any(ChunkUploadInitCommand.class)))
                .thenReturn(new ChunkUploadInitResult("upload-movie-1", 5242880L, List.of()));

        mockMvc.perform(post("/file/v1/upload/chunk/init")
                        .header(WebHeaders.USER_ID, String.valueOf(UPLOADER_ID))
                        .header(WebHeaders.PERMISSION_CODES,
                                VIDEO_UPLOAD_PERMISSION + "," + MOVIE_MANAGE_PERMISSION)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fileName\":\"movie.mp4\",\"size\":4294967296,\"contentType\":\"video/mp4\"}"))
                .andExpect(status().isOk());

        ArgumentCaptor<ChunkUploadInitCommand> captor = ArgumentCaptor.forClass(ChunkUploadInitCommand.class);
        verify(fileInfoService).initChunkUpload(captor.capture());
        assertEquals(new UploadLimits(4294967296L, 0L), captor.getValue().limits());
    }

    @Test
    @DisplayName("POST /file/v1/upload/chunk/part：单片超出生效分片大小时拒绝且不读取内容")
    void uploadChunkPartShouldRejectOversizePartBeforeReading() throws Exception {
        when(fileInfoService.chunkSizeBytes()).thenReturn(4L);
        MockMultipartFile part = new MockMultipartFile("file", "part.bin",
                "application/octet-stream", new byte[5]);

        mockMvc.perform(multipart("/file/v1/upload/chunk/part")
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
    @DisplayName("POST /file/v1/upload/chunk/part：单片未超限时委托服务")
    void uploadChunkPartShouldDelegateWithinLimit() throws Exception {
        when(fileInfoService.chunkSizeBytes()).thenReturn(8L);
        when(fileInfoService.uploadChunkPart(eq("upload-1"), eq(0), any(UploadContent.class)))
                .thenReturn(List.of(0));
        MockMultipartFile part = new MockMultipartFile("file", "part.bin",
                "application/octet-stream", new byte[5]);

        mockMvc.perform(multipart("/file/v1/upload/chunk/part")
                        .file(part)
                        .param("uploadId", "upload-1")
                        .param("index", "0")
                        .header(WebHeaders.USER_ID, String.valueOf(UPLOADER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCode.SUCCESS.getCode()));

        verify(fileInfoService).uploadChunkPart(eq("upload-1"), eq(0), any(UploadContent.class));
    }

    @Test
    @DisplayName("GET /file/v1/upload/chunk/{uploadId}/status：返回已上传分片索引")
    void chunkStatusShouldReturnUploadedIndexes() throws Exception {
        when(fileInfoService.chunkUploadStatus("upload-1")).thenReturn(List.of(0, 2));

        mockMvc.perform(get("/file/v1/upload/chunk/{uploadId}/status", "upload-1")
                        .header(WebHeaders.USER_ID, String.valueOf(UPLOADER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0]").value(0))
                .andExpect(jsonPath("$.data[1]").value(2));

        verify(fileInfoService).chunkUploadStatus("upload-1");
    }
}
