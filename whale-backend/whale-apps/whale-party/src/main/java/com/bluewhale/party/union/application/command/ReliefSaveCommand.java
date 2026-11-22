package com.bluewhale.party.union.application.command;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 慰问帮扶申请新建 / 编辑命令。
 *
 * <p>申请年度不在命令里：它按创建时的业务年度落库并由服务端计算，
 * 让页面传年度等于允许把去年的额度挪到今年用。</p>
 *
 * @author BlueWhale
 */
@Data
public class ReliefSaveCommand {

    /** 工会会员 ID */
    @NotNull(message = "{validation.party.relief.union-member-id-required}")
    private Long unionMemberId;

    /** 慰问类型：DIFFICULTY / CONDOLENCE / MEDICAL / EDUCATION / OTHER */
    @NotBlank(message = "{validation.party.relief.relief-type-required}")
    private String reliefType;

    /** 申请金额 */
    @NotNull(message = "{validation.party.relief.amount-required}")
    @DecimalMin(value = "0.01", message = "{validation.party.relief.amount-min}")
    private BigDecimal amount;

    /** 申请事由 */
    @NotBlank(message = "{validation.party.relief.reason-required}")
    @Size(max = 500, message = "{validation.party.relief.reason-size}")
    private String reason;

    /** 备注 */
    @Size(max = 255, message = "{validation.party.relief.remark-size}")
    private String remark;
}
