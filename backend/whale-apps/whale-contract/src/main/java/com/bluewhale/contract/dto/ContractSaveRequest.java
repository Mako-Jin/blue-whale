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
    @NotBlank(message = "合同标题不能为空")
    @Size(max = 255, message = "合同标题长度不能超过 255")
    private String title;

    /** 合同类别 */
    @NotBlank(message = "合同类别不能为空")
    @Size(max = 32, message = "合同类别长度不能超过 32")
    private String category;

    /** 合同金额 */
    @DecimalMin(value = "0", message = "合同金额不能为负数")
    private BigDecimal amount;

    /** 币种，默认 CNY */
    @Size(max = 8, message = "币种长度不能超过 8")
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
    @Size(max = 32, message = "业务类型长度不能超过 32")
    private String bizType;

    /** 弱关联业务ID */
    @Size(max = 64, message = "业务ID长度不能超过 64")
    private String bizId;

    /** 备注 */
    @Size(max = 255, message = "备注长度不能超过 255")
    private String remark;

    /** 相对方列表 */
    private List<@Valid ContractPartyRequest> parties;
}
