package com.bluewhale.mes.masterdata.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * MES 车间主数据视图
 *
 * <p>各类型共用视图，未使用字段为 null。</p>
 *
 * @author BlueWhale
 */
@Data
public class MesMasterDataVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键 */
    private Long id;

    /** 编码 */
    private String code;

    /** 名称 */
    private String name;

    /** 状态：ENABLED/DISABLED */
    private String status;

    /** 备注 */
    private String remark;

    /** 所属车间 ID */
    private Long workshopId;

    /** 所属产线 ID */
    private Long lineId;

    /** 所属工作中心 ID */
    private Long workCenterId;

    /** 工作中心类型 */
    private String centerType;

    /** 每班产能 */
    private BigDecimal capacityPerShift;

    /** 车间位置 */
    private String address;

    /** 车间负责人 */
    private String managerName;

    /** 班组长 */
    private String leaderName;

    /** 班次开始时间 */
    private LocalTime startTime;

    /** 班次结束时间 */
    private LocalTime endTime;

    /** 是否跨日 */
    private Integer crossDay;

    /** 工序类型 */
    private String operationType;

    /** 标准工时（分钟） */
    private BigDecimal standardMinutes;

    /** 分类 */
    private String category;

    /** 是否影响 OEE 可用率 */
    private Integer affectAvailability;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
