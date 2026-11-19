package com.bluewhale.message.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.message.converter.MessageConverter;
import com.bluewhale.message.dto.MessageTemplateQueryRequest;
import com.bluewhale.message.dto.MessageTemplateSaveRequest;
import com.bluewhale.message.entity.MessageTemplate;
import com.bluewhale.message.mapper.MessageTemplateMapper;
import com.bluewhale.message.service.MessageTemplateService;
import com.bluewhale.message.vo.MessageTemplateVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Locale;

/**
 * 消息模板管理服务实现。
 *
 * <p>模板编码在租户内唯一（逻辑删除行仍占用，避免复用后历史消息语义漂移）。
 * 平台共享模板位于默认租户，业务租户只维护自身模板；发送侧当前租户未配置时回落到平台模板。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MessageTemplateServiceImpl implements MessageTemplateService {

    /** 单页最大条数：防止深度分页拖垮数据库 */
    private static final long MAX_PAGE_SIZE = 200L;

    /** 模板启用状态 */
    private static final int STATUS_ENABLED = 1;

    private final MessageTemplateMapper templateMapper;

    @Override
    public PageResult<MessageTemplateVO> page(MessageTemplateQueryRequest request) {
        LambdaQueryWrapper<MessageTemplate> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(StringUtils.hasText(request.getTemplateCode()), MessageTemplate::getTemplateCode,
                request.getTemplateCode());
        wrapper.like(StringUtils.hasText(request.getTemplateName()), MessageTemplate::getTemplateName,
                request.getTemplateName());
        wrapper.eq(request.getStatus() != null, MessageTemplate::getStatus, request.getStatus());
        wrapper.orderByDesc(MessageTemplate::getCreateTime);
        long size = Math.min(Math.max(request.getSize(), 1L), MAX_PAGE_SIZE);
        Page<MessageTemplate> page = templateMapper.selectPage(new Page<>(request.getCurrent(), size), wrapper);
        List<MessageTemplateVO> records = page.getRecords().stream()
                .map(MessageTemplateServiceImpl::toVo).toList();
        return PageResult.of(records, page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Override
    public MessageTemplateVO detail(Long id) {
        return toVo(requireTemplate(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MessageTemplateVO create(MessageTemplateSaveRequest request) {
        String code = normalizeCode(request.getTemplateCode());
        assertCodeUnique(code, null);
        MessageTemplate template = MessageConverter.INSTANCE.toEntity(request);
        template.setTemplateCode(code);
        template.setStatus(request.getStatus() == null ? STATUS_ENABLED : normalizeStatus(request.getStatus()));
        templateMapper.insert(template);
        log.info("消息模板已创建: id={}, code={}", template.getId(), template.getTemplateCode());
        return toVo(template);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MessageTemplateVO update(Long id, MessageTemplateSaveRequest request) {
        MessageTemplate template = requireTemplate(id);
        Integer originalStatus = template.getStatus();
        String code = normalizeCode(request.getTemplateCode());
        assertCodeUnique(code, id);
        MessageConverter.INSTANCE.updateEntity(request, template);
        template.setTemplateCode(code);
        // 未传状态时保留原值：MapStruct 会把 null 一并覆盖，不能直接依赖数据库的 NOT_NULL 更新策略
        template.setStatus(request.getStatus() == null ? originalStatus : normalizeStatus(request.getStatus()));
        templateMapper.updateById(template);
        log.info("消息模板已更新: id={}, code={}", id, template.getTemplateCode());
        return toVo(template);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        requireTemplate(id);
        templateMapper.deleteById(id);
        log.info("消息模板已删除: id={}", id);
    }

    /**
     * 按 ID 查询未删除模板。
     *
     * @param id 模板ID
     * @return 模板实体
     */
    private MessageTemplate requireTemplate(Long id) {
        MessageTemplate template = id == null ? null : templateMapper.selectById(id);
        if (template == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "消息模板不存在: " + id);
        }
        return template;
    }

    /**
     * 校验模板编码在租户内唯一。
     *
     * @param code      模板编码
     * @param excludeId 更新时排除自身ID，新增传 {@code null}
     */
    private void assertCodeUnique(String code, Long excludeId) {
        long exclude = excludeId == null ? 0L : excludeId;
        if (templateMapper.countByCodeIncludeDeleted(code, exclude) > 0) {
            throw new BizException(ErrorCode.PARAM_ERROR, "模板编码已存在: " + code);
        }
    }

    /**
     * 规范化模板编码：去空白并转大写；编码为空直接拒绝，避免落库空编码后无法通过编码回查。
     *
     * @param code 原始编码
     * @return 规范化编码
     */
    private static String normalizeCode(String code) {
        if (!StringUtils.hasText(code)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "模板编码不能为空");
        }
        return code.trim().toUpperCase(Locale.ROOT);
    }

    /**
     * 校验模板状态：仅支持 0（停用）/ 1（启用）。
     *
     * @param status 状态值
     * @return 校验后的状态
     */
    private static int normalizeStatus(Integer status) {
        if (status != null && status != 0 && status != 1) {
            throw new BizException(ErrorCode.PARAM_ERROR, "模板状态仅支持 0（停用）/ 1（启用）: " + status);
        }
        return status == null ? STATUS_ENABLED : status;
    }

    /**
     * 实体转视图对象。
     *
     * @param template 模板实体
     * @return 视图对象
     */
    private static MessageTemplateVO toVo(MessageTemplate template) {
        return MessageConverter.INSTANCE.toTemplateVo(template);
    }
}
