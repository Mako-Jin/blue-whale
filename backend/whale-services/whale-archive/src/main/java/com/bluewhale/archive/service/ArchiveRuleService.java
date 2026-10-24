package com.bluewhale.archive.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.archive.dto.ArchiveRuleQueryRequest;
import com.bluewhale.archive.dto.ArchiveRuleSaveRequest;
import com.bluewhale.archive.entity.ArchiveCategory;
import com.bluewhale.archive.entity.ArchiveRule;
import com.bluewhale.archive.mapper.ArchiveCategoryMapper;
import com.bluewhale.archive.mapper.ArchiveRuleMapper;
import com.bluewhale.archive.support.ArchiveConstants;
import com.bluewhale.archive.vo.ArchiveRuleVO;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.core.result.PageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 归档规则服务：工作流定义编码到归档门类的映射维护。
 *
 * <p>规则为同步流程的命中依据；{@code autoArchive=1} 时命中即归档，否则进入待整理。</p>
 *
 * @author BlueWhale
 */
@Service
@RequiredArgsConstructor
public class ArchiveRuleService {

    /** 单页最大条数：防止深度分页拖垮数据库 */
    private static final long MAX_PAGE_SIZE = 200L;

    /** 流程定义编码最大长度 */
    private static final int MAX_DEFINITION_CODE_LENGTH = 64;

    private final ArchiveRuleMapper ruleMapper;

    private final ArchiveCategoryMapper categoryMapper;

    /**
     * 分页查询归档规则。
     *
     * @param request 查询条件
     * @return 规则分页结果（含门类名称）
     */
    public PageResult<ArchiveRuleVO> page(ArchiveRuleQueryRequest request) {
        LambdaQueryWrapper<ArchiveRule> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(StringUtils.hasText(request.getDefinitionCode()),
                ArchiveRule::getDefinitionCode, request.getDefinitionCode());
        wrapper.eq(request.getCategoryId() != null, ArchiveRule::getCategoryId, request.getCategoryId());
        wrapper.eq(request.getEnabled() != null, ArchiveRule::getEnabled, request.getEnabled());
        wrapper.orderByDesc(ArchiveRule::getCreateTime);
        long current = Math.max(request.getCurrent(), 1L);
        long size = Math.min(Math.max(request.getSize(), 1L), MAX_PAGE_SIZE);
        Page<ArchiveRule> page = ruleMapper.selectPage(new Page<>(current, size), wrapper);
        Map<Long, String> categoryNames = categoryNames(page.getRecords());
        List<ArchiveRuleVO> records = page.getRecords().stream()
                .map(rule -> toVo(rule, categoryNames.get(rule.getCategoryId())))
                .toList();
        return PageResult.of(records, page.getTotal(), page.getCurrent(), page.getSize());
    }

    /**
     * 新增归档规则。
     *
     * @param request 保存请求
     * @return 新规则视图
     */
    @Transactional(rollbackFor = Exception.class)
    public ArchiveRuleVO create(ArchiveRuleSaveRequest request) {
        String definitionCode = normalizeDefinitionCode(request.getDefinitionCode());
        requireDefinitionCodeAvailable(definitionCode, null);
        ArchiveCategory category = requireCategory(request.getCategoryId());
        ArchiveRule entity = new ArchiveRule();
        apply(entity, request, definitionCode);
        ruleMapper.insert(entity);
        return toVo(entity, category.getName());
    }

    /**
     * 修改归档规则。
     *
     * @param id 规则ID
     * @param request 保存请求
     * @return 修改后的规则视图
     */
    @Transactional(rollbackFor = Exception.class)
    public ArchiveRuleVO update(Long id, ArchiveRuleSaveRequest request) {
        requireRule(id);
        String definitionCode = normalizeDefinitionCode(request.getDefinitionCode());
        requireDefinitionCodeAvailable(definitionCode, id);
        ArchiveCategory category = requireCategory(request.getCategoryId());
        ArchiveRule entity = new ArchiveRule();
        entity.setId(id);
        apply(entity, request, definitionCode);
        ruleMapper.updateById(entity);
        return toVo(entity, category.getName());
    }

