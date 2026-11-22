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
    @NotBlank(message = "{validation.contract.contract-party.party-type-required}")
    @Size(max = 8, message = "{validation.contract.contract-party.party-type-size}")
    private String partyType;

    /** 相对方名称 */
    @NotBlank(message = "{validation.contract.contract-party.name-required}")
    @Size(max = 128, message = "{validation.contract.contract-party.name-size}")
    private String name;

    /** 统一社会信用代码 */
    @Size(max = 64, message = "{validation.contract.contract-party.credit-code-size}")
    private String creditCode;

    /** 联系人 */
    @Size(max = 64, message = "{validation.contract.contract-party.contact-size}")
    private String contact;

    /** 联系电话 */
    @Size(max = 32, message = "{validation.contract.contract-party.phone-size}")
    private String phone;

    /** 签署人用户ID（外部相对方留空） */
    private Long signerUserId;

    /** 签署人姓名 */
    @Size(max = 64, message = "{validation.contract.contract-party.signer-name-size}")
    private String signerName;
}
