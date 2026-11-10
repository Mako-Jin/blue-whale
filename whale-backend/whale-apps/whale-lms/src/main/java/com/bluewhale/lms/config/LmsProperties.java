package com.bluewhale.lms.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 学习培训考试应用运行参数（配置前缀 {@code whale.lms}）。
 *
 * <p>{@link #demandGatePassed} 是业务功能总开关。Wave 5.87 已确认需求门禁通过
 * （REQ-FUNC-087..090 / REQ-ARCH-025 / REQ-SEC-035），默认放开 M0 与 M1 接口；
 * 生产环境仍可通过环境变量 {@code WHALE_LMS_DEMAND_GATE_PASSED=false} 一键回退到
 * 仅暴露 {@code /lms/meta} 的维护态。</p>
 *
 * @author BlueWhale
 */
@Data
@ConfigurationProperties(prefix = "whale.lms")
public class LmsProperties {

    /** 需求门禁是否通过；false 时仅暴露 /lms/meta */
    private boolean demandGatePassed = true;

    /** 课程单页上限 */
    private int coursePageLimit = 100;

    /** 单课程章节数上限 */
    private int chapterLimit = 100;

    /** 单章节课件数上限 */
    private int wareLimitPerChapter = 20;

    /** 报进度百分比上限 */
    private int progressMaxPercent = 100;

    /** 场次结束后允许自助签到的宽限分钟数（负数按 0 处理） */
    private int checkinGraceMinutes = 30;

    /** 单次必修分配的人数上限 */
    private int assignBatchLimit = 500;

    /** 题库单页上限 */
    private int questionPageLimit = 100;

    /** 试卷单页上限 */
    private int paperPageLimit = 100;

    /** 单份试卷题目数上限 */
    private int paperQuestionLimit = 200;

    /** 考试单页上限 */
    private int examPageLimit = 100;

    /** 作答记录 / 成绩单页上限 */
    private int attemptPageLimit = 100;

    /** 单次提交判分的题目数上限 */
    private int gradeItemLimit = 200;

    /** 证书、学分规则与台账的单页上限 */
    private int certPageLimit = 100;

    /** 证书编号取号冲突的最大重试次数 */
    private int certIssueRetry = 3;

    /** 员工目录同步的单页条数（同时不得超过 ERP 内网契约上限 500） */
    private int memberSyncPageSize = 100;

    /** 员工快照与通知日志的单页上限 */
    private int memberPageLimit = 100;

    /** 通知日志单页上限 */
    private int notifyPageLimit = 100;

    /** 单轮催办的培训班数上限（避免一次跑太久拖住后续调度） */
    private int urgeBatchSize = 200;

    /** 指标快照分页上限 */
    private int metricPageLimit = 100;
}
