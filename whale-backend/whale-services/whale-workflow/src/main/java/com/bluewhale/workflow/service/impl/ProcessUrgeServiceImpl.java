package com.bluewhale.workflow.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.workflow.converter.WorkflowConverter;
import com.bluewhale.workflow.dto.UrgeRequest;
import com.bluewhale.workflow.entity.ProcessInstance;
import com.bluewhale.workflow.entity.ProcessTask;
import com.bluewhale.workflow.entity.ProcessUrge;
import com.bluewhale.workflow.mapper.ProcessInstanceMapper;
import com.bluewhale.workflow.mapper.ProcessTaskMapper;
import com.bluewhale.workflow.mapper.ProcessUrgeMapper;
import com.bluewhale.workflow.service.ProcessUrgeService;
import com.bluewhale.workflow.vo.ProcessUrgeVO;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 流程催办服务实现。
 *
 * @author BlueWhale
 */
@Service
@RequiredArgsConstructor
public class ProcessUrgeServiceImpl implements ProcessUrgeService {

    /** 频控窗口（小时） */
    private static final long FREQUENCY_WINDOW_HOURS = 1L;

    /** 频控时间窗格式（创建时间截断到小时） */
    private static final DateTimeFormatter WINDOW_BUCKET_FORMATTER =
            DateTimeFormatter.ofPattern("yyyyMMddHH");

    /** 单页最大条数 */
    private static final int MAX_PAGE_SIZE = 200;

    /** 默认每页条数 */
    private static final long DEFAULT_PAGE_SIZE = 10L;

    /** 催办记录 Mapper */
    private final ProcessUrgeMapper processUrgeMapper;

    /** 流程实例 Mapper */
    private final ProcessInstanceMapper processInstanceMapper;

    /** 待办任务 Mapper */
    private final ProcessTaskMapper processTaskMapper;

    @Override
    public void urge(Long instanceId, UrgeRequest request) {
        ProcessInstance instance = processInstanceMapper.selectById(instanceId);
        if (instance == null) {
            throw new BizException(ErrorCode.NOT_FOUND.getCode(), "流程实例不存在");
        }
        if (request.getTaskId() != null) {
            ProcessTask task = processTaskMapper.selectById(request.getTaskId());
            if (task == null || !instanceId.equals(task.getInstanceId())) {
                throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "催办任务不属于该流程实例");
            }
        }
        String urger = request.getUrger().trim();
        LocalDateTime now = LocalDateTime.now();
        Long recent = processUrgeMapper.selectCount(new LambdaQueryWrapper<ProcessUrge>()
                .eq(ProcessUrge::getInstanceId, instanceId)
                .eq(ProcessUrge::getUrger, urger)
                .ge(ProcessUrge::getCreateTime, now.minusHours(FREQUENCY_WINDOW_HOURS)));
        if (recent != null && recent > 0) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "催办过于频繁，1 小时内仅允许一次");
        }
        ProcessUrge entity = new ProcessUrge();
        entity.setInstanceId(instanceId);
        entity.setTaskId(request.getTaskId());
        entity.setUrger(urger);
        entity.setTargetAccount(blankToNull(request.getTargetAccount()));
        entity.setComment(blankToNull(request.getComment()));
        entity.setWindowBucket(now.format(WINDOW_BUCKET_FORMATTER));
        try {
            processUrgeMapper.insert(entity);
        } catch (DuplicateKeyException e) {
            // 唯一键 (tenant_id, instance_id, urger, window_bucket) 兜底并发绕过
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "催办过于频繁，1 小时内仅允许一次");
        }
    }

    @Override
    public PageResult<ProcessUrgeVO> page(Long instanceId, long current, long size) {
        long safeCurrent = Math.max(1L, current);
        long safeSize = size <= 0 ? DEFAULT_PAGE_SIZE : Math.min(size, MAX_PAGE_SIZE);
        LambdaQueryWrapper<ProcessUrge> wrapper = new LambdaQueryWrapper<ProcessUrge>()
                .eq(ProcessUrge::getInstanceId, instanceId)
                .orderByDesc(ProcessUrge::getCreateTime)
                .orderByDesc(ProcessUrge::getId);
        Page<ProcessUrge> page = processUrgeMapper.selectPage(new Page<>(safeCurrent, safeSize, false), wrapper);
        List<ProcessUrgeVO> records = page.getRecords().stream()
                .map(WorkflowConverter.INSTANCE::toUrgeVo)
                .toList();
        return PageResult.of(records, page.getTotal(), safeCurrent, safeSize);
    }

    /**
     * 空白文本转 {@code null}。
     *
     * @param value 文本
     * @return 去空白后的文本或 {@code null}
     */
    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
