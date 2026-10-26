package com.bluewhale.contract.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 合同相对方实体，对应表 {@code ct_contract_party}。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ct_contract_party")
public class CtContractParty extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 合同ID */
    private Long contractId;

    /** 相对方类型：A 甲方 / B 乙方 / OTHER 其他 */
    private String partyType;

    /** 相对方名称 */
    private String name;

    /** 统一社会信用代码 */
    private String creditCode;

    /** 联系人 */
    private String contact;

    /** 联系电话 */
    private String phone;

    /** 签署人用户ID（外部相对方留空） */
    private Long signerUserId;

    /** 签署人姓名 */
    private String signerName;
}
