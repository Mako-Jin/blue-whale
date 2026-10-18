package com.bluewhale.audit.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.audit.dto.ApiLogQueryRequest;
import com.bluewhale.audit.service.ApiLogService;
import com.bluewhale.audit.vo.ApiLogVO;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.log.entity.ApiLogRecord;
import com.bluewhale.common.log.mapper.ApiLogMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 后端接口日志服务实现：分页查询（REQ-SEC-006 通道①）。
 *
 * <p>写入由 {@code ApiLogFilter} 在业务服务侧完成，本服务只读；条件均带「是否提供」开关，
 * 避免生成恒真/冗余条件导致索引失效。</p>
 *
 * @author BlueWhale
 */
@Service
@RequiredArgsConstructor
public class ApiLogServiceImpl implements ApiLogService {

    /** 单页最大条数：防止深度分页拖垮数据库 */
    private static final long MAX_PAGE_SIZE = 200L;

    /** 单页最小条数 */
    private static final long MIN_PAGE_SIZE = 1L;

    private final ApiLogMapper apiLogMapper;

    @Override
    public PageResult<ApiLogVO> page(ApiLogQueryRequest request) {
        LambdaQueryWrapper<ApiLogRecord> wrapper = buildWrapper(request);
        wrapper.orderByDesc(ApiLogRecord::getCreateTime);
        Page<ApiLogRecord> page = new Page<>(request.getCurrent(), clampPageSize(request.getSize()));
        IPage<ApiLogRecord> result = apiLogMapper.selectPage(page, wrapper);
        List<ApiLogVO> records = result.getRecords().stream().map(ApiLogServiceImpl::toVo).toList();
        return PageResult.of(records, result.getTotal(), result.getCurrent(), result.getSize());
    }

    /**
     * 按查询条件构造 wrapper：等值条件 + 时间范围
     *
     * @param request 查询条件
     * @return MyBatis-Plus 条件构造器
     */
    private static LambdaQueryWrapper<ApiLogRecord> buildWrapper(ApiLogQueryRequest request) {
        LambdaQueryWrapper<ApiLogRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(StringUtils.hasText(request.getTraceId()), ApiLogRecord::getTraceId, request.getTraceId());
        wrapper.eq(request.getUserId() != null, ApiLogRecord::getUserId, request.getUserId());
        wrapper.eq(StringUtils.hasText(request.getModule()), ApiLogRecord::getModule, request.getModule());
        wrapper.eq(StringUtils.hasText(request.getRequestUri()), ApiLogRecord::getRequestUri, request.getRequestUri());
        wrapper.eq(StringUtils.hasText(request.getHttpMethod()), ApiLogRecord::getHttpMethod, request.getHttpMethod());
        wrapper.eq(request.getBizStatus() != null, ApiLogRecord::getBizStatus, request.getBizStatus());
        wrapper.ge(request.getStartTime() != null, ApiLogRecord::getCreateTime, request.getStartTime());
        wrapper.le(request.getEndTime() != null, ApiLogRecord::getCreateTime, request.getEndTime());
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
     * 接口日志实体转视图对象
     *
     * @param record 接口日志实体
     * @return 接口日志视图对象
     */
    private static ApiLogVO toVo(ApiLogRecord record) {
        ApiLogVO vo = new ApiLogVO();
        BeanUtils.copyProperties(record, vo);
        return vo;
    }
}
