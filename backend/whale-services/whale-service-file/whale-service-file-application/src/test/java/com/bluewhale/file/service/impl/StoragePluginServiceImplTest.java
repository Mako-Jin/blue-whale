package com.bluewhale.file.service.impl;

import com.bluewhale.file.domain.storage.StoragePluginCatalog;
import com.bluewhale.file.domain.storage.StoragePluginInfo;
import com.bluewhale.file.vo.StoragePluginVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link StoragePluginServiceImpl} 插件清单映射单元测试。
 *
 * @author BlueWhale
 */
class StoragePluginServiceImplTest {

    @Test
    @DisplayName("plugins：按目录输出插件清单并标记活动插件")
    void shouldMapPluginsWithActiveFlag() {
        StoragePluginCatalog catalog = mock(StoragePluginCatalog.class);
        when(catalog.activeType()).thenReturn("minio");
        when(catalog.plugins()).thenReturn(List.of(
                new StoragePluginInfo("local", "Local", false),
                new StoragePluginInfo("minio", "MinIO", true)));
        StoragePluginServiceImpl service = new StoragePluginServiceImpl(catalog);

        List<StoragePluginVO> plugins = service.plugins();

        assertEquals(2, plugins.size());
        assertEquals("local", plugins.get(0).getType());
        assertEquals("Local", plugins.get(0).getDisplayName());
        assertFalse(plugins.get(0).isPresignedSupported());
        assertFalse(plugins.get(0).isActive());
        assertEquals("minio", plugins.get(1).getType());
        assertTrue(plugins.get(1).isActive());
    }
}
