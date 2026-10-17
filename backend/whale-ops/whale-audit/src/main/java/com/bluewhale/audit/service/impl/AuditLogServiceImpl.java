package com.bluewhale.audit.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.audit.dto.AuditLogQueryRequest;
import com.bluewhale.audit.service.AuditLogService;
import com.bluewhale.audit.vo.AuditLogVO;
import com.bluewhale.common.audit.entity.AuditLogRecord;
import com.bluewhale.common.audit.mapper.AuditLogMapper;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.core.result.PageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 审计日志查询服务实现（只读，不做任何写入，保持表 append-only 语义）
 *
 * @author BlueWhale
 */
@Service
@RequiredArgsConstructor
public class AuditLogServiceImpl implements AuditLogService {

    /** 单页最大条数：防止深度分页拖垮数据库 */
    private static final long MAX_PAGE_SIZE = 200L;

    /** 单页最小条数 */
    private static final long MIN_PAGE_SIZE = 1L;

    private final AuditLogMapper auditLogMapper;

    @Override
    public PageResult<AuditLogVO> page(AuditLogQueryRequest request) {
        LambdaQueryWrapper<AuditLogRecord> wrapper = buildWrapper(request);
        wrapper.orderByDesc(AuditLogRecord::getCreateTime);
        Page<AuditLogRecord> page = new Page<>(request.getCurrent(), clampPageSize(request.getSize()));
        IPage<AuditLogRecord> result = auditLogMapper.selectPage(page, wrapper);
        List<AuditLogVO> records = result.getRecords().stream().map(AuditLogServiceImpl::toVo).toList();
        return PageResult.of(records, result.getTotal(), result.getCurrent(), result.getSize());
    }

    @Override
    public AuditLogVO getDetail(Long id) {
        AuditLogRecord record = auditLogMapper.selectById(id);
        if (record == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        return toVo(record);
    }

    @Override
    public List<AuditLogVO> listByTraceId(String traceId) {
        if (!StringUtils.hasText(traceId)) {
            return List.of();
        }
        return auditLogMapper.selectList(new LambdaQueryWrapper<AuditLogRecord>()
                        .eq(AuditLogRecord::getTraceId, traceId)
                        .orderByAsc(AuditLogRecord::getCreateTime))
                .stream()
                .map(AuditLogServiceImpl::toVo)
                .toList();
    }

    /**
     * 按查询条件构造 wrapper：等值条件 + 时间范围；username 支持模糊匹配
     * <p>条件均带「是否提供」开关，避免生成恒真/冗余条件导致索引失效。</p>
     */
    private static LambdaQueryWrapper<AuditLogRecord> buildWrapper(AuditLogQueryRequest request) {
        LambdaQueryWrapper<AuditLogRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(request.getUserId() != null, AuditLogRecord::getUserId, request.getUserId());
        wrapper.like(StringUtils.hasText(request.getUsername()), AuditLogRecord::getUsername, request.getUsername());
        wrapper.eq(StringUtils.hasText(request.getModule()), AuditLogRecord::getModule, request.getModule());
        wrapper.eq(StringUtils.hasText(request.getOperateType()),
                AuditLogRecord::getOperateType, request.getOperateType());
        wrapper.eq(request.getBizStatus() != null, AuditLogRecord::getBizStatus, request.getBizStatus());
        wrapper.eq(StringUtils.hasText(request.getTraceId()), AuditLogRecord::getTraceId, request.getTraceId());
        wrapper.ge(request.getStartTime() != null, AuditLogRecord::getCreateTime, request.getStartTime());
        wrapper.le(request.getEndTime() != null, AuditLogRecord::getCreateTime, request.getEndTime());
        return wrapper;
    }

    /** 单页条数收敛到 [1, 200] */
    private static long clampPageSize(long size) {
        return Math.min(Math.max(size, MIN_PAGE_SIZE), MAX_PAGE_SIZE);
    }

    private static AuditLogVO toVo(AuditLogRecord record) {
        AuditLogVO vo = new AuditLogVO();
        BeanUtils.copyProperties(record, vo);
        return vo;
    }
}
