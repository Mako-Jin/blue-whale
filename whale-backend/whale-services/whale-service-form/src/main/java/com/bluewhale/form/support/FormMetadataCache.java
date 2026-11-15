package com.bluewhale.form.support;

import com.bluewhale.form.po.FormDefinitionPo;
import com.bluewhale.form.po.FormTableRegistryPo;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * 表单元数据本地缓存（表单定义 + 物理表登记）。
 *
 * <p>表单定义与物理表登记是典型的读多写少数据，提交/详情/查询每次请求都按主键或编码回查数据库，
 * 在表单数量与并发上升后成为无谓的数据库压力。本缓存以 Caffeine 承载，<b>写时主动失效</b>：
 * 定义新增/修改/删除/回滚与物理表发布成功后由调用方显式调用 {@code invalidate*}，
 * 避免读到旧 schema；TTL 仅作为「漏失效」时的兜底。</p>
 *
 * <p><b>边界</b>：缓存为进程内单级缓存，多实例部署时各实例独立失效（写操作所在实例立即失效，
 * 其他实例最长延迟一个 TTL 才能看到变更）。表单发布与定义修改属于低频运维操作，
 * 该窗口可接受；若后续要求多实例强一致，再替换为 Redis 共享缓存。</p>
 *
 * <p>缓存值为实体引用，调用方只读使用，不得原地修改后再依赖缓存（修改必须走数据库并失效缓存）。</p>
 *
 * @author BlueWhale
 */
@Component
public class FormMetadataCache {

    /** 缓存有效期（分钟）：作为漏失效兜底，写时主动失效是主路径 */
    private static final long TTL_MINUTES = 5L;

    /** 单类缓存最大条目数（防止异常编码场景无限增长） */
    private static final long MAX_SIZE = 10000L;

    /** 表单定义缓存（key 为定义ID） */
    private final Cache<Long, FormDefinitionPo> definitions = Caffeine.newBuilder()
            .expireAfterWrite(TTL_MINUTES, TimeUnit.MINUTES)
            .maximumSize(MAX_SIZE)
            .build();

    /** 表单定义缓存（key 为表单编码，值为该编码下版本号最大的定义） */
    private final Cache<String, FormDefinitionPo> definitionsByCode = Caffeine.newBuilder()
            .expireAfterWrite(TTL_MINUTES, TimeUnit.MINUTES)
            .maximumSize(MAX_SIZE)
            .build();

    /** 物理表登记缓存（key 为 {@code code@version}） */
    private final Cache<String, FormTableRegistryPo> registries = Caffeine.newBuilder()
            .expireAfterWrite(TTL_MINUTES, TimeUnit.MINUTES)
            .maximumSize(MAX_SIZE)
            .build();

    /**
     * 按主键读取表单定义
     *
     * @param definitionId 表单定义ID
     * @return 缓存命中返回定义；未命中返回 {@link Optional#empty()}
     */
    public Optional<FormDefinitionPo> findDefinition(Long definitionId) {
        return definitionId == null ? Optional.empty() : Optional.ofNullable(definitions.getIfPresent(definitionId));
    }

    /**
     * 按编码读取表单定义（该编码下版本号最大者）
     *
     * @param code 表单编码
     * @return 缓存命中返回定义；未命中返回 {@link Optional#empty()}
     */
    public Optional<FormDefinitionPo> findDefinitionByCode(String code) {
        return code == null ? Optional.empty() : Optional.ofNullable(definitionsByCode.getIfPresent(code));
    }

    /**
     * 按（编码 + 版本）读取物理表登记
     *
     * @param code 路由编码（表编码或表单编码）
     * @param version 路由版本
     * @return 缓存命中返回登记；未命中返回 {@link Optional#empty()}
     */
    public Optional<FormTableRegistryPo> findRegistry(String code, Integer version) {
        if (code == null || version == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(registries.getIfPresent(registryKey(code, version)));
    }

    /**
     * 缓存表单定义（同时按主键与编码各写一份）
     *
     * @param definition 表单定义实体
     */
    public void cacheDefinition(FormDefinitionPo definition) {
        if (definition == null) {
            return;
        }
        if (definition.getId() != null) {
            definitions.put(definition.getId(), definition);
        }
        if (definition.getCode() != null) {
            definitionsByCode.put(definition.getCode(), definition);
        }
    }

    /**
     * 缓存物理表登记
     *
     * @param registry 物理表登记实体（需同时具备路由编码与版本）
     */
    public void cacheRegistry(FormTableRegistryPo registry) {
        if (registry == null) {
            return;
        }
        String code = registry.getTableCode() == null ? registry.getDefinitionCode() : registry.getTableCode();
        Integer version = registry.getTableCode() == null
                ? registry.getDefinitionVersion() : registry.getTableVersion();
        if (code != null && version != null) {
            registries.put(registryKey(code, version), registry);
        }
    }

    /**
     * 按键失效表单定义缓存
     *
     * @param definitionId 表单定义ID（可空）
     * @param code 表单编码（可空）
     */
    public void invalidateDefinition(Long definitionId, String code) {
        if (definitionId != null) {
            definitions.invalidate(definitionId);
        }
        if (code != null) {
            definitionsByCode.invalidate(code);
        }
    }

    /**
     * 按（编码 + 版本）失效物理表登记缓存
     *
     * @param code 路由编码（可空）
     * @param version 路由版本（可空）
     */
    public void invalidateRegistry(String code, Integer version) {
        if (code != null && version != null) {
            registries.invalidate(registryKey(code, version));
        }
    }

    /**
     * 清空全部缓存（元数据整体迁移等批量变更后使用）
     */
    public void invalidateAll() {
        definitions.invalidateAll();
        definitionsByCode.invalidateAll();
        registries.invalidateAll();
    }

    /**
     * 构造登记缓存键
     *
     * @param code 路由编码
     * @param version 路由版本
     * @return 缓存键
     */
    private static String registryKey(String code, Integer version) {
        return code + "@" + version;
    }
}
