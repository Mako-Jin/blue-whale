package com.bluewhale.form.support;

import java.util.Set;

/**
 * 表单物理表保留列常量。
 *
 * <p>schema 解析（字段 key 白名单校验）与 DDL 生成（公共列不参与增量变更）必须使用同一份定义，
 * 抽出常量避免两处各自维护导致漂移。</p>
 *
 * @author BlueWhale
 */
public final class FormReservedColumns {

    /** 物理表公共列：动态字段 key 不得命中，DDL 增量变更永不触碰 */
    private static final Set<String> KEYS = Set.of(
            "id", "tenant_id", "definition_id", "definition_version", "biz_key",
            "submitter", "status", "create_time", "update_time", "deleted");

    /** 工具类不允许实例化 */
    private FormReservedColumns() {
    }

    /**
     * 物理表公共列集合
     *
     * @return 不可变保留列集合
     */
    public static Set<String> keys() {
        return KEYS;
    }

    /**
     * 判断字段 key 是否为保留列
     *
     * @param key 字段 key
     * @return 命中保留列时返回 {@code true}
     */
    public static boolean isReserved(String key) {
        return KEYS.contains(key);
    }
}
