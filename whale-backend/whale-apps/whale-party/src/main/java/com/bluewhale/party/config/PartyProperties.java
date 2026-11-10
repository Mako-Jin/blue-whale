package com.bluewhale.party.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;

/**
 * 党政工会应用运行参数（配置前缀 {@code whale.party}）。
 *
 * <p>{@link #demandGatePassed} 是业务功能总开关。Wave 5.88 已确认需求门禁通过
 * （REQ-FUNC-094 / REQ-ARCH-026 / REQ-SEC-036），但 M-P1 起才逐个开放业务接口；
 * 生产环境可通过环境变量 {@code WHALE_PARTY_DEMAND_GATE_PASSED=false}
 * 一键回退到仅暴露 {@code /party/meta} 的维护态。</p>
 *
 * @author BlueWhale
 */
@Data
@ConfigurationProperties(prefix = "whale.party")
public class PartyProperties {

    /** 需求门禁是否通过；false 时仅暴露 /party/meta */
    private boolean demandGatePassed = true;

    /** 支部层级深度上限（防止上级支部成环时无限上溯） */
    private int branchMaxDepth = 10;

    /** 支部单页上限 */
    private int branchPageLimit = 100;

    /** 党员单页上限 */
    private int memberPageLimit = 100;

    /** 组织生活记录单页上限 */
    private int activityPageLimit = 100;

    /** 党建内容单页上限 */
    private int contentPageLimit = 50;

    /** 工会业务单页上限 */
    private int unionPageLimit = 100;

    /** 员工快照同步单页条数（对端 ERP 单页上限 500） */
    private int memberSyncPageSize = 100;

    /** 同一会员同年同类型慰问的已批次数上限（M-P3） */
    private int reliefCountLimit = 2;

    /** 同一会员同年同类型慰问的已批金额上限（元，M-P3） */
    private BigDecimal reliefAmountLimit = new BigDecimal("5000.00");

    /** 慰问审批流程定义编码（workflow，M-P3） */
    private String reliefDefinitionCode = "UNION_RELIEF";

    /** 看板趋势展示天数（含统计日往前推算，M-P4） */
    private int metricTrendDays = 30;

    /** 单次导出统计区间上限（天，M-P4） */
    private int exportMaxDays = 366;

    /** 支部换届届期（年，M-P4） */
    private int branchTermYears = 5;

    /** 换届到期提前提醒天数（M-P4） */
    private int branchTermReminderDays = 90;

    /** 预备党员转正提前提醒天数（M-P4） */
    private int probationReminderDays = 30;

    /** 活动通知提前天数（仅通知该窗口内即将开始的活动，M-P4） */
    private int activityNoticeDays = 7;

    /** 通知留痕单页上限（M-P4） */
    private int noticePageLimit = 100;

    /** 通知扫描定时表达式（M-P4，默认每天 09:00） */
    private String noticeCron = "0 0 9 * * *";

    /** ERP 员工目录同步单页条数（M-P4） */
    private int employeeSyncPageSize = 500;
}
