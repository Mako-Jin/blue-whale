package com.bluewhale.crm.customer.application.view;

import com.bluewhale.crm.customer.domain.model.DuplicateCandidate;
import lombok.Data;

/**
 * 客户查重候选视图。
 *
 * @author BlueWhale
 */
@Data
public class DuplicateCandidateView {

    /** 客户 ID */
    private Long id;

    /** 客户编码 */
    private String customerCode;

    /** 客户名称 */
    private String name;

    /** 等级 */
    private String level;

    /** 负责人 */
    private Long ownerId;

    /** 冲突级别：HARD / SOFT */
    private String severity;

    /** 命中字段 */
    private String matchedFields;

    /**
     * 由领域模型构建视图。
     *
     * @param candidate 查重候选
     * @return 查重视图
     */
    public static DuplicateCandidateView from(DuplicateCandidate candidate) {
        DuplicateCandidateView view = new DuplicateCandidateView();
        view.setId(candidate.getCustomer().getId());
        view.setCustomerCode(candidate.getCustomer().getCustomerCode());
        view.setName(candidate.getCustomer().getName());
        view.setLevel(candidate.getCustomer().getLevel());
        view.setOwnerId(candidate.getCustomer().getOwnerId());
        view.setSeverity(candidate.getSeverity());
        view.setMatchedFields(candidate.getMatchedFields());
        return view;
    }
}
