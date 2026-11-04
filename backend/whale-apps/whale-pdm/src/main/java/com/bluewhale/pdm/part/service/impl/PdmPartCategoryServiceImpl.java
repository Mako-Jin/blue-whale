package com.bluewhale.pdm.part.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.pdm.converter.PdmConverter;
import com.bluewhale.pdm.entity.PdmProduct;
import com.bluewhale.pdm.mapper.PdmProductMapper;
import com.bluewhale.pdm.part.dto.PdmPartCategorySaveRequest;
import com.bluewhale.pdm.part.entity.PdmPartCategory;
import com.bluewhale.pdm.part.mapper.PdmPartCategoryMapper;
import com.bluewhale.pdm.part.service.PdmPartCategoryService;
import com.bluewhale.pdm.part.vo.PdmPartCategoryVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 物料分类服务实现
 *
 * <p>分类以 {@code path} 维护层级；移动时递归重写子树路径。
 * 分类被零件引用或存在子分类时禁止删除。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PdmPartCategoryServiceImpl implements PdmPartCategoryService {

    /** 根分类 ID */
    private static final long ROOT_ID = 0L;

    private final PdmPartCategoryMapper categoryMapper;

    private final PdmProductMapper productMapper;

    @Override
    public List<PdmPartCategoryVO> tree() {
        List<PdmPartCategory> categories = categoryMapper.selectList(new LambdaQueryWrapper<PdmPartCategory>()
                .orderByAsc(PdmPartCategory::getSortNo)
                .orderByAsc(PdmPartCategory::getId));
        Map<Long, PdmPartCategoryVO> nodes = new LinkedHashMap<>();
        for (PdmPartCategory category : categories) {
            PdmPartCategoryVO node = PdmConverter.INSTANCE.toPartCategoryVo(category);
            node.setChildren(new ArrayList<>());
            nodes.put(node.getId(), node);
        }
        List<PdmPartCategoryVO> roots = new ArrayList<>();
        for (PdmPartCategoryVO node : nodes.values()) {
            PdmPartCategoryVO parent = node.getParentId() == null ? null : nodes.get(node.getParentId());
            if (parent == null || ROOT_ID == node.getParentId()) {
                roots.add(node);
            } else {
                parent.getChildren().add(node);
            }
        }
        roots.sort(Comparator.comparing(PdmPartCategoryVO::getSortNo, Comparator.nullsLast(Integer::compareTo))
                .thenComparing(PdmPartCategoryVO::getId));
        return roots;
    }

    @Override
    public PdmPartCategoryVO get(Long id) {
        return PdmConverter.INSTANCE.toPartCategoryVo(requireCategory(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PdmPartCategoryVO create(PdmPartCategorySaveRequest request) {
        Long parentId = normalizeParentId(request.getParentId());
        PdmPartCategory parent = requireParent(parentId);
        String code = request.getCode().trim();
        String name = request.getName().trim();
        assertCodeUnique(code, null);
        assertNameUnique(parentId, name, null);
        PdmPartCategory category = PdmConverter.INSTANCE.toPartCategoryEntity(request);
        category.setParentId(parentId);
        category.setCode(code);
        category.setName(name);
        category.setSortNo(request.getSortNo() == null ? 0 : request.getSortNo());
        categoryMapper.insert(category);
        category.setPath(parentPath(parent) + category.getId() + "/");
        categoryMapper.updateById(category);
        log.info("物料分类已创建: id={}, code={}", category.getId(), code);
        return PdmConverter.INSTANCE.toPartCategoryVo(category);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PdmPartCategoryVO update(Long id, PdmPartCategorySaveRequest request) {
        PdmPartCategory category = requireCategory(id);
        Long newParentId = normalizeParentId(request.getParentId());
        PdmPartCategory parent = requireParent(newParentId);
        if (!newParentId.equals(category.getParentId())) {
            assertNotDescendant(category, parent);
        }
        String code = request.getCode().trim();
        String name = request.getName().trim();
        assertCodeUnique(code, id);
        assertNameUnique(newParentId, name, id);
        String oldPath = category.getPath();
        String newPath = parentPath(parent) + id + "/";
        category.setParentId(newParentId);
        category.setCode(code);
        category.setName(name);
        category.setSortNo(request.getSortNo() == null ? category.getSortNo() : request.getSortNo());
        category.setRemark(request.getRemark());
        category.setPath(newPath);
        categoryMapper.updateById(category);
        if (oldPath != null && !oldPath.equals(newPath)) {
            rewriteSubtreePath(oldPath, newPath);
        }
        return PdmConverter.INSTANCE.toPartCategoryVo(category);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        PdmPartCategory category = requireCategory(id);
        long childCount = categoryMapper.selectCount(new LambdaQueryWrapper<PdmPartCategory>()
                .eq(PdmPartCategory::getParentId, id));
        if (childCount > 0) {
            throw new BizException(ErrorCode.PARAM_ERROR, "分类下存在子分类，不能删除");
        }
        long partCount = productMapper.selectCount(new LambdaQueryWrapper<PdmProduct>()
                .eq(PdmProduct::getCategoryCode, category.getCode()));
        if (partCount > 0) {
            throw new BizException(ErrorCode.PARAM_ERROR, "分类下存在零件，不能删除");
        }
        categoryMapper.deleteById(id);
    }

    @Override
    public boolean existsCode(String code) {
        if (!StringUtils.hasText(code)) {
            return false;
        }
        return categoryMapper.selectCount(new LambdaQueryWrapper<PdmPartCategory>()
                .eq(PdmPartCategory::getCode, code.trim())) > 0;
    }

    /**
     * 校验分类存在。
     *
     * @param id 分类ID
     * @return 分类实体
     */
    private PdmPartCategory requireCategory(Long id) {
        if (id == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "物料分类不存在");
        }
        PdmPartCategory category = categoryMapper.selectById(id);
        if (category == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "物料分类不存在");
        }
        return category;
    }

    /**
     * 校验并返回上级分类。
     *
     * @param parentId 上级分类ID
     * @return 上级分类；根返回 null
     */
    private PdmPartCategory requireParent(Long parentId) {
        if (ROOT_ID == parentId) {
            return null;
        }
        return requireCategory(parentId);
    }

    /**
     * 校验分类编码唯一。
     *
     * @param code    分类编码
     * @param exclude 排除ID
     */
    private void assertCodeUnique(String code, Long exclude) {
        LambdaQueryWrapper<PdmPartCategory> wrapper = new LambdaQueryWrapper<PdmPartCategory>()
                .eq(PdmPartCategory::getCode, code);
        if (exclude != null) {
            wrapper.ne(PdmPartCategory::getId, exclude);
        }
        if (categoryMapper.selectCount(wrapper) > 0) {
            throw new BizException(ErrorCode.PARAM_ERROR, "分类编码已存在");
        }
    }

    /**
     * 校验同级分类名称唯一。
     *
     * @param parentId 上级分类ID
     * @param name     名称
     * @param exclude  排除ID
     */
    private void assertNameUnique(Long parentId, String name, Long exclude) {
        LambdaQueryWrapper<PdmPartCategory> wrapper = new LambdaQueryWrapper<PdmPartCategory>()
                .eq(PdmPartCategory::getParentId, parentId)
                .eq(PdmPartCategory::getName, name);
        if (exclude != null) {
            wrapper.ne(PdmPartCategory::getId, exclude);
        }
        if (categoryMapper.selectCount(wrapper) > 0) {
            throw new BizException(ErrorCode.PARAM_ERROR, "同级分类名称已存在");
        }
    }

    /**
     * 禁止移动到自身或后代分类下。
     *
     * @param category 待移动分类
     * @param parent   目标上级
     */
    private void assertNotDescendant(PdmPartCategory category, PdmPartCategory parent) {
        if (parent == null) {
            return;
        }
        if (parent.getId().equals(category.getId())
                || (parent.getPath() != null && category.getPath() != null
                && parent.getPath().startsWith(category.getPath()))) {
            throw new BizException(ErrorCode.PARAM_ERROR, "不能把分类移动到自身或子分类下");
        }
    }

    /**
     * 递归重写子树路径。
     *
     * @param oldPath 旧前缀
     * @param newPath 新前缀
     */
    private void rewriteSubtreePath(String oldPath, String newPath) {
        List<PdmPartCategory> descendants = categoryMapper.selectList(
                new LambdaQueryWrapper<PdmPartCategory>().likeRight(PdmPartCategory::getPath, oldPath));
        for (PdmPartCategory descendant : descendants) {
            if (descendant.getPath() == null || descendant.getPath().equals(oldPath)) {
                continue;
            }
            descendant.setPath(newPath + descendant.getPath().substring(oldPath.length()));
            categoryMapper.updateById(descendant);
        }
    }

    /**
     * 上级路径前缀。
     *
     * @param parent 上级分类
     * @return 路径前缀
     */
    private String parentPath(PdmPartCategory parent) {
        return parent == null ? "/" : parent.getPath();
    }

    /**
     * 归一化上级分类ID。
     *
     * @param parentId 原始上级ID
     * @return 非空上级ID
     */
    private Long normalizeParentId(Long parentId) {
        return parentId == null ? ROOT_ID : parentId;
    }
}
