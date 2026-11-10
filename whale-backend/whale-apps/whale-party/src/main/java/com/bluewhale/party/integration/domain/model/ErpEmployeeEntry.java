package com.bluewhale.party.integration.domain.model;

import lombok.Data;

import java.time.LocalDate;

/**
 * ERP 员工目录条目（只读契约的领域投影）。
 *
 * <p>只取覆盖率计算需要的字段：党员与工会覆盖率的分母是「在职员工总数」，
 * 工资、社保基数等敏感字段不进入本服务。</p>
 *
 * @author BlueWhale
 */
@Data
public class ErpEmployeeEntry {

    /** 员工编号 */
    private String employeeNo;

    /** 平台账号 ID（未开通为空） */
    private Long userId;

    /** 姓名 */
    private String name;

    /** 组织编码 */
    private String orgCode;

    /** 岗位 */
    private String position;

    /** 在职状态：ACTIVE / LEFT */
    private String status;

    /** 入职日期 */
    private LocalDate hireDate;

    /** 离职日期 */
    private LocalDate leaveDate;
}
