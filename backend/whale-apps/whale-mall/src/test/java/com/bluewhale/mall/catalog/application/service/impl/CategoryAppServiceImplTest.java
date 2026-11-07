package com.bluewhale.mall.catalog.application.service.impl;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.mall.catalog.application.command.CategorySaveCommand;
import com.bluewhale.mall.catalog.application.view.CategoryTreeView;
import com.bluewhale.mall.catalog.domain.model.Category;
import com.bluewhale.mall.catalog.domain.model.CategoryQuery;
import com.bluewhale.mall.catalog.domain.repository.CategoryRepository;
import com.bluewhale.mall.catalog.domain.repository.SpuRepository;
import com.bluewhale.mall.common.MallActor;
import com.bluewhale.mall.common.MallErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 类目应用服务：层级推导、同级重名、删除前置条件、树组装。
 *
 * @author BlueWhale
 */
@ExtendWith(MockitoExtension.class)
class CategoryAppServiceImplTest {

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private SpuRepository spuRepository;

    @InjectMocks
    private CategoryAppServiceImpl service;

    private MallActor actor;

    @BeforeEach
    void setUp() {
        actor = new MallActor(1L, "admin", Set.of("mall_admin"), Set.of());
    }

    @Test
    @DisplayName("按上级推导层级")
    void createResolvesLevel() {
        when(categoryRepository.findById(5L)).thenReturn(Optional.of(category(5L, 2L, 2)));
        when(categoryRepository.existsSiblingName(5L, "手机", null)).thenReturn(false);
        when(categoryRepository.save(any(Category.class))).thenReturn(9L);

        CategoryTreeView view = service.create(command(5L, "手机"), actor);

        assertThat(view.getCategoryLevel()).isEqualTo(3);
        verify(categoryRepository).save(any(Category.class));
    }

    @Test
    @DisplayName("超过三级被拒绝")
    void createRejectsTooDeep() {
        when(categoryRepository.findById(6L)).thenReturn(Optional.of(category(6L, 5L, 3)));

        assertThatThrownBy(() -> service.create(command(6L, "耳机"), actor))
                .isInstanceOf(BizException.class)
                .satisfies(exception -> assertThat(((BizException) exception).getCode())
                        .isEqualTo(MallErrorCode.CATEGORY_INVALID));
        verify(categoryRepository, never()).save(any(Category.class));
    }

    @Test
    @DisplayName("上级不存在被拒绝")
    void createRejectsMissingParent() {
        when(categoryRepository.findById(7L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.create(command(7L, "耳机"), actor))
                .isInstanceOf(BizException.class)
                .satisfies(exception -> assertThat(((BizException) exception).getCode())
                        .isEqualTo(MallErrorCode.CATEGORY_NOT_FOUND));
    }

    @Test
    @DisplayName("同级重名被拒绝")
    void createRejectsDuplicateName() {
        when(categoryRepository.findById(5L)).thenReturn(Optional.of(category(5L, 2L, 2)));
        when(categoryRepository.existsSiblingName(5L, "手机", null)).thenReturn(true);

        assertThatThrownBy(() -> service.create(command(5L, "手机"), actor))
                .isInstanceOf(BizException.class)
                .satisfies(exception -> assertThat(((BizException) exception).getCode())
                        .isEqualTo(MallErrorCode.CATEGORY_NAME_CONFLICT));
    }

    @Test
    @DisplayName("有子类目时不允许改上级")
    void updateRejectsMoveWithChildren() {
        when(categoryRepository.findById(9L)).thenReturn(Optional.of(category(9L, 0L, 1)));
        when(categoryRepository.listChildren(9L)).thenReturn(List.of(category(10L, 9L, 2)));

        assertThatThrownBy(() -> service.update(9L, command(5L, "手机"), actor))
                .isInstanceOf(BizException.class)
                .satisfies(exception -> assertThat(((BizException) exception).getCode())
                        .isEqualTo(MallErrorCode.CATEGORY_STATE_CONFLICT));
    }

    @Test
    @DisplayName("叶子类目可以改上级并重算层级")
    void updateMovesLeaf() {
        when(categoryRepository.findById(9L)).thenReturn(Optional.of(category(9L, 0L, 1)));
        when(categoryRepository.findById(5L)).thenReturn(Optional.of(category(5L, 2L, 2)));
        when(categoryRepository.listChildren(9L)).thenReturn(List.of());
        when(categoryRepository.existsSiblingName(5L, "手机", 9L)).thenReturn(false);

        CategoryTreeView view = service.update(9L, command(5L, "手机"), actor);

        assertThat(view.getParentId()).isEqualTo(5L);
        assertThat(view.getCategoryLevel()).isEqualTo(3);
    }

    @Test
    @DisplayName("有子类目不能删除")
    void removeRejectsWhenHasChildren() {
        when(categoryRepository.findById(9L)).thenReturn(Optional.of(category(9L, 0L, 1)));
        when(categoryRepository.listChildren(9L)).thenReturn(List.of(category(10L, 9L, 2)));

        assertThatThrownBy(() -> service.remove(9L, actor))
                .isInstanceOf(BizException.class)
                .satisfies(exception -> assertThat(((BizException) exception).getCode())
                        .isEqualTo(MallErrorCode.CATEGORY_STATE_CONFLICT));
    }

    @Test
    @DisplayName("有商品不能删除")
    void removeRejectsWhenHasProducts() {
        when(categoryRepository.findById(9L)).thenReturn(Optional.of(category(9L, 0L, 1)));
        when(categoryRepository.listChildren(9L)).thenReturn(List.of());
        when(spuRepository.countByCategoryIds(anyCollection())).thenReturn(2L);

        assertThatThrownBy(() -> service.remove(9L, actor))
                .isInstanceOf(BizException.class)
                .satisfies(exception -> assertThat(((BizException) exception).getCode())
                        .isEqualTo(MallErrorCode.CATEGORY_STATE_CONFLICT));
        verify(categoryRepository, never()).remove(anyLong());
    }

    @Test
    @DisplayName("空类目可删除")
    void removeEmptyCategory() {
        when(categoryRepository.findById(9L)).thenReturn(Optional.of(category(9L, 0L, 1)));
        when(categoryRepository.listChildren(9L)).thenReturn(List.of());
        when(spuRepository.countByCategoryIds(anyCollection())).thenReturn(0L);

        service.remove(9L, actor);

        verify(categoryRepository).remove(9L);
    }

    @Test
    @DisplayName("树按上级挂接")
    void treeBuildsHierarchy() {
        when(categoryRepository.list(any(CategoryQuery.class))).thenReturn(List.of(
                category(1L, 0L, 1), category(2L, 1L, 2)));
        lenient().when(categoryRepository.list(null)).thenReturn(List.of());

        List<CategoryTreeView> roots = service.tree(new CategoryQuery(), actor);

        assertThat(roots).hasSize(1);
        assertThat(roots.get(0).getChildren()).hasSize(1);
    }

    private CategorySaveCommand command(Long parentId, String name) {
        CategorySaveCommand command = new CategorySaveCommand();
        command.setParentId(parentId);
        command.setCategoryName(name);
        return command;
    }

    private Category category(Long id, Long parentId, int level) {
        Category category = new Category();
        category.setId(id);
        category.setParentId(parentId);
        category.setCategoryName("类目" + id);
        category.setCategoryLevel(level);
        return category;
    }
}
