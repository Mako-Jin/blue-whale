package com.bluewhale.pdm.collab.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.pdm.collab.dto.PdmClbReviewCloseRequest;
import com.bluewhale.pdm.collab.dto.PdmClbReviewQueryRequest;
import com.bluewhale.pdm.collab.dto.PdmClbReviewSaveRequest;
import com.bluewhale.pdm.collab.entity.PdmClbReview;
import com.bluewhale.pdm.collab.mapper.PdmClbReviewMapper;
import com.bluewhale.pdm.collab.service.PdmClbReviewService;
import com.bluewhale.pdm.collab.support.PdmClbObjectType;
import com.bluewhale.pdm.collab.support.PdmClbReviewConstants;
import com.bluewhale.pdm.collab.vo.PdmClbReviewVO;
import com.bluewhale.pdm.document.entity.PdmDocDocument;
import com.bluewhale.pdm.document.entity.PdmDocVersion;
import com.bluewhale.pdm.document.mapper.PdmDocDocumentMapper;
import com.bluewhale.pdm.document.mapper.PdmDocVersionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 研发评审服务实现
 *
 * <p>草稿可修改；关闭必须填写合法结论并落关闭人/时间；关闭后不可修改或重复关闭。
 * 附件仅校验文档与版本存在，不要求已发布（评审阶段允许引用草稿证据）。</p>
 *
 * @author BlueWhale
 */
@Service
@RequiredArgsConstructor
public class PdmClbReviewServiceImpl implements PdmClbReviewService {

    /** 单页最大条数 */
    private static final long MAX_PAGE_SIZE = 200L;

    /** 评审编号日期格式 */
    private static final DateTimeFormatter NO_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final PdmClbReviewMapper reviewMapper;

    private final PdmDocDocumentMapper documentMapper;

    private final PdmDocVersionMapper versionMapper;

