package com.bluewhale.alert.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.alert.dto.AlertRecordQueryRequest;
import com.bluewhale.alert.entity.AlertRecord;
import com.bluewhale.alert.mapper.AlertRecordMapper;
import com.bluewhale.alert.service.AlertRecordService;
import com.bluewhale.alert.vo.AlertRecordVO;
import com.bluewhale.common.core.result.PageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 告警记录服务实现
 *
 * @author BlueWhale
 */
@Service
@RequiredArgsConstructor
public class AlertRecordServiceImpl implements AlertRecordService {

    /** 单页最大条数：防止深度分页拖垮数据库 */
    private static final long MAX_PAGE_SIZE = 200L;

    private final AlertRecordMapper alertRecordMapper;

    @Override
    public PageResult<AlertRecordVO> page(AlertRecordQueryRequest request) {
        LambdaQueryWrapper<AlertRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(StringUtils.hasText(request.getRuleCode()), AlertRecord::getRuleCode, request.getRuleCode());
        wrapper.eq(StringUtils.hasText(request.getAlertLevel()), AlertRecord::getAlertLevel, request.getAlertLevel());
        wrapper.eq(StringUtils.hasText(request.getStatus()), AlertRecord::getStatus, request.getStatus());
        wrapper.orderByDesc(AlertRecord::getTriggerTime);
        long size = Math.min(Math.max(request.getSize(), 1L), MAX_PAGE_SIZE);
        Page<AlertRecord> page = alertRecordMapper.selectPage(new Page<>(request.getCurrent(), size), wrapper);
        List<AlertRecordVO> records = page.getRecords().stream().map(AlertRecordServiceImpl::toVo).toList();
        return PageResult.of(records, page.getTotal(), page.getCurrent(), page.getSize());
    }

    /**
     * 记录实体转视图对象
     *
     * @param record 记录实体
     * @return 记录视图对象
     */
    private static AlertRecordVO toVo(AlertRecord record) {
        AlertRecordVO vo = new AlertRecordVO();
        BeanUtils.copyProperties(record, vo);
        return vo;
    }
}
