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
    @NotNull(message = "工会会员不能为空")
    private Long unionMemberId;

    /** 慰问类型：DIFFICULTY / CONDOLENCE / MEDICAL / EDUCATION / OTHER */
    @NotBlank(message = "慰问类型不能为空")
    private String reliefType;

    /** 申请金额 */
    @NotNull(message = "申请金额不能为空")
    @DecimalMin(value = "0.01", message = "申请金额必须大于 0")
    private BigDecimal amount;

    /** 申请事由 */
    @NotBlank(message = "申请事由不能为空")
    @Size(max = 500, message = "申请事由不得超过 500 字符")
    private String reason;

    /** 备注 */
    @Size(max = 255, message = "备注不得超过 255 字符")
    private String remark;
}
