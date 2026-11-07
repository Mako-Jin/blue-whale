package com.bluewhale.mes.dispatch.vo;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 排程计划视图对象。
 *
 * <p>分页列表不返回时段明细，详情接口返回 {@code slots}。</p>
 *
 * @author BlueWhale
 */
@Data
public class MesSchedulePlanVO {

    /** 主键 ID */
    private Long id;

    /** 排程计划号 */
    private String planNo;

    /** 排程日期 */
    private LocalDate planDate;

    /** 工单 ID */
    private Long workOrderId;

    /** 工单编码 */
    private String workOrderCode;

    /** 状态：DRAFT/LOCKED/CANCELLED */
    private String status;

    /** 排程人 */
    private String plannedBy;

    /** 备注 */
    private String remark;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 时段明细（仅详情返回） */
    private List<MesScheduleSlotVO> slots;
}
