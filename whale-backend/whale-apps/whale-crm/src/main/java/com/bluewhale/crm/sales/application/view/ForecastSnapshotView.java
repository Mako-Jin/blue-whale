package com.bluewhale.crm.sales.application.view;

import com.bluewhale.crm.sales.domain.model.ForecastSnapshot;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 销售预测快照视图。
 *
 * @author BlueWhale
 */
@Data
public class ForecastSnapshotView {

    /** 快照 ID */
    private Long id;

    /** 周期 */
    private String period;

    /** 周期类型 */
    private String periodType;

    /** 范围类型 */
    private String scopeType;

    /** 负责人 */
    private Long ownerId;

    /** 归属部门 */
    private Long deptId;

    /** 管道金额 */
    private BigDecimal pipelineAmount;

    /** 最佳金额 */
    private BigDecimal bestAmount;

    /** 承诺金额 */
    private BigDecimal commitAmount;

    /** 加权金额 */
    private BigDecimal weightedAmount;

    /** 未关闭商机数 */
    private Integer opportunityCount;

    /** 是否手工调整 */
    private Boolean manualAdjusted;

    /** 备注 */
    private String remark;

    /** 生成人 */
    private Long createdBy;

    /** 生成 / 更新时间 */
    private LocalDateTime updateTime;

    /**
     * 领域模型转视图。
     *
     * @param snapshot 快照
     * @return 视图
     */
    public static ForecastSnapshotView from(ForecastSnapshot snapshot) {
        ForecastSnapshotView view = new ForecastSnapshotView();
        view.setId(snapshot.getId());
        view.setPeriod(snapshot.getPeriod());
        view.setPeriodType(snapshot.getPeriodType() == null ? null : snapshot.getPeriodType().name());
        view.setScopeType(snapshot.getScopeType() == null ? null : snapshot.getScopeType().name());
        view.setOwnerId(snapshot.getOwnerId());
        view.setDeptId(snapshot.getDeptId());
        view.setPipelineAmount(snapshot.getPipelineAmount());
        view.setBestAmount(snapshot.getBestAmount());
        view.setCommitAmount(snapshot.getCommitAmount());
        view.setWeightedAmount(snapshot.getWeightedAmount());
        view.setOpportunityCount(snapshot.getOpportunityCount());
        view.setManualAdjusted(snapshot.getManualAdjusted());
        view.setRemark(snapshot.getRemark());
        view.setCreatedBy(snapshot.getCreatedBy());
        view.setUpdateTime(snapshot.getUpdateTime());
        return view;
    }
}
