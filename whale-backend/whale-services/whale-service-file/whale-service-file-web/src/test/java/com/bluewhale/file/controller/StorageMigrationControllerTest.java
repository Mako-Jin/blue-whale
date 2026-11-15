package com.bluewhale.file.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.core.handler.GlobalExceptionHandler;
import com.bluewhale.file.assemble.StorageMigrationAssembler;
import com.bluewhale.file.model.command.StorageMigrationCommand;
import com.bluewhale.file.model.result.StorageMigrationResult;
import com.bluewhale.file.service.StorageMigrationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.accept.ApiVersionStrategy;
import org.springframework.web.accept.DefaultApiVersionStrategy;
import org.springframework.web.accept.PathApiVersionResolver;
import org.springframework.web.accept.SemanticApiVersionParser;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@link StorageMigrationController} 存储迁移接口单元测试（standalone MockMvc）。
 *
 * @author BlueWhale
 */
class StorageMigrationControllerTest {

    private final StorageMigrationService storageMigrationService = mock(StorageMigrationService.class);

    private final StorageMigrationAssembler storageMigrationAssembler =
            Mappers.getMapper(StorageMigrationAssembler.class);

    private final StorageMigrationController controller =
            new StorageMigrationController(storageMigrationService, storageMigrationAssembler);

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

    @Test
    @DisplayName("POST /file/v1/storage-migrations：返回迁移汇总并透传参数")
    void shouldMigrate() throws Exception {
        when(storageMigrationService.migrate(any(StorageMigrationCommand.class)))
                .thenReturn(new StorageMigrationResult("local", "minio", 3, 2, 1, 0, false, List.of()));

        mockMvc.perform(post("/file/v1/storage-migrations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sourceStorageType\":\"local\",\"bucket\":\"bluewhale\",\"limit\":10,"
                                + "\"dryRun\":false,\"deleteSource\":true,\"confirm\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCode.SUCCESS.getCode()))
                .andExpect(jsonPath("$.data.sourceStorageType").value("local"))
                .andExpect(jsonPath("$.data.targetStorageType").value("minio"))
                .andExpect(jsonPath("$.data.scanned").value(3))
                .andExpect(jsonPath("$.data.migrated").value(2))
                .andExpect(jsonPath("$.data.skipped").value(1));

        ArgumentCaptor<StorageMigrationCommand> captor =
                ArgumentCaptor.forClass(StorageMigrationCommand.class);
        verify(storageMigrationService).migrate(captor.capture());
        StorageMigrationCommand command = captor.getValue();
        assertEquals("local", command.sourceStorageType());
        assertEquals("bluewhale", command.bucket());
        assertEquals(10L, command.limit());
        assertFalse(command.dryRun());
        assertTrue(command.deleteSource());
        assertTrue(command.confirm());
    }

    @Test
    @DisplayName("POST /file/v1/storage-migrations：缺少源类型返回参数错误")
    void shouldRejectMissingSourceType() throws Exception {
        mockMvc.perform(post("/file/v1/storage-migrations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"confirm\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCode.PARAM_ERROR.getCode()));
    }

    @Test
    @DisplayName("迁移接口要求 sys:file:manage 权限码")
    void shouldRequireManagePermission() throws Exception {
        Method method = StorageMigrationController.class.getMethod("migrate",
                com.bluewhale.file.model.request.StorageMigrationRequest.class);
        RequirePermission annotation = method.getAnnotation(RequirePermission.class);
        assertEquals("sys:file:manage", annotation == null ? null : annotation.value());
    }
}
