package com.bluewhale.pdm.change.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.pdm.change.dto.PdmChgTraceQueryRequest;
import com.bluewhale.pdm.change.entity.PdmChgTrace;
import com.bluewhale.pdm.change.mapper.PdmChgTraceMapper;
import com.bluewhale.pdm.change.service.PdmChgTraceService;
import com.bluewhale.pdm.change.vo.PdmChgTraceVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 变更追溯服务实现
 *
 * <p>仅查询，不提供写入/编辑/删除端点。</p>
 *
 * @author BlueWhale
 */
@Service
@RequiredArgsConstructor
public class PdmChgTraceServiceImpl implements PdmChgTraceService {

    /** 单页最大条数 */
    private static final long MAX_PAGE_SIZE = 200L;

    private final PdmChgTraceMapper traceMapper;

    @Override
    public PageResult<PdmChgTraceVO> page(PdmChgTraceQueryRequest request) {
        LambdaQueryWrapper<PdmChgTrace> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(StringUtils.hasText(request.getBizType()), PdmChgTrace::getBizType, request.getBizType());
        wrapper.eq(request.getBizId() != null, PdmChgTrace::getBizId, request.getBizId());
        wrapper.eq(StringUtils.hasText(request.getObjectType()), PdmChgTrace::getObjectType,
                request.getObjectType());
        wrapper.eq(request.getObjectId() != null, PdmChgTrace::getObjectId, request.getObjectId());
        wrapper.eq(StringUtils.hasText(request.getObjectCode()), PdmChgTrace::getObjectCode,
                request.getObjectCode());
        wrapper.ge(request.getFromTime() != null, PdmChgTrace::getTraceTime, request.getFromTime());
        wrapper.le(request.getToTime() != null, PdmChgTrace::getTraceTime, request.getToTime());
        wrapper.orderByDesc(PdmChgTrace::getTraceTime).orderByDesc(PdmChgTrace::getId);
        long size = Math.min(Math.max(request.getSize(), 1L), MAX_PAGE_SIZE);
        Page<PdmChgTrace> page = traceMapper.selectPage(new Page<>(request.getCurrent(), size), wrapper);
        List<PdmChgTraceVO> records = page.getRecords().stream()
                .map(PdmChgTraceServiceImpl::toVo)
                .toList();
        return PageResult.of(records, page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Override
    public List<PdmChgTraceVO> timeline(String objectType, Long objectId) {
        if (!StringUtils.hasText(objectType) || objectId == null) {
            throw new BizException(ErrorCode.PARAM_ERROR, "对象类型与对象ID不能为空");
        }
        return traceMapper.selectList(new LambdaQueryWrapper<PdmChgTrace>()
                        .eq(PdmChgTrace::getObjectType, objectType.trim().toUpperCase())
                        .eq(PdmChgTrace::getObjectId, objectId)
                        .orderByAsc(PdmChgTrace::getTraceTime)
                        .orderByAsc(PdmChgTrace::getId))
                .stream()
                .map(PdmChgTraceServiceImpl::toVo)
                .toList();
    }

    /**
     * 实体转视图。
     *
     * @param entity 追溯实体
     * @return 追溯视图
     */
    private static PdmChgTraceVO toVo(PdmChgTrace entity) {
        PdmChgTraceVO vo = new PdmChgTraceVO();
        vo.setId(entity.getId());
        vo.setBizType(entity.getBizType());
        vo.setBizId(entity.getBizId());
        vo.setBizNo(entity.getBizNo());
        vo.setObjectType(entity.getObjectType());
        vo.setObjectId(entity.getObjectId());
        vo.setObjectCode(entity.getObjectCode());
        vo.setAction(entity.getAction());
        vo.setFromVersion(entity.getFromVersion());
        vo.setToVersion(entity.getToVersion());
        vo.setContent(entity.getContent());
        vo.setOperatorId(entity.getOperatorId());
        vo.setTraceTime(entity.getTraceTime());
        return vo;
    }
}
