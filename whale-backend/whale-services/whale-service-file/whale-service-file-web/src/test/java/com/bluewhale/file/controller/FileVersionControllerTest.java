package com.bluewhale.file.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.core.handler.GlobalExceptionHandler;
import com.bluewhale.common.web.WebHeaders;
import com.bluewhale.file.assemble.FileAssembler;
import com.bluewhale.file.config.FileProperties;
import com.bluewhale.file.model.UploadContent;
import com.bluewhale.file.model.UploadLimits;
import com.bluewhale.file.model.result.FileInfoResult;
import com.bluewhale.file.model.result.FileVersionResult;
import com.bluewhale.file.service.FileInfoService;
import com.bluewhale.file.service.support.FileUploadPolicy;
import com.bluewhale.file.service.support.UploadPolicyRegistry;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.accept.ApiVersionStrategy;
import org.springframework.web.accept.DefaultApiVersionStrategy;
import org.springframework.web.accept.PathApiVersionResolver;
import org.springframework.web.accept.SemanticApiVersionParser;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@link FileVersionController} 文件版本接口单元测试（standalone MockMvc）。
 *
 * <p>权限拦截器不在 standalone 环境装配，权限通过反射校验实现方法上的
 * {@code @RequirePermission} 注解。</p>
 *
 * @author BlueWhale
 */
class FileVersionControllerTest {

    private static final long ROOT_ID = 7L;
    private static final long VERSION_ID = 8L;
    private static final long UPLOADER_ID = 9L;

    private final FileInfoService fileInfoService = mock(FileInfoService.class);

    private final FileProperties fileProperties = new FileProperties();

    private final FileUploadPolicy fileUploadPolicy =
            new FileUploadPolicy(fileProperties, new UploadPolicyRegistry(fileProperties));

    private final FileAssembler fileAssembler = Mappers.getMapper(FileAssembler.class);

    private final FileVersionController controller =
            new FileVersionController(fileInfoService, fileUploadPolicy, fileAssembler);

    private MockMvc mockMvc;

    /**
     * 构建 standalone MockMvc：仅注册控制器、校验器与全局异常处理器
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
     * 构造版本结果
     *
     * @param id 版本行主键
     * @param parentId 版本组根文件 ID；根版本为 {@code null}
     * @param version 版本号
     * @return 文件版本结果
     */
    private static FileVersionResult version(Long id, Long parentId, int version) {
        return version(id, parentId, version, false);
    }

    /**
     * 构造带最新标识的版本结果
     *
     * @param id 版本行主键
     * @param parentId 版本组根文件 ID；根版本为 {@code null}
     * @param version 版本号
     * @param latest 是否为当前最新版本
     * @return 文件版本结果
     */
    private static FileVersionResult version(Long id, Long parentId, int version, boolean latest) {
        return new FileVersionResult(id, parentId, version, latest, "stored.pdf", "sample.pdf", "bluewhale",
                "tenants/1/2026/10/25/stored.pdf", 5L, "application/pdf", "md5", UPLOADER_ID,
                LocalDateTime.of(2026, 10, 25, 9, 0));
    }

    @Test
    @DisplayName("GET /file/v1/{id}/versions：返回版本组并按版本号倒序")
    void shouldListVersions() throws Exception {
        when(fileInfoService.versions(ROOT_ID, UPLOADER_ID, false))
                .thenReturn(List.of(version(VERSION_ID, ROOT_ID, 2, true), version(ROOT_ID, null, 1)));

        mockMvc.perform(get("/file/v1/{id}/versions", ROOT_ID)
                        .header(WebHeaders.USER_ID, String.valueOf(UPLOADER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCode.SUCCESS.getCode()))
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].id").value(VERSION_ID))
                .andExpect(jsonPath("$.data[0].parentId").value(ROOT_ID))
                .andExpect(jsonPath("$.data[0].version").value(2))
                .andExpect(jsonPath("$.data[0].latest").value(true))
                .andExpect(jsonPath("$.data[1].version").value(1))
                .andExpect(jsonPath("$.data[1].latest").value(false));
    }

