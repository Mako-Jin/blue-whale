package com.bluewhale.oa.culture.domain.model;

import lombok.Data;

import java.time.LocalDate;

/**
 * 员工档案文化展示所需最小字段。
 *
 * @author BlueWhale
 */
@Data
public class CultureEmployeeProfile {

    /** 用户ID */
    private Long userId;

    /** 部门ID */
    private Long deptId;

    /** 生日 */
    private LocalDate birthday;

    /** 入职日期 */
    private LocalDate hireDate;
}
