package com.bluewhale.erp.qc.application.command;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 新增不合格品单命令（手工登记）。
 *
 * @author BlueWhale
 */
@Data
public class NcrCreateCommand {

    /** 来源类型：MANUAL */
    private String sourceType;

    /** 来源单据号 */
    @NotBlank
    private String sourceNo;

    /** 物料名称 */
    @NotBlank
    private String itemName;

    /** 批次号 */
    private String lotNo;

    /** 不合格数量 */
    private BigDecimal quantity;

    /** 缺陷描述 */
    @NotBlank
    private String defectDesc;

    /** 备注 */
    private String remark;
}
