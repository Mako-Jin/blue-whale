package com.bluewhale.contract.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 合同相对方请求
 *
 * @author BlueWhale
 */
@Data
public class ContractPartyRequest {

    /** 相对方类型：A / B / OTHER */
    @NotBlank(message = "相对方类型不能为空")
    @Size(max = 8, message = "相对方类型长度不能超过 8")
    private String partyType;

    /** 相对方名称 */
    @NotBlank(message = "相对方名称不能为空")
    @Size(max = 128, message = "相对方名称长度不能超过 128")
    private String name;

    /** 统一社会信用代码 */
    @Size(max = 64, message = "统一社会信用代码长度不能超过 64")
    private String creditCode;

    /** 联系人 */
    @Size(max = 64, message = "联系人长度不能超过 64")
    private String contact;

    /** 联系电话 */
    @Size(max = 32, message = "联系电话长度不能超过 32")
    private String phone;

    /** 签署人用户ID（外部相对方留空） */
    private Long signerUserId;

    /** 签署人姓名 */
    @Size(max = 64, message = "签署人姓名长度不能超过 64")
    private String signerName;
}
