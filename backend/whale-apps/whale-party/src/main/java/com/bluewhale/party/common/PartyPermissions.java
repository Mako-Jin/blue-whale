package com.bluewhale.party.common;

/**
 * 党政工会权限码常量（与 {@code migration-5.88-party-m0.sql} 登记保持一致）。
 *
 * <p>号段：1950–1969 归党建（{@code sys:party:*}），1970–1999 归工会（{@code sys:union:*}）。
 * 该段原在 Wave 5.87 登记为「OA 扩展段」并计划使用 {@code sys:oa:party:*} 前缀；
 * 2026-11-06 党政工会改为独立服务后，<b>号段不重排、前缀改为 {@code sys:party:} /
 * {@code sys:union:}</b>——换前缀是为了让日志与授权清单里的「权限归属哪个服务」
 * 一眼可读，不换号段是为了不牵动已发布的权限迁移。</p>
 *
 * <p>M-P0 只登记 1950–1953 四项基线；M-P1 起随波次在 1954 之后追加。</p>
 *
 * @author BlueWhale
 */
public final class PartyPermissions {

    /** 门禁状态与工作台查看（1950） */
    public static final String BROWSE = "sys:party:browse";

    /** 支部列表 / 详情（1951） */
    public static final String BRANCH_LIST = "sys:party:branch:list";

    /** 支部维护：新建 / 编辑 / 换届 / 停用（1952） */
    public static final String BRANCH_MANAGE = "sys:party:branch:manage";

    /** 党员台账维护：建档 / 组织关系转入转出 / 党费基数（1953） */
    public static final String MEMBER_MANAGE = "sys:party:member:manage";

    /**
     * 私有构造器：常量类，禁止实例化。
     */
    private PartyPermissions() {
    }
}
