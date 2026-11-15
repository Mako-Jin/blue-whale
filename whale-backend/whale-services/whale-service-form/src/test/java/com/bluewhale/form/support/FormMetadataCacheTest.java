package com.bluewhale.form.support;

import com.bluewhale.form.po.FormDefinitionPo;
import com.bluewhale.form.po.FormTableRegistryPo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link FormMetadataCache} 本地缓存读写与失效单元测试。
 *
 * @author BlueWhale
 */
class FormMetadataCacheTest {

    private final FormMetadataCache cache = new FormMetadataCache();

    /**
     * 构造表单定义桩对象
     *
     * @param id 定义ID
     * @param code 表单编码
     * @return 表单定义实体
     */
    private static FormDefinitionPo definition(Long id, String code) {
        FormDefinitionPo definition = new FormDefinitionPo();
        definition.setId(id);
        definition.setCode(code);
        definition.setVersion(1);
        return definition;
    }

    /**
     * 构造物理表登记桩对象
     *
     * @param tableCode 表编码
     * @param version 表版本
     * @return 登记实体
     */
    private static FormTableRegistryPo registry(String tableCode, Integer version) {
        FormTableRegistryPo registry = new FormTableRegistryPo();
        registry.setTableCode(tableCode);
        registry.setTableVersion(version);
        registry.setPhysicalTable("form_data_" + tableCode + "_v" + version);
        return registry;
    }

    @Test
    @DisplayName("cacheDefinition：按主键与编码均可命中")
    void shouldCacheDefinitionByIdAndCode() {
        cache.cacheDefinition(definition(10L, "fm_a"));

        assertTrue(cache.findDefinition(10L).isPresent());
        assertTrue(cache.findDefinitionByCode("fm_a").isPresent());
        assertEquals("fm_a", cache.findDefinition(10L).orElseThrow().getCode());
    }

    @Test
    @DisplayName("invalidateDefinition：按主键与编码分别失效")
    void shouldInvalidateDefinition() {
        cache.cacheDefinition(definition(10L, "fm_a"));

        cache.invalidateDefinition(10L, null);
        assertTrue(cache.findDefinition(10L).isEmpty());
        assertTrue(cache.findDefinitionByCode("fm_a").isPresent());

        cache.invalidateDefinition(null, "fm_a");
        assertTrue(cache.findDefinitionByCode("fm_a").isEmpty());
    }

    @Test
    @DisplayName("cacheRegistry：按编码与版本命中，可按键失效")
    void shouldCacheAndInvalidateRegistry() {
        FormTableRegistryPo registry = registry("t_order", 2);
        cache.cacheRegistry(registry);

        assertTrue(cache.findRegistry("t_order", 2).isPresent());
        assertTrue(cache.findRegistry("t_order", 1).isEmpty());

        cache.invalidateRegistry("t_order", 2);
        assertTrue(cache.findRegistry("t_order", 2).isEmpty());
    }

    @Test
    @DisplayName("invalidateAll：清空全部缓存")
    void shouldInvalidateAll() {
        cache.cacheDefinition(definition(10L, "fm_a"));
        cache.cacheRegistry(registry("t_order", 1));

        cache.invalidateAll();

        assertTrue(cache.findDefinition(10L).isEmpty());
        assertTrue(cache.findDefinitionByCode("fm_a").isEmpty());
        assertTrue(cache.findRegistry("t_order", 1).isEmpty());
    }

    @Test
    @DisplayName("空入参：不缓存也不报错")
    void shouldIgnoreNullArguments() {
        cache.cacheDefinition(null);
        cache.cacheRegistry(null);

        assertTrue(cache.findDefinition(null).isEmpty());
        assertTrue(cache.findDefinitionByCode(null).isEmpty());
        assertTrue(cache.findRegistry(null, null).isEmpty());
        cache.invalidateDefinition(null, null);
        cache.invalidateRegistry(null, null);
    }
}
