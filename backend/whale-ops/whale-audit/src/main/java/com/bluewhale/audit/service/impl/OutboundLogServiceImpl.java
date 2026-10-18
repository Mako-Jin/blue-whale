package com.bluewhale.audit.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.audit.dto.OutboundLogQueryRequest;
import com.bluewhale.audit.service.OutboundLogService;
import com.bluewhale.audit.vo.OutboundLogVO;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.log.entity.OutboundLogRecord;
import com.bluewhale.common.log.mapper.OutboundLogMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 后端出站三方调用日志服务实现：分页查询（REQ-SEC-006 通道②）。
 *
 * <p>写入由 {@code OutboundLogInterceptor} 在业务服务侧完成，本服务只读；条件均带「是否提供」开关，
 * 避免生成恒真/冗余条件导致索引失效。</p>
 *
 * @author BlueWhale
 */
@Service
@RequiredArgsConstructor
public class OutboundLogServiceImpl implements OutboundLogService {

    /** 单页最大条数：防止深度分页拖垮数据库 */
    private static final long MAX_PAGE_SIZE = 200L;

    /** 单页最小条数 */
    private static final long MIN_PAGE_SIZE = 1L;

    private final OutboundLogMapper outboundLogMapper;

    @Override
    public PageResult<OutboundLogVO> page(OutboundLogQueryRequest request) {
        LambdaQueryWrapper<OutboundLogRecord> wrapper = buildWrapper(request);
        wrapper.orderByDesc(OutboundLogRecord::getCreateTime);
        Page<OutboundLogRecord> page = new Page<>(request.getCurrent(), clampPageSize(request.getSize()));
        IPage<OutboundLogRecord> result = outboundLogMapper.selectPage(page, wrapper);
        List<OutboundLogVO> records = result.getRecords().stream().map(OutboundLogServiceImpl::toVo).toList();
        return PageResult.of(records, result.getTotal(), result.getCurrent(), result.getSize());
    }

    /**
     * 按查询条件构造 wrapper：等值条件 + 时间范围
     *
     * @param request 查询条件
     * @return MyBatis-Plus 条件构造器
     */
    private static LambdaQueryWrapper<OutboundLogRecord> buildWrapper(OutboundLogQueryRequest request) {
        LambdaQueryWrapper<OutboundLogRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(StringUtils.hasText(request.getTraceId()), OutboundLogRecord::getTraceId, request.getTraceId());
        wrapper.eq(request.getUserId() != null, OutboundLogRecord::getUserId, request.getUserId());
        wrapper.eq(StringUtils.hasText(request.getModule()), OutboundLogRecord::getModule, request.getModule());
        wrapper.eq(StringUtils.hasText(request.getTargetHost()), OutboundLogRecord::getTargetHost, request.getTargetHost());
        wrapper.eq(StringUtils.hasText(request.getHttpMethod()), OutboundLogRecord::getHttpMethod, request.getHttpMethod());
        wrapper.eq(request.getBizStatus() != null, OutboundLogRecord::getBizStatus, request.getBizStatus());
        wrapper.ge(request.getStartTime() != null, OutboundLogRecord::getCreateTime, request.getStartTime());
        wrapper.le(request.getEndTime() != null, OutboundLogRecord::getCreateTime, request.getEndTime());
        return wrapper;
    }

    /**
     * 单页条数收敛到 [1, 200]
     *
     * @param size 请求单页条数
     * @return 收敛后的单页条数
     */
    private static long clampPageSize(long size) {
        return Math.min(Math.max(size, MIN_PAGE_SIZE), MAX_PAGE_SIZE);
    }

    /**
     * 出站日志实体转视图对象
     *
     * @param record 出站日志实体
     * @return 出站日志视图对象
     */
    private static OutboundLogVO toVo(OutboundLogRecord record) {
        OutboundLogVO vo = new OutboundLogVO();
        BeanUtils.copyProperties(record, vo);
        return vo;
    }
}
