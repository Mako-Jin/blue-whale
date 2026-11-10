package com.bluewhale.crm.commerce.application.command;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * 回款计划整批保存命令。
 *
 * <p>按合同全量替换分期，分期金额合计必须等于合同金额。</p>
 *
 * @author BlueWhale
 */
@Data
public class CollectionPlanSaveCommand {

    /** CRM 合同台账 ID */
    @NotNull
    private Long contractId;

    /** 回款分期 */
    @Valid
    @NotEmpty
    private List<CollectionInstallmentCommand> installments;
}
