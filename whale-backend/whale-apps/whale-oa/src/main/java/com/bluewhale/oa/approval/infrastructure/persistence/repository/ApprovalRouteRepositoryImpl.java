package com.bluewhale.oa.approval.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bluewhale.oa.approval.domain.model.ApprovalRoute;
import com.bluewhale.oa.approval.domain.repository.ApprovalRouteRepository;
import com.bluewhale.oa.approval.infrastructure.persistence.entity.ApprovalRouteEntity;
import com.bluewhale.oa.approval.infrastructure.persistence.mapper.ApprovalRouteMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 审批人快照仓储实现（MyBatis-Plus）。
 *
 * @author BlueWhale
 */
@Repository
@RequiredArgsConstructor
public class ApprovalRouteRepositoryImpl implements ApprovalRouteRepository {

    private final ApprovalRouteMapper approvalRouteMapper;

    @Override
    public List<ApprovalRoute> listByInstance(Long approvalInstanceId) {
        return approvalRouteMapper.selectList(new LambdaQueryWrapper<ApprovalRouteEntity>()
                        .eq(ApprovalRouteEntity::getApprovalInstanceId, approvalInstanceId)
                        .orderByAsc(ApprovalRouteEntity::getNodeKey)
                        .orderByAsc(ApprovalRouteEntity::getSortNo))
                .stream().map(this::toDomain).toList();
    }

    @Override
    public void insertAll(List<ApprovalRoute> routes) {
        for (ApprovalRoute route : routes) {
            ApprovalRouteEntity entity = new ApprovalRouteEntity();
            entity.setApprovalInstanceId(route.getApprovalInstanceId());
            entity.setNodeKey(route.getNodeKey());
            entity.setNodeName(route.getNodeName());
            entity.setAssigneeId(route.getAssigneeId());
            entity.setAssigneeAccount(route.getAssigneeAccount());
            entity.setResolverType(route.getResolverType());
            entity.setSortNo(route.getSortNo());
            try {
                approvalRouteMapper.insert(entity);
                route.setId(entity.getId());
            } catch (DuplicateKeyException exception) {
                // 快照幂等：同一实例同一节点同一审批人只保留一条
                route.setId(null);
            }
        }
    }

    /**
     * 实体转领域模型。
     *
     * @param entity 实体
     * @return 领域模型
     */
    private ApprovalRoute toDomain(ApprovalRouteEntity entity) {
        ApprovalRoute route = new ApprovalRoute();
        route.setId(entity.getId());
        route.setApprovalInstanceId(entity.getApprovalInstanceId());
        route.setNodeKey(entity.getNodeKey());
        route.setNodeName(entity.getNodeName());
        route.setAssigneeId(entity.getAssigneeId());
        route.setAssigneeAccount(entity.getAssigneeAccount());
        route.setResolverType(entity.getResolverType());
        route.setSortNo(entity.getSortNo() == null ? 0 : entity.getSortNo());
        return route;
    }
}
