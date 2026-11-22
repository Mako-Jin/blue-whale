package com.bluewhale.mes.masterdata.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalTime;

/**
 * MES 车间主数据保存请求
 *
 * <p>各类型共用字段，服务层按类型做必填与引用校验。</p>
 *
 * @author BlueWhale
 */
@Data
public class MesMasterDataSaveRequest {

    /** 编码 */
    @NotBlank(message = "{validation.mes.mes-master-data.code-required}")
    @Size(max = 64, message = "{validation.mes.mes-master-data.code-size}")
    private String code;

    /** 名称 */
    @NotBlank(message = "{validation.mes.mes-master-data.name-required}")
    @Size(max = 128, message = "{validation.mes.mes-master-data.name-size}")
    private String name;

    /** 状态：ENABLED/DISABLED */
    @Pattern(regexp = "ENABLED|DISABLED", message = "{validation.mes.mes-master-data.status-pattern}")
    private String status;

    /** 备注 */
    @Size(max = 255, message = "{validation.mes.mes-master-data.remark-size}")
    private String remark;

    /** 所属车间 ID */
    private Long workshopId;

    /** 所属产线 ID */
    private Long lineId;

    /** 所属工作中心 ID */
    private Long workCenterId;

    /** 工作中心类型：MACHINE/LINE/CELL */
    @Pattern(regexp = "MACHINE|LINE|CELL", message = "{validation.mes.mes-master-data.center-type-pattern}")
    private String centerType;

    /** 每班产能 */
    @DecimalMin(value = "0", message = "{validation.mes.mes-master-data.capacity-per-shift-min}")
    private BigDecimal capacityPerShift;

    /** 车间位置 */
    @Size(max = 255, message = "{validation.mes.mes-master-data.address-size}")
    private String address;

    /** 车间负责人 */
    @Size(max = 64, message = "{validation.mes.mes-master-data.manager-name-size}")
    private String managerName;

    /** 班组长 */
    @Size(max = 64, message = "{validation.mes.mes-master-data.leader-name-size}")
    private String leaderName;

    /** 班次开始时间 */
    private LocalTime startTime;

    /** 班次结束时间 */
    private LocalTime endTime;

    /** 是否跨日：0 否，1 是 */
    @Min(value = 0, message = "{validation.mes.mes-master-data.cross-day-min}")
    @Max(value = 1, message = "{validation.mes.mes-master-data.cross-day-max}")
    private Integer crossDay;

    /** 工序类型：PROCESS/INSPECT/PACK */
    @Pattern(regexp = "PROCESS|INSPECT|PACK", message = "{validation.mes.mes-master-data.operation-type-pattern}")
    private String operationType;

    /** 标准工时（分钟） */
    @DecimalMin(value = "0", message = "{validation.mes.mes-master-data.standard-minutes-min}")
    private BigDecimal standardMinutes;

    /** 分类 */
    @Size(max = 64, message = "{validation.mes.mes-master-data.category-size}")
    private String category;

    /** 是否影响 OEE 可用率：0 否，1 是 */
    @Min(value = 0, message = "{validation.mes.mes-master-data.affect-availability-min}")
    @Max(value = 1, message = "{validation.mes.mes-master-data.affect-availability-max}")
    private Integer affectAvailability;
}
