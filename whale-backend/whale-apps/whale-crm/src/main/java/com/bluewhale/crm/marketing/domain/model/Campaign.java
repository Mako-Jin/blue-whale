package com.bluewhale.crm.marketing.domain.model;

import com.bluewhale.crm.common.CrmErrorCode;
import lombok.Data;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 营销活动聚合根。
 *
 * <p>状态机 {@code DRAFT → RUNNING → ENDED}；{@code CANCELLED} 仅允许从
 * {@code DRAFT} 或 {@code RUNNING} 取消。预算不得为负、进行中不可修改预算，
 * 结束日期不得早于开始日期；ROI = 归因赢单金额 ÷ 实际费用（费用为 0 时 ROI 为 0）。</p>
 *
 * @author BlueWhale
 */
@Data
public class Campaign {

    /** ROI 小数位 */
    private static final int ROI_SCALE = 4;

    /** 主键 */
    private Long id;

    /** 租户 ID */
    private Long tenantId;

    /** 活动编码 */
    private String campaignCode;

    /** 活动名称 */
    private String campaignName;

    /** 活动类型 */
    private CampaignType campaignType;

    /** 活动状态 */
    private CampaignStatus status;

    /** 主要渠道 */
    private String channel;

    /** 预算 */
    private BigDecimal budget;

    /** 实际费用 */
    private BigDecimal actualCost;

    /** 计划开始日期 */
    private LocalDate plannedStart;

    /** 计划结束日期 */
    private LocalDate plannedEnd;

    /** 锁定人群快照 ID */
    private Long audienceId;

    /** 目标人群数量 */
    private Integer audienceCount;

    /** 归因线索数量 */
    private Integer attributedLeadCount;

    /** 归因赢单金额 */
    private BigDecimal attributedWonAmount;

    /** 投入产出比 */
    private BigDecimal roi;

    /** 负责人 */
    private Long ownerId;

    /** 归属部门 */
    private Long deptId;

    /** 乐观锁版本 */
    private Integer lockVersion;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /**
     * 校验预算非负。
     *
     * @param value 预算或费用
     */
    public static void validateBudget(BigDecimal value) {
        if (value != null && value.signum() < 0) {
            throw CrmErrorCode.exception(CrmErrorCode.CAMPAIGN_BUDGET_INVALID, "营销活动预算不得为负数");
        }
    }

    /**
     * 校验活动周期。
     *
     * @param start 计划开始日期
     * @param end 计划结束日期
     */
    public static void validatePeriod(LocalDate start, LocalDate end) {
        if (start != null && end != null && end.isBefore(start)) {
            throw CrmErrorCode.exception(CrmErrorCode.CAMPAIGN_BUDGET_INVALID, "活动结束日期不能早于开始日期");
        }
    }

    /**
     * 启动活动：{@code DRAFT → RUNNING}。
     */
    public void start() {
        if (status != CampaignStatus.DRAFT) {
            throw CrmErrorCode.exception(CrmErrorCode.CAMPAIGN_STATE_CONFLICT, "仅草稿活动允许启动");
        }
        this.status = CampaignStatus.RUNNING;
    }

    /**
     * 结束活动：{@code RUNNING → ENDED}。
     */
    public void end() {
        if (status != CampaignStatus.RUNNING) {
            throw CrmErrorCode.exception(CrmErrorCode.CAMPAIGN_STATE_CONFLICT, "仅进行中的活动允许结束");
        }
        this.status = CampaignStatus.ENDED;
    }

    /**
     * 取消活动：仅 {@code DRAFT} / {@code RUNNING} 可取消。
     */
    public void cancel() {
        if (status != CampaignStatus.DRAFT && status != CampaignStatus.RUNNING) {
            throw CrmErrorCode.exception(CrmErrorCode.CAMPAIGN_STATE_CONFLICT, "仅草稿或进行中的活动允许取消");
        }
        this.status = CampaignStatus.CANCELLED;
    }

    /**
     * 修改预算；活动进行中后预算不可改。
     *
     * @param next 新预算
     */
    public void changeBudget(BigDecimal next) {
        if (status == CampaignStatus.RUNNING) {
            throw CrmErrorCode.exception(CrmErrorCode.CAMPAIGN_STATE_CONFLICT, "活动进行中不允许修改预算");
        }
        validateBudget(next);
        this.budget = next;
    }

    /**
     * 绑定已锁定的人群快照；同一活动已绑定后不可替换。
     *
     * @param snapshotId 人群快照 ID
     * @param count 人群数量
     */
    public void applyAudience(Long snapshotId, Integer count) {
        if (this.audienceId != null && snapshotId != null && !this.audienceId.equals(snapshotId)) {
            throw CrmErrorCode.exception(CrmErrorCode.AUDIENCE_INVALID, "活动已锁定人群快照，不可变更");
        }
        this.audienceId = snapshotId;
        this.audienceCount = count == null ? 0 : count;
    }

    /**
     * 回填归因数据并复算 ROI。
     *
     * @param leadCount 归因线索数量
     * @param wonAmount 归因赢单金额
     * @param cost 实际费用
     */
    public void applyRoi(Integer leadCount, BigDecimal wonAmount, BigDecimal cost) {
        validateBudget(wonAmount);
        validateBudget(cost);
        this.attributedLeadCount = leadCount == null ? 0 : leadCount;
        this.attributedWonAmount = wonAmount == null ? BigDecimal.ZERO : wonAmount;
        this.actualCost = cost == null ? BigDecimal.ZERO : cost;
        recalculateRoi();
    }

    /**
     * 复算投入产出比：实际费用为 0 时 ROI 为 0。
     *
     * @return ROI（保留 4 位小数）
     */
    public BigDecimal recalculateRoi() {
        BigDecimal cost = actualCost == null ? BigDecimal.ZERO : actualCost;
        BigDecimal won = attributedWonAmount == null ? BigDecimal.ZERO : attributedWonAmount;
        if (cost.signum() == 0) {
            this.roi = BigDecimal.ZERO.setScale(ROI_SCALE, RoundingMode.HALF_UP);
        } else {
            this.roi = won.divide(cost, ROI_SCALE, RoundingMode.HALF_UP);
        }
        return this.roi;
    }
}
