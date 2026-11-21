package com.bluewhale.alert.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.alert.dto.AlertSilenceCreateRequest;
import com.bluewhale.alert.dto.AlertSilenceQueryRequest;
import com.bluewhale.alert.po.AlertSilencePo;
import com.bluewhale.alert.mapper.AlertSilenceMapper;
import com.bluewhale.alert.service.AlertSilenceService;
import com.bluewhale.alert.vo.AlertSilenceVO;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.core.result.PageResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 告警静默服务实现。
 *
 * <p>静默在告警<b>落库前</b>匹配：命中的告警以 {@code SILENCED} 状态落库但不发送通知，
 * 既保留审计证据，也避免静默期间丢失告警。匹配逻辑按租户自动隔离（依赖租户 SQL 插件）。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AlertSilenceServiceImpl implements AlertSilenceService {

    private final AlertSilenceMapper alertSilenceMapper;

    @Override
    public AlertSilenceVO create(AlertSilenceCreateRequest request) {
        if (request.getEndTime().isBefore(request.getStartTime())) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        AlertSilencePo entity = new AlertSilencePo();
        entity.setRuleCode(request.getRuleCode());
        entity.setAlertLevel(request.getAlertLevel());
        entity.setKeyword(request.getKeyword());
        entity.setStartTime(request.getStartTime());
        entity.setEndTime(request.getEndTime());
        entity.setReason(request.getReason());
        entity.setCreatedBy(request.getCreatedBy());
        alertSilenceMapper.insert(entity);
        log.info("创建告警静默: id={}, ruleCode={}, level={}, window=[{}, {}]",
                entity.getId(), entity.getRuleCode(), entity.getAlertLevel(),
                entity.getStartTime(), entity.getEndTime());
        return toVo(entity);
    }

    @Override
    public PageResult<AlertSilenceVO> page(AlertSilenceQueryRequest request) {
        LambdaQueryWrapper<AlertSilencePo> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(StringUtils.hasText(request.getRuleCode()), AlertSilencePo::getRuleCode,
                request.getRuleCode());
        if (Boolean.TRUE.equals(request.getActiveOnly())) {
            LocalDateTime now = LocalDateTime.now();
            wrapper.le(AlertSilencePo::getStartTime, now).ge(AlertSilencePo::getEndTime, now);
        }
        wrapper.orderByDesc(AlertSilencePo::getStartTime);
        Page<AlertSilencePo> page = alertSilenceMapper.selectPage(request.toPage(), wrapper);
        List<AlertSilenceVO> records = page.getRecords().stream()
                .map(AlertSilenceServiceImpl::toVo).toList();
        return PageResult.of(records, page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Override
    public void delete(Long id) {
        AlertSilencePo existing = alertSilenceMapper.selectById(id);
        if (existing == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        alertSilenceMapper.deleteById(id);
        log.info("删除告警静默: id={}", id);
    }

    @Override
    public boolean isSilenced(String ruleCode, String level, String content) {
        LocalDateTime now = LocalDateTime.now();
        LambdaQueryWrapper<AlertSilencePo> wrapper = new LambdaQueryWrapper<>();
        wrapper.le(AlertSilencePo::getStartTime, now).ge(AlertSilencePo::getEndTime, now);
        for (AlertSilencePo silence : alertSilenceMapper.selectList(wrapper)) {
            if (!matches(silence, ruleCode, level, content)) {
                continue;
            }
            log.info("告警命中静默: silenceId={}, ruleCode={}, level={}",
                    silence.getId(), ruleCode, level);
            return true;
        }
        return false;
    }

    /**
     * 判断单条静默规则是否匹配告警。
     *
     * @param silence  静默规则
     * @param ruleCode 规则编码
     * @param level    告警级别
     * @param content  告警内容
     * @return 匹配返回 {@code true}
     */
    private static boolean matches(AlertSilencePo silence, String ruleCode, String level, String content) {
        if (StringUtils.hasText(silence.getRuleCode())
                && !silence.getRuleCode().equals(ruleCode)) {
            return false;
        }
        if (StringUtils.hasText(silence.getAlertLevel())
                && !silence.getAlertLevel().equalsIgnoreCase(level)) {
            return false;
        }
        return !StringUtils.hasText(silence.getKeyword())
                || (content != null && content.contains(silence.getKeyword()));
    }

    /**
     * 实体转视图对象
     *
     * @param entity 静默实体
     * @return 静默视图对象
     */
    private static AlertSilenceVO toVo(AlertSilencePo entity) {
        AlertSilenceVO vo = new AlertSilenceVO();
        vo.setId(entity.getId());
        vo.setRuleCode(entity.getRuleCode());
        vo.setAlertLevel(entity.getAlertLevel());
        vo.setKeyword(entity.getKeyword());
        vo.setStartTime(entity.getStartTime());
        vo.setEndTime(entity.getEndTime());
        vo.setReason(entity.getReason());
        vo.setCreatedBy(entity.getCreatedBy());
        vo.setCreateTime(entity.getCreateTime());
        return vo;
    }
}
