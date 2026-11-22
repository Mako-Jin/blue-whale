package com.bluewhale.pdm.integration.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.pdm.change.entity.PdmChgOrder;
import com.bluewhale.pdm.change.entity.PdmChgRequest;
import com.bluewhale.pdm.change.mapper.PdmChgOrderMapper;
import com.bluewhale.pdm.change.mapper.PdmChgRequestMapper;
import com.bluewhale.pdm.change.support.PdmChgOrderStatus;
import com.bluewhale.pdm.change.support.PdmChgRequestStatus;
import com.bluewhale.pdm.integration.dto.PdmIntReconcileQueryRequest;
import com.bluewhale.pdm.integration.entity.PdmIntReconcile;
import com.bluewhale.pdm.integration.entity.PdmIntSyncLog;
import com.bluewhale.pdm.integration.mapper.PdmIntReconcileMapper;
import com.bluewhale.pdm.integration.mapper.PdmIntSyncLogMapper;
import com.bluewhale.pdm.integration.service.PdmIntReconcileService;
import com.bluewhale.pdm.integration.service.PdmIntSyncService;
import com.bluewhale.pdm.integration.support.PdmIntAction;
import com.bluewhale.pdm.integration.support.PdmIntChannel;
import com.bluewhale.pdm.integration.support.PdmIntDirection;
import com.bluewhale.pdm.integration.support.PdmIntReconcileStatus;
import com.bluewhale.pdm.integration.vo.PdmIntReconcileVO;
import com.bluewhale.pdm.integration.vo.PdmIntSyncLogVO;
import com.bluewhale.pdm.part.entity.PdmBomRelease;
import com.bluewhale.pdm.part.mapper.PdmBomReleaseMapper;
import com.bluewhale.pdm.part.service.PdmBomService;
import com.bluewhale.pdm.part.vo.PdmBomReleaseVO;
import com.bluewhale.pdm.process.entity.PdmPrcPublish;
import com.bluewhale.pdm.process.mapper.PdmPrcPublishMapper;
import com.bluewhale.pdm.process.service.PdmPrcRouteService;
import com.bluewhale.pdm.process.support.PdmPrcPublishStatus;
import com.bluewhale.pdm.process.vo.PdmPrcRouteVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 集成对账服务实现
 *
 * @author BlueWhale
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PdmIntReconcileServiceImpl implements PdmIntReconcileService {

    /** 单页最大条数 */
    private static final long MAX_PAGE_SIZE = 200L;

    /** OA 业务类型：变更申请 */
    private static final String OA_TYPE_REQUEST = "REQUEST";

    /** OA 业务类型：变更单 */
    private static final String OA_TYPE_ORDER = "ORDER";

    private final PdmIntReconcileMapper reconcileMapper;

    private final PdmIntSyncLogMapper syncLogMapper;

    private final PdmIntSyncService syncService;

    private final PdmBomService bomService;

    private final PdmBomReleaseMapper bomReleaseMapper;

    private final PdmPrcPublishMapper publishMapper;

    private final PdmPrcRouteService routeService;

    private final PdmChgRequestMapper requestMapper;

    private final PdmChgOrderMapper orderMapper;

    @Override
    public PageResult<PdmIntReconcileVO> page(PdmIntReconcileQueryRequest request) {
        LambdaQueryWrapper<PdmIntReconcile> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(StringUtils.hasText(request.getChannel()), PdmIntReconcile::getChannel, request.getChannel());
        wrapper.eq(StringUtils.hasText(request.getBizType()), PdmIntReconcile::getBizType, request.getBizType());
        wrapper.like(StringUtils.hasText(request.getBizNo()), PdmIntReconcile::getBizNo, request.getBizNo());
        wrapper.eq(StringUtils.hasText(request.getMatchStatus()),
                PdmIntReconcile::getMatchStatus, request.getMatchStatus());
        wrapper.orderByDesc(PdmIntReconcile::getCheckedAt);
        long size = Math.min(Math.max(request.getSize(), 1L), MAX_PAGE_SIZE);
        Page<PdmIntReconcile> page = reconcileMapper.selectPage(new Page<>(request.getCurrent(), size), wrapper);
        List<PdmIntReconcileVO> records = new ArrayList<>(page.getRecords().size());
        for (PdmIntReconcile entity : page.getRecords()) {
            records.add(toVo(entity));
        }
        return PageResult.of(records, page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Override
    public List<PdmIntReconcileVO> reconcileBom() {
        List<PdmBomRelease> releases = bomReleaseMapper.selectList(
                new LambdaQueryWrapper<PdmBomRelease>().orderByDesc(PdmBomRelease::getCreateTime));
        List<PdmIntReconcileVO> results = new ArrayList<>(releases.size());
        for (PdmBomRelease release : releases) {
            PdmIntSyncLog latest = latestLog(PdmIntChannel.ERP, release.getBomNo(), release.getVersionNo(),
                    List.of(PdmIntAction.BOM_PULL, PdmIntAction.BOM_NOTIFY));
            String remoteChecksum = latest == null ? null : latest.getChecksum();
            String remoteStatus = latest == null ? null : latest.getStatus();
            String match;
            String summary;
            if (latest == null) {
                match = PdmIntReconcileStatus.MISSING;
                summary = "ERP 尚未拉取该版本快照";
            } else if (!StringUtils.hasText(release.getChecksum())
                    || !release.getChecksum().equals(remoteChecksum)) {
                match = PdmIntReconcileStatus.DIFF;
                summary = "校验和不一致，ERP 快照可能过期";
            } else {
                match = PdmIntReconcileStatus.MATCH;
                summary = null;
            }
            results.add(upsert(PdmIntChannel.ERP, "BOM", release.getBomNo(), release.getBomId(),
                    release.getVersionNo(), "RELEASED", remoteStatus, release.getChecksum(), remoteChecksum,
                    match, summary));
        }
        return results;
    }

    @Override
    public List<PdmIntReconcileVO> reconcileRoute() {
        List<PdmPrcPublish> publishes = publishMapper.selectList(
                new LambdaQueryWrapper<PdmPrcPublish>().orderByDesc(PdmPrcPublish::getCreateTime));
        List<PdmIntReconcileVO> results = new ArrayList<>(publishes.size());
        for (PdmPrcPublish publish : publishes) {
            String match;
            String summary;
            if (PdmPrcPublishStatus.FAILED.equals(publish.getStatus())) {
                match = PdmIntReconcileStatus.DIFF;
                summary = StringUtils.hasText(publish.getLastError())
                        ? publish.getLastError() : "MES 下发失败，可重试";
            } else {
                match = PdmIntReconcileStatus.MATCH;
                summary = null;
            }
            results.add(upsert(PdmIntChannel.MES, "ROUTE", publish.getRouteNo(), publish.getRouteId(),
                    publish.getVersionNo(), publish.getStatus(), publish.getStatus(), null,
                    publish.getMesRouteNo(), match, summary));
        }
        return results;
    }

    @Override
    public List<PdmIntReconcileVO> reconcileOa() {
        List<PdmIntReconcileVO> results = new ArrayList<>();
        List<PdmChgRequest> requests = requestMapper.selectList(
                new LambdaQueryWrapper<PdmChgRequest>().isNotNull(PdmChgRequest::getApprovalInstanceId));
        for (PdmChgRequest request : requests) {
            String[] verdict = judgeRequest(request.getStatus(), request.getApprovalStatus());
            results.add(upsert(PdmIntChannel.OA, "CHANGE", request.getChangeNo(), request.getId(), "",
                    request.getStatus(), request.getApprovalStatus(), null, null, verdict[0], verdict[1]));
        }
        List<PdmChgOrder> orders = orderMapper.selectList(
                new LambdaQueryWrapper<PdmChgOrder>().isNotNull(PdmChgOrder::getApprovalInstanceId));
        for (PdmChgOrder order : orders) {
            String[] verdict = judgeOrder(order.getStatus(), order.getApprovalStatus());
            results.add(upsert(PdmIntChannel.OA, "CHANGE", order.getEcoNo(), order.getId(), "",
                    order.getStatus(), order.getApprovalStatus(), null, null, verdict[0], verdict[1]));
        }
        return results;
    }

    @Override
    public PdmIntSyncLogVO notifyBom(String bomNo, String versionNo, Long operatorId) {
        PdmBomReleaseVO release = bomService.releases(bomNo).stream()
                .filter(item -> versionNo.equals(item.getVersionNo()))
                .findFirst()
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "biz.pdm.integration.b-o-m-publish-version-found", "BOM 发布版本不存在: {0}",
                                           versionNo));
        String key = "ERP:BOM_NOTIFY:" + bomNo + ":" + versionNo;
        return syncService.record(PdmIntChannel.ERP, PdmIntAction.BOM_NOTIFY, PdmIntDirection.OUTBOUND,
                "BOM", release.getId(), bomNo, versionNo, key, null, release.getChecksum(), null, null, operatorId);
    }

    @Override
    public PdmPrcRouteVO repushRoute(Long routeId, Long operatorId) {
        PdmPrcRouteVO route = routeService.retryPublish(routeId);
        String key = "MES:ROUTE:" + route.getRouteNo() + ":" + route.getVersionNo();
        syncService.record(PdmIntChannel.MES, PdmIntAction.ROUTE_PUSH, PdmIntDirection.OUTBOUND,
                "ROUTE", routeId, route.getRouteNo(), route.getVersionNo(), key, null,
                String.valueOf(routeId), null, null, operatorId);
        return route;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PdmIntReconcileVO compensateOa(String bizType, Long bizId, Long operatorId) {
        if (OA_TYPE_REQUEST.equalsIgnoreCase(bizType)) {
            return compensateRequest(bizId, operatorId);
        }
        if (OA_TYPE_ORDER.equalsIgnoreCase(bizType)) {
            return compensateOrder(bizId, operatorId);
        }
        throw new BizException(ErrorCode.PARAM_ERROR, "biz.pdm.integration.o-compensate-business-type-supports-request",
                      "OA 补偿业务类型仅支持 REQUEST/ORDER");
    }

    /**
     * 补偿变更申请状态。
     *
     * @param bizId      变更申请ID
     * @param operatorId 操作人
     * @return 对账结果
     */
    private PdmIntReconcileVO compensateRequest(Long bizId, Long operatorId) {
        PdmChgRequest request = bizId == null ? null : requestMapper.selectById(bizId);
        if (request == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "biz.pdm.integration.change-application-found", "变更申请不存在");
        }
        if (!List.of(PdmChgRequestStatus.DRAFT, PdmChgRequestStatus.SUBMITTED).contains(request.getStatus())) {
            throw new BizException(ErrorCode.PARAM_ERROR, "biz.pdm.integration.change-application-status-supported-o-compensate",
                          "当前变更申请状态不支持 OA 补偿");
        }
        String target = switch (request.getApprovalStatus() == null ? "" : request.getApprovalStatus()) {
            case "APPROVED" -> PdmChgRequestStatus.APPROVED;
            case "REJECTED" -> PdmChgRequestStatus.REJECTED;
            case "WITHDRAWN" -> PdmChgRequestStatus.DRAFT;
            case "RUNNING" -> PdmChgRequestStatus.SUBMITTED;
            default -> throw new BizException(ErrorCode.PARAM_ERROR, "biz.pdm.integration.o-approval-status-missing-unknown-cannot",
                                     "OA 审批状态缺失或未知，无法补偿");
        };
        String previous = request.getStatus();
        if (!target.equals(previous)) {
            request.setStatus(target);
            request.setApprovalSyncTime(LocalDateTime.now());
            if (PdmChgRequestStatus.APPROVED.equals(target) && request.getApproveTime() == null) {
                request.setApproveTime(LocalDateTime.now());
            }
            requestMapper.updateById(request);
        }
        recordCompensate(OA_TYPE_REQUEST, bizId, request.getChangeNo(), previous, target, operatorId);
        String[] verdict = judgeRequest(request.getStatus(), request.getApprovalStatus());
        return upsert(PdmIntChannel.OA, "CHANGE", request.getChangeNo(), request.getId(), "",
                request.getStatus(), request.getApprovalStatus(), null, null, verdict[0], verdict[1]);
    }

    /**
     * 补偿变更单状态。
     *
     * @param bizId      变更单ID
     * @param operatorId 操作人
     * @return 对账结果
     */
    private PdmIntReconcileVO compensateOrder(Long bizId, Long operatorId) {
        PdmChgOrder order = bizId == null ? null : orderMapper.selectById(bizId);
        if (order == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "biz.pdm.integration.change-order-found", "变更单不存在");
        }
        if (!List.of(PdmChgOrderStatus.ASSESSING, PdmChgOrderStatus.APPROVING).contains(order.getStatus())) {
            throw new BizException(ErrorCode.PARAM_ERROR, "biz.pdm.integration.change-order-status-supported-o-compensate", "当前变更单状态不支持 OA 补偿");
        }
        String target = switch (order.getApprovalStatus() == null ? "" : order.getApprovalStatus()) {
            case "APPROVED" -> PdmChgOrderStatus.APPROVED;
            case "REJECTED" -> PdmChgOrderStatus.REJECTED;
            case "WITHDRAWN" -> PdmChgOrderStatus.CANCELLED;
            case "RUNNING" -> PdmChgOrderStatus.APPROVING;
            default -> throw new BizException(ErrorCode.PARAM_ERROR, "biz.pdm.integration.o-approval-status-missing-unknown-cannot-2",
                                     "OA 审批状态缺失或未知，无法补偿");
        };
        String previous = order.getStatus();
        if (!target.equals(previous)) {
            order.setStatus(target);
            order.setApprovalSyncTime(LocalDateTime.now());
            if (PdmChgOrderStatus.APPROVED.equals(target) && order.getApproveTime() == null) {
                order.setApproveTime(LocalDateTime.now());
            }
            orderMapper.updateById(order);
        }
        recordCompensate(OA_TYPE_ORDER, bizId, order.getEcoNo(), previous, target, operatorId);
        String[] verdict = judgeOrder(order.getStatus(), order.getApprovalStatus());
        return upsert(PdmIntChannel.OA, "CHANGE", order.getEcoNo(), order.getId(), "",
                order.getStatus(), order.getApprovalStatus(), null, null, verdict[0], verdict[1]);
    }

    /**
     * 记录 OA 补偿日志。
     *
     * @param bizType    业务类型
     * @param bizId      业务ID
     * @param bizNo      业务编号
     * @param previous   原业务状态
     * @param target     补偿后状态
     * @param operatorId 操作人
     */
    private void recordCompensate(String bizType, Long bizId, String bizNo, String previous, String target,
                                  Long operatorId) {
        String key = "OA:COMPENSATE:" + bizType + ":" + bizId + ":" + target;
        String payload = "{\"from\":\"" + previous + "\",\"to\":\"" + target + "\"}";
        syncService.record(PdmIntChannel.OA, PdmIntAction.OA_COMPENSATE, PdmIntDirection.INBOUND,
                "CHANGE", bizId, bizNo, null, key, payload, null, null, null, operatorId);
    }

    /**
     * 判定变更申请一致性。
     *
     * @param status         业务状态
     * @param approvalStatus OA 审批状态
     * @return [结论, 差异说明]
     */
    private String[] judgeRequest(String status, String approvalStatus) {
        if (PdmChgRequestStatus.CLOSED.equals(status)) {
            return match();
        }
        String expected = switch (approvalStatus == null ? "" : approvalStatus) {
            case "APPROVED" -> PdmChgRequestStatus.APPROVED;
            case "REJECTED" -> PdmChgRequestStatus.REJECTED;
            case "WITHDRAWN" -> PdmChgRequestStatus.DRAFT;
            case "RUNNING" -> PdmChgRequestStatus.SUBMITTED;
            default -> null;
        };
        return verdict(status, approvalStatus, expected);
    }

    /**
     * 判定变更单一致性。
     *
     * @param status         业务状态
     * @param approvalStatus OA 审批状态
     * @return [结论, 差异说明]
     */
    private String[] judgeOrder(String status, String approvalStatus) {
        if (List.of(PdmChgOrderStatus.COMPLETED, PdmChgOrderStatus.CLOSED).contains(status)) {
            return match();
        }
        String expected = switch (approvalStatus == null ? "" : approvalStatus) {
            case "APPROVED" -> PdmChgOrderStatus.APPROVED;
            case "REJECTED" -> PdmChgOrderStatus.REJECTED;
            case "WITHDRAWN" -> PdmChgOrderStatus.CANCELLED;
            case "RUNNING" -> PdmChgOrderStatus.APPROVING;
            default -> null;
        };
        return verdict(status, approvalStatus, expected);
    }

    /**
     * 依据期望状态给出结论。
     *
     * @param status         业务状态
     * @param approvalStatus OA 审批状态
     * @param expected       期望业务状态
     * @return [结论, 差异说明]
     */
    private String[] verdict(String status, String approvalStatus, String expected) {
        if (expected == null) {
            return new String[]{PdmIntReconcileStatus.DIFF, "OA 审批状态缺失，无法判定一致性"};
        }
        if (expected.equals(status)) {
            return match();
        }
        return new String[]{PdmIntReconcileStatus.DIFF,
                "业务状态 " + status + " 与 OA 状态 " + approvalStatus + " 不一致"};
    }

    /**
     * 一致结论。
     *
     * @return [MATCH, null]
     */
    private String[] match() {
        return new String[]{PdmIntReconcileStatus.MATCH, null};
    }

    /**
     * 查询最近一次同步日志。
     *
     * @param channel   通道
     * @param bizNo     业务编号
     * @param versionNo 版本号
     * @param actions   动作集合
     * @return 最近日志，不存在返回 {@code null}
     */
    private PdmIntSyncLog latestLog(String channel, String bizNo, String versionNo, List<String> actions) {
        LambdaQueryWrapper<PdmIntSyncLog> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PdmIntSyncLog::getChannel, channel);
        wrapper.eq(PdmIntSyncLog::getBizNo, bizNo);
        wrapper.eq(PdmIntSyncLog::getVersionNo, versionNo);
        wrapper.in(PdmIntSyncLog::getAction, actions);
        wrapper.orderByDesc(PdmIntSyncLog::getCreateTime);
        wrapper.last("LIMIT 1");
        return syncLogMapper.selectOne(wrapper);
    }

    /**
     * 写入或更新对账结果。
     *
     * @param channel        通道
     * @param bizType        业务类型
     * @param bizNo          业务编号
     * @param bizId          业务主键
     * @param versionNo      版本号
     * @param localStatus    本地状态
     * @param remoteStatus   外部状态
     * @param localChecksum  本地校验和
     * @param remoteChecksum 外部校验和
     * @param matchStatus    结论
     * @param diffSummary    差异说明
     * @return 对账结果
     */
    private PdmIntReconcileVO upsert(String channel, String bizType, String bizNo, Long bizId, String versionNo,
                                     String localStatus, String remoteStatus, String localChecksum,
                                     String remoteChecksum, String matchStatus, String diffSummary) {
        String safeVersion = versionNo == null ? "" : versionNo;
        LambdaQueryWrapper<PdmIntReconcile> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PdmIntReconcile::getChannel, channel);
        wrapper.eq(PdmIntReconcile::getBizType, bizType);
        wrapper.eq(PdmIntReconcile::getBizNo, bizNo);
        wrapper.eq(PdmIntReconcile::getVersionNo, safeVersion);
        wrapper.last("LIMIT 1");
        PdmIntReconcile entity = reconcileMapper.selectOne(wrapper);
        boolean created = entity == null;
        if (created) {
            entity = new PdmIntReconcile();
            entity.setChannel(channel);
            entity.setBizType(bizType);
            entity.setBizNo(bizNo);
            entity.setVersionNo(safeVersion);
        }
        entity.setBizId(bizId);
        entity.setLocalStatus(localStatus);
        entity.setRemoteStatus(remoteStatus);
        entity.setLocalChecksum(localChecksum);
        entity.setRemoteChecksum(remoteChecksum);
        entity.setMatchStatus(matchStatus);
        entity.setDiffSummary(diffSummary == null ? null : truncate(diffSummary));
        entity.setCheckedAt(LocalDateTime.now());
        if (created) {
            reconcileMapper.insert(entity);
        } else {
            reconcileMapper.updateById(entity);
        }
        return toVo(entity);
    }

    /**
     * 截断差异说明到列宽。
     *
     * @param message 原始信息
     * @return 截断结果
     */
    private static String truncate(String message) {
        return message.length() <= 500 ? message : message.substring(0, 500);
    }

    /**
     * 实体转视图对象。
     *
     * @param entity 对账实体
     * @return 视图对象
     */
    private static PdmIntReconcileVO toVo(PdmIntReconcile entity) {
        PdmIntReconcileVO vo = new PdmIntReconcileVO();
        vo.setId(entity.getId());
        vo.setChannel(entity.getChannel());
        vo.setBizType(entity.getBizType());
        vo.setBizNo(entity.getBizNo());
        vo.setBizId(entity.getBizId());
        vo.setVersionNo(entity.getVersionNo());
        vo.setLocalStatus(entity.getLocalStatus());
        vo.setRemoteStatus(entity.getRemoteStatus());
        vo.setLocalChecksum(entity.getLocalChecksum());
        vo.setRemoteChecksum(entity.getRemoteChecksum());
        vo.setMatchStatus(entity.getMatchStatus());
        vo.setDiffSummary(entity.getDiffSummary());
        vo.setCheckedAt(entity.getCheckedAt());
        return vo;
    }
}
