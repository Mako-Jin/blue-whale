package com.bluewhale.oa.approval.domain.repository;

import com.bluewhale.oa.approval.domain.model.ApprovalEffect;

import java.util.List;
import java.util.Optional;

/**
 * 审批业务副作用仓储。
 *
 * @author BlueWhale
 */
public interface ApprovalEffectRepository {

    /**
     * 查询审批实例的副作用记录。
     *
     * @param approvalInstanceId 审批实例ID
     * @return 副作用列表
     */
    List<ApprovalEffect> listByInstance(Long approvalInstanceId);

    /**
     * 按实例与类型查询副作用。
     *
     * @param approvalInstanceId 审批实例ID
     * @param effectType 副作用类型
     * @return 副作用
     */
    Optional<ApprovalEffect> findByInstanceAndType(Long approvalInstanceId, String effectType);

    /**
     * 新增副作用；同实例同类型已存在时不重复写入。
     *
     * @param effect 副作用
     * @return 新增返回 {@code true}，已存在返回 {@code false}
     */
    boolean insertIfAbsent(ApprovalEffect effect);

    /**
     * 更新副作用执行状态。
     *
     * @param effect 副作用
     */
    void update(ApprovalEffect effect);
}
