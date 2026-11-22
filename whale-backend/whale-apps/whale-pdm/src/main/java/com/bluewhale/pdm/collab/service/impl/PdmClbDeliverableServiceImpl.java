package com.bluewhale.pdm.collab.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.pdm.collab.dto.PdmClbDeliverableQueryRequest;
import com.bluewhale.pdm.collab.dto.PdmClbDeliverableSaveRequest;
import com.bluewhale.pdm.collab.entity.PdmClbDeliverable;
import com.bluewhale.pdm.collab.mapper.PdmClbDeliverableMapper;
import com.bluewhale.pdm.collab.service.PdmClbDeliverableService;
import com.bluewhale.pdm.collab.support.PdmClbDeliverableStatus;
import com.bluewhale.pdm.collab.support.PdmClbObjectType;
import com.bluewhale.pdm.collab.vo.PdmClbDeliverableVO;
import com.bluewhale.pdm.document.entity.PdmDocDocument;
import com.bluewhale.pdm.document.entity.PdmDocVersion;
import com.bluewhale.pdm.document.mapper.PdmDocDocumentMapper;
import com.bluewhale.pdm.document.mapper.PdmDocVersionMapper;
import com.bluewhale.pdm.part.entity.PdmBom;
import com.bluewhale.pdm.part.mapper.PdmBomMapper;
import com.bluewhale.pdm.process.entity.PdmPrcRoute;
import com.bluewhale.pdm.process.mapper.PdmPrcRouteMapper;
import com.bluewhale.pdm.process.support.PdmPrcStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 项目交付物服务实现
 *
 * <p>只允许归档已发布对象版本：文档版本必须 {@code RELEASED}，BOM 与工艺路线必须为
 * {@code RELEASED} 且版本号一致；同项目同对象同版本重复归档返回 409。</p>
 *
 * @author BlueWhale
 */
@Service
@RequiredArgsConstructor
public class PdmClbDeliverableServiceImpl implements PdmClbDeliverableService {

    /** 单页最大条数 */
    private static final long MAX_PAGE_SIZE = 200L;

    /** 文档已发布状态 */
    private static final String DOC_RELEASED = "RELEASED";

    private final PdmClbDeliverableMapper deliverableMapper;

    private final PdmDocDocumentMapper documentMapper;

    private final PdmDocVersionMapper versionMapper;

    private final PdmBomMapper bomMapper;

    private final PdmPrcRouteMapper routeMapper;

    @Override
    public PageResult<PdmClbDeliverableVO> page(PdmClbDeliverableQueryRequest request) {
        LambdaQueryWrapper<PdmClbDeliverable> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(request.getProjectId() != null, PdmClbDeliverable::getProjectId, request.getProjectId());
        wrapper.eq(StringUtils.hasText(request.getObjectType()), PdmClbDeliverable::getObjectType,
                request.getObjectType());
        wrapper.eq(StringUtils.hasText(request.getDeliverableType()), PdmClbDeliverable::getDeliverableType,
                request.getDeliverableType());
        if (StringUtils.hasText(request.getKeyword())) {
            String keyword = request.getKeyword().trim();
            wrapper.and(query -> query.like(PdmClbDeliverable::getObjectNo, keyword)
                    .or().like(PdmClbDeliverable::getObjectTitle, keyword)
                    .or().like(PdmClbDeliverable::getProjectName, keyword));
        }
        wrapper.orderByDesc(PdmClbDeliverable::getCreateTime).orderByDesc(PdmClbDeliverable::getId);
        long size = Math.min(Math.max(request.getSize(), 1L), MAX_PAGE_SIZE);
        Page<PdmClbDeliverable> page =
                deliverableMapper.selectPage(new Page<>(request.getCurrent(), size), wrapper);
        List<PdmClbDeliverableVO> records = page.getRecords().stream().map(this::toVo).toList();
        return PageResult.of(records, page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PdmClbDeliverableVO archive(PdmClbDeliverableSaveRequest request, Long userId) {
        String objectType = request.getObjectType().trim().toUpperCase();
        if (!PdmClbObjectType.isValidDeliverable(objectType)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "biz.pdm.collab.deliverable-object-type-supports-document-bom",
                          "交付物对象类型仅支持 DOCUMENT/BOM/ROUTE");
        }
        String deliverableType = StringUtils.hasText(request.getDeliverableType())
                ? request.getDeliverableType().trim().toUpperCase()
                : PdmClbDeliverableStatus.TYPE_OTHER;
        if (!PdmClbDeliverableStatus.isValidType(deliverableType)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "biz.pdm.collab.deliverable-type-invalid", "交付物类型不合法");
        }
        PdmClbDeliverable entity = new PdmClbDeliverable();
        entity.setProjectId(request.getProjectId());
        entity.setProjectName(request.getProjectName());
        entity.setWorkItemId(request.getWorkItemId());
        entity.setWorkItemName(request.getWorkItemName());
        entity.setObjectType(objectType);
        entity.setObjectId(request.getObjectId());
        entity.setObjectVersionNo(request.getObjectVersionNo().trim());
        entity.setDeliverableType(deliverableType);
        entity.setRemark(request.getRemark());
        fillObjectSnapshot(entity);
        ensureNotArchived(entity);
        entity.setStatus(PdmClbDeliverableStatus.ARCHIVED);
        entity.setArchivedBy(userId);
        entity.setArchivedAt(LocalDateTime.now());
        deliverableMapper.insert(entity);
        return toVo(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        PdmClbDeliverable entity = deliverableMapper.selectById(id);
        if (entity == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "biz.pdm.collab.deliverable-found", "交付物不存在");
        }
        deliverableMapper.deleteById(id);
    }

