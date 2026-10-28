package com.bluewhale.config.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.config.dto.ConfigItemCreateRequest;
import com.bluewhale.config.dto.ConfigItemQueryRequest;
import com.bluewhale.config.dto.ConfigItemRollbackRequest;
import com.bluewhale.config.dto.ConfigItemUpdateRequest;
import com.bluewhale.config.dto.OperatorInfo;
import com.bluewhale.config.entity.ConfigItem;
import com.bluewhale.config.entity.ConfigItemHistory;
import com.bluewhale.config.mapper.ConfigItemHistoryMapper;
import com.bluewhale.config.mapper.ConfigItemMapper;
import com.bluewhale.config.service.ConfigItemService;
import com.bluewhale.config.vo.ConfigItemHistoryVO;
import com.bluewhale.config.vo.ConfigItemVO;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * 配置项服务实现
 *
 * <p>配置键租户内唯一（服务层前置校验 + 并发撞唯一索引兜底）；值每次变更版本号 +1
 * 并落 {@code config_item_history}；回滚按目标版本取值产生新版本，不覆盖历史；
 * 仅改说明 / 状态不产生版本。NUMBER / BOOLEAN 类型做字面量校验，JSON 存原文不校验语法。</p>
 *
 * @author BlueWhale
 */
@Service
@RequiredArgsConstructor
public class ConfigItemServiceImpl implements ConfigItemService {

    /** 单页最大条数：防止深度分页拖垮数据库 */
    private static final long MAX_PAGE_SIZE = 200L;

    /** 历史查询最大条数 */
    private static final long MAX_HISTORY_SIZE = 200L;

    /** 值类型：字符串 */
    private static final String TYPE_STRING = "STRING";

    /** 值类型：数字 */
    private static final String TYPE_NUMBER = "NUMBER";

    /** 值类型：布尔 */
    private static final String TYPE_BOOLEAN = "BOOLEAN";

    /** 值类型：JSON */
    private static final String TYPE_JSON = "JSON";

    /** 操作类型：创建 */
    private static final String OP_CREATE = "CREATE";

    /** 操作类型：更新 */
    private static final String OP_UPDATE = "UPDATE";

    /** 操作类型：回滚 */
    private static final String OP_ROLLBACK = "ROLLBACK";

    /** 操作类型：删除 */
    private static final String OP_DELETE = "DELETE";

    /** 状态：启用 */
    private static final int STATUS_ENABLED = 1;

    /** 状态：停用 */
    private static final int STATUS_DISABLED = 0;

    /** 初始版本号 */
    private static final int INITIAL_VERSION = 1;

    private final ConfigItemMapper configItemMapper;

    private final ConfigItemHistoryMapper configItemHistoryMapper;

