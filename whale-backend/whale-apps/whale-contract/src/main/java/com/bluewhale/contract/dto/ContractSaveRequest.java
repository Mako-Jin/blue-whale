package com.bluewhale.contract.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 合同新增/编辑请求
 *
 * @author BlueWhale
 */
@Data
public class ContractSaveRequest {

    /** 合同标题 */
    @NotBlank(message = "{validation.contract.contract.title-required}")
    @Size(max = 255, message = "{validation.contract.contract.title-size}")
    private String title;

    /** 合同类别 */
    @NotBlank(message = "{validation.contract.contract.category-required}")
    @Size(max = 32, message = "{validation.contract.contract.category-size}")
    private String category;

    /** 合同金额 */
    @DecimalMin(value = "0", message = "{validation.contract.contract.amount-min}")
    private BigDecimal amount;

    /** 币种，默认 CNY */
    @Size(max = 8, message = "{validation.contract.contract.currency-size}")
    private String currency;

    /** 负责人用户ID */
    private Long ownerId;

    /** 所属部门ID */
    private Long deptId;

    /** 合同正文文件ID */
    private Long fileId;

    /** 生效日期 */
    private LocalDate effectiveDate;

    /** 到期日期 */
    private LocalDate expireDate;

    /** 弱关联业务类型 */
    @Size(max = 32, message = "{validation.contract.contract.biz-type-size}")
    private String bizType;

    /** 弱关联业务ID */
    @Size(max = 64, message = "{validation.contract.contract.biz-id-size}")
    private String bizId;

    /** 备注 */
    @Size(max = 255, message = "{validation.contract.contract.remark-size}")
    private String remark;

    /** 相对方列表 */
    private List<@Valid ContractPartyRequest> parties;
}
