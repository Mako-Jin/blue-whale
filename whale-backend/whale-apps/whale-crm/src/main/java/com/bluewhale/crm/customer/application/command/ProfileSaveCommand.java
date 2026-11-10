package com.bluewhale.crm.customer.application.command;

import com.bluewhale.crm.customer.domain.model.DecisionChainEntry;
import lombok.Data;

import java.util.List;

/**
 * 客户画像保存命令。
 *
 * @author BlueWhale
 */
@Data
public class ProfileSaveCommand {

    /** 客户偏好 */
    private String preference;

    /** 痛点 */
    private String painPoints;

    /** 决策链 */
    private List<DecisionChainEntry> decisionChain;

    /** 关键人说明 */
    private String keyPerson;

    /** 敏感备注 */
    private String sensitiveRemark;
}