    @Override
    public PageResult<ConfigItemVO> page(ConfigItemQueryRequest request) {
        LambdaQueryWrapper<ConfigItem> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(request.getKeyword())) {
            wrapper.and(inner -> inner.like(ConfigItem::getConfigKey, request.getKeyword())
                    .or().like(ConfigItem::getDescription, request.getKeyword()));
        }
        wrapper.eq(request.getStatus() != null, ConfigItem::getStatus, request.getStatus());
        wrapper.eq(StringUtils.hasText(request.getValueType()), ConfigItem::getValueType,
                normalizeType(request.getValueType()));
        wrapper.orderByDesc(ConfigItem::getUpdateTime);
        long size = Math.min(Math.max(request.getSize(), 1L), MAX_PAGE_SIZE);
        Page<ConfigItem> page = configItemMapper.selectPage(new Page<>(request.getCurrent(), size), wrapper);
        List<ConfigItemVO> records = page.getRecords().stream().map(ConfigItemServiceImpl::toVo).toList();
        return PageResult.of(records, page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Override
    public ConfigItemVO getDetail(Long id) {
        return toVo(requireItem(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ConfigItemVO create(ConfigItemCreateRequest request, OperatorInfo operator) {
        String configKey = request.getConfigKey().trim();
        String valueType = normalizeType(request.getValueType());
        String value = request.getConfigValue();
        validateValue(valueType, value);
        ensureKeyAvailable(configKey);
        ConfigItem entity = new ConfigItem();
        entity.setConfigKey(configKey);
        entity.setConfigValue(value);
        entity.setValueType(valueType);
        entity.setDescription(request.getDescription());
        entity.setVersion(INITIAL_VERSION);
        entity.setStatus(normalizeStatus(request.getStatus()));
        try {
            configItemMapper.insert(entity);
        } catch (DuplicateKeyException ex) {
            throw new BizException(ErrorCode.PARAM_ERROR, "配置键已存在: " + configKey);
        }
        insertHistory(entity, OP_CREATE, null, value, operator);
        return toVo(configItemMapper.selectById(entity.getId()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ConfigItemVO update(Long id, ConfigItemUpdateRequest request, OperatorInfo operator) {
        ConfigItem existing = requireItem(id);
        String valueType = request.getValueType() == null
                ? existing.getValueType() : normalizeType(request.getValueType());
        String newValue = request.getConfigValue() == null
                ? existing.getConfigValue() : request.getConfigValue();
        validateValue(valueType, newValue);
        String oldValue = existing.getConfigValue();
        existing.setValueType(valueType);
        existing.setConfigValue(newValue);
        if (request.getDescription() != null) {
            existing.setDescription(request.getDescription());
        }
        if (request.getStatus() != null) {
            existing.setStatus(normalizeStatus(request.getStatus()));
        }
        boolean valueChanged = !Objects.equals(oldValue, newValue);
        if (valueChanged) {
            existing.setVersion(existing.getVersion() + 1);
        }
        configItemMapper.updateById(existing);
        if (valueChanged) {
            insertHistory(existing, OP_UPDATE, oldValue, newValue, operator);
        }
        return toVo(configItemMapper.selectById(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id, OperatorInfo operator) {
        ConfigItem existing = requireItem(id);
        configItemMapper.deleteById(id);
        insertHistory(existing, OP_DELETE, existing.getConfigValue(), null, operator);
    }

    @Override
    public List<ConfigItemHistoryVO> listHistory(Long id) {
        requireItem(id);
        LambdaQueryWrapper<ConfigItemHistory> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ConfigItemHistory::getItemId, id)
                .orderByDesc(ConfigItemHistory::getVersion);
        Page<ConfigItemHistory> page = configItemHistoryMapper.selectPage(
                new Page<>(1L, MAX_HISTORY_SIZE), wrapper);
        return page.getRecords().stream().map(ConfigItemServiceImpl::toHistoryVo).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ConfigItemVO rollback(Long id, ConfigItemRollbackRequest request, OperatorInfo operator) {
        ConfigItem existing = requireItem(id);
        Integer targetVersion = request.getTargetVersion();
        if (targetVersion == null || targetVersion < INITIAL_VERSION
                || targetVersion > existing.getVersion()) {
            throw new BizException(ErrorCode.PARAM_ERROR,
                    "目标版本号必须在 1 到 " + existing.getVersion() + " 之间");
        }
        if (targetVersion.equals(existing.getVersion())) {
            throw new BizException(ErrorCode.PARAM_ERROR, "配置项已处于版本 " + targetVersion);
        }
        ConfigItemHistory history = selectValueHistory(id, targetVersion);
        if (history == null || history.getNewValue() == null) {
            throw new BizException(ErrorCode.PARAM_ERROR,
                    "版本 " + targetVersion + " 不存在或无可回滚的值");
        }
        validateValue(existing.getValueType(), history.getNewValue());
        String oldValue = existing.getConfigValue();
        existing.setConfigValue(history.getNewValue());
        existing.setVersion(existing.getVersion() + 1);
        configItemMapper.updateById(existing);
        insertHistory(existing, OP_ROLLBACK, oldValue, history.getNewValue(), operator);
        return toVo(configItemMapper.selectById(id));
    }

    /**
     * 查询指定版本且变更后值非空的历史行（CREATE / UPDATE / ROLLBACK）
     *
     * @param itemId 配置项ID
     * @param version 目标版本号
     * @return 历史行；不存在时为 {@code null}
     */
    private ConfigItemHistory selectValueHistory(Long itemId, Integer version) {
        LambdaQueryWrapper<ConfigItemHistory> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ConfigItemHistory::getItemId, itemId)
                .eq(ConfigItemHistory::getVersion, version)
                .isNotNull(ConfigItemHistory::getNewValue)
                .orderByDesc(ConfigItemHistory::getId)
                .last("LIMIT 1");
        return configItemHistoryMapper.selectOne(wrapper);
    }

    /**
     * 校验配置键未被占用
     *
     * @param configKey 配置键
     */
    private void ensureKeyAvailable(String configKey) {
        LambdaQueryWrapper<ConfigItem> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ConfigItem::getConfigKey, configKey);
        if (configItemMapper.selectCount(wrapper) > 0) {
            throw new BizException(ErrorCode.PARAM_ERROR, "配置键已存在: " + configKey);
        }
    }

    /**
     * 追加变更历史
     *
     * @param item      配置项
     * @param operation 操作类型
     * @param oldValue  变更前值
     * @param newValue  变更后值
     * @param operator  操作人
     */
    private void insertHistory(ConfigItem item, String operation, String oldValue,
                               String newValue, OperatorInfo operator) {
        ConfigItemHistory history = new ConfigItemHistory();
        history.setItemId(item.getId());
        history.setConfigKey(item.getConfigKey());
        history.setOperation(operation);
        history.setOldValue(oldValue);
        history.setNewValue(newValue);
        history.setVersion(item.getVersion());
        if (operator != null) {
            history.setOperatorId(operator.getOperatorId());
            history.setOperator(operator.getOperatorName());
        }
        configItemHistoryMapper.insert(history);
    }

    /**
     * 值类型字面量校验：NUMBER 必须可解析为数值，BOOLEAN 必须为 true/false，JSON 存原文不校验
     *
     * @param valueType 值类型
     * @param value     配置值
     */
    private static void validateValue(String valueType, String value) {
        if (!StringUtils.hasText(value)) {
            return;
        }
        switch (valueType) {
            case TYPE_NUMBER -> {
                try {
                    new BigDecimal(value.trim());
                } catch (NumberFormatException e) {
                    throw new BizException(ErrorCode.PARAM_ERROR, "配置值必须是合法数字: " + value);
                }
            }
            case TYPE_BOOLEAN -> {
                String normalized = value.trim().toLowerCase(Locale.ROOT);
                if (!"true".equals(normalized) && !"false".equals(normalized)) {
                    throw new BizException(ErrorCode.PARAM_ERROR, "布尔类型配置值只能是 true / false");
                }
            }
            default -> {
                // STRING / JSON 存原文，不做字面量校验
            }
        }
    }

    /**
     * 状态规范化：仅允许 0 / 1，空值回落启用
     *
     * @param status 原始状态
     * @return 规范化状态
     */
    private static int normalizeStatus(Integer status) {
        if (status == null) {
            return STATUS_ENABLED;
        }
        if (status != STATUS_ENABLED && status != STATUS_DISABLED) {
            throw new BizException(ErrorCode.PARAM_ERROR, "状态仅支持 1 启用 / 0 停用");
        }
        return status;
    }

    /**
     * 值类型规范化：去空白转大写，空值回落 STRING
     *
     * @param valueType 原始值类型
     * @return 规范化后的值类型
     */
    private static String normalizeType(String valueType) {
        if (!StringUtils.hasText(valueType)) {
            return TYPE_STRING;
        }
        String normalized = valueType.trim().toUpperCase(Locale.ROOT);
        if (!TYPE_STRING.equals(normalized) && !TYPE_NUMBER.equals(normalized)
                && !TYPE_BOOLEAN.equals(normalized) && !TYPE_JSON.equals(normalized)) {
            throw new BizException(ErrorCode.PARAM_ERROR,
                    "值类型仅支持 STRING / NUMBER / BOOLEAN / JSON");
        }
        return normalized;
    }

    /**
     * 查询配置项，不存在则抛出 {@code NOT_FOUND}
     *
     * @param id 配置项ID
     * @return 配置项实体
     */
    private ConfigItem requireItem(Long id) {
        ConfigItem item = id == null ? null : configItemMapper.selectById(id);
        if (item == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        return item;
    }

    /**
     * 配置项实体转视图对象
     *
     * @param item 配置项实体
     * @return 配置项视图对象
     */
    private static ConfigItemVO toVo(ConfigItem item) {
        ConfigItemVO vo = new ConfigItemVO();
        BeanUtils.copyProperties(item, vo);
        return vo;
    }

    /**
     * 历史实体转视图对象
     *
     * @param history 历史实体
     * @return 历史视图对象
     */
    private static ConfigItemHistoryVO toHistoryVo(ConfigItemHistory history) {
        ConfigItemHistoryVO vo = new ConfigItemHistoryVO();
        BeanUtils.copyProperties(history, vo);
        return vo;
    }
}
