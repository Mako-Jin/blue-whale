package com.bluewhale.party.analytics.domain.model;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 看板指标编码（编码即契约）。
 *
 * <p>编码一旦落库就进入导出文件与历史曲线，因此只增不改：新增指标用新编码，
 * 口径变化用新编码并把旧编码标为停用，而不是让同一条历史曲线在中途换算法。</p>
 *
 * <p>党员结构与学历属于分布指标，分桶编码直接并入 metric_type（如
 * {@code MEMBER_AGE_30_39}），避免再引入 bucket 列并改动唯一键。</p>
 *
 * @author BlueWhale
 */
public enum MetricType {

    /** 在册党员数 */
    MEMBER_TOTAL("在册党员数", false),

    /** 党员年龄结构（分桶并入编码） */
    MEMBER_AGE_UNDER_30("30 岁以下党员", false),
    MEMBER_AGE_30_39("30–39 岁党员", false),
    MEMBER_AGE_40_49("40–49 岁党员", false),
    MEMBER_AGE_50_59("50–59 岁党员", false),
    MEMBER_AGE_60_PLUS("60 岁及以上党员", false),
    MEMBER_AGE_UNKNOWN("年龄未知党员", false),

    /** 党员党龄结构 */
    PARTY_AGE_UNDER_5("5 年以下党龄", false),
    PARTY_AGE_5_10("5–10 年党龄", false),
    PARTY_AGE_11_20("11–20 年党龄", false),
    PARTY_AGE_21_30("21–30 年党龄", false),
    PARTY_AGE_31_PLUS("31 年及以上党龄", false),
    PARTY_AGE_UNKNOWN("党龄未知党员", false),

    /** 党员学历结构 */
    EDUCATION_DOCTOR("博士", false),
    EDUCATION_MASTER("硕士", false),
    EDUCATION_BACHELOR("本科", false),
    EDUCATION_COLLEGE("大专", false),
    EDUCATION_SECONDARY("中专及以下", false),
    EDUCATION_OTHER("其他学历", false),
    EDUCATION_UNKNOWN("学历未知", false),

    /** 党费收缴率（金额口径） */
    FEE_COLLECTION_AMOUNT("党费收缴率（金额）", true),

    /** 党费收缴率（账单口径） */
    FEE_COLLECTION_BILL("党费收缴率（账单）", true),

    /** 三会一课开展率 */
    ACTIVITY_HOLD("三会一课开展率", true),

    /** 组织生活参会率 */
    ACTIVITY_ATTENDANCE("组织生活参会率", true),

    /** 待转正预备党员数 */
    PROBATION_DUE("待转正预备党员数", false),

    /** 换届到期支部占比 */
    BRANCH_TERM_DUE("换届到期支部占比", true),

    /** 工会会员覆盖率 */
    UNION_COVERAGE("工会会员覆盖率", true),

    /** 工会活动参与率（年度累计） */
    UNION_ACTIVITY_PARTICIPATION("工会活动参与率", true),

    /** 提案办结率（年度累计） */
    PROPOSAL_CLOSED("提案办结率", true);

    /** 指标名称（导出与看板展示） */
    private final String label;

    /** 是否展示比率（false 表示纯计数，看板只显示数） */
    private final boolean ratio;

    /** 编码 → 枚举索引 */
    private static final Map<String, MetricType> INDEX = new LinkedHashMap<>();

    static {
        for (MetricType type : values()) {
            INDEX.put(type.name(), type);
        }
    }

    /**
     * 构造指标类型。
     *
     * @param label 中文名称
     * @param ratio 是否展示比率
     */
    MetricType(String label, boolean ratio) {
        this.label = label;
        this.ratio = ratio;
    }

    /**
     * 指标名称。
     *
     * @return 中文名称
     */
    public String getLabel() {
        return label;
    }

    /**
     * 是否展示比率。
     *
     * @return 展示比率返回 true
     */
    public boolean isRatio() {
        return ratio;
    }

    /**
     * 按编码解析；未知编码按纯计数处理，名称回退为编码本身。
     *
     * <p>历史快照里可能存在已停用编码，看板与导出遇到未知编码不能抛错，
     * 否则一条历史数据就能让整页打不开。</p>
     *
     * @param code 指标编码
     * @return 指标类型
     */
    public static MetricType parse(String code) {
        if (code == null) {
            return null;
        }
        return INDEX.get(code.trim().toUpperCase(java.util.Locale.ROOT));
    }

    /**
     * 指标名称（未知编码回退为编码）。
     *
     * @param code 指标编码
     * @return 展示名称
     */
    public static String labelOf(String code) {
        MetricType type = parse(code);
        return type == null ? code : type.getLabel();
    }

    /**
     * 是否展示比率（未知编码按纯计数）。
     *
     * @param code 指标编码
     * @return 展示比率返回 true
     */
    public static boolean isRatio(String code) {
        MetricType type = parse(code);
        return type != null && type.isRatio();
    }
}
