package com.bluewhale.job.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.job.converter.JobConverter;
import com.bluewhale.job.dto.JobAlertQueryRequest;
import com.bluewhale.job.po.JobAlertPo;
import com.bluewhale.job.mapper.JobAlertMapper;
import com.bluewhale.job.service.JobAlertService;
import com.bluewhale.job.vo.JobAlertVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 任务告警记录服务实现（写入由失败告警组件完成，本服务只读）。
 *
 * @author BlueWhale
 */
@Service
@RequiredArgsConstructor
public class JobAlertServiceImpl implements JobAlertService {

    /** 单页最大条数：防止深度分页拖垮数据库 */
    private static final long MAX_PAGE_SIZE = 200L;

    private final JobAlertMapper jobAlertMapper;

    @Override
    public PageResult<JobAlertVO> page(JobAlertQueryRequest request) {
        LambdaQueryWrapper<JobAlertPo> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(request.getJobId() != null, JobAlertPo::getJobId, request.getJobId());
        wrapper.eq(StringUtils.hasText(request.getLevel()), JobAlertPo::getLevel, request.getLevel());
        wrapper.orderByDesc(JobAlertPo::getCreateTime);
        long size = Math.min(Math.max(request.getSize(), 1L), MAX_PAGE_SIZE);
        Page<JobAlertPo> page = jobAlertMapper.selectPage(new Page<>(request.getCurrent(), size), wrapper);
        List<JobAlertVO> records = page.getRecords().stream().map(JobAlertServiceImpl::toVo).toList();
        return PageResult.of(records, page.getTotal(), page.getCurrent(), page.getSize());
    }

    /**
     * 告警实体转视图对象
     *
     * @param jobAlert 告警实体
     * @return 告警视图对象
     */
    private static JobAlertVO toVo(JobAlertPo jobAlert) {
        return JobConverter.INSTANCE.toAlertVo(jobAlert);
    }
}
