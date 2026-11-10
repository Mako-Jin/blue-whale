package com.bluewhale.crm.customer.application.view;

import com.bluewhale.crm.common.CrmMasking;
import com.bluewhale.crm.customer.domain.model.CustomerProfile;
import com.bluewhale.crm.customer.domain.model.DecisionChainEntry;
import lombok.Data;

import java.util.List;

/**
 * 客户画像视图。
 *
 * @author BlueWhale
 */
@Data
public class CustomerProfileView {

    /** 主键 */
    private Long id;

    /** 客户 ID */
    private Long customerId;

    /** 偏好 */
    private String preference;

    /** 痛点 */
    private String painPoints;

    /** 决策链 */
    private List<DecisionChainEntry> decisionChain;

    /** 关键人说明 */
    private String keyPerson;

    /** 敏感备注（按权限脱敏） */
    private String sensitiveRemark;

    /** 版本号 */
    private Integer version;

    /**
     * 由领域模型构建视图。
     *
     * @param profile 画像
     * @param allowPlain 是否允许查看敏感明文
     * @return 画像视图
     */
    public static CustomerProfileView from(CustomerProfile profile, boolean allowPlain) {
        CustomerProfileView view = new CustomerProfileView();
        view.setId(profile.getId());
        view.setCustomerId(profile.getCustomerId());
        view.setPreference(profile.getPreference());
        view.setPainPoints(profile.getPainPoints());
        view.setDecisionChain(profile.getDecisionChain());
        view.setKeyPerson(profile.getKeyPerson());
        view.setSensitiveRemark(CrmMasking.note(profile.getSensitiveRemark(), allowPlain));
        view.setVersion(profile.getVersion());
        return view;
    }

    /**
     * 构建空画像视图（客户尚无画像）。
     *
     * @param customerId 客户 ID
     * @return 空画像
     */
    public static CustomerProfileView empty(Long customerId) {
        CustomerProfileView view = new CustomerProfileView();
        view.setCustomerId(customerId);
        view.setDecisionChain(List.of());
        view.setVersion(0);
        return view;
    }
}
