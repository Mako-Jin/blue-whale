package com.bluewhale.party.member.application.command;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 党员档案新建 / 编辑命令。
 *
 * <p>不接收 {@code memberNo}（由编号流水生成）与 {@code status}（由转出、停籍等业务动作改变）：
 * 让前端能直接改状态，等于把「转出」这件有日期、有前置校验的事降级成一次普通编辑。</p>
 *
 * @author BlueWhale
 */
@Data
public class MemberSaveCommand {

    /** 员工编号（引用 ERP，一人一档） */
    @NotBlank(message = "{validation.party.member.employee-no-required}")
    @Size(max = 64, message = "{validation.party.member.employee-no-size}")
    private String employeeNo;

    /** 平台用户 ID（未开通账号可空） */
    private Long userId;

    /** 党员姓名 */
    @NotBlank(message = "{validation.party.member.member-name-required}")
    @Size(max = 64, message = "{validation.party.member.member-name-size}")
    private String memberName;

    /** 所属支部 ID */
    @NotNull(message = "{validation.party.member.branch-id-required}")
    private Long branchId;

    /** 政治面貌：FULL_MEMBER / PROBATIONARY */
    private String politicalStatus;

    /** 出生日期（年龄结构统计，可空） */
    private LocalDate birthDate;

    /** 学历编码（DOCTOR/MASTER/BACHELOR/COLLEGE/SECONDARY/OTHER，可空） */
    @Size(max = 32, message = "{validation.party.member.education-size}")
    private String education;

    /** 入党日期 */
    @NotNull(message = "{validation.party.member.join-party-date-required}")
    private LocalDate joinPartyDate;

    /** 党费基数 */
    @DecimalMin(value = "0", message = "{validation.party.member.dues-base-min}")
    private BigDecimal duesBase;

    /** 联系电话 */
    @Size(max = 32, message = "{validation.party.member.phone-size}")
    private String phone;

    /** 备注 */
    @Size(max = 255, message = "{validation.party.member.remark-size}")
    private String remark;
}
