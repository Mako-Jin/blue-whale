package com.bluewhale.file.controller;

import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.core.handler.GlobalExceptionHandler;
import com.bluewhale.common.web.WebHeaders;
import com.bluewhale.file.model.command.FileBatchDeleteCommand;
import com.bluewhale.file.model.result.FileArchiveEntryResult;
import com.bluewhale.file.model.result.FileBatchDeleteItemResult;
import com.bluewhale.file.model.result.FileBatchDeleteResult;
import com.bluewhale.file.service.FileArchiveService;
import com.bluewhale.file.service.FileInfoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.accept.ApiVersionStrategy;
import org.springframework.web.accept.DefaultApiVersionStrategy;
import org.springframework.web.accept.PathApiVersionResolver;
import org.springframework.web.accept.SemanticApiVersionParser;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@link FileBatchController} 控制器层单元测试：批量删除请求映射与打包响应头/内容。
 *
 * <p>打包下载直接调用控制器读取 {@link StreamingResponseBody}，避免 standalone MockMvc
 * 对异步流式响应的额外调度配置，同时完整校验响应头与 ZIP 内容。</p>
 *
 * @author BlueWhale
 */
class FileBatchControllerTest {

    private static final long UPLOADER_ID = 9L;

    private final FileInfoService fileInfoService = mock(FileInfoService.class);

    private final FileArchiveService fileArchiveService = mock(FileArchiveService.class);

    private final FileBatchController controller = new FileBatchController(fileInfoService, fileArchiveService);

    private MockMvc mockMvc;

    /**
     * 构建 standalone MockMvc：只注册控制器、校验器与全局异常处理器
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
    @DisplayName("POST /file/v1/batch/delete：返回逐项结果与计数")
    void batchDeleteShouldReturnPerItemResult() throws Exception {
        when(fileInfoService.batchDelete(any(FileBatchDeleteCommand.class))).thenReturn(
                new FileBatchDeleteResult(List.of(new FileBatchDeleteItemResult(7L, true, null),
                        new FileBatchDeleteItemResult(8L, false, "文件已归档锁定，禁止删除"))));

        mockMvc.perform(post("/file/v1/batch/delete")
                        .header(WebHeaders.USER_ID, String.valueOf(UPLOADER_ID))
                        .contentType("application/json")
                        .content("{\"ids\":[7,8]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(2))
                .andExpect(jsonPath("$.data.successCount").value(1))
                .andExpect(jsonPath("$.data.failureCount").value(1))
                .andExpect(jsonPath("$.data.items[0].id").value(7))
                .andExpect(jsonPath("$.data.items[1].message").value("文件已归档锁定，禁止删除"));

        ArgumentCaptor<FileBatchDeleteCommand> captor = ArgumentCaptor.forClass(FileBatchDeleteCommand.class);
        verify(fileInfoService).batchDelete(captor.capture());
        assertEquals(List.of(7L, 8L), captor.getValue().ids());
        assertEquals(UPLOADER_ID, captor.getValue().requesterId());
        assertEquals(false, captor.getValue().canManage());
    }

    @Test
    @DisplayName("POST /file/v1/batch/delete：空列表校验失败且不调用服务")
    void batchDeleteShouldRejectEmptyIds() throws Exception {
        mockMvc.perform(post("/file/v1/batch/delete")
                        .header(WebHeaders.USER_ID, String.valueOf(UPLOADER_ID))
                        .contentType("application/json")
                        .content("{\"ids\":[]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCode.PARAM_ERROR.getCode()));

        verify(fileInfoService, never()).batchDelete(any(FileBatchDeleteCommand.class));
    }

    @Test
    @DisplayName("GET /file/v1/batch/download：返回 ZIP 响应头并委派流式写出")
    void batchDownloadShouldStreamZip() {
        List<FileArchiveEntryResult> entries = List.of(new FileArchiveEntryResult(7L, "a.txt", 5L));
        when(fileArchiveService.prepare(eq(List.of(7L)), eq(UPLOADER_ID), anyBoolean())).thenReturn(entries);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(WebHeaders.USER_ID, String.valueOf(UPLOADER_ID));

        ResponseEntity<StreamingResponseBody> response = controller.batchDownload(List.of(7L), request);

        assertEquals("application/zip", response.getHeaders().getContentType().toString());
        String disposition = response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION);
        assertTrue(disposition.contains("attachment"));
        assertTrue(disposition.contains("files-"));
        assertEquals("nosniff", response.getHeaders().getFirst("X-Content-Type-Options"));
        verify(fileArchiveService).prepare(eq(List.of(7L)), eq(UPLOADER_ID), eq(false));
        verify(fileInfoService, never()).batchDelete(any(FileBatchDeleteCommand.class));
    }
}
