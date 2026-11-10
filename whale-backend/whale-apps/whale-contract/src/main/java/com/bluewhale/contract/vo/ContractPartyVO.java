package com.bluewhale.contract.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 合同相对方视图对象
 *
 * @author BlueWhale
 */
@Data
public class ContractPartyVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键ID（字符串返回） */
    private String id;

    /** 合同ID（字符串返回） */
    private String contractId;

    /** 相对方类型 */
    private String partyType;

    /** 相对方名称 */
    private String name;

    /** 统一社会信用代码 */
    private String creditCode;

    /** 联系人 */
    private String contact;

    /** 联系电话 */
    private String phone;

    /** 签署人用户ID（字符串返回） */
    private String signerUserId;

    /** 签署人姓名 */
    private String signerName;
}
