package com.bluewhale.file.controller;

import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.file.storage.api.StoragePlugin;
import com.bluewhale.file.storage.plugin.StoragePluginRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

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

    private final StoragePluginRegistry storagePluginRegistry = mock(StoragePluginRegistry.class);

    private final StoragePluginController controller = new StoragePluginController(storagePluginRegistry);

    private MockMvc mockMvc;

    /**
     * 构建 standalone MockMvc：仅注册控制器，不装配权限拦截器。
     */
    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    /**
     * 构造存储插件桩。
     *
     * @param type 插件类型
     * @param presigned 是否支持预签名
     * @return 存储插件桩
     */
    private static StoragePlugin plugin(String type, boolean presigned) {
        StoragePlugin plugin = mock(StoragePlugin.class);
        when(plugin.getType()).thenReturn(type);
        when(plugin.getDisplayName()).thenReturn(type + "-storage");
        when(plugin.supportsPresigned()).thenReturn(presigned);
        return plugin;
    }

    @Test
    @DisplayName("GET /file/storage/plugins：返回插件清单并标记活动插件")
    void pluginsShouldReturnActiveFlag() throws Exception {
        StoragePlugin local = plugin("local", false);
        StoragePlugin minio = plugin("minio", true);
        when(storagePluginRegistry.getActiveType()).thenReturn("minio");
        when(storagePluginRegistry.getPlugins()).thenReturn(List.of(local, minio));

        mockMvc.perform(get("/file/storage/plugins"))
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
