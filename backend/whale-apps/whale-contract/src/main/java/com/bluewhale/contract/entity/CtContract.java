package com.bluewhale.contract.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 合同主表实体，对应表 {@code ct_contract}。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ct_contract")
public class CtContract extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 合同编号（租户内唯一，HT-{类别码}-{yyyy}-{6位流水}） */
    private String code;

    /** 合同标题 */
    private String title;

    /** 合同类别：PURCHASE/SALES/SERVICE/LEASE/OTHER */
    private String category;

    /** 合同金额 */
    private BigDecimal amount;

    /** 币种 */
    private String currency;

    /** 负责人用户ID */
    private Long ownerId;

    /** 所属部门ID */
    private Long deptId;

    /** 状态：DRAFT/APPROVING/APPROVED/REJECTED/WITHDRAWN/SIGNING/SIGNED/EFFECTIVE/ARCHIVED/TERMINATED/VOID */
    private String status;

    /** 当前版本号 */
    private Integer currentVersion;

    /** 合同正文文件ID */
    private Long fileId;

    /** 生效日期 */
    private LocalDate effectiveDate;

    /** 到期日期 */
    private LocalDate expireDate;

    /** 归档条目ID */
    private Long archiveItemId;

    /** 弱关联业务类型 */
    private String bizType;

    /** 弱关联业务ID */
    private String bizId;

    /** 备注 */
    private String remark;
}
