package com.bluewhale.oa.attendance.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 原始打卡记录实体，对应表 {@code oa_att_record}。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("oa_att_record")
public class AttendanceRecordEntity extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 用户ID */
    private Long userId;

    /** 用户姓名快照 */
    private String userName;

    /** 打卡时间 */
    private LocalDateTime punchTime;

    /** 打卡类型：IN / OUT */
    private String punchType;

    /** 来源：APP / DEVICE / WEB */
    private String source;

    /** 纬度 */
    private BigDecimal latitude;

    /** 经度 */
    private BigDecimal longitude;

    /** 打卡地址 */
    private String address;

    /** 设备编码 */
    private String deviceCode;

    /** 是否补卡写入 */
    private Integer correction;

    /** 备注 */
    private String remark;
}
