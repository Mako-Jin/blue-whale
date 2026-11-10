package com.bluewhale.oa.attendance.domain.model;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 打卡地点。
 *
 * @author BlueWhale
 */
@Data
public class AttendanceLocation {

    private Long id;

    /** 地点编码 */
    private String code;

    /** 地点名称 */
    private String name;

    /** 纬度 */
    private BigDecimal latitude;

    /** 经度 */
    private BigDecimal longitude;

    /** 允许半径（米） */
    private Integer radiusMeters;

    /** 启停 */
    private Integer enabled;

    /** 备注 */
    private String remark;
}
