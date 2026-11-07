package com.bluewhale.mall.catalog.application.service.impl;

import com.bluewhale.mall.catalog.application.command.CategorySaveCommand;
import com.bluewhale.mall.catalog.application.service.CategoryAppService;
import com.bluewhale.mall.catalog.application.view.CategoryTreeView;
import com.bluewhale.mall.catalog.domain.model.Category;
import com.bluewhale.mall.catalog.domain.model.CategoryQuery;
import com.bluewhale.mall.catalog.domain.model.CategoryStatus;
import com.bluewhale.mall.catalog.domain.repository.CategoryRepository;
import com.bluewhale.mall.catalog.domain.repository.SpuRepository;
import com.bluewhale.mall.common.MallActor;
import com.bluewhale.mall.common.MallErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 类目应用服务实现。
 *
 * <p>三条硬约束都在这里：<b>层级不超过三级</b>、<b>同级不允许重名</b>、
 * <b>有子类目或商品时不允许删除</b>。前两条需要查库才能判定，
 * 第三条如果只在界面上置灰按钮，接口被直接调用时就会留下一批孤儿商品。</p>
 *
 * @author BlueWhale
 */
@Service
@RequiredArgsConstructor
public class CategoryAppServiceImpl implements CategoryAppService {

    private final CategoryRepository categoryRepository;

    private final SpuRepository spuRepository;

    @Override
    public List<CategoryTreeView> tree(CategoryQuery query, MallActor actor) {
        List<Category> categories = categoryRepository.list(query == null ? new CategoryQuery() : query);
        Map<Long, CategoryTreeView> nodes = new LinkedHashMap<>();
        List<CategoryTreeView> roots = new ArrayList<>();
        for (Category category : categories) {
            nodes.put(category.getId(), toNode(category));
        }
        for (Category category : categories) {
            CategoryTreeView node = nodes.get(category.getId());
            CategoryTreeView parent = category.isRoot() ? null : nodes.get(category.getParentId());
            if (parent == null) {
                roots.add(node);
            } else {
                parent.getChildren().add(node);
            }
        }
        return roots;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CategoryTreeView create(CategorySaveCommand command, MallActor actor) {
        actor.requireUserId();
        Category category = new Category();
        category.setParentId(normalizeParent(command.getParentId()));
        category.setCategoryLevel(resolveLevel(category.getParentId()));
        apply(category, command);
        category.validate();
        assertNoSiblingName(category.getParentId(), category.getCategoryName(), null);
        categoryRepository.save(category);
        return toNode(category);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CategoryTreeView update(Long id, CategorySaveCommand command, MallActor actor) {
        actor.requireUserId();
        Category category = requireCategory(id);
        Long newParent = normalizeParent(command.getParentId());
        if (!newParent.equals(category.getParentId())) {
            assertNoChildren(id, "有子类目的类目不允许变更上级");
            category.setCategoryLevel(resolveLevel(newParent));
            category.setParentId(newParent);
        }
        apply(category, command);
        category.validate();
        assertNoSiblingName(category.getParentId(), category.getCategoryName(), id);
        categoryRepository.save(category);
        return toNode(category);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void remove(Long id, MallActor actor) {
        actor.requireUserId();
        Category category = requireCategory(id);
        assertNoChildren(id, "类目下仍有子类目，无法删除");
        if (spuRepository.countByCategoryIds(List.of(category.getId())) > 0) {
            throw MallErrorCode.exception(MallErrorCode.CATEGORY_STATE_CONFLICT,
                    "类目下仍有商品，无法删除（可先停用或迁移商品）");
        }
        categoryRepository.remove(id);
    }

    /**
     * 将命令写入类目。
     *
     * @param category 类目
     * @param command 命令
     */
    private void apply(Category category, CategorySaveCommand command) {
        category.setCategoryName(command.getCategoryName());
        category.setSortNo(command.getSortNo() == null ? 0 : command.getSortNo());
        category.setStatus(StringUtils.hasText(command.getStatus())
                ? CategoryStatus.parse(command.getStatus()) : CategoryStatus.ACTIVE);
        category.setRemark(command.getRemark());
    }

    /**
     * 归一上级 ID。
     *
     * @param parentId 上级 ID
     * @return 非空的上级 ID
     */
    private Long normalizeParent(Long parentId) {
        return parentId == null || parentId < 0 ? Category.ROOT_PARENT_ID : parentId;
    }

    /**
     * 按上级链计算层级。
     *
     * @param parentId 上级 ID
     * @return 层级（一级为 1）
     */
    private int resolveLevel(Long parentId) {
        if (parentId == null || parentId == Category.ROOT_PARENT_ID) {
            return 1;
        }
        Category parent = categoryRepository.findById(parentId)
                .orElseThrow(() -> MallErrorCode.exception(MallErrorCode.CATEGORY_NOT_FOUND,
                        "上级类目不存在"));
        int level = (parent.getCategoryLevel() == null ? 1 : parent.getCategoryLevel()) + 1;
        if (level > Category.MAX_LEVEL) {
            throw MallErrorCode.exception(MallErrorCode.CATEGORY_INVALID,
                    "类目最多支持 " + Category.MAX_LEVEL + " 级");
        }
        return level;
    }

    /**
     * 校验同级重名。
     *
     * @param parentId 上级 ID
     * @param categoryName 类目名称
     * @param excludeId 需排除的 ID
     */
    private void assertNoSiblingName(Long parentId, String categoryName, Long excludeId) {
        if (categoryRepository.existsSiblingName(parentId, categoryName, excludeId)) {
            throw MallErrorCode.exception(MallErrorCode.CATEGORY_NAME_CONFLICT);
        }
    }

    /**
     * 校验类目没有下级。
     *
     * @param id 类目 ID
     * @param message 冲突提示
     */
    private void assertNoChildren(Long id, String message) {
        if (!categoryRepository.listChildren(id).isEmpty()) {
            throw MallErrorCode.exception(MallErrorCode.CATEGORY_STATE_CONFLICT, message);
        }
    }

    /**
     * 查询类目，不存在即失败。
     *
     * @param id 类目 ID
     * @return 类目
     */
    private Category requireCategory(Long id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> MallErrorCode.exception(MallErrorCode.CATEGORY_NOT_FOUND));
    }

    /**
     * 领域模型转树节点。
     *
     * @param category 类目
     * @return 树节点
     */
    private CategoryTreeView toNode(Category category) {
        CategoryTreeView view = new CategoryTreeView();
        view.setId(category.getId());
        view.setParentId(category.getParentId());
        view.setCategoryName(category.getCategoryName());
        view.setCategoryLevel(category.getCategoryLevel());
        view.setSortNo(category.getSortNo());
        view.setStatus(category.getStatus() == null ? CategoryStatus.ACTIVE.name() : category.getStatus().name());
        return view;
    }
}
