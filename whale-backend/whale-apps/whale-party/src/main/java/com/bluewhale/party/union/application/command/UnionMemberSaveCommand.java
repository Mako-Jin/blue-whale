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
    @NotBlank(message = "员工编号不能为空")
    @Size(max = 64, message = "员工编号不得超过 64 字符")
    private String employeeNo;

    /** 平台用户 ID（未开通账号可为空） */
    private Long userId;

    /** 会员姓名 */
    @NotBlank(message = "会员姓名不能为空")
    @Size(max = 64, message = "会员姓名不得超过 64 字符")
    private String memberName;

    /** 所属部门名称（ERP 快照） */
    @Size(max = 128, message = "所属部门不得超过 128 字符")
    private String deptName;

    /** 入会日期（不晚于今天由领域层校验） */
    @NotNull(message = "入会日期不能为空")
    private LocalDate joinDate;

    /** 会费基数（月收入口径，可为 0） */
    @DecimalMin(value = "0.00", message = "会费基数不能为负数")
    private BigDecimal duesBase;

    /** 会费缴至月份（yyyy-MM） */
    @Pattern(regexp = "^\\d{4}-(0[1-9]|1[0-2])$", message = "会费缴至月份格式应为 yyyy-MM")
    private String duesPaidTo;

    /** 备注 */
    @Size(max = 255, message = "备注不得超过 255 字符")
    private String remark;
}
