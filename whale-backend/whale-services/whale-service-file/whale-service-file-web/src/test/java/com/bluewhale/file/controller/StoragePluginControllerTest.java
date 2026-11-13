package com.bluewhale.file.controller;

import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.file.assemble.StoragePluginAssembler;
import com.bluewhale.file.model.result.StoragePluginResult;
import com.bluewhale.file.service.StoragePluginService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.accept.ApiVersionStrategy;
import org.springframework.web.accept.DefaultApiVersionStrategy;
import org.springframework.web.accept.PathApiVersionResolver;
import org.springframework.web.accept.SemanticApiVersionParser;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@link StoragePluginController} 存储插件运维接口单元测试。
 *
 * @author BlueWhale
 */
class StoragePluginControllerTest {

    private final StoragePluginService storagePluginService = mock(StoragePluginService.class);

    private final StoragePluginAssembler storagePluginAssembler = Mappers.getMapper(StoragePluginAssembler.class);

    private final StoragePluginController controller =
            new StoragePluginController(storagePluginService, storagePluginAssembler);

    private MockMvc mockMvc;

    /**
     * 构建 standalone MockMvc：仅注册控制器，不装配权限拦截器。
     */
    @BeforeEach
    void setUp() {
        ApiVersionStrategy versionStrategy = new DefaultApiVersionStrategy(
                List.of(new PathApiVersionResolver(1, requestPath -> true)),
                new SemanticApiVersionParser(), false, null, true, null, null);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setApiVersionStrategy(versionStrategy)
                .build();
    }

    @Test
    @DisplayName("GET /file/v1/storage/plugins：返回插件清单并标记活动插件")
    void pluginsShouldReturnActiveFlag() throws Exception {
        when(storagePluginService.plugins()).thenReturn(List.of(
                new StoragePluginResult("local", "local-storage", false, false),
                new StoragePluginResult("minio", "minio-storage", true, true)));

        mockMvc.perform(get("/file/v1/storage/plugins"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCode.SUCCESS.getCode()))
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].type").value("local"))
                .andExpect(jsonPath("$.data[0].displayName").value("local-storage"))
                .andExpect(jsonPath("$.data[0].presignedSupported").value(false))
                .andExpect(jsonPath("$.data[0].active").value(false))
                .andExpect(jsonPath("$.data[1].type").value("minio"))
                .andExpect(jsonPath("$.data[1].presignedSupported").value(true))
                .andExpect(jsonPath("$.data[1].active").value(true));
    }
}
