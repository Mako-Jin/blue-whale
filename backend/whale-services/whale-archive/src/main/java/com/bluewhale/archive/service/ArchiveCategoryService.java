package com.bluewhale.archive.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bluewhale.archive.dto.ArchiveCategorySaveRequest;
import com.bluewhale.archive.entity.ArchiveCategory;
import com.bluewhale.archive.entity.ArchiveRule;
import com.bluewhale.archive.mapper.ArchiveCategoryMapper;
import com.bluewhale.archive.mapper.ArchiveRuleMapper;
import com.bluewhale.archive.support.ArchiveConstants;
import com.bluewhale.archive.vo.ArchiveCategoryVO;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 档案门类服务：分类树查询与增删改。
 *
 * <p>删除前校验「无下级门类 + 无归档规则引用」，避免产生孤儿规则；
 * 修改父节点时校验不成环（不能挂到自身或后代节点下）。</p>
 *
 * @author BlueWhale
 */
@Service
@RequiredArgsConstructor
public class ArchiveCategoryService {

    /** 门类编码最大长度 */
    private static final int MAX_CODE_LENGTH = 32;

    private final ArchiveCategoryMapper categoryMapper;

    private final ArchiveRuleMapper ruleMapper;

    /**
     * 查询完整门类树（含停用节点，供管理端展示）。
     *
     * @return 根节点列表（同级按排序号、ID 升序）
     */
    public List<ArchiveCategoryVO> tree() {
        LambdaQueryWrapper<ArchiveCategory> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByAsc(ArchiveCategory::getSort);
        wrapper.orderByAsc(ArchiveCategory::getId);
        return buildTree(categoryMapper.selectList(wrapper));
    }

    /**
     * 新增门类。
     *
     * @param request 保存请求
     * @return 新门类视图
     */
    @Transactional(rollbackFor = Exception.class)
    public ArchiveCategoryVO create(ArchiveCategorySaveRequest request) {
        String code = normalizeCode(request.getCode());
        requireCodeAvailable(code, null);
        long parentId = resolveParentId(request.getParentId());
        int level = resolveLevel(parentId);
        ArchiveCategory entity = new ArchiveCategory();
        apply(entity, request, code, parentId, level);
        categoryMapper.insert(entity);
        return toVo(entity);
    }

    /**
     * 修改门类。
     *
     * @param id 门类ID
     * @param request 保存请求
     * @return 修改后的门类视图
     */
    @Transactional(rollbackFor = Exception.class)
    public ArchiveCategoryVO update(Long id, ArchiveCategorySaveRequest request) {
        requireCategory(id);
        String code = normalizeCode(request.getCode());
        requireCodeAvailable(code, id);
        long parentId = resolveParentId(request.getParentId());
        requireNoCycle(id, parentId);
        int level = resolveLevel(parentId);
        ArchiveCategory entity = new ArchiveCategory();
        entity.setId(id);
        apply(entity, request, code, parentId, level);
        categoryMapper.updateById(entity);
        return toVo(entity);
    }

