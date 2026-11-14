package com.bluewhale.workflow.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.workflow.converter.WorkflowConverter;
import com.bluewhale.workflow.dto.ProcessCcQueryRequest;
import com.bluewhale.workflow.entity.ProcessCc;
import com.bluewhale.workflow.mapper.ProcessCcMapper;
import com.bluewhale.workflow.service.ProcessCcService;
import com.bluewhale.workflow.support.WorkflowConstants;
import com.bluewhale.workflow.vo.ProcessCcVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 流程抄送服务实现。
 *
 * @author BlueWhale
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProcessCcServiceImpl implements ProcessCcService {

    /** 发起级抄送节点键 */
    public static final String START_NODE_KEY = "START";

    /** 发起级抄送节点名 */
    public static final String START_NODE_NAME = "发起抄送";

    /** 候选收件人上限（账号口径下超限显式报错而非静默截断） */
    private static final int MAX_RECIPIENT_CANDIDATES = 100;

    /** 抄送 Mapper */
    private final ProcessCcMapper processCcMapper;

    @Override
    public PageResult<ProcessCcVO> page(ProcessCcQueryRequest query) {
        ProcessCcQueryRequest condition = query == null ? new ProcessCcQueryRequest() : query;
        long current = Math.min(Math.max(condition.getCurrent(), 1L), WorkflowConstants.MAX_PAGE_CURRENT);
        long size = condition.getSize() <= 0
                ? WorkflowConstants.DEFAULT_PAGE_SIZE
                : Math.min(condition.getSize(), WorkflowConstants.MAX_PAGE_SIZE);
        List<String> candidates = normalizeCandidates(condition.getRecipientCandidates());
        boolean hasCandidates = !candidates.isEmpty();
        LambdaQueryWrapper<ProcessCc> wrapper = new LambdaQueryWrapper<ProcessCc>()
                .eq(condition.getInstanceId() != null, ProcessCc::getInstanceId, condition.getInstanceId())
                .eq(!hasCandidates && hasText(condition.getRecipient()), ProcessCc::getRecipient, condition.getRecipient())
                .in(hasCandidates, ProcessCc::getRecipient, candidates)
                .eq(condition.getReadFlag() != null, ProcessCc::getReadFlag, condition.getReadFlag())
                .orderByDesc(ProcessCc::getCreateTime)
                .orderByDesc(ProcessCc::getId);
        Page<ProcessCc> page = processCcMapper.selectPage(new Page<>(current, size, false), wrapper);
        List<ProcessCcVO> records = page.getRecords().stream()
                .map(WorkflowConverter.INSTANCE::toCcVo)
                .toList();
        return PageResult.of(records, page.getTotal(), current, size);
    }

    @Override
    public ProcessCcVO get(Long id) {
        ProcessCc cc = processCcMapper.selectById(id);
        if (cc == null) {
            throw new BizException(ErrorCode.NOT_FOUND.getCode(), "抄送记录不存在");
        }
        return WorkflowConverter.INSTANCE.toCcVo(cc);
    }

    @Override
    public void markRead(Long id) {
        ProcessCc cc = processCcMapper.selectById(id);
        if (cc == null) {
            throw new BizException(ErrorCode.NOT_FOUND.getCode(), "抄送记录不存在");
        }
        if (cc.getReadFlag() != null && cc.getReadFlag() == WorkflowConstants.CC_READ) {
            return;
        }
        cc.setReadFlag(WorkflowConstants.CC_READ);
        cc.setReadTime(LocalDateTime.now());
        processCcMapper.updateById(cc);
    }

    @Override
    public void createStartCc(Long instanceId, List<String> ccAccounts) {
        createNodeCc(instanceId, START_NODE_KEY, START_NODE_NAME, ccAccounts);
    }

    @Override
    public void createNodeCc(Long instanceId, String nodeKey, String nodeName, List<String> recipients) {
        if (instanceId == null || recipients == null || recipients.isEmpty()) {
            return;
        }
        for (String recipient : recipients) {
            if (recipient == null || recipient.isBlank()) {
                continue;
            }
            ProcessCc cc = new ProcessCc();
            cc.setInstanceId(instanceId);
            cc.setNodeKey(nodeKey);
            cc.setNodeName(nodeName);
            cc.setRecipient(recipient.trim());
            cc.setReadFlag(WorkflowConstants.CC_UNREAD);
            try {
                processCcMapper.insert(cc);
            } catch (DuplicateKeyException ignored) {
                // 唯一键命中视为已抄送，保持幂等
                log.debug("抄送记录已存在，忽略重复写入: instanceId={}, nodeKey={}, recipient={}",
                        instanceId, nodeKey, cc.getRecipient());
            }
        }
    }

    /**
     * 规范化候选收件人：去空、trim、去重；超过上限时显式报错，绝不静默截断。
     *
     * @param values 原始候选值（账号）
     * @return 规范化后的候选列表；无有效值时返回空列表
     */
    private static List<String> normalizeCandidates(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        List<String> normalized = values.stream()
                .filter(ProcessCcServiceImpl::hasText)
                .map(String::trim)
                .distinct()
                .toList();
        if (normalized.size() > MAX_RECIPIENT_CANDIDATES) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(),
                    "候选收件人去重后 " + normalized.size() + " 个，超过上限 " + MAX_RECIPIENT_CANDIDATES);
        }
        return normalized;
    }

    /**
     * 判断文本非空。
     *
     * @param value 文本
     * @return 非空返回 {@code true}
     */
    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
