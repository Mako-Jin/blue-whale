package com.bluewhale.crm.sales.domain.model;

import com.bluewhale.crm.common.CrmErrorCode;
import lombok.Data;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Locale;

/**
 * 销售预测快照（按月 / 季，按个人 / 部门唯一）。
 *
 * <p>预测口径：管道 = 全部未关闭商机金额；最佳 = 谈判阶段商机金额；
 * 承诺 = 已赢单金额 + 概率 ≥ 90 的未关闭商机金额；加权 = Σ 金额 × 概率 / 100。
 * 手工调整只允许修改管道 / 最佳 / 承诺，并保留调整记录。</p>
 *
 * @author BlueWhale
 */
@Data
public class ForecastSnapshot {

    /** 金额小数位 */
    private static final int AMOUNT_SCALE = 2;

    /** 调整字段：管道 */
    public static final String FIELD_PIPELINE = "PIPELINE";

    /** 调整字段：最佳 */
    public static final String FIELD_BEST = "BEST";

    /** 调整字段：承诺 */
    public static final String FIELD_COMMIT = "COMMIT";

    /** 主键 */
    private Long id;

    /** 周期 */
    private String period;

    /** 周期类型 */
    private ForecastPeriodType periodType;

    /** 范围类型 */
    private ForecastScopeType scopeType;

    /** 负责人（USER 范围） */
    private Long ownerId;

    /** 归属部门（DEPT 范围） */
    private Long deptId;

    /** 管道金额 */
    private BigDecimal pipelineAmount;

    /** 最佳金额 */
    private BigDecimal bestAmount;

    /** 承诺金额 */
    private BigDecimal commitAmount;

    /** 概率加权金额 */
    private BigDecimal weightedAmount;

    /** 未关闭商机数 */
    private Integer opportunityCount;

    /** 是否经过手工调整 */
    private Boolean manualAdjusted;

    /** 备注 */
    private String remark;

    /** 生成人 */
    private Long createdBy;

    /** 乐观锁版本号 */
    private Integer lockVersion;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /**
     * 写入聚合结果并清除手工调整标记。
     *
     * @param pipeline 管道金额
     * @param best 最佳金额
     * @param commit 承诺金额
     * @param weighted 加权金额
     * @param count 未关闭商机数
     * @param at 生成时间
     */
    public void applyAggregation(BigDecimal pipeline, BigDecimal best, BigDecimal commit,
                                 BigDecimal weighted, int count, LocalDateTime at) {
        this.pipelineAmount = scale(pipeline);
        this.bestAmount = scale(best);
        this.commitAmount = scale(commit);
        this.weightedAmount = scale(weighted);
        this.opportunityCount = count;
        this.manualAdjusted = Boolean.FALSE;
        this.updateTime = at;
    }

    /**
     * 规范化调整字段码。
     *
     * @param fieldCode 字段码
     * @return 规范化字段码
     */
    public static String normalizeField(String fieldCode) {
        if (fieldCode == null || fieldCode.isBlank()) {
            throw CrmErrorCode.exception(CrmErrorCode.FORECAST_ADJUST_INVALID);
        }
        String text = fieldCode.trim().toUpperCase(Locale.ROOT);
        if (!FIELD_PIPELINE.equals(text) && !FIELD_BEST.equals(text) && !FIELD_COMMIT.equals(text)) {
            throw CrmErrorCode.exception(CrmErrorCode.FORECAST_ADJUST_INVALID);
        }
        return text;
    }

    /**
     * 查询指定字段当前金额。
     *
     * @param fieldCode 调整字段码
     * @return 当前金额
     */
    public BigDecimal amountOf(String fieldCode) {
        return switch (normalizeField(fieldCode)) {
            case FIELD_PIPELINE -> nullSafe(pipelineAmount);
            case FIELD_BEST -> nullSafe(bestAmount);
            default -> nullSafe(commitAmount);
        };
    }

    /**
     * 手工调整指定字段金额。
     *
     * @param fieldCode 调整字段码
     * @param amount 调整后金额
     */
    public void adjust(String fieldCode, BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) < 0) {
            throw CrmErrorCode.exception(CrmErrorCode.FORECAST_ADJUST_INVALID);
        }
        BigDecimal value = scale(amount);
        switch (normalizeField(fieldCode)) {
            case FIELD_PIPELINE -> this.pipelineAmount = value;
            case FIELD_BEST -> this.bestAmount = value;
            default -> this.commitAmount = value;
        }
        this.manualAdjusted = Boolean.TRUE;
    }

    /**
     * 金额保留两位小数。
     *
     * @param amount 金额
     * @return 结果
     */
    private BigDecimal scale(BigDecimal amount) {
        return (amount == null ? BigDecimal.ZERO : amount).setScale(AMOUNT_SCALE, RoundingMode.HALF_UP);
    }

    /**
     * 空值归零。
     *
     * @param amount 金额
     * @return 非空金额
     */
    private BigDecimal nullSafe(BigDecimal amount) {
        return amount == null ? BigDecimal.ZERO.setScale(AMOUNT_SCALE, RoundingMode.HALF_UP) : amount;
    }
}
