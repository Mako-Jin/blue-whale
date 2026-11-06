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
 * <p>M-P0 只登记 1950–1953 四项基线；M-P1 追加 1954–1958（党员查看、组织生活查看/维护、
 * 党费查看/维护）。</p>
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

    /** 党员台账查看（1954） */
    public static final String MEMBER_LIST = "sys:party:member:list";

    /** 组织生活查看：三会一课与主题党日（1955） */
    public static final String ACTIVITY_LIST = "sys:party:activity:list";

    /** 组织生活维护：计划 / 完成 / 归档 / 签到（1956） */
    public static final String ACTIVITY_MANAGE = "sys:party:activity:manage";

    /** 党费台账查看：标准与月度账单（1957） */
    public static final String FEE_LIST = "sys:party:fee:list";

    /** 党费维护：配置标准 / 生成账单 / 登记缴费（1958） */
    public static final String FEE_MANAGE = "sys:party:fee:manage";

    /**
     * 私有构造器：常量类，禁止实例化。
     */
    private PartyPermissions() {
    }
}