    @Override
    public PageResult<PdmClbReviewVO> page(PdmClbReviewQueryRequest request) {
        LambdaQueryWrapper<PdmClbReview> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(request.getProjectId() != null, PdmClbReview::getProjectId, request.getProjectId());
        wrapper.eq(StringUtils.hasText(request.getReviewType()), PdmClbReview::getReviewType,
                request.getReviewType());
        wrapper.eq(StringUtils.hasText(request.getStatus()), PdmClbReview::getStatus, request.getStatus());
        if (StringUtils.hasText(request.getKeyword())) {
            String keyword = request.getKeyword().trim();
            wrapper.and(query -> query.like(PdmClbReview::getReviewNo, keyword)
                    .or().like(PdmClbReview::getTitle, keyword)
                    .or().like(PdmClbReview::getObjectNo, keyword));
        }
        wrapper.orderByDesc(PdmClbReview::getCreateTime).orderByDesc(PdmClbReview::getId);
        long size = Math.min(Math.max(request.getSize(), 1L), MAX_PAGE_SIZE);
        Page<PdmClbReview> page = reviewMapper.selectPage(new Page<>(request.getCurrent(), size), wrapper);
        List<PdmClbReviewVO> records = page.getRecords().stream().map(this::toVo).toList();
        return PageResult.of(records, page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Override
    public PdmClbReviewVO detail(Long id) {
        return toVo(requireReview(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PdmClbReviewVO create(PdmClbReviewSaveRequest request, Long userId) {
        PdmClbReview entity = new PdmClbReview();
        entity.setReviewNo(newReviewNo());
        entity.setStatus(PdmClbReviewConstants.STATUS_DRAFT);
        entity.setCreatedBy(userId);
        applyRequest(entity, request);
        reviewMapper.insert(entity);
        return toVo(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PdmClbReviewVO update(Long id, PdmClbReviewSaveRequest request, Long userId) {
        PdmClbReview entity = requireReview(id);
        requireDraft(entity);
        applyRequest(entity, request);
        reviewMapper.updateById(entity);
        return toVo(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PdmClbReviewVO close(Long id, PdmClbReviewCloseRequest request, Long userId) {
        PdmClbReview entity = requireReview(id);
        requireDraft(entity);
        String conclusion = request.getConclusion().trim().toUpperCase();
        if (!PdmClbReviewConstants.isValidConclusion(conclusion)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "biz.pdm.collab.review-conclusion-supports-passed-conditional-rejected",
                          "评审结论仅支持 PASSED/CONDITIONAL/REJECTED");
        }
        entity.setConclusion(conclusion);
        entity.setConclusionComment(request.getConclusionComment());
        entity.setStatus(PdmClbReviewConstants.STATUS_CLOSED);
        entity.setClosedBy(userId);
        entity.setClosedAt(LocalDateTime.now());
        reviewMapper.updateById(entity);
        return toVo(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        PdmClbReview entity = requireReview(id);
        requireDraft(entity);
        reviewMapper.deleteById(id);
    }

    /**
     * 应用保存请求并校验
     *
     * @param entity  评审实体
     * @param request 保存请求
     */
    private void applyRequest(PdmClbReview entity, PdmClbReviewSaveRequest request) {
        String reviewType = request.getReviewType().trim().toUpperCase();
        if (!PdmClbReviewConstants.isValidType(reviewType)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "biz.pdm.collab.review-type-supports-design-prototype-transfer",
                          "评审类型仅支持 DESIGN/PROTOTYPE/TRANSFER");
        }
        String objectType = StringUtils.hasText(request.getObjectType())
                ? request.getObjectType().trim().toUpperCase() : null;
        if (objectType != null && !PdmClbObjectType.isValidReview(objectType)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "biz.pdm.collab.review-object-type-invalid", "评审对象类型不合法");
        }
        validateAttachment(request);
        entity.setReviewType(reviewType);
        entity.setTitle(request.getTitle().trim());
        entity.setProjectId(request.getProjectId());
        entity.setProjectName(request.getProjectName());
        entity.setObjectType(objectType);
        entity.setObjectId(request.getObjectId());
        entity.setObjectNo(request.getObjectNo());
        entity.setObjectVersionNo(request.getObjectVersionNo());
        entity.setParticipants(request.getParticipants());
        entity.setAttachmentDocumentId(request.getAttachmentDocumentId());
        entity.setAttachmentVersionNo(request.getAttachmentVersionNo());
        entity.setApprovalInstanceId(request.getApprovalInstanceId());
    }

    /**
     * 校验附件文档与版本存在
     *
     * @param request 保存请求
     */
    private void validateAttachment(PdmClbReviewSaveRequest request) {
        if (request.getAttachmentDocumentId() == null) {
            return;
        }
        PdmDocDocument document = documentMapper.selectById(request.getAttachmentDocumentId());
        if (document == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "biz.pdm.collab.review-attachment-document-found", "评审附件文档不存在");
        }
        if (!StringUtils.hasText(request.getAttachmentVersionNo())) {
            return;
        }
        PdmDocVersion version = versionMapper.selectOne(new LambdaQueryWrapper<PdmDocVersion>()
                .eq(PdmDocVersion::getDocumentId, request.getAttachmentDocumentId())
                .eq(PdmDocVersion::getVersionNo, request.getAttachmentVersionNo().trim()));
        if (version == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "biz.pdm.collab.review-attachment-document-version-found", "评审附件文档版本不存在");
        }
    }

    /**
     * 要求评审存在
     *
     * @param id 评审 ID
     * @return 评审实体
     */
    private PdmClbReview requireReview(Long id) {
        PdmClbReview entity = reviewMapper.selectById(id);
        if (entity == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "biz.pdm.collab.review-record-found", "评审记录不存在");
        }
        return entity;
    }

    /**
     * 要求评审为草稿
     *
     * @param entity 评审实体
     */
    private void requireDraft(PdmClbReview entity) {
        if (!PdmClbReviewConstants.STATUS_DRAFT.equals(entity.getStatus())) {
            throw new BizException(ErrorCode.PARAM_ERROR, "biz.pdm.collab.review-close-cannot-modify", "评审已关闭，不能再修改");
        }
    }

    /**
     * 生成评审编号：RV-日期-4 位随机数
     *
     * @return 评审编号
     */
    private String newReviewNo() {
        return "RV-" + LocalDateTime.now().format(NO_DATE_FORMATTER) + "-"
                + ThreadLocalRandom.current().nextInt(1000, 10000);
    }

    /**
     * 实体转视图
     *
     * @param entity 评审
     * @return 视图
     */
    private PdmClbReviewVO toVo(PdmClbReview entity) {
        PdmClbReviewVO vo = new PdmClbReviewVO();
        vo.setId(entity.getId());
        vo.setReviewNo(entity.getReviewNo());
        vo.setReviewType(entity.getReviewType());
        vo.setTitle(entity.getTitle());
        vo.setProjectId(entity.getProjectId());
        vo.setProjectName(entity.getProjectName());
        vo.setObjectType(entity.getObjectType());
        vo.setObjectId(entity.getObjectId());
        vo.setObjectNo(entity.getObjectNo());
        vo.setObjectVersionNo(entity.getObjectVersionNo());
        vo.setParticipants(entity.getParticipants());
        vo.setConclusion(entity.getConclusion());
        vo.setConclusionComment(entity.getConclusionComment());
        vo.setAttachmentDocumentId(entity.getAttachmentDocumentId());
        vo.setAttachmentVersionNo(entity.getAttachmentVersionNo());
        vo.setApprovalInstanceId(entity.getApprovalInstanceId());
        vo.setStatus(entity.getStatus());
        vo.setCreatedBy(entity.getCreatedBy());
        vo.setClosedBy(entity.getClosedBy());
        vo.setClosedAt(entity.getClosedAt());
        vo.setCreateTime(entity.getCreateTime());
        vo.setUpdateTime(entity.getUpdateTime());
        return vo;
    }
}
