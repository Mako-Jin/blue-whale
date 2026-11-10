package com.bluewhale.scene.domain.model;

/**
 * 入口绑定类型常量（与 DDL 的 {@code sc_node_binding.entry_type} 取值一致）。
 *
 * <p>绑定只保存入口地址与权限码，不直连业务库；业务系统数据仍归对应服务所有。</p>
 *
 * @author BlueWhale
 */
public final class EntryType {

    /** 平台业务系统一级入口 */
    public static final String SYSTEM = "SYSTEM";

    /** 平台内部页面路由 */
    public static final String PAGE = "PAGE";

    /** 知识库 / 问答入口，跳转 whale-agent */
    public static final String KNOWLEDGE = "KNOWLEDGE";

    /** 已登记的外部系统链接 */
    public static final String EXTERNAL = "EXTERNAL";

    /**
     * 私有构造器：常量类，禁止实例化。
     */
    private EntryType() {
    }

    /**
     * 判断入口类型是否合法。
     *
     * @param entryType 入口类型
     * @return 合法返回 {@code true}
     */
    public static boolean isValid(String entryType) {
        return SYSTEM.equals(entryType) || PAGE.equals(entryType)
                || KNOWLEDGE.equals(entryType) || EXTERNAL.equals(entryType);
    }
}
