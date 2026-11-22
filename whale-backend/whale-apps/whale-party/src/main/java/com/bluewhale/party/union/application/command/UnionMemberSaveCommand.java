package com.bluewhale.party.union.application.command;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 工会会员入会 / 编辑命令。
 *
 * <p>刻意不接收 {@code memberNo} 与 {@code status}：会员编号由取号器生成，状态只由
 * 「入会 / 退会」两个业务动作改变。若允许页面直接改这两个字段，退会终态就可能被编辑绕开。</p>
 *
 * @author BlueWhale
 */
@Data
public class UnionMemberSaveCommand {

    /** 员工编号（引用 ERP） */
    @NotBlank(message = "{validation.party.union-member.employee-no-required}")
    @Size(max = 64, message = "{validation.party.union-member.employee-no-size}")
    private String employeeNo;

    /** 平台用户 ID（未开通账号可为空） */
    private Long userId;

    /** 会员姓名 */
    @NotBlank(message = "{validation.party.union-member.member-name-required}")
    @Size(max = 64, message = "{validation.party.union-member.member-name-size}")
    private String memberName;

    /** 所属部门名称（ERP 快照） */
    @Size(max = 128, message = "{validation.party.union-member.dept-name-size}")
    private String deptName;

    /** 入会日期（不晚于今天由领域层校验） */
    @NotNull(message = "{validation.party.union-member.join-date-required}")
    private LocalDate joinDate;

    /** 会费基数（月收入口径，可为 0） */
    @DecimalMin(value = "0.00", message = "{validation.party.union-member.dues-base-min}")
    private BigDecimal duesBase;

    /** 会费缴至月份（yyyy-MM） */
    @Pattern(regexp = "^\\d{4}-(0[1-9]|1[0-2])$", message = "{validation.party.union-member.dues-paid-to-pattern}")
    private String duesPaidTo;

    /** 备注 */
    @Size(max = 255, message = "{validation.party.union-member.remark-size}")
    private String remark;
}
