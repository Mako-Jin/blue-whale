package com.bluewhale.i18n.admin.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.i18n.I18nCacheEvictHelper;
import com.bluewhale.i18n.admin.I18nLocalMessageService;
import com.bluewhale.i18n.admin.dto.I18nMessageQueryRequest;
import com.bluewhale.i18n.admin.dto.I18nMessageSaveRequest;
import com.bluewhale.i18n.admin.vo.I18nMessageVO;
import com.bluewhale.i18n.entity.SysI18nMessage;
import com.bluewhale.i18n.mapper.SysI18nMessageMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Objects;

/**
 * 服务本地国际化文案管理实现
 *
 * <p>任写操作落库成功后，按「消息编码 + 语言环境」清理 Redis 缓存，
 * 使新文案立即可见。</p>
 *
 * @author BlueWhale
 */
@RequiredArgsConstructor
public class I18nLocalMessageServiceImpl implements I18nLocalMessageService {

    /**
     * 单页最大条数：防止深度分页拖垮数据库
     */
    private static final long MAX_PAGE_SIZE = 200L;

    private final SysI18nMessageMapper i18nMessageMapper;

    private final I18nCacheEvictHelper cacheEvictHelper;

    @Override
    public PageResult<I18nMessageVO> page(I18nMessageQueryRequest request) {
        LambdaQueryWrapper<SysI18nMessage> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(StringUtils.hasText(request.getKeyword()), SysI18nMessage::getMessageKey, request.getKeyword());
        wrapper.eq(StringUtils.hasText(request.getLocale()), SysI18nMessage::getLocale, request.getLocale());
        wrapper.eq(StringUtils.hasText(request.getModule()), SysI18nMessage::getModule, request.getModule());
        wrapper.orderByDesc(SysI18nMessage::getCreateTime);
        long size = Math.min(Math.max(request.getSize(), 1L), MAX_PAGE_SIZE);
        Page<SysI18nMessage> page = i18nMessageMapper.selectPage(new Page<>(request.getCurrent(), size), wrapper);
        List<I18nMessageVO> records = page.getRecords().stream().map(I18nLocalMessageServiceImpl::toVo).toList();
        return PageResult.of(records, page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Override
    public I18nMessageVO getDetail(Long id) {
        return toVo(requireMessage(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public I18nMessageVO create(I18nMessageSaveRequest request) {
        ensureUnique(request.getMessageKey(), request.getLocale(), null);
        SysI18nMessage entity = new SysI18nMessage();
        BeanUtils.copyProperties(request, entity);
        i18nMessageMapper.insert(entity);
        cacheEvictHelper.evict(request.getMessageKey(), request.getLocale());
        return toVo(i18nMessageMapper.selectById(entity.getId()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public I18nMessageVO update(Long id, I18nMessageSaveRequest request) {
        SysI18nMessage existing = requireMessage(id);
        String oldKey = existing.getMessageKey();
        String oldLocale = existing.getLocale();
        ensureUnique(request.getMessageKey(), request.getLocale(), id);
        BeanUtils.copyProperties(request, existing);
        existing.setId(id);
        i18nMessageMapper.updateById(existing);
        if (!Objects.equals(oldKey, request.getMessageKey()) || !Objects.equals(oldLocale, request.getLocale())) {
            cacheEvictHelper.evict(oldKey, oldLocale);
        }
        cacheEvictHelper.evict(request.getMessageKey(), request.getLocale());
        return toVo(i18nMessageMapper.selectById(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        SysI18nMessage existing = requireMessage(id);
        i18nMessageMapper.deleteById(id);
        cacheEvictHelper.evict(existing.getMessageKey(), existing.getLocale());
    }

    /**
     * 查询消息，不存在则抛出 {@code NOT_FOUND}
     *
     * @param id 消息ID
     * @return 消息实体
     */
    private SysI18nMessage requireMessage(Long id) {
        SysI18nMessage message = i18nMessageMapper.selectById(id);
        if (message == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        return message;
    }

    /**
     * 唯一键校验：{@code message_key + locale} 冲突时抛出 {@code PARAM_ERROR}
     *
     * @param messageKey 消息编码
     * @param locale     语言环境
     * @param excludeId  更新场景下排除自身ID，新增场景传 {@code null}
     */
    private void ensureUnique(String messageKey, String locale, Long excludeId) {
        LambdaQueryWrapper<SysI18nMessage> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysI18nMessage::getMessageKey, messageKey);
        wrapper.eq(SysI18nMessage::getLocale, locale);
        if (excludeId != null) {
            wrapper.ne(SysI18nMessage::getId, excludeId);
        }
        if (i18nMessageMapper.selectCount(wrapper) > 0) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
    }

    /**
     * 消息实体转视图对象
     *
     * @param message 消息实体
     * @return 消息视图对象
     */
    private static I18nMessageVO toVo(SysI18nMessage message) {
        I18nMessageVO vo = new I18nMessageVO();
        BeanUtils.copyProperties(message, vo);
        return vo;
    }
}