    /**
     * 删除门类（逻辑删除）：存在下级门类或被归档规则引用时拒绝。
     *
     * @param id 门类ID
     */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        requireCategory(id);
        LambdaQueryWrapper<ArchiveCategory> childWrapper = new LambdaQueryWrapper<>();
        childWrapper.eq(ArchiveCategory::getParentId, id);
        if (categoryMapper.selectCount(childWrapper) > 0) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "存在下级门类，不能删除");
        }
        LambdaQueryWrapper<ArchiveRule> ruleWrapper = new LambdaQueryWrapper<>();
        ruleWrapper.eq(ArchiveRule::getCategoryId, id);
        if (ruleMapper.selectCount(ruleWrapper) > 0) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "存在归档规则引用该门类，不能删除");
        }
        categoryMapper.deleteById(id);
    }

    /**
     * 把请求字段写入实体。
     *
     * @param entity 待持久化实体
     * @param request 保存请求
     * @param code 规范化后的门类编码
     * @param parentId 父节点ID
     * @param level 层级
     */
    private static void apply(ArchiveCategory entity, ArchiveCategorySaveRequest request, String code,
                              long parentId, int level) {
        entity.setCode(code);
        entity.setName(request.getName().trim());
        entity.setParentId(parentId);
        entity.setLevel(level);
        entity.setRetentionYears(request.getRetentionYears());
        entity.setSecurityLevel(request.getSecurityLevel() == null
                ? ArchiveConstants.SECURITY_INTERNAL : request.getSecurityLevel());
        entity.setSort(request.getSort() == null ? 0 : request.getSort());
        entity.setStatus(request.getStatus() == null ? ArchiveConstants.STATUS_ENABLED : request.getStatus());
    }

    /**
     * 查询门类，不存在时抛出 {@code NOT_FOUND}。
     *
     * @param id 门类ID
     * @return 门类实体
     */
    private ArchiveCategory requireCategory(Long id) {
        ArchiveCategory entity = categoryMapper.selectById(id);
        if (entity == null) {
            throw new BizException(ErrorCode.NOT_FOUND.getCode(), "门类不存在");
        }
        return entity;
    }

    /**
     * 校验门类编码唯一。
     *
     * @param code 规范化后的编码
     * @param excludeId 修改时排除的自身ID；新增传 {@code null}
     */
    private void requireCodeAvailable(String code, Long excludeId) {
        LambdaQueryWrapper<ArchiveCategory> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ArchiveCategory::getCode, code);
        wrapper.ne(excludeId != null, ArchiveCategory::getId, excludeId);
        if (categoryMapper.selectCount(wrapper) > 0) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "门类编码已存在");
        }
    }

    /**
     * 规范化并校验门类编码：去空白、转大写、长度与字符集校验。
     *
     * @param rawCode 原始编码
     * @return 规范化编码
     */
    private static String normalizeCode(String rawCode) {
        if (!StringUtils.hasText(rawCode)) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "门类编码不能为空");
        }
        String code = rawCode.trim().toUpperCase(Locale.ROOT);
        if (code.length() > MAX_CODE_LENGTH || !code.matches(ArchiveConstants.CATEGORY_CODE_PATTERN)) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "门类编码仅允许字母、数字与下划线且不超过 32 位");
        }
        return code;
    }

    /**
     * 解析父节点ID：空值视为根节点。
     *
     * @param parentId 原始父节点ID
     * @return 非空父节点ID
     */
    private static long resolveParentId(Long parentId) {
        return parentId == null ? ArchiveConstants.ROOT_PARENT_ID : parentId;
    }

    /**
     * 计算层级：根节点为 1，否则父层级 + 1，并校验树深度上限。
     *
     * @param parentId 父节点ID
     * @return 层级
     */
    private int resolveLevel(long parentId) {
        if (parentId == ArchiveConstants.ROOT_PARENT_ID) {
            return ArchiveConstants.ROOT_LEVEL;
        }
        ArchiveCategory parent = requireCategory(parentId);
        int parentLevel = parent.getLevel() == null ? ArchiveConstants.ROOT_LEVEL : parent.getLevel();
        int level = parentLevel + 1;
        if (level > ArchiveConstants.MAX_TREE_DEPTH) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "门类层级超过上限");
        }
        return level;
    }

    /**
     * 校验父节点不是自身或自身后代，防止树成环。
     *
     * @param id 当前门类ID
     * @param parentId 目标父节点ID
     */
    private void requireNoCycle(Long id, long parentId) {
        if (parentId == id) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "上级门类不能是自身");
        }
        long cursor = parentId;
        for (int depth = 0; depth < ArchiveConstants.MAX_TREE_DEPTH
                && cursor != ArchiveConstants.ROOT_PARENT_ID; depth++) {
            if (cursor == id) {
                throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "上级门类不能是自身后代");
            }
            ArchiveCategory parent = categoryMapper.selectById(cursor);
            if (parent == null || parent.getParentId() == null) {
                return;
            }
            cursor = parent.getParentId();
        }
    }

    /**
     * 由扁平列表构建门类树；父节点缺失的节点按根节点处理，避免脏数据丢节点。
     *
     * @param categories 门类列表（已按排序号升序）
     * @return 根节点列表
     */
    private static List<ArchiveCategoryVO> buildTree(List<ArchiveCategory> categories) {
        Map<Long, ArchiveCategoryVO> nodeById = new HashMap<>(categories.size());
        for (ArchiveCategory category : categories) {
            nodeById.put(category.getId(), toVo(category));
        }
        List<ArchiveCategoryVO> roots = new ArrayList<>();
        for (ArchiveCategory category : categories) {
            ArchiveCategoryVO node = nodeById.get(category.getId());
            ArchiveCategoryVO parent = category.getParentId() == null ? null : nodeById.get(category.getParentId());
            if (parent == null) {
                roots.add(node);
            } else {
                parent.getChildren().add(node);
            }
        }
        return roots;
    }

    /**
     * 实体转视图对象。
     *
     * @param entity 门类实体
     * @return 门类视图
     */
    private static ArchiveCategoryVO toVo(ArchiveCategory entity) {
        ArchiveCategoryVO vo = new ArchiveCategoryVO();
        vo.setId(String.valueOf(entity.getId()));
        vo.setCode(entity.getCode());
        vo.setName(entity.getName());
        vo.setParentId(String.valueOf(entity.getParentId() == null
                ? ArchiveConstants.ROOT_PARENT_ID : entity.getParentId()));
        vo.setLevel(entity.getLevel());
        vo.setRetentionYears(entity.getRetentionYears());
        vo.setSecurityLevel(entity.getSecurityLevel());
        vo.setSort(entity.getSort());
        vo.setStatus(entity.getStatus());
        return vo;
    }
}