    /**
     * 校验对象版本已发布并回填编号/标题快照
     *
     * @param entity 交付物
     */
    private void fillObjectSnapshot(PdmClbDeliverable entity) {
        switch (entity.getObjectType()) {
            case PdmClbObjectType.DOCUMENT -> fillDocumentSnapshot(entity);
            case PdmClbObjectType.BOM -> fillBomSnapshot(entity);
            case PdmClbObjectType.ROUTE -> fillRouteSnapshot(entity);
            default -> throw new BizException(ErrorCode.PARAM_ERROR, "biz.pdm.collab.deliverable-object-type-supports-document-bom-2",
                                     "交付物对象类型仅支持 DOCUMENT/BOM/ROUTE");
        }
    }

    /**
     * 校验文档版本已发布并回填快照
     *
     * @param entity 交付物
     */
    private void fillDocumentSnapshot(PdmClbDeliverable entity) {
        PdmDocDocument document = documentMapper.selectById(entity.getObjectId());
        if (document == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "biz.pdm.collab.archive-document-found", "归档文档不存在");
        }
        PdmDocVersion version = versionMapper.selectOne(new LambdaQueryWrapper<PdmDocVersion>()
                .eq(PdmDocVersion::getDocumentId, entity.getObjectId())
                .eq(PdmDocVersion::getVersionNo, entity.getObjectVersionNo()));
        if (version == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "biz.pdm.collab.archive-document-version-found", "归档文档版本不存在");
        }
        if (!DOC_RELEASED.equals(version.getStatus())) {
            throw new BizException(ErrorCode.PARAM_ERROR, "biz.pdm.collab.can-archive-published-document-version", "只能归档已发布文档版本");
        }
        if (!StringUtils.hasText(entity.getObjectNo())) {
            entity.setObjectNo(document.getDocNo());
        }
        if (!StringUtils.hasText(entity.getObjectTitle())) {
            entity.setObjectTitle(document.getTitle());
        }
    }

    /**
     * 校验 BOM 版本已发布并回填快照
     *
     * @param entity 交付物
     */
    private void fillBomSnapshot(PdmClbDeliverable entity) {
        PdmBom bom = bomMapper.selectById(entity.getObjectId());
        if (bom == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "biz.pdm.collab.archive-b-o-m-found", "归档 BOM 不存在");
        }
        if (!PdmPrcStatus.RELEASED.equals(bom.getStatus())
                || !entity.getObjectVersionNo().equals(bom.getVersionNo())) {
            throw new BizException(ErrorCode.PARAM_ERROR, "biz.pdm.collab.can-archive-published-b-o-m", "只能归档已发布 BOM 版本");
        }
        if (!StringUtils.hasText(entity.getObjectNo())) {
            entity.setObjectNo(bom.getBomNo());
        }
        if (!StringUtils.hasText(entity.getObjectTitle())) {
            entity.setObjectTitle(bom.getProductCode());
        }
    }

    /**
     * 校验工艺路线版本已发布并回填快照
     *
     * @param entity 交付物
     */
    private void fillRouteSnapshot(PdmClbDeliverable entity) {
        PdmPrcRoute route = routeMapper.selectById(entity.getObjectId());
        if (route == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "biz.pdm.collab.archive-routing-found", "归档工艺路线不存在");
        }
        if (!PdmPrcStatus.RELEASED.equals(route.getStatus())
                || !entity.getObjectVersionNo().equals(route.getVersionNo())) {
            throw new BizException(ErrorCode.PARAM_ERROR, "biz.pdm.collab.can-archive-published-routing-version", "只能归档已发布工艺路线版本");
        }
        if (!StringUtils.hasText(entity.getObjectNo())) {
            entity.setObjectNo(route.getRouteNo());
        }
        if (!StringUtils.hasText(entity.getObjectTitle())) {
            entity.setObjectTitle(route.getPartCode());
        }
    }

    /**
     * 校验同项目同对象同版本未归档
     *
     * @param entity 交付物
     */
    private void ensureNotArchived(PdmClbDeliverable entity) {
        Long count = deliverableMapper.selectCount(new LambdaQueryWrapper<PdmClbDeliverable>()
                .eq(PdmClbDeliverable::getProjectId, entity.getProjectId())
                .eq(PdmClbDeliverable::getObjectType, entity.getObjectType())
                .eq(PdmClbDeliverable::getObjectId, entity.getObjectId())
                .eq(PdmClbDeliverable::getObjectVersionNo, entity.getObjectVersionNo()));
        if (count != null && count > 0) {
            throw new BizException(ErrorCode.REPEAT_SUBMIT, "biz.pdm.collab.deliverable-been-archived", "该交付物已归档");
        }
    }

    /**
     * 实体转视图
     *
     * @param entity 交付物
     * @return 视图
     */
    private PdmClbDeliverableVO toVo(PdmClbDeliverable entity) {
        PdmClbDeliverableVO vo = new PdmClbDeliverableVO();
        vo.setId(entity.getId());
        vo.setProjectId(entity.getProjectId());
        vo.setProjectName(entity.getProjectName());
        vo.setWorkItemId(entity.getWorkItemId());
        vo.setWorkItemName(entity.getWorkItemName());
        vo.setObjectType(entity.getObjectType());
        vo.setObjectId(entity.getObjectId());
        vo.setObjectNo(entity.getObjectNo());
        vo.setObjectTitle(entity.getObjectTitle());
        vo.setObjectVersionNo(entity.getObjectVersionNo());
        vo.setDeliverableType(entity.getDeliverableType());
        vo.setStatus(entity.getStatus());
        vo.setArchivedBy(entity.getArchivedBy());
        vo.setArchivedAt(entity.getArchivedAt());
        vo.setRemark(entity.getRemark());
        vo.setCreateTime(entity.getCreateTime());
        return vo;
    }
}
