package com.bluewhale.pdm.change.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.pdm.change.dto.PdmChgRequestQueryRequest;
import com.bluewhale.pdm.change.dto.PdmChgRequestSaveRequest;
import com.bluewhale.pdm.change.entity.PdmChgRequest;
import com.bluewhale.pdm.change.mapper.PdmChgRequestMapper;
import com.bluewhale.pdm.change.service.PdmChgRequestService;
import com.bluewhale.pdm.change.support.PdmChgObjectType;
import com.bluewhale.pdm.change.support.PdmChgRequestStatus;
import com.bluewhale.pdm.change.vo.PdmChgImpactObjectVO;
import com.bluewhale.pdm.change.vo.PdmChgRequestVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 变更申请服务实现
 *
 * <p>仅草稿可修改/删除；提交要求影响对象非空；审批只允许从已提交流转；
 * 已通过/已驳回可关闭。变更落地由 M2-3/M2-4 的 ECO/ECN 负责。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PdmChgRequestServiceImpl implements PdmChgRequestService {

    /** 单页最大条数 */
    private static final long MAX_PAGE_SIZE = 200L;

    /** 编号时间戳格式 */
    private static final DateTimeFormatter NO_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    /** 影响对象序列化器 */
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final PdmChgRequestMapper chgRequestMapper;

    @Override
    public PageResult<PdmChgRequestVO> page(PdmChgRequestQueryRequest request) {
        LambdaQueryWrapper<PdmChgRequest> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(StringUtils.hasText(request.getChangeNo()), PdmChgRequest::getChangeNo,
                request.getChangeNo());
        wrapper.eq(StringUtils.hasText(request.getStatus()), PdmChgRequest::getStatus, request.getStatus());
        wrapper.eq(request.getInitiatorId() != null, PdmChgRequest::getInitiatorId, request.getInitiatorId());
        wrapper.orderByDesc(PdmChgRequest::getCreateTime).orderByDesc(PdmChgRequest::getId);
        long size = Math.min(Math.max(request.getSize(), 1L), MAX_PAGE_SIZE);
        Page<PdmChgRequest> page = chgRequestMapper.selectPage(new Page<>(request.getCurrent(), size), wrapper);
        List<PdmChgRequestVO> records = page.getRecords().stream().map(this::toVo).toList();
        return PageResult.of(records, page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Override
    public PdmChgRequestVO detail(Long id) {
        return toVo(requireRequest(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PdmChgRequestVO create(PdmChgRequestSaveRequest request, Long userId) {
        PdmChgRequest entity = new PdmChgRequest();
        entity.setChangeNo(newChangeNo());
        entity.setChangeReason(request.getChangeReason().trim());
        entity.setChangeScope(request.getChangeScope());
        entity.setImpactJson(toImpactJson(request.getImpactObjects()));
        entity.setStatus(PdmChgRequestStatus.DRAFT);
        entity.setInitiatorId(userId);
        chgRequestMapper.insert(entity);
        log.info("变更申请已创建: id={}, changeNo={}", entity.getId(), entity.getChangeNo());
        return toVo(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PdmChgRequestVO update(Long id, PdmChgRequestSaveRequest request) {
        PdmChgRequest entity = requireDraft(id);
        entity.setChangeReason(request.getChangeReason().trim());
        entity.setChangeScope(request.getChangeScope());
        entity.setImpactJson(toImpactJson(request.getImpactObjects()));
        chgRequestMapper.updateById(entity);
        return toVo(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        requireDraft(id);
        chgRequestMapper.deleteById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PdmChgRequestVO submit(Long id) {
        PdmChgRequest entity = requireDraft(id);
        if (parseImpact(entity.getImpactJson()).isEmpty()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "影响对象不能为空");
        }
        entity.setStatus(PdmChgRequestStatus.SUBMITTED);
        entity.setSubmitTime(LocalDateTime.now());
        chgRequestMapper.updateById(entity);
        return toVo(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PdmChgRequestVO approve(Long id, String comment, Long userId) {
        PdmChgRequest entity = requireSubmitted(id);
        entity.setStatus(PdmChgRequestStatus.APPROVED);
        entity.setApproveBy(userId);
        entity.setApproveTime(LocalDateTime.now());
        entity.setApproveComment(comment);
        chgRequestMapper.updateById(entity);
        return toVo(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PdmChgRequestVO reject(Long id, String comment, Long userId) {
        if (!StringUtils.hasText(comment)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "驳回必须填写审批意见");
        }
        PdmChgRequest entity = requireSubmitted(id);
        entity.setStatus(PdmChgRequestStatus.REJECTED);
        entity.setApproveBy(userId);
        entity.setApproveTime(LocalDateTime.now());
        entity.setApproveComment(comment.trim());
        chgRequestMapper.updateById(entity);
        return toVo(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PdmChgRequestVO close(Long id) {
        PdmChgRequest entity = requireRequest(id);
        if (!PdmChgRequestStatus.APPROVED.equals(entity.getStatus())
                && !PdmChgRequestStatus.REJECTED.equals(entity.getStatus())) {
            throw new BizException(ErrorCode.PARAM_ERROR, "仅已通过/已驳回的申请可关闭");
        }
        entity.setStatus(PdmChgRequestStatus.CLOSED);
        entity.setCloseTime(LocalDateTime.now());
        chgRequestMapper.updateById(entity);
        return toVo(entity);
    }

    /**
     * 查询申请，不存在报参数错误。
     *
     * @param id 申请ID
     * @return 申请实体
     */
    private PdmChgRequest requireRequest(Long id) {
        PdmChgRequest entity = chgRequestMapper.selectById(id);
        if (entity == null) {
            throw new BizException(ErrorCode.PARAM_ERROR, "变更申请不存在");
        }
        return entity;
    }

    /**
     * 查询草稿申请。
     *
     * @param id 申请ID
     * @return 申请实体
     */
    private PdmChgRequest requireDraft(Long id) {
        PdmChgRequest entity = requireRequest(id);
        if (!PdmChgRequestStatus.DRAFT.equals(entity.getStatus())) {
            throw new BizException(ErrorCode.PARAM_ERROR, "仅草稿状态的申请可修改/删除/提交");
        }
        return entity;
    }

    /**
     * 查询待审批申请。
     *
     * @param id 申请ID
     * @return 申请实体
     */
    private PdmChgRequest requireSubmitted(Long id) {
        PdmChgRequest entity = requireRequest(id);
        if (!PdmChgRequestStatus.SUBMITTED.equals(entity.getStatus())) {
            throw new BizException(ErrorCode.PARAM_ERROR, "仅已提交的申请可审批");
        }
        return entity;
    }

    /**
     * 影响对象转 JSON，并校验类型与标识。
     *
     * @param objects 影响对象
     * @return JSON 文本
     */
    private String toImpactJson(List<PdmChgImpactObjectVO> objects) {
        List<PdmChgImpactObjectVO> normalized = new ArrayList<>();
        if (objects != null) {
            for (PdmChgImpactObjectVO object : objects) {
                if (object == null) {
                    continue;
                }
                object.setObjectType(PdmChgObjectType.requireValid(object.getObjectType()));
                if (object.getObjectId() == null && !StringUtils.hasText(object.getObjectCode())) {
                    throw new BizException(ErrorCode.PARAM_ERROR, "影响对象必须提供对象ID或编码");
                }
                normalized.add(object);
            }
        }
        try {
            return OBJECT_MAPPER.writeValueAsString(normalized);
        } catch (Exception ex) {
            throw new BizException(ErrorCode.PARAM_ERROR, "影响对象格式不正确");
        }
    }

    /**
     * 解析影响对象 JSON。
     *
     * @param json JSON 文本
     * @return 影响对象列表
     */
    private List<PdmChgImpactObjectVO> parseImpact(String json) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            return OBJECT_MAPPER.readValue(json,
                    OBJECT_MAPPER.getTypeFactory().constructCollectionType(List.class, PdmChgImpactObjectVO.class));
        } catch (Exception ex) {
            log.warn("变更影响对象解析失败", ex);
            return List.of();
        }
    }

    /**
     * 实体转视图。
     *
     * @param entity 实体
     * @return 视图
     */
    private PdmChgRequestVO toVo(PdmChgRequest entity) {
        PdmChgRequestVO vo = new PdmChgRequestVO();
        vo.setId(entity.getId());
        vo.setChangeNo(entity.getChangeNo());
        vo.setChangeReason(entity.getChangeReason());
        vo.setChangeScope(entity.getChangeScope());
        vo.setImpactObjects(parseImpact(entity.getImpactJson()));
        vo.setStatus(entity.getStatus());
        vo.setInitiatorId(entity.getInitiatorId());
        vo.setSubmitTime(entity.getSubmitTime());
        vo.setApproveBy(entity.getApproveBy());
        vo.setApproveTime(entity.getApproveTime());
        vo.setApproveComment(entity.getApproveComment());
        vo.setCloseTime(entity.getCloseTime());
        vo.setCreateTime(entity.getCreateTime());
        return vo;
    }

    /**
     * 生成变更申请编号：CR-时间戳-4 位随机数。
     *
     * @return 申请编号
     */
    private static String newChangeNo() {
        return "CR-" + LocalDateTime.now().format(NO_TIME_FORMATTER) + "-"
                + ThreadLocalRandom.current().nextInt(1000, 10000);
    }
}
