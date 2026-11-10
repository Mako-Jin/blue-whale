package com.bluewhale.erp.masterdata.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 会计科目实体，对应表 {@code erp_account}。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("erp_account")
public class AccountEntity extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 科目编码 */
    private String code;

    /** 科目名称 */
    private String name;

    /** 科目类型 */
    private String accountType;

    /** 余额方向 */
    private String direction;

    /** 上级科目编码 */
    private String parentCode;

    /** 是否末级科目（库列 leaf_flag） */
    @TableField("leaf_flag")
    private Boolean leaf;

    /** 状态 */
    private String status;

    /** 备注 */
    private String remark;
}