    /**
     * 删除归档规则（逻辑删除）。
     *
     * @param id 规则ID
     */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        requireRule(id);
        ruleMapper.deleteById(id);
    }

    /**
     * 把请求字段写入实体。
     *
     * @param entity 待持久化实体
     * @param request 保存请求
     * @param definitionCode 规范化后的流程定义编码
     */
    private static void apply(ArchiveRule entity, ArchiveRuleSaveRequest request, String definitionCode) {
        entity.setDefinitionCode(definitionCode);
        entity.setCategoryId(request.getCategoryId());
        entity.setRetentionYears(request.getRetentionYears());
        entity.setSecurityLevel(request.getSecurityLevel() == null
                ? ArchiveConstants.SECURITY_INTERNAL : request.getSecurityLevel());
        entity.setAutoArchive(request.getAutoArchive() == null ? 0 : request.getAutoArchive());
        entity.setEnabled(request.getEnabled() == null ? ArchiveConstants.STATUS_ENABLED : request.getEnabled());
    }

    /**
     * 查询规则，不存在时抛出 {@code NOT_FOUND}。
     *
     * @param id 规则ID
     * @return 规则实体
     */
    private ArchiveRule requireRule(Long id) {
        ArchiveRule entity = ruleMapper.selectById(id);
        if (entity == null) {
            throw new BizException(ErrorCode.NOT_FOUND.getCode(), "归档规则不存在");
        }
        return entity;
    }

    /**
     * 查询门类，不存在时抛出 {@code NOT_FOUND}。
     *
     * @param categoryId 门类ID
     * @return 门类实体
     */
    private ArchiveCategory requireCategory(Long categoryId) {
        ArchiveCategory category = categoryMapper.selectById(categoryId);
        if (category == null) {
            throw new BizException(ErrorCode.NOT_FOUND.getCode(), "归档门类不存在");
        }
        return category;
    }

    /**
     * 校验流程定义编码唯一。
     *
     * @param definitionCode 规范化后的编码
     * @param excludeId 修改时排除的自身ID；新增传 {@code null}
     */
    private void requireDefinitionCodeAvailable(String definitionCode, Long excludeId) {
        LambdaQueryWrapper<ArchiveRule> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ArchiveRule::getDefinitionCode, definitionCode);
        wrapper.ne(excludeId != null, ArchiveRule::getId, excludeId);
        if (ruleMapper.selectCount(wrapper) > 0) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "该流程定义已存在归档规则");
        }
    }

    /**
     * 规范化并校验流程定义编码。
     *
     * @param rawCode 原始编码
     * @return 去除首尾空白后的编码
     */
    private static String normalizeDefinitionCode(String rawCode) {
        if (!StringUtils.hasText(rawCode)) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "流程定义编码不能为空");
        }
        String code = rawCode.trim();
        if (code.length() > MAX_DEFINITION_CODE_LENGTH
                || !code.matches(ArchiveConstants.DEFINITION_CODE_PATTERN)) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(),
                    "流程定义编码仅允许字母、数字、下划线、点与连字符且不超过 64 位");
        }
        return code;
    }

    /**
     * 批量查询规则涉及的门类名称。
     *
     * @param rules 规则列表
     * @return 门类ID → 门类名称
     */
    private Map<Long, String> categoryNames(List<ArchiveRule> rules) {
        Set<Long> categoryIds = new HashSet<>();
        for (ArchiveRule rule : rules) {
            if (rule.getCategoryId() != null) {
                categoryIds.add(rule.getCategoryId());
            }
        }
        if (categoryIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> names = new HashMap<>(categoryIds.size());
        for (ArchiveCategory category : categoryMapper.selectBatchIds(categoryIds)) {
            names.put(category.getId(), category.getName());
        }
        return names;
    }

    /**
     * 实体转视图对象。
     *
     * @param entity 规则实体
     * @param categoryName 门类名称，可为 {@code null}
     * @return 规则视图
     */
    private static ArchiveRuleVO toVo(ArchiveRule entity, String categoryName) {
        ArchiveRuleVO vo = new ArchiveRuleVO();
        vo.setId(String.valueOf(entity.getId()));
        vo.setDefinitionCode(entity.getDefinitionCode());
        vo.setCategoryId(entity.getCategoryId() == null ? null : String.valueOf(entity.getCategoryId()));
        vo.setCategoryName(categoryName);
        vo.setRetentionYears(entity.getRetentionYears());
        vo.setSecurityLevel(entity.getSecurityLevel());
        vo.setAutoArchive(entity.getAutoArchive());
        vo.setEnabled(entity.getEnabled());
        vo.setCreateTime(entity.getCreateTime());
        return vo;
    }
}
