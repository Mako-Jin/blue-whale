package com.bluewhale.erp.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDate;

/**
 * ERP 内网员工目录只读视图。
 *
 * <p>刻意只输出 LMS 侧判断「谁在职、属于哪个组织、有没有平台账号」所需的最小字段集。
 * 员工视图里还有基本工资、津贴、社保基数等敏感信息，逐字段透传出去等于把薪酬数据
 * 复制到第二个库，因此这里另建契约而不是直接复用 {@code EmployeeView}。</p>
 *
 * @author BlueWhale
 */
@Data
public class ErpInternalEmployeeView implements Serializable {

    private static final long serialVersionUID = 1L;

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
