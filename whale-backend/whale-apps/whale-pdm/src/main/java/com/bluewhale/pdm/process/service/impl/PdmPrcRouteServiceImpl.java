package com.bluewhale.pdm.process.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.tenant.TenantContext;
import com.bluewhale.pdm.document.entity.PdmDocDocument;
import com.bluewhale.pdm.document.entity.PdmDocVersion;
import com.bluewhale.pdm.document.mapper.PdmDocDocumentMapper;
import com.bluewhale.pdm.document.mapper.PdmDocVersionMapper;
import com.bluewhale.pdm.entity.PdmProduct;
import com.bluewhale.pdm.mapper.PdmProductMapper;
import com.bluewhale.pdm.process.client.PdmPrcMesClient;
import com.bluewhale.pdm.process.dto.PdmPrcMesPublishRequest;
import com.bluewhale.pdm.process.dto.PdmPrcRouteQueryRequest;
import com.bluewhale.pdm.process.dto.PdmPrcRouteSaveRequest;
import com.bluewhale.pdm.process.dto.PdmPrcSopSaveRequest;
import com.bluewhale.pdm.process.dto.PdmPrcStepSaveRequest;
import com.bluewhale.pdm.process.dto.PdmPrcToolingSaveRequest;
import com.bluewhale.pdm.process.entity.PdmPrcPublish;
import com.bluewhale.pdm.process.entity.PdmPrcRoute;
import com.bluewhale.pdm.process.entity.PdmPrcSop;
import com.bluewhale.pdm.process.entity.PdmPrcStep;
import com.bluewhale.pdm.process.entity.PdmPrcTooling;
import com.bluewhale.pdm.process.mapper.PdmPrcPublishMapper;
import com.bluewhale.pdm.process.mapper.PdmPrcRouteMapper;
import com.bluewhale.pdm.process.mapper.PdmPrcSopMapper;
import com.bluewhale.pdm.process.mapper.PdmPrcStepMapper;
import com.bluewhale.pdm.process.mapper.PdmPrcToolingMapper;
import com.bluewhale.pdm.process.service.PdmPrcRouteService;
import com.bluewhale.pdm.process.support.PdmPrcChecksum;
import com.bluewhale.pdm.process.support.PdmPrcPublishStatus;
import com.bluewhale.pdm.process.support.PdmPrcStatus;
import com.bluewhale.pdm.process.support.PdmPrcVersionNo;
import com.bluewhale.pdm.process.vo.PdmPrcPublishVO;
import com.bluewhale.pdm.process.vo.PdmPrcRouteSnapshotVO;
import com.bluewhale.pdm.process.vo.PdmPrcRouteVO;
import com.bluewhale.pdm.process.vo.PdmPrcSopVO;
import com.bluewhale.pdm.process.vo.PdmPrcStepVO;
import com.bluewhale.pdm.process.vo.PdmPrcToolingVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 工艺路线服务实现
 *
 * <p>草稿可维护工序、工装与 SOP；发布后冻结。SOP 只允许引用已发布文档版本。
 * 发布时向 MES 下发，失败只记录下发状态，不回滚路线发布结果。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PdmPrcRouteServiceImpl implements PdmPrcRouteService {

    /** 单页最大条数 */
    private static final long MAX_PAGE_SIZE = 200L;

    /** 失败原因最大长度（与 last_error 列宽一致） */
    private static final int MAX_ERROR_LENGTH = 500;

    /** 文档已发布状态 */
    private static final String DOC_RELEASED = "RELEASED";

    /** 零件启用状态 */
    private static final String PART_ENABLED = "ENABLED";

    /** 默认路线编号前缀 */
    private static final String ROUTE_NO_PREFIX = "RT-";

    private final PdmPrcRouteMapper routeMapper;

    private final PdmPrcStepMapper stepMapper;

    private final PdmPrcToolingMapper toolingMapper;

    private final PdmPrcSopMapper sopMapper;

    private final PdmPrcPublishMapper publishMapper;

    private final PdmProductMapper productMapper;

    private final PdmDocDocumentMapper docMapper;

    private final PdmDocVersionMapper docVersionMapper;

    private final PdmPrcMesClient mesClient;

    @Override
    public PageResult<PdmPrcRouteVO> page(PdmPrcRouteQueryRequest request) {
        LambdaQueryWrapper<PdmPrcRoute> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(StringUtils.hasText(request.getRouteNo()), PdmPrcRoute::getRouteNo, request.getRouteNo());
        wrapper.like(StringUtils.hasText(request.getPartCode()), PdmPrcRoute::getPartCode,
                request.getPartCode());
        wrapper.eq(StringUtils.hasText(request.getStatus()), PdmPrcRoute::getStatus, request.getStatus());
        wrapper.orderByDesc(PdmPrcRoute::getCreateTime).orderByDesc(PdmPrcRoute::getId);
        long size = Math.min(Math.max(request.getSize(), 1L), MAX_PAGE_SIZE);
        Page<PdmPrcRoute> page = routeMapper.selectPage(new Page<>(request.getCurrent(), size), wrapper);
        Map<Long, PdmPrcPublish> publishes = publishesOf(page.getRecords().stream()
                .map(PdmPrcRoute::getId)
                .collect(java.util.stream.Collectors.toSet()));
        List<PdmPrcRouteVO> records = page.getRecords().stream()
                .map(route -> toRouteVo(route, List.of(), publishes.get(route.getId())))
                .toList();
        return PageResult.of(records, page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Override
    public PdmPrcRouteVO detail(Long id) {
        PdmPrcRoute route = requireRoute(id);
        return assemble(route);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PdmPrcRouteVO create(PdmPrcRouteSaveRequest request, Long userId) {
        PdmProduct part = requirePart(request.getPartId());
        String routeNo = StringUtils.hasText(request.getRouteNo())
                ? request.getRouteNo().trim()
                : ROUTE_NO_PREFIX + part.getCode();
        List<PdmPrcRoute> existing = routeMapper.selectList(new LambdaQueryWrapper<PdmPrcRoute>()
                .eq(PdmPrcRoute::getPartId, part.getId())
                .orderByDesc(PdmPrcRoute::getVersionSeq));
        String versionNo = existing.isEmpty()
                ? PdmPrcVersionNo.INITIAL
                : PdmPrcVersionNo.nextMajor(existing.get(0).getVersionNo());
        assertRouteVersionUnique(part.getId(), versionNo);

        PdmPrcRoute route = new PdmPrcRoute();
        route.setRouteNo(routeNo);
        route.setPartId(part.getId());
        route.setPartCode(part.getCode());
        route.setPartName(part.getName());
        route.setVersionNo(versionNo);
        route.setVersionSeq(PdmPrcVersionNo.seqOf(versionNo));
        route.setStatus(PdmPrcStatus.DRAFT);
        route.setRemark(request.getRemark());
        route.setCreatedBy(userId);
        routeMapper.insert(route);
        log.info("工艺路线已创建: id={}, routeNo={}, version={}", route.getId(), routeNo, versionNo);
        return assemble(route);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PdmPrcRouteVO update(Long id, PdmPrcRouteSaveRequest request) {
        PdmPrcRoute route = requireDraftRoute(id);
        route.setRemark(request.getRemark());
        routeMapper.updateById(route);
        return assemble(route);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        PdmPrcRoute route = requireDraftRoute(id);
        toolingMapper.delete(new LambdaQueryWrapper<PdmPrcTooling>().eq(PdmPrcTooling::getRouteId, id));
        sopMapper.delete(new LambdaQueryWrapper<PdmPrcSop>().eq(PdmPrcSop::getRouteId, id));
        stepMapper.delete(new LambdaQueryWrapper<PdmPrcStep>().eq(PdmPrcStep::getRouteId, id));
        routeMapper.deleteById(route.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PdmPrcRouteVO addStep(Long id, PdmPrcStepSaveRequest request) {
        PdmPrcRoute route = requireDraftRoute(id);
        Integer stepNo = request.getStepNo() == null ? nextStepNo(id) : request.getStepNo();
        validateStep(stepNo, request, null, id);
        PdmPrcStep step = new PdmPrcStep();
        step.setRouteId(id);
        step.setStepNo(stepNo);
        step.setName(request.getName().trim());
        step.setWorkCenter(request.getWorkCenter());
        step.setStandardHours(normalizeHours(request.getStandardHours()));
        step.setSetupHours(normalizeHours(request.getSetupHours()));
        step.setDescription(request.getDescription());
        stepMapper.insert(step);
        return assemble(route);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PdmPrcRouteVO updateStep(Long id, Long stepId, PdmPrcStepSaveRequest request) {
        PdmPrcRoute route = requireDraftRoute(id);
        PdmPrcStep step = requireStep(id, stepId);
        Integer stepNo = request.getStepNo() == null ? step.getStepNo() : request.getStepNo();
        validateStep(stepNo, request, stepId, id);
        step.setStepNo(stepNo);
        step.setName(request.getName().trim());
        step.setWorkCenter(request.getWorkCenter());
        step.setStandardHours(normalizeHours(request.getStandardHours()));
        step.setSetupHours(normalizeHours(request.getSetupHours()));
        step.setDescription(request.getDescription());
        stepMapper.updateById(step);
        return assemble(route);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PdmPrcRouteVO deleteStep(Long id, Long stepId) {
        PdmPrcRoute route = requireDraftRoute(id);
        requireStep(id, stepId);
        toolingMapper.delete(new LambdaQueryWrapper<PdmPrcTooling>().eq(PdmPrcTooling::getStepId, stepId));
        sopMapper.delete(new LambdaQueryWrapper<PdmPrcSop>().eq(PdmPrcSop::getStepId, stepId));
        stepMapper.deleteById(stepId);
        return assemble(route);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PdmPrcRouteVO addTooling(Long id, PdmPrcToolingSaveRequest request) {
        PdmPrcRoute route = requireDraftRoute(id);
        if (request.getStepId() != null) {
            requireStep(id, request.getStepId());
        }
        PdmPrcTooling tooling = new PdmPrcTooling();
        tooling.setRouteId(id);
        tooling.setStepId(request.getStepId());
        tooling.setToolingType(request.getToolingType());
        tooling.setToolingCode(request.getToolingCode().trim());
        tooling.setSpec(request.getSpec());
        tooling.setQuantity(request.getQuantity() == null ? 1 : request.getQuantity());
        tooling.setRemark(request.getRemark());
        toolingMapper.insert(tooling);
        return assemble(route);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PdmPrcRouteVO deleteTooling(Long id, Long toolingId) {
        PdmPrcRoute route = requireDraftRoute(id);
        PdmPrcTooling tooling = toolingMapper.selectById(toolingId);
        if (tooling == null || !tooling.getRouteId().equals(id)) {
            throw new BizException(ErrorCode.NOT_FOUND, "biz.pdm.process.tooling-record-found", "工装记录不存在");
        }
        toolingMapper.deleteById(toolingId);
        return assemble(route);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PdmPrcRouteVO saveSop(Long id, PdmPrcSopSaveRequest request) {
        PdmPrcRoute route = requireDraftRoute(id);
        PdmPrcStep step = requireStep(id, request.getStepId());
        PdmDocVersion version = requireReleasedVersion(request.getDocumentId(),
                request.getDocumentVersionNo().trim());
        PdmDocDocument document = docMapper.selectById(request.getDocumentId());
        PdmPrcSop existing = sopMapper.selectOne(new LambdaQueryWrapper<PdmPrcSop>()
                .eq(PdmPrcSop::getRouteId, id)
                .eq(PdmPrcSop::getStepId, step.getId())
                .last("LIMIT 1"));
        PdmPrcSop sop = existing == null ? new PdmPrcSop() : existing;
        sop.setRouteId(id);
        sop.setStepId(step.getId());
        sop.setDocumentId(request.getDocumentId());
        sop.setDocumentVersionNo(version.getVersionNo());
        sop.setDocumentTitle(document == null ? null : document.getTitle());
        if (existing == null) {
            sopMapper.insert(sop);
        } else {
            sopMapper.updateById(sop);
        }
        return assemble(route);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PdmPrcRouteVO deleteSop(Long id, Long sopId) {
        PdmPrcRoute route = requireDraftRoute(id);
        PdmPrcSop sop = sopMapper.selectById(sopId);
        if (sop == null || !sop.getRouteId().equals(id)) {
            throw new BizException(ErrorCode.NOT_FOUND, "biz.pdm.process.s-o-p-record-found", "SOP 记录不存在");
        }
        sopMapper.deleteById(sopId);
        return assemble(route);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PdmPrcRouteVO release(Long id, Long userId) {
        PdmPrcRoute route = requireRoute(id);
        PdmPrcPublish publish = findPublish(id);
        if (PdmPrcStatus.RELEASED.equals(route.getStatus())) {
            return assemble(route);
        }
        if (!PdmPrcStatus.DRAFT.equals(route.getStatus())) {
            throw new BizException(ErrorCode.PARAM_ERROR, "biz.pdm.process.draft-routing-can-publish", "仅草稿工艺路线可发布");
        }
        List<PdmPrcStep> steps = stepsOf(id);
        if (steps.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "biz.pdm.process.routing-requires-at-least-one-operation", "工艺路线至少需要一道工序才能发布");
        }
        assertSopsReleased(id, steps);
        LocalDateTime now = LocalDateTime.now();
        route.setStatus(PdmPrcStatus.RELEASED);
        route.setReleasedBy(userId);
        route.setReleasedAt(now);
        routeMapper.updateById(route);
        obsoleteFamily(route);
        if (publish == null) {
            publish = new PdmPrcPublish();
            publish.setRouteId(id);
            publish.setRouteNo(route.getRouteNo());
            publish.setVersionNo(route.getVersionNo());
            publish.setIdempotencyKey(route.getRouteNo() + ":" + route.getVersionNo());
            publish.setRetryCount(0);
            publish.setStatus(PdmPrcPublishStatus.FAILED);
            publishMapper.insert(publish);
        }
        attemptPush(route, steps, publish);
        log.info("工艺路线已发布: routeNo={}, version={}, publishStatus={}",
                route.getRouteNo(), route.getVersionNo(), publish.getStatus());
        return assemble(route);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PdmPrcRouteVO retryPublish(Long id) {
        PdmPrcRoute route = requireRoute(id);
        if (!PdmPrcStatus.RELEASED.equals(route.getStatus())) {
            throw new BizException(ErrorCode.PARAM_ERROR, "biz.pdm.process.published-routing-can-retry-dispatch", "仅已发布工艺路线可重试下发");
        }
        PdmPrcPublish publish = findPublish(id);
        if (publish == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "biz.pdm.process.routing-dispatch-record-found", "工艺路线下发记录不存在");
        }
        if (PdmPrcPublishStatus.SUCCESS.equals(publish.getStatus())) {
            return assemble(route);
        }
        if (PdmPrcPublishStatus.WITHDRAWN.equals(publish.getStatus())) {
            throw new BizException(ErrorCode.PARAM_ERROR, "biz.pdm.process.routing-withdraw-cannot-retry-dispatch", "工艺路线已撤回，不能重试下发");
        }
        publish.setRetryCount(publish.getRetryCount() == null ? 1 : publish.getRetryCount() + 1);
        attemptPush(route, stepsOf(id), publish);
        return assemble(route);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PdmPrcRouteVO withdraw(Long id) {
        PdmPrcRoute route = requireRoute(id);
        if (!PdmPrcStatus.RELEASED.equals(route.getStatus())) {
            throw new BizException(ErrorCode.PARAM_ERROR, "biz.pdm.process.published-routing-can-withdraw", "仅已发布工艺路线可撤回");
        }
        route.setStatus(PdmPrcStatus.OBSOLETE);
        routeMapper.updateById(route);
        PdmPrcPublish publish = findPublish(id);
        if (publish != null) {
            publish.setStatus(PdmPrcPublishStatus.WITHDRAWN);
            publish.setWithdrawnAt(LocalDateTime.now());
            publish.setLastError(null);
            publishMapper.updateById(publish);
            try {
                mesClient.withdraw(TenantContext.requireId(), route.getRouteNo(), route.getVersionNo());
            } catch (Exception ex) {
                publish.setLastError(truncateError(ex.getMessage()));
                publishMapper.updateById(publish);
                log.warn("工艺路线 MES 撤回失败: routeNo={}, version={}",
                        route.getRouteNo(), route.getVersionNo(), ex);
            }
        }
        return assemble(route);
    }

    @Override
    public PdmPrcRouteSnapshotVO snapshot(String routeNo, String versionNo) {
        PdmPrcRoute route = routeMapper.selectList(new LambdaQueryWrapper<PdmPrcRoute>()
                        .eq(PdmPrcRoute::getRouteNo, routeNo)
                        .eq(PdmPrcRoute::getVersionNo, versionNo)
                        .last("limit 1"))
                .stream().findFirst()
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "biz.pdm.process.routing-exist-been-published", "工艺路线不存在或未发布"));
        return toSnapshot(requireReleased(route));
    }

    @Override
    public PdmPrcRouteSnapshotVO latestReleased(String partCode) {
        PdmPrcRoute route = routeMapper.selectList(new LambdaQueryWrapper<PdmPrcRoute>()
                        .eq(PdmPrcRoute::getPartCode, partCode)
                        .eq(PdmPrcRoute::getStatus, PdmPrcStatus.RELEASED)
                        .orderByDesc(PdmPrcRoute::getVersionSeq)
                        .orderByDesc(PdmPrcRoute::getId)
                        .last("limit 1"))
                .stream().findFirst()
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "biz.pdm.process.part-published-routing", "零件 {0} 没有已发布工艺路线", partCode));
        return toSnapshot(requireReleased(route));
    }

    /**
     * 校验路线已发布并返回。
     *
     * @param route 路线
     * @return 已发布路线
     */
    private PdmPrcRoute requireReleased(PdmPrcRoute route) {
        if (!PdmPrcStatus.RELEASED.equals(route.getStatus())) {
            throw new BizException(ErrorCode.NOT_FOUND, "biz.pdm.process.routing-exist-been-published-2", "工艺路线不存在或未发布");
        }
        return route;
    }

    /**
     * 路线转发布快照。
     *
     * @param route 已发布路线
     * @return 快照
     */
    private PdmPrcRouteSnapshotVO toSnapshot(PdmPrcRoute route) {
        PdmPrcRouteSnapshotVO snapshot = new PdmPrcRouteSnapshotVO();
        snapshot.setRouteNo(route.getRouteNo());
        snapshot.setVersionNo(route.getVersionNo());
        snapshot.setPartCode(route.getPartCode());
        snapshot.setPartName(route.getPartName());
        snapshot.setReleasedAt(route.getReleasedAt());
        for (PdmPrcStep step : stepsOf(route.getId())) {
            PdmPrcRouteSnapshotVO.Step item = new PdmPrcRouteSnapshotVO.Step();
            item.setStepNo(step.getStepNo());
            item.setName(step.getName());
            item.setWorkCenter(step.getWorkCenter());
            item.setStandardHours(step.getStandardHours());
            item.setSetupHours(step.getSetupHours());
            snapshot.getSteps().add(item);
        }
        return snapshot;
    }

    /**
     * 尝试向 MES 下发并更新记录状态
     *
     * @param route   路线
     * @param steps   工序
     * @param publish 下发记录
     */
    private void attemptPush(PdmPrcRoute route, List<PdmPrcStep> steps, PdmPrcPublish publish) {
        try {
            PdmPrcMesPublishRequest payload = buildPayload(route, steps);
            String mesId = mesClient.push(TenantContext.requireId(), payload);
            publish.setStatus(PdmPrcPublishStatus.SUCCESS);
            publish.setMesRouteNo(mesId);
            publish.setLastError(null);
            publish.setPushedAt(LocalDateTime.now());
        } catch (Exception ex) {
            publish.setStatus(PdmPrcPublishStatus.FAILED);
            publish.setLastError(truncateError(ex.getMessage()));
            log.warn("工艺路线 MES 下发失败: routeNo={}, version={}",
                    route.getRouteNo(), route.getVersionNo(), ex);
        }
        publishMapper.updateById(publish);
    }

    /**
     * 构造 MES 下发内容
     *
     * @param route 路线
     * @param steps 工序
     * @return 下发内容
     */
    private PdmPrcMesPublishRequest buildPayload(PdmPrcRoute route, List<PdmPrcStep> steps) {
        Map<Long, List<PdmPrcTooling>> toolings = toolingsOf(route.getId());
        Map<Long, PdmPrcSop> sops = sopsOf(route.getId());
        PdmPrcMesPublishRequest payload = new PdmPrcMesPublishRequest();
        payload.setRouteNo(route.getRouteNo());
        payload.setVersionNo(route.getVersionNo());
        payload.setPartCode(route.getPartCode());
        payload.setPartName(route.getPartName());
        for (PdmPrcStep step : steps) {
            PdmPrcMesPublishRequest.Step item = new PdmPrcMesPublishRequest.Step();
            item.setStepNo(step.getStepNo());
            item.setName(step.getName());
            item.setWorkCenter(step.getWorkCenter());
            item.setStandardHours(step.getStandardHours());
            item.setSetupHours(step.getSetupHours());
            item.setDescription(step.getDescription());
            for (PdmPrcTooling tooling : toolings.getOrDefault(step.getId(), List.of())) {
                PdmPrcMesPublishRequest.Tooling tool = new PdmPrcMesPublishRequest.Tooling();
                tool.setToolingType(tooling.getToolingType());
                tool.setToolingCode(tooling.getToolingCode());
                tool.setSpec(tooling.getSpec());
                tool.setQuantity(tooling.getQuantity());
                item.getToolings().add(tool);
            }
            PdmPrcSop sop = sops.get(step.getId());
            if (sop != null) {
                item.setSopDocumentId(sop.getDocumentId());
                item.setSopDocumentVersionNo(sop.getDocumentVersionNo());
            }
            payload.getSteps().add(item);
        }
        payload.setChecksum(PdmPrcChecksum.of(payload));
        return payload;
    }

    /**
     * 发布新版本时作废同编号旧版本
     *
     * @param route 已发布路线
     */
    private void obsoleteFamily(PdmPrcRoute route) {
        List<PdmPrcRoute> family = routeMapper.selectList(new LambdaQueryWrapper<PdmPrcRoute>()
                .eq(PdmPrcRoute::getRouteNo, route.getRouteNo()));
        for (PdmPrcRoute other : family) {
            if (!other.getId().equals(route.getId()) && PdmPrcStatus.RELEASED.equals(other.getStatus())) {
                other.setStatus(PdmPrcStatus.OBSOLETE);
                routeMapper.updateById(other);
            }
        }
    }

    /**
     * 校验路线下所有 SOP 仍然引用已发布版本
     *
     * @param routeId 路线ID
     * @param steps   工序
     */
    private void assertSopsReleased(Long routeId, List<PdmPrcStep> steps) {
        List<PdmPrcSop> sops = sopMapper.selectList(new LambdaQueryWrapper<PdmPrcSop>()
                .eq(PdmPrcSop::getRouteId, routeId));
        for (PdmPrcSop sop : sops) {
            PdmDocVersion version = requireReleasedVersion(sop.getDocumentId(), sop.getDocumentVersionNo());
            if (!Objects.equals(version.getVersionNo(), sop.getDocumentVersionNo())) {
                throw new BizException(ErrorCode.PARAM_ERROR, "biz.pdm.process.s-o-p-document-version-found", "SOP 文档版本不存在");
            }
        }
    }

    /**
     * 查询并校验已发布文档版本
     *
     * @param documentId 文档ID
     * @param versionNo  版本号
     * @return 文档版本
     */
    private PdmDocVersion requireReleasedVersion(Long documentId, String versionNo) {
        List<PdmDocVersion> versions = docVersionMapper.selectList(new LambdaQueryWrapper<PdmDocVersion>()
                .eq(PdmDocVersion::getDocumentId, documentId)
                .eq(PdmDocVersion::getVersionNo, versionNo)
                .last("LIMIT 1"));
        if (versions.isEmpty()) {
            throw new BizException(ErrorCode.NOT_FOUND, "biz.pdm.process.s-o-p-reference-document-version", "SOP 引用的文档版本不存在");
        }
        PdmDocVersion version = versions.get(0);
        if (!DOC_RELEASED.equals(version.getStatus())) {
            throw new BizException(ErrorCode.PARAM_ERROR, "biz.pdm.process.s-o-p-can-reference-published", "SOP 只能引用已发布文档版本");
        }
        return version;
    }

    /**
     * 校验工序序号与工时
     *
     * @param stepNo   工序序号
     * @param request  工序请求
     * @param excludeId 排除的工序ID
     * @param routeId  路线ID
     */
    private void validateStep(Integer stepNo, PdmPrcStepSaveRequest request, Long excludeId, Long routeId) {
        if (stepNo == null || stepNo <= 0) {
            throw new BizException(ErrorCode.PARAM_ERROR, "biz.pdm.process.operation-sequence-number-greater-than-0", "工序序号必须大于 0");
        }
        if (request.getStandardHours() != null && request.getStandardHours().signum() < 0) {
            throw new BizException(ErrorCode.PARAM_ERROR, "biz.pdm.process.standard-hours-negative", "标准工时不能为负");
        }
        if (request.getSetupHours() != null && request.getSetupHours().signum() < 0) {
            throw new BizException(ErrorCode.PARAM_ERROR, "biz.pdm.process.setup-work-hours-negative", "准备工时不能为负");
        }
        LambdaQueryWrapper<PdmPrcStep> wrapper = new LambdaQueryWrapper<PdmPrcStep>()
                .eq(PdmPrcStep::getRouteId, routeId)
                .eq(PdmPrcStep::getStepNo, stepNo);
        if (excludeId != null) {
            wrapper.ne(PdmPrcStep::getId, excludeId);
        }
        if (stepMapper.selectCount(wrapper) > 0) {
            throw new BizException(ErrorCode.REPEAT_SUBMIT, "biz.pdm.process.operation-sequence-number-already-exists", "工序序号已存在");
        }
    }

    /**
     * 计算下一个工序序号
     *
     * @param routeId 路线ID
     * @return 下一个序号
     */
    private Integer nextStepNo(Long routeId) {
        List<PdmPrcStep> steps = stepMapper.selectList(new LambdaQueryWrapper<PdmPrcStep>()
                .eq(PdmPrcStep::getRouteId, routeId)
                .orderByDesc(PdmPrcStep::getStepNo)
                .last("LIMIT 1"));
        return steps.isEmpty() ? 1 : steps.get(0).getStepNo() + 1;
    }

    /**
     * 校验同零件同版本唯一
     *
     * @param partId    零件ID
     * @param versionNo 版本号
     */
    private void assertRouteVersionUnique(Long partId, String versionNo) {
        long count = routeMapper.selectCount(new LambdaQueryWrapper<PdmPrcRoute>()
                .eq(PdmPrcRoute::getPartId, partId)
                .eq(PdmPrcRoute::getVersionNo, versionNo));
        if (count > 0) {
            throw new BizException(ErrorCode.REPEAT_SUBMIT, "biz.pdm.process.routing-same-part-version-already-exists", "同零件同版本工艺路线已存在");
        }
    }

    /**
     * 查询并校验路线存在
     *
     * @param id 路线ID
     * @return 路线
     */
    private PdmPrcRoute requireRoute(Long id) {
        PdmPrcRoute route = id == null ? null : routeMapper.selectById(id);
        if (route == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "biz.pdm.process.routing-found", "工艺路线不存在");
        }
        return route;
    }

    /**
     * 查询并校验草稿路线
     *
     * @param id 路线ID
     * @return 路线
     */
    private PdmPrcRoute requireDraftRoute(Long id) {
        PdmPrcRoute route = requireRoute(id);
        if (!PdmPrcStatus.DRAFT.equals(route.getStatus())) {
            throw new BizException(ErrorCode.PARAM_ERROR, "biz.pdm.process.routing-published-cannot-modify", "工艺路线已发布，不能再修改");
        }
        return route;
    }

    /**
     * 查询并校验零件
     *
     * @param partId 零件ID
     * @return 零件
     */
    private PdmProduct requirePart(Long partId) {
        PdmProduct part = partId == null ? null : productMapper.selectById(partId);
        if (part == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "biz.pdm.process.part-found", "零件不存在");
        }
        if (!PART_ENABLED.equals(part.getStatus())) {
            throw new BizException(ErrorCode.PARAM_ERROR, "biz.pdm.process.disabled-part-cannot-creation-routing", "停用零件不能创建工艺路线");
        }
        return part;
    }

    /**
     * 查询并校验工序属于路线
     *
     * @param routeId 路线ID
     * @param stepId  工序ID
     * @return 工序
     */
    private PdmPrcStep requireStep(Long routeId, Long stepId) {
        PdmPrcStep step = stepId == null ? null : stepMapper.selectById(stepId);
        if (step == null || !step.getRouteId().equals(routeId)) {
            throw new BizException(ErrorCode.NOT_FOUND, "biz.pdm.process.operation-found", "工序不存在");
        }
        return step;
    }

    /**
     * 查询路线工序
     *
     * @param routeId 路线ID
     * @return 工序列表
     */
    private List<PdmPrcStep> stepsOf(Long routeId) {
        return stepMapper.selectList(new LambdaQueryWrapper<PdmPrcStep>()
                .eq(PdmPrcStep::getRouteId, routeId)
                .orderByAsc(PdmPrcStep::getStepNo));
    }

    /**
     * 查询路线工装（按工序分组）
     *
     * @param routeId 路线ID
     * @return 工序ID → 工装列表
     */
    private Map<Long, List<PdmPrcTooling>> toolingsOf(Long routeId) {
        List<PdmPrcTooling> toolings = toolingMapper.selectList(new LambdaQueryWrapper<PdmPrcTooling>()
                .eq(PdmPrcTooling::getRouteId, routeId));
        Map<Long, List<PdmPrcTooling>> grouped = new HashMap<>();
        for (PdmPrcTooling tooling : toolings) {
            grouped.computeIfAbsent(tooling.getStepId(), key -> new ArrayList<>()).add(tooling);
        }
        return grouped;
    }

    /**
     * 查询路线 SOP（按工序分组）
     *
     * @param routeId 路线ID
     * @return 工序ID → SOP
     */
    private Map<Long, PdmPrcSop> sopsOf(Long routeId) {
        List<PdmPrcSop> sops = sopMapper.selectList(new LambdaQueryWrapper<PdmPrcSop>()
                .eq(PdmPrcSop::getRouteId, routeId));
        Map<Long, PdmPrcSop> grouped = new HashMap<>();
        for (PdmPrcSop sop : sops) {
            grouped.put(sop.getStepId(), sop);
        }
        return grouped;
    }

    /**
     * 查询下发记录
     *
     * @param routeId 路线ID
     * @return 下发记录，不存在返回 {@code null}
     */
    private PdmPrcPublish findPublish(Long routeId) {
        List<PdmPrcPublish> publishes = publishMapper.selectList(new LambdaQueryWrapper<PdmPrcPublish>()
                .eq(PdmPrcPublish::getRouteId, routeId)
                .last("LIMIT 1"));
        return publishes.isEmpty() ? null : publishes.get(0);
    }

    /**
     * 批量查询下发记录
     *
     * @param routeIds 路线ID集合
     * @return 路线ID → 下发记录
     */
    private Map<Long, PdmPrcPublish> publishesOf(Collection<Long> routeIds) {
        Map<Long, PdmPrcPublish> result = new HashMap<>();
        if (routeIds == null || routeIds.isEmpty()) {
            return result;
        }
        Set<Long> ids = new HashSet<>(routeIds);
        for (PdmPrcPublish publish : publishMapper.selectList(new LambdaQueryWrapper<PdmPrcPublish>()
                .in(PdmPrcPublish::getRouteId, ids))) {
            result.put(publish.getRouteId(), publish);
        }
        return result;
    }

    /**
     * 装配路线视图
     *
     * @param route 路线
     * @return 路线视图
     */
    private PdmPrcRouteVO assemble(PdmPrcRoute route) {
        List<PdmPrcStep> steps = stepsOf(route.getId());
        Map<Long, List<PdmPrcTooling>> toolings = toolingsOf(route.getId());
        Map<Long, PdmPrcSop> sops = sopsOf(route.getId());
        return toRouteVo(route, steps, findPublish(route.getId()), toolings, sops);
    }

    /**
     * 路线实体转视图
     *
     * @param route    路线
     * @param steps    工序
     * @param publish  下发记录
     * @return 路线视图
     */
    private PdmPrcRouteVO toRouteVo(PdmPrcRoute route, List<PdmPrcStep> steps, PdmPrcPublish publish) {
        return toRouteVo(route, steps, publish, Map.of(), Map.of());
    }

    /**
     * 路线实体转视图（含工装与 SOP）
     *
     * @param route    路线
     * @param steps    工序
     * @param publish  下发记录
     * @param toolings 工序ID → 工装列表
     * @param sops     工序ID → SOP
     * @return 路线视图
     */
    private PdmPrcRouteVO toRouteVo(PdmPrcRoute route, List<PdmPrcStep> steps, PdmPrcPublish publish,
                                    Map<Long, List<PdmPrcTooling>> toolings, Map<Long, PdmPrcSop> sops) {
        PdmPrcRouteVO vo = new PdmPrcRouteVO();
        vo.setId(route.getId());
        vo.setRouteNo(route.getRouteNo());
        vo.setPartId(route.getPartId());
        vo.setPartCode(route.getPartCode());
        vo.setPartName(route.getPartName());
        vo.setVersionNo(route.getVersionNo());
        vo.setStatus(route.getStatus());
        vo.setRemark(route.getRemark());
        vo.setCreatedBy(route.getCreatedBy());
        vo.setReleasedBy(route.getReleasedBy());
        vo.setReleasedAt(route.getReleasedAt());
        vo.setCreateTime(route.getCreateTime());
        vo.setUpdateTime(route.getUpdateTime());
        for (PdmPrcStep step : steps) {
            PdmPrcStepVO stepVo = new PdmPrcStepVO();
            stepVo.setId(step.getId());
            stepVo.setStepNo(step.getStepNo());
            stepVo.setName(step.getName());
            stepVo.setWorkCenter(step.getWorkCenter());
            stepVo.setStandardHours(step.getStandardHours());
            stepVo.setSetupHours(step.getSetupHours());
            stepVo.setDescription(step.getDescription());
            for (PdmPrcTooling tooling : toolings.getOrDefault(step.getId(), List.of())) {
                PdmPrcToolingVO toolingVo = new PdmPrcToolingVO();
                toolingVo.setId(tooling.getId());
                toolingVo.setStepId(tooling.getStepId());
                toolingVo.setToolingType(tooling.getToolingType());
                toolingVo.setToolingCode(tooling.getToolingCode());
                toolingVo.setSpec(tooling.getSpec());
                toolingVo.setQuantity(tooling.getQuantity());
                toolingVo.setRemark(tooling.getRemark());
                stepVo.getToolings().add(toolingVo);
            }
            PdmPrcSop sop = sops.get(step.getId());
            if (sop != null) {
                PdmPrcSopVO sopVo = new PdmPrcSopVO();
                sopVo.setId(sop.getId());
                sopVo.setStepId(sop.getStepId());
                sopVo.setDocumentId(sop.getDocumentId());
                sopVo.setDocumentVersionNo(sop.getDocumentVersionNo());
                sopVo.setDocumentTitle(sop.getDocumentTitle());
                stepVo.setSop(sopVo);
            }
            vo.getSteps().add(stepVo);
        }
        if (publish != null) {
            PdmPrcPublishVO publishVo = new PdmPrcPublishVO();
            publishVo.setId(publish.getId());
            publishVo.setStatus(publish.getStatus());
            publishVo.setMesRouteNo(publish.getMesRouteNo());
            publishVo.setRetryCount(publish.getRetryCount());
            publishVo.setLastError(publish.getLastError());
            publishVo.setPushedAt(publish.getPushedAt());
            publishVo.setWithdrawnAt(publish.getWithdrawnAt());
            vo.setPublish(publishVo);
        }
        return vo;
    }

    /**
     * 规范化工时（空值按 0）
     *
     * @param hours 工时
     * @return 非负工时
     */
    private BigDecimal normalizeHours(BigDecimal hours) {
        if (hours == null) {
            return BigDecimal.ZERO;
        }
        if (hours.signum() < 0) {
            throw new BizException(ErrorCode.PARAM_ERROR, "biz.pdm.process.work-hours-negative", "工时不能为负");
        }
        return hours;
    }

    /**
     * 截断失败原因
     *
     * @param message 原始信息
     * @return 截断后的信息
     */
    private String truncateError(String message) {
        String safe = StringUtils.hasText(message) ? message : "未知错误";
        return safe.length() > MAX_ERROR_LENGTH ? safe.substring(0, MAX_ERROR_LENGTH) : safe;
    }
}
