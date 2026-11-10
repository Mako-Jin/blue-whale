package com.bluewhale.pdm.change.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bluewhale.pdm.change.dto.PdmChgApprovalCallbackRequest;
import com.bluewhale.pdm.change.entity.PdmChgOrder;
import com.bluewhale.pdm.change.entity.PdmChgRequest;
import com.bluewhale.pdm.change.mapper.PdmChgOrderMapper;
import com.bluewhale.pdm.change.mapper.PdmChgRequestMapper;
import com.bluewhale.pdm.change.service.PdmChgApprovalCallbackService;
import com.bluewhale.pdm.change.support.PdmChgApprovalStatus;
import com.bluewhale.pdm.change.support.PdmChgBizType;
import com.bluewhale.pdm.change.support.PdmChgOrderStatus;
import com.bluewhale.pdm.change.support.PdmChgRequestStatus;
import com.bluewhale.pdm.change.support.PdmChgTraceAction;
import com.bluewhale.pdm.change.support.PdmChgTraceRecorder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 变更审批回调服务实现
 *
 * <p>按审批实例ID定位 ECO/ECR；相同状态重复回调直接返回，不重复流转、
 * 不重复写追溯。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PdmChgApprovalCallbackServiceImpl implements PdmChgApprovalCallbackService {

    /** 撤销时的默认审批意见 */
    private static final String WITHDRAWN_COMMENT = "审批已撤销";

    private final PdmChgOrderMapper chgOrderMapper;

    private final PdmChgRequestMapper chgRequestMapper;

    private final PdmChgTraceRecorder traceRecorder;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void handle(PdmChgApprovalCallbackRequest request) {
        String targetStatus = PdmChgApprovalStatus.fromWorkflow(request.getStatus());
        PdmChgOrder order = findOrder(request.getInstanceId());
        if (order != null) {
            handleOrder(order, targetStatus, request);
            return;
        }
        PdmChgRequest requestEntity = findRequest(request.getInstanceId());
        if (requestEntity != null) {
            handleRequest(requestEntity, targetStatus, request);
            return;
        }
        log.info("审批回调未匹配任何变更业务，忽略: instanceId={}", request.getInstanceId());
    }

    /**
     * 处理变更单回调。
     */
    private void handleOrder(PdmChgOrder order, String targetStatus, PdmChgApprovalCallbackRequest request) {
        if (targetStatus.equals(order.getApprovalStatus())) {
            return;
        }
        boolean approving = PdmChgOrderStatus.APPROVING.equals(order.getStatus());
        order.setApprovalStatus(targetStatus);
        order.setApprovalSyncTime(LocalDateTime.now());
        if (PdmChgApprovalStatus.APPROVED.equals(targetStatus) && approving) {
            order.setStatus(PdmChgOrderStatus.APPROVED);
            order.setApproveBy(request.getOperatorId());
            order.setApproveTime(LocalDateTime.now());
            order.setApproveComment(blankToNull(request.getComment()));
            traceRecorder.recordImpact(PdmChgBizType.ECO, order.getId(), order.getEcoNo(),
                    PdmChgTraceAction.APPROVE, request.getOperatorId(), order.getImpactJson(),
                    text(request.getComment(), "变更单审批通过"));
        } else if (PdmChgApprovalStatus.REJECTED.equals(targetStatus) && approving) {
            order.setStatus(PdmChgOrderStatus.REJECTED);
            order.setApproveBy(request.getOperatorId());
            order.setApproveTime(LocalDateTime.now());
            order.setApproveComment(text(request.getComment(), "审批驳回"));
            traceRecorder.recordImpact(PdmChgBizType.ECO, order.getId(), order.getEcoNo(),
                    PdmChgTraceAction.REJECT, request.getOperatorId(), order.getImpactJson(),
                    order.getApproveComment());
        } else if (PdmChgApprovalStatus.WITHDRAWN.equals(targetStatus)) {
            if (PdmChgOrderStatus.DRAFT.equals(order.getStatus())
                    || PdmChgOrderStatus.ASSESSING.equals(order.getStatus())
                    || PdmChgOrderStatus.APPROVING.equals(order.getStatus())) {
                order.setStatus(PdmChgOrderStatus.CANCELLED);
            }
            traceRecorder.recordBiz(PdmChgBizType.ECO, order.getId(), order.getEcoNo(),
                    PdmChgTraceAction.CANCEL, request.getOperatorId(), WITHDRAWN_COMMENT);
        }
        chgOrderMapper.updateById(order);
    }

    /**
     * 处理变更申请回调。
     */
    private void handleRequest(PdmChgRequest ecr, String targetStatus,
                               PdmChgApprovalCallbackRequest request) {
        if (targetStatus.equals(ecr.getApprovalStatus())) {
            return;
        }
        boolean submitted = PdmChgRequestStatus.SUBMITTED.equals(ecr.getStatus());
        ecr.setApprovalStatus(targetStatus);
        ecr.setApprovalSyncTime(LocalDateTime.now());
        if (PdmChgApprovalStatus.APPROVED.equals(targetStatus) && submitted) {
            ecr.setStatus(PdmChgRequestStatus.APPROVED);
            ecr.setApproveBy(request.getOperatorId());
            ecr.setApproveTime(LocalDateTime.now());
            ecr.setApproveComment(blankToNull(request.getComment()));
            traceRecorder.recordImpact(PdmChgBizType.ECR, ecr.getId(), ecr.getChangeNo(),
                    PdmChgTraceAction.APPROVE, request.getOperatorId(), ecr.getImpactJson(),
                    text(request.getComment(), "变更申请审批通过"));
        } else if ((PdmChgApprovalStatus.REJECTED.equals(targetStatus)
                || PdmChgApprovalStatus.WITHDRAWN.equals(targetStatus)) && submitted) {
            ecr.setStatus(PdmChgRequestStatus.REJECTED);
            ecr.setApproveBy(request.getOperatorId());
            ecr.setApproveTime(LocalDateTime.now());
            ecr.setApproveComment(text(request.getComment(),
                    PdmChgApprovalStatus.WITHDRAWN.equals(targetStatus) ? WITHDRAWN_COMMENT : "审批驳回"));
            traceRecorder.recordImpact(PdmChgBizType.ECR, ecr.getId(), ecr.getChangeNo(),
                    PdmChgTraceAction.REJECT, request.getOperatorId(), ecr.getImpactJson(),
                    ecr.getApproveComment());
        }
        chgRequestMapper.updateById(ecr);
    }

    /**
     * 按审批实例ID查询变更单。
     */
    private PdmChgOrder findOrder(Long instanceId) {
        List<PdmChgOrder> list = chgOrderMapper.selectList(new LambdaQueryWrapper<PdmChgOrder>()
                .eq(PdmChgOrder::getApprovalInstanceId, instanceId)
                .last("LIMIT 1"));
        return list.isEmpty() ? null : list.get(0);
    }

    /**
     * 按审批实例ID查询变更申请。
     */
    private PdmChgRequest findRequest(Long instanceId) {
        List<PdmChgRequest> list = chgRequestMapper.selectList(new LambdaQueryWrapper<PdmChgRequest>()
                .eq(PdmChgRequest::getApprovalInstanceId, instanceId)
                .last("LIMIT 1"));
        return list.isEmpty() ? null : list.get(0);
    }

    /**
     * 空串转 null。
     */
    private static String blankToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    /**
     * 取默认文本。
     */
    private static String text(String value, String fallback) {
        return StringUtils.hasText(value) ? value.trim() : fallback;
    }
}