    @Test
    @DisplayName("POST /file/v1/{id}/versions：上传新版本并返回版本元数据")
    void shouldUploadVersion() throws Exception {
        when(fileInfoService.uploadVersion(eq(ROOT_ID), any(UploadContent.class), eq(UPLOADER_ID), eq(false),
                any(UploadLimits.class))).thenReturn(version(VERSION_ID, ROOT_ID, 2, true));

        mockMvc.perform(multipart("/file/v1/{id}/versions", ROOT_ID)
                        .file(new MockMultipartFile("file", "sample-v2.pdf", "application/pdf",
                                "v2".getBytes(StandardCharsets.UTF_8)))
                        .header(WebHeaders.USER_ID, String.valueOf(UPLOADER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCode.SUCCESS.getCode()))
                .andExpect(jsonPath("$.data.id").value(VERSION_ID))
                .andExpect(jsonPath("$.data.parentId").value(ROOT_ID))
                .andExpect(jsonPath("$.data.version").value(2))
                .andExpect(jsonPath("$.data.latest").value(true));

        verify(fileInfoService).uploadVersion(eq(ROOT_ID), any(UploadContent.class), eq(UPLOADER_ID), eq(false),
                any(UploadLimits.class));
    }

    @Test
    @DisplayName("POST /file/v1/{id}/versions/{targetVersion}/rollback：回滚并返回新版本")
    void shouldRollbackVersion() throws Exception {
        when(fileInfoService.rollback(eq(ROOT_ID), eq(1), eq(UPLOADER_ID), eq(false),
                any(UploadLimits.class))).thenReturn(version(VERSION_ID, ROOT_ID, 4, true));

        mockMvc.perform(post("/file/v1/{id}/versions/{targetVersion}/rollback", ROOT_ID, 1)
                        .header(WebHeaders.USER_ID, String.valueOf(UPLOADER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCode.SUCCESS.getCode()))
                .andExpect(jsonPath("$.data.id").value(VERSION_ID))
                .andExpect(jsonPath("$.data.parentId").value(ROOT_ID))
                .andExpect(jsonPath("$.data.version").value(4))
                .andExpect(jsonPath("$.data.latest").value(true));

        verify(fileInfoService).rollback(eq(ROOT_ID), eq(1), eq(UPLOADER_ID), eq(false),
                any(UploadLimits.class));
    }

    @Test
    @DisplayName("DELETE /file/v1/{id}/versions/{targetVersion}：删除历史版本")
    void shouldDeleteVersion() throws Exception {
        mockMvc.perform(delete("/file/v1/{id}/versions/{targetVersion}", ROOT_ID, 2)
                        .header(WebHeaders.USER_ID, String.valueOf(UPLOADER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCode.SUCCESS.getCode()));

        verify(fileInfoService).deleteVersion(ROOT_ID, 2, UPLOADER_ID, false);
    }

    @Test
    @DisplayName("GET /file/v1/{id}/versions/deleted：查询已删除历史版本")
    void shouldListDeletedVersions() throws Exception {
        when(fileInfoService.deletedVersions(ROOT_ID, UPLOADER_ID, false))
                .thenReturn(List.of(version(VERSION_ID, ROOT_ID, 2)));

        mockMvc.perform(get("/file/v1/{id}/versions/deleted", ROOT_ID)
                        .header(WebHeaders.USER_ID, String.valueOf(UPLOADER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCode.SUCCESS.getCode()))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].version").value(2));
    }

    @Test
    @DisplayName("POST /file/v1/{id}/versions/{targetVersion}/restore：找回已删除版本")
    void shouldRestoreVersion() throws Exception {
        when(fileInfoService.restoreVersion(eq(ROOT_ID), eq(2), eq(UPLOADER_ID), eq(false),
                any(UploadLimits.class))).thenReturn(version(VERSION_ID, ROOT_ID, 2));

        mockMvc.perform(post("/file/v1/{id}/versions/{targetVersion}/restore", ROOT_ID, 2)
                        .header(WebHeaders.USER_ID, String.valueOf(UPLOADER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCode.SUCCESS.getCode()))
                .andExpect(jsonPath("$.data.id").value(VERSION_ID))
                .andExpect(jsonPath("$.data.version").value(2));

        verify(fileInfoService).restoreVersion(eq(ROOT_ID), eq(2), eq(UPLOADER_ID), eq(false),
                any(UploadLimits.class));
    }

    @Test
    @DisplayName("GET /file/v1/{id}/versions/{targetVersion}/download：下载历史版本内容")
    void shouldDownloadVersion() throws Exception {
        FileInfoResult info = new FileInfoResult(VERSION_ID, "stored.pdf", "sample.pdf", "bluewhale",
                "tenants/1/2026/10/25/stored.pdf", 5L, "application/pdf", "md5", UPLOADER_ID,
                LocalDateTime.of(2026, 10, 25, 9, 0));
        when(fileInfoService.versionDetail(ROOT_ID, 2, UPLOADER_ID, false)).thenReturn(info);
        when(fileInfoService.openVersionContent(ROOT_ID, 2, UPLOADER_ID, false))
                .thenReturn(new ByteArrayInputStream("hello".getBytes(StandardCharsets.UTF_8)));

        mockMvc.perform(get("/file/v1/{id}/versions/{targetVersion}/download", ROOT_ID, 2)
                        .header(WebHeaders.USER_ID, String.valueOf(UPLOADER_ID)))
                .andExpect(status().isOk())
                .andExpect(content().bytes("hello".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    @DisplayName("GET 要求 sys:file:list，上传/回滚要求 sys:file:upload，删除/找回要求 sys:file:delete，下载要求 sys:file:download")
    void shouldRequirePermissions() throws Exception {
        assertPermission("list", "sys:file:list",
                FileVersionController.class.getMethod("list", Long.class, HttpServletRequest.class));
        assertPermission("upload", "sys:file:upload",
                FileVersionController.class.getMethod("upload", Long.class, MultipartFile.class,
                        HttpServletRequest.class));
        assertPermission("rollback", "sys:file:upload",
                FileVersionController.class.getMethod("rollback", Long.class, int.class,
                        HttpServletRequest.class));
        assertPermission("delete", "sys:file:delete",
                FileVersionController.class.getMethod("delete", Long.class, int.class,
                        HttpServletRequest.class));
        assertPermission("deleted", "sys:file:list",
                FileVersionController.class.getMethod("deleted", Long.class, HttpServletRequest.class));
        assertPermission("restore", "sys:file:delete",
                FileVersionController.class.getMethod("restore", Long.class, int.class,
                        HttpServletRequest.class));
        assertPermission("download", "sys:file:download",
                FileVersionController.class.getMethod("download", Long.class, int.class, String.class,
                        String.class, HttpServletRequest.class));
    }

    /**
     * 校验方法上的权限注解
     *
     * @param expectedPermission 期望权限码
     * @param method 控制器方法
     */
    private static void assertPermission(String methodName, String expectedPermission, Method method) {
        RequirePermission annotation = method.getAnnotation(RequirePermission.class);
        assertEquals(expectedPermission, annotation == null ? null : annotation.value(), methodName);
    }
}
