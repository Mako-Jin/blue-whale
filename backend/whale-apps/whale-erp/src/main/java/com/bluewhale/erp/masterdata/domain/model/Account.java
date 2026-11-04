package com.bluewhale.erp.masterdata.domain.model;

import lombok.Data;

/**
 * 会计科目。
 *
 * <p>只有 {@code leaf=true} 且启用状态的科目才允许在凭证分录中使用；
 * 科目编码在租户内唯一。</p>
 *
 * @author BlueWhale
 */
@Data
public class Account {

    /** 主键 */
    private Long id;

    /** 科目编码 */
    private String code;

    /** 科目名称 */
    private String name;

    /** 科目类型：ASSET / LIABILITY / EQUITY / REVENUE / EXPENSE */
    private String accountType;

    /** 余额方向：DEBIT / CREDIT */
    private String direction;

    /** 上级科目编码 */
    private String parentCode;

    /** 是否末级科目 */
    private Boolean leaf;

    /** 状态：ENABLED / DISABLED */
    private String status;

    /** 备注 */
    private String remark;

    /**
     * 是否可用于记账：启用且为末级科目。
     *
     * @return 可用于记账返回 true
     */
    public boolean postable() {
        return MasterDataStatus.ENABLED.equals(status) && Boolean.TRUE.equals(leaf);
    }
}
