package com.bluewhale.file.infrastructure.storage;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.file.config.FileProperties;
import com.bluewhale.file.domain.storage.StorageHealth;
import com.bluewhale.file.domain.storage.StoragePlugin;
import com.bluewhale.file.domain.storage.StoragePluginInfo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link StoragePluginRegistry} 插件选择、委派与失败提示单元测试。
 *
 * @author BlueWhale
 */
class StoragePluginRegistryTest {

    /**
     * 构造指定类型与预签名能力的插件桩。
     *
     * @param type 插件类型
     * @param presigned 是否支持预签名
     * @return 插件桩
     */
    private static StoragePlugin plugin(String type, boolean presigned) {
        StoragePlugin plugin = mock(StoragePlugin.class);
        when(plugin.getType()).thenReturn(type);
        when(plugin.getDisplayName()).thenReturn(type + "-plugin");
        when(plugin.supportsPresigned()).thenReturn(presigned);
        return plugin;
    }

    /**
     * 构造指定活动类型的文件配置。
     *
     * @param type 活动插件类型
     * @return 文件配置
     */
    private static FileProperties properties(String type) {
        FileProperties properties = new FileProperties();
        properties.getStorage().setType(type);
        return properties;
    }

    @Test
    @DisplayName("按配置选择活动插件，并按类型字典序列出全部插件")
    void shouldSelectActivePlugin() {
        StoragePlugin local = plugin("local", false);
        StoragePlugin minio = plugin("minio", true);

        StoragePluginRegistry registry = new StoragePluginRegistry(List.of(minio, local), properties("minio"));

        assertEquals("minio", registry.activeType());
        assertEquals(List.of(
                new StoragePluginInfo("local", "local-plugin", false),
                new StoragePluginInfo("minio", "minio-plugin", true)), registry.plugins());
    }

    @Test
    @DisplayName("store/load/delete 委派给当前活动插件")
    void shouldDelegateOperations() {
        StoragePlugin local = plugin("local", false);
        StoragePluginRegistry registry = new StoragePluginRegistry(List.of(local), properties("local"));
        ByteArrayInputStream in = new ByteArrayInputStream(new byte[0]);
        InputStream loaded = mock(InputStream.class);
        when(local.load("b", "k")).thenReturn(loaded);

        registry.store("b", "k", in);
        assertSame(loaded, registry.load("b", "k"));
        registry.delete("b", "k");

        verify(local).store("b", "k", in);
        verify(local).delete("b", "k");
    }

    @Test
    @DisplayName("预签名：不支持时抛参数错误，支持时委派插件")
    void shouldHandlePresigned() {
        StoragePlugin local = plugin("local", false);
        StoragePluginRegistry localRegistry = new StoragePluginRegistry(List.of(local), properties("local"));

        BizException ex = assertThrows(BizException.class,
                () -> localRegistry.presignedDownloadUrl("b", "k", 600));
        assertEquals(ErrorCode.PARAM_ERROR.getCode(), ex.getCode());
        assertTrue(ex.getMessage().contains("不支持预签名"));

        StoragePlugin minio = plugin("minio", true);
        when(minio.presignedDownloadUrl("b", "k", 600)).thenReturn("http://signed");
        StoragePluginRegistry minioRegistry = new StoragePluginRegistry(List.of(minio), properties("minio"));

        assertEquals("http://signed", minioRegistry.presignedDownloadUrl("b", "k", 600));
    }

    @Test
    @DisplayName("按落盘存储类型路由读取，空类型回落活动插件")
    void shouldRouteByStorageType() {
        StoragePlugin local = plugin("local", false);
        StoragePlugin minio = plugin("minio", true);
        StoragePluginRegistry registry = new StoragePluginRegistry(List.of(local, minio), properties("minio"));
        InputStream localContent = mock(InputStream.class);
        InputStream localRange = mock(InputStream.class);
        InputStream activeContent = mock(InputStream.class);
        when(local.load("b", "k")).thenReturn(localContent);
        when(local.load("b", "k", 0L, 10L)).thenReturn(localRange);
        when(minio.load("b", "k")).thenReturn(activeContent);

        assertSame(localContent, registry.load("b", "k", "local"));
        assertSame(localRange, registry.load("b", "k", 0L, 10L, " local "));
        // 切换活动插件后，未标注存储类型的历史行仍走活动插件
        assertSame(activeContent, registry.load("b", "k", null));
    }

    @Test
    @DisplayName("落盘存储类型对应的插件未装配时抛系统错误并列出可用插件")
    void shouldRejectMissingStorageType() {
        StoragePlugin local = plugin("local", false);
        StoragePluginRegistry registry = new StoragePluginRegistry(List.of(local), properties("local"));

        BizException ex = assertThrows(BizException.class, () -> registry.load("b", "k", "oss"));

        assertEquals(ErrorCode.SYSTEM_ERROR.getCode(), ex.getCode());
        assertTrue(ex.getMessage().contains("oss"));
        assertTrue(ex.getMessage().contains("local"));
    }

    @Test
    @DisplayName("健康检查：逐插件探测，单插件异常降级为 DOWN 不中断")
    void shouldAggregatePluginHealth() {
        StoragePlugin local = plugin("local", false);
        StoragePlugin minio = plugin("minio", true);
        when(local.health()).thenReturn(StorageHealth.up("ok"));
        when(minio.health()).thenThrow(new IllegalStateException("connection refused"));
        StoragePluginRegistry registry = new StoragePluginRegistry(List.of(local, minio), properties("local"));

        Map<String, StorageHealth> health = registry.health();

        assertTrue(health.get("local").up());
        assertFalse(health.get("minio").up());
        assertTrue(health.get("minio").message().contains("connection refused"));
    }

    @Test
    @DisplayName("无任何插件时启动失败并提示")
    void shouldRejectEmptyPlugins() {
        BizException ex = assertThrows(BizException.class,
                () -> new StoragePluginRegistry(List.of(), properties("local")));

        assertEquals(ErrorCode.SYSTEM_ERROR.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("配置的活动插件不存在时启动失败并列出可用插件")
    void shouldRejectMissingActivePlugin() {
        StoragePlugin local = plugin("local", false);

        BizException ex = assertThrows(BizException.class,
                () -> new StoragePluginRegistry(List.of(local), properties("oss")));

        assertEquals(ErrorCode.SYSTEM_ERROR.getCode(), ex.getCode());
        assertTrue(ex.getMessage().contains("oss"));
        assertTrue(ex.getMessage().contains("local"));
    }
}
