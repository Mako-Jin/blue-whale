package com.bluewhale.file.controller;

import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.core.handler.GlobalExceptionHandler;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.web.WebHeaders;
import com.bluewhale.file.api.FileShareApi;
import com.bluewhale.file.model.command.CreateShareCommand;
import com.bluewhale.file.model.query.FileSharePageQuery;
import com.bluewhale.file.model.result.FileInfoResult;
import com.bluewhale.file.model.result.FileShareAccessResult;
import com.bluewhale.file.model.result.FileShareCreateResult;
import com.bluewhale.file.model.result.FileShareResult;
import com.bluewhale.file.service.FileShareService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.accept.ApiVersionStrategy;
import org.springframework.web.accept.DefaultApiVersionStrategy;
import org.springframework.web.accept.PathApiVersionResolver;
import org.springframework.web.accept.SemanticApiVersionParser;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@link FileShareController} 控制器层单元测试（standalone MockMvc）。
 *
 * @author BlueWhale
 */
class FileShareControllerTest {

    private static final long FILE_ID = 21L;

    private static final long UPLOADER_ID = 9L;

    private static final String TOKEN = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMN";

    private final FileShareService fileShareService = mock(FileShareService.class);

    private final FileShareController controller = new FileShareController(fileShareService);

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
    @DisplayName("POST /file/v1/share：Request 转换为 Command 并返回令牌")
    void createShouldConvertRequest() throws Exception {
        when(fileShareService.create(any(CreateShareCommand.class))).thenReturn(new FileShareCreateResult(
                31L, FILE_ID, TOKEN, LocalDateTime.now().plusDays(7), 5, true));

        mockMvc.perform(post("/file/v1/share")
                        .header(WebHeaders.USER_ID, String.valueOf(UPLOADER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fileId\":21,\"expireHours\":24,\"maxDownloads\":5,\"password\":\"whale-1234\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.token").value(TOKEN))
                .andExpect(jsonPath("$.data.hasPassword").value(true));

        ArgumentCaptor<CreateShareCommand> captor = ArgumentCaptor.forClass(CreateShareCommand.class);
        verify(fileShareService).create(captor.capture());
        assertEquals(FILE_ID, captor.getValue().fileId());
        assertEquals(24, captor.getValue().expireHours());
        assertEquals(5, captor.getValue().maxDownloads());
        assertEquals(UPLOADER_ID, captor.getValue().requesterId());
    }

    @Test
    @DisplayName("GET /file/v1/share/page：Request 转换为 Query 并返回分页")
    void pageShouldConvertRequest() throws Exception {
        when(fileShareService.page(any(FileSharePageQuery.class))).thenReturn(PageResult.of(
                List.of(new FileShareResult(31L, FILE_ID, "报表.xlsx", TOKEN, null, null, 0, 1,
                        LocalDateTime.now())), 1L, 1L, 10L));

        mockMvc.perform(get("/file/v1/share/page")
                        .header(WebHeaders.USER_ID, String.valueOf(UPLOADER_ID))
                        .param("current", "1")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].fileName").value("报表.xlsx"));

        ArgumentCaptor<FileSharePageQuery> captor = ArgumentCaptor.forClass(FileSharePageQuery.class);
        verify(fileShareService).page(captor.capture());
        assertEquals(UPLOADER_ID, captor.getValue().requesterId());
        assertEquals(10L, captor.getValue().size());
    }

    @Test
    @DisplayName("DELETE /file/v1/share/{id}：调用撤销并返回成功")
    void revokeShouldDelegate() throws Exception {
        mockMvc.perform(delete("/file/v1/share/31")
                        .header(WebHeaders.USER_ID, String.valueOf(UPLOADER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCode.SUCCESS.getCode()));

        verify(fileShareService).revoke(31L, UPLOADER_ID, false);
    }

    @Test
    @DisplayName("GET /file/v1/share/{token}：需密码时不返回文件名")
    void accessShouldHideFileInfoWhenPasswordRequired() throws Exception {
        when(fileShareService.access(TOKEN, null)).thenReturn(new FileShareAccessResult(null, true,
                LocalDateTime.now().plusDays(1), 5, 0));

        mockMvc.perform(get("/file/v1/share/" + TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.needPassword").value(true))
                .andExpect(jsonPath("$.data.fileName").doesNotExist());
    }

    @Test
    @DisplayName("GET /file/v1/share/{token}/download：无 Range 返回 200 全量")
    void downloadShouldReturnFullContent() throws Exception {
        when(fileShareService.access(TOKEN, null)).thenReturn(new FileShareAccessResult(fileInfo(), false,
                null, null, 0));
        when(fileShareService.openContent(TOKEN, null, null, null))
                .thenReturn(new ByteArrayInputStream("whale".getBytes(StandardCharsets.UTF_8)));

        mockMvc.perform(get("/file/v1/share/" + TOKEN + "/download"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_LENGTH, "5"))
                .andExpect(header().string(HttpHeaders.ACCEPT_RANGES, "bytes"));

        verify(fileShareService).openContent(TOKEN, null, null, null);
    }

    @Test
    @DisplayName("GET /file/v1/share/{token}/download：Range 命中返回 206 与 Content-Range")
    void downloadShouldSupportRange() throws Exception {
        when(fileShareService.access(TOKEN, null)).thenReturn(new FileShareAccessResult(fileInfo(), false,
                null, null, 0));
        when(fileShareService.openContent(TOKEN, null, 0L, 3L))
                .thenReturn(new ByteArrayInputStream("whal".getBytes(StandardCharsets.UTF_8)));

        mockMvc.perform(get("/file/v1/share/" + TOKEN + "/download")
                        .header(HttpHeaders.RANGE, "bytes=0-2"))
                .andExpect(status().isPartialContent())
                .andExpect(header().string(HttpHeaders.CONTENT_RANGE, "bytes 0-2/5"));

        verify(fileShareService).openContent(TOKEN, null, 0L, 3L);
    }

    /**
     * 构造分享文件元数据
     *
     * @return 文件元数据
     */
    private static FileInfoResult fileInfo() {
        return new FileInfoResult(FILE_ID, "stored.pdf", "sample.pdf", "bluewhale",
                "tenants/1/stored.pdf", 5L, "application/pdf", "md5", UPLOADER_ID, null);
    }
}
