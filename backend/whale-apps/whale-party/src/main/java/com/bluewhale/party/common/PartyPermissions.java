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
 * 党费查看/维护）；M-P2 追加 1959–1961（党建内容查看/维护/审核）；
 * M-P3 追加 1970–1980（工会浏览、会员/慰问/福利/活动/提案的查看与维护）。</p>
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

    /** 党建内容查看：管理列表 / 详情 / 审核轨迹 / 阅读记录（1959） */
    public static final String CONTENT_LIST = "sys:party:content:list";

    /** 党建内容维护：新建 / 编辑 / 提交审核（1960） */
    public static final String CONTENT_MANAGE = "sys:party:content:manage";

    /** 党建内容审核：通过 / 退回（意见必填）/ 下线（1961） */
    public static final String CONTENT_AUDIT = "sys:party:content:audit";

    /** 工会园地浏览与自助：活动报名 / 提案提交 / 福利签收（1970） */
    public static final String UNION_BROWSE = "sys:union:browse";

    /** 工会会员台账查看（1971） */
    public static final String UNION_MEMBER_LIST = "sys:union:member:list";

    /** 工会会员维护：入会 / 编辑 / 退会 / 会费（1972） */
    public static final String UNION_MEMBER_MANAGE = "sys:union:member:manage";

    /** 慰问帮扶查看（1973） */
    public static final String UNION_RELIEF_LIST = "sys:union:relief:list";

    /** 慰问帮扶维护：申请 / 提交 / 同步 / 发放（1974） */
    public static final String UNION_RELIEF_MANAGE = "sys:union:relief:manage";

    /** 节日福利查看（含名单）（1975） */
    public static final String UNION_WELFARE_LIST = "sys:union:welfare:list";

    /** 节日福利维护：新建 / 编辑 / 发放 / 关闭（1976） */
    public static final String UNION_WELFARE_MANAGE = "sys:union:welfare:manage";

    /** 工会活动查看（含报名名单）（1977） */
    public static final String UNION_ACTIVITY_LIST = "sys:union:activity:list";

    /** 工会活动维护：新建 / 编辑 / 发布 / 关闭 / 取消（1978） */
    public static final String UNION_ACTIVITY_MANAGE = "sys:union:activity:manage";

    /** 职代会提案查看（1979） */
    public static final String UNION_PROPOSAL_LIST = "sys:union:proposal:list";

    /** 提案办理：立案 / 办理 / 反馈（1980） */
    public static final String UNION_PROPOSAL_MANAGE = "sys:union:proposal:manage";

    /**
     * 私有构造器：常量类，禁止实例化。
     */
    private PartyPermissions() {
    }
}
