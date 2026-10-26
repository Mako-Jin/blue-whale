package com.bluewhale.contract.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 合同版本实体，对应表 {@code ct_contract_version}。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ct_contract_version")
public class CtContractVersion extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 合同ID */
    private Long contractId;

    /** 版本号（从 1 起） */
    private Integer versionNo;

    /** 该版本正文文件ID */
    private Long fileId;

    /** 正文文件 SHA-256 */
    private String sha256;

    /** 变更说明 */
    private String changeNote;

    /** 版本状态：DRAFT/SUPERSEDED/EFFECTIVE */
    private String status;
}
