package com.bluewhale.oa.culture.domain.model;

import lombok.Data;

import java.time.LocalDate;

/**
 * 今日生日 / 入职周年（由员工档案派生）。
 *
 * @author BlueWhale
 */
@Data
public class BirthdayCelebration {

    /** 用户ID */
    private Long userId;

    /** 用户姓名 */
    private String userName;

    /** 部门ID */
    private Long deptId;

    /** 生日 */
    private LocalDate birthday;

    /** 入职日期 */
    private LocalDate hireDate;

    /** 周年年限（生日可为空，入职周年须大于 0） */
    private Integer years;

    /** 类型：BIRTHDAY / ANNIVERSARY */
    private String type;
}
