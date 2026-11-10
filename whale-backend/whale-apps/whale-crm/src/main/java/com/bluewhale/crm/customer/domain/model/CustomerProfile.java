package com.bluewhale.crm.customer.domain.model;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 客户画像。
 *
 * @author BlueWhale
 */
@Data
public class CustomerProfile {

    /** 主键 */
    private Long id;

    /** 所属客户 */
    private Long customerId;

    /** 客户偏好 */
    private String preference;

    /** 痛点 */
    private String painPoints;

    /** 决策链 */
    private List<DecisionChainEntry> decisionChain = new ArrayList<>();

    /** 关键人说明 */
    private String keyPerson;

    /** 敏感备注 */
    private String sensitiveRemark;

    /** 版本号 */
    private Integer version;

    /**
     * 递增版本号。
     */
    public void increaseVersion() {
        this.version = version == null ? 1 : version + 1;
    }
}
