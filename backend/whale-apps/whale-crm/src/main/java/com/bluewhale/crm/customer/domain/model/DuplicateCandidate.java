package com.bluewhale.crm.customer.domain.model;

import lombok.Data;

/**
 * 客户查重候选。
 *
 * @author BlueWhale
 */
@Data
public class DuplicateCandidate {

    /** 命中的客户 */
    private Customer customer;

    /** 冲突级别：HARD 阻断 / SOFT 可确认 */
    private String severity;

    /** 命中字段（名称 / 税号 / 域名 / 电话 / 邮箱） */
    private String matchedFields;

    /**
     * 是否硬冲突。
     *
     * @return 硬冲突返回 true
     */
    public boolean hard() {
        return "HARD".equals(severity);
    }
}
