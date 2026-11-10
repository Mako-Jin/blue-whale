package com.bluewhale.oa.administrative.domain.model;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 司机主数据。
 *
 * @author BlueWhale
 */
@Data
public class Driver {

    /** 主键ID */
    private Long id;

    /** 姓名 */
    private String name;

    /** 联系电话 */
    private String phone;

    /** 驾驶证号 */
    private String licenseNo;

    /** 准驾车型 */
    private String licenseType;

    /** 状态 ENABLED/DISABLED */
    private String status;

    /** 备注 */
    private String remark;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
