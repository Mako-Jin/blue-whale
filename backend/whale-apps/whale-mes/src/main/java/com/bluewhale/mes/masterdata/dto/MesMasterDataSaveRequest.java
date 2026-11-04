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
    @NotBlank(message = "编码不能为空")
    @Size(max = 64, message = "编码长度不能超过 64")
    private String code;

    /** 名称 */
    @NotBlank(message = "名称不能为空")
    @Size(max = 128, message = "名称长度不能超过 128")
    private String name;

    /** 状态：ENABLED/DISABLED */
    @Pattern(regexp = "ENABLED|DISABLED", message = "状态只能为 ENABLED 或 DISABLED")
    private String status;

    /** 备注 */
    @Size(max = 255, message = "备注长度不能超过 255")
    private String remark;

    /** 所属车间 ID */
    private Long workshopId;

    /** 所属产线 ID */
    private Long lineId;

    /** 所属工作中心 ID */
    private Long workCenterId;

    /** 工作中心类型：MACHINE/LINE/CELL */
    @Pattern(regexp = "MACHINE|LINE|CELL", message = "工作中心类型只能为 MACHINE/LINE/CELL")
    private String centerType;

    /** 每班产能 */
    @DecimalMin(value = "0", message = "每班产能不能为负数")
    private BigDecimal capacityPerShift;

    /** 车间位置 */
    @Size(max = 255, message = "车间位置长度不能超过 255")
    private String address;

    /** 车间负责人 */
    @Size(max = 64, message = "车间负责人长度不能超过 64")
    private String managerName;

    /** 班组长 */
    @Size(max = 64, message = "班组长长度不能超过 64")
    private String leaderName;

    /** 班次开始时间 */
    private LocalTime startTime;

    /** 班次结束时间 */
    private LocalTime endTime;

    /** 是否跨日：0 否，1 是 */
    @Min(value = 0, message = "跨日标识只能为 0 或 1")
    @Max(value = 1, message = "跨日标识只能为 0 或 1")
    private Integer crossDay;

    /** 工序类型：PROCESS/INSPECT/PACK */
    @Pattern(regexp = "PROCESS|INSPECT|PACK", message = "工序类型只能为 PROCESS/INSPECT/PACK")
    private String operationType;

    /** 标准工时（分钟） */
    @DecimalMin(value = "0", message = "标准工时不能为负数")
    private BigDecimal standardMinutes;

    /** 分类 */
    @Size(max = 64, message = "分类长度不能超过 64")
    private String category;

    /** 是否影响 OEE 可用率：0 否，1 是 */
    @Min(value = 0, message = "影响可用率标识只能为 0 或 1")
    @Max(value = 1, message = "影响可用率标识只能为 0 或 1")
    private Integer affectAvailability;
}
