package com.bluewhale.pdm.part.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.pdm.entity.PdmProduct;
import com.bluewhale.pdm.mapper.PdmProductMapper;
import com.bluewhale.pdm.part.dto.PdmPartCategorySaveRequest;
import com.bluewhale.pdm.part.entity.PdmPartCategory;
import com.bluewhale.pdm.part.mapper.PdmPartCategoryMapper;
import com.bluewhale.pdm.part.vo.PdmPartCategoryVO;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 物料分类服务单元测试。
 *
 * @author BlueWhale
 */
@ExtendWith(MockitoExtension.class)
class PdmPartCategoryServiceImplTest {

    @Mock
    private PdmPartCategoryMapper categoryMapper;

    @Mock
    private PdmProductMapper productMapper;

    @InjectMocks
    private PdmPartCategoryServiceImpl categoryService;

    /**
     * 初始化 MyBatis-Plus 表元数据。
     */
    @BeforeAll
    static void initTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, PdmPartCategory.class);
        TableInfoHelper.initTableInfo(assistant, PdmProduct.class);
    }

    /**
     * 分类树按父级组装并保留排序。
     */
    @Test
    void treeShouldAssembleHierarchy() {
        PdmPartCategory root = category(1L, 0L, "STD", "标准件", "/1/", 10);
        PdmPartCategory child = category(2L, 1L, "BOLT", "螺栓", "/1/2/", 5);
        when(categoryMapper.selectList(any())).thenReturn(List.of(root, child));

        List<PdmPartCategoryVO> tree = categoryService.tree();

        assertEquals(1, tree.size());
        assertEquals("STD", tree.get(0).getCode());
        assertEquals(1, tree.get(0).getChildren().size());
        assertEquals("BOLT", tree.get(0).getChildren().get(0).getCode());
    }

    /**
     * 新建分类计算路径并写回。
     */
    @Test
    void createShouldWritePath() {
        when(categoryMapper.selectById(1L)).thenReturn(
                category(1L, 0L, "STD", "标准件", "/1/", 10));
        when(categoryMapper.selectCount(any())).thenReturn(0L);
        when(categoryMapper.insert(any(PdmPartCategory.class))).thenAnswer(invocation -> {
            PdmPartCategory entity = invocation.getArgument(0);
            entity.setId(9L);
            return 1;
        });

        PdmPartCategoryVO view = categoryService.create(request(1L, "BOLT", "螺栓"));

        assertEquals(9L, view.getId());
        assertEquals("/1/9/", view.getPath());
        verify(categoryMapper).updateById(any(PdmPartCategory.class));
    }

    /**
     * 分类编码重复被拒绝。
     */
    @Test
    void createShouldRejectDuplicatedCode() {
        when(categoryMapper.selectCount(any())).thenReturn(1L);

        BizException exception = assertThrows(BizException.class,
                () -> categoryService.create(request(0L, "STD", "标准件")));

        assertEquals(ErrorCode.PARAM_ERROR.getCode(), exception.getCode());
        verify(categoryMapper, never()).insert(any(PdmPartCategory.class));
    }

    /**
     * 存在子分类时禁止删除。
     */
    @Test
    void deleteShouldRejectWhenChildExists() {
        when(categoryMapper.selectById(1L)).thenReturn(
                category(1L, 0L, "STD", "标准件", "/1/", 10));
        when(categoryMapper.selectCount(any())).thenReturn(1L);

        BizException exception = assertThrows(BizException.class, () -> categoryService.delete(1L));

        assertEquals(ErrorCode.PARAM_ERROR.getCode(), exception.getCode());
        verify(categoryMapper, never()).deleteById(1L);
    }

    /**
     * 分类被零件引用时禁止删除。
     */
    @Test
    void deleteShouldRejectWhenReferencedByPart() {
        when(categoryMapper.selectById(1L)).thenReturn(
                category(1L, 0L, "STD", "标准件", "/1/", 10));
        when(categoryMapper.selectCount(any())).thenReturn(0L);
        when(productMapper.selectCount(any())).thenReturn(1L);

        BizException exception = assertThrows(BizException.class, () -> categoryService.delete(1L));

        assertEquals(ErrorCode.PARAM_ERROR.getCode(), exception.getCode());
        verify(categoryMapper, never()).deleteById(1L);
    }

    /**
     * 移动到自身后代分类被拒绝。
     */
    @Test
    void updateShouldRejectMoveIntoDescendant() {
        when(categoryMapper.selectById(2L)).thenReturn(
                category(2L, 1L, "BOLT", "螺栓", "/1/2/", 5));
        when(categoryMapper.selectById(3L)).thenReturn(
                category(3L, 2L, "BOLT-M8", "M8 螺栓", "/1/2/3/", 1));

        BizException exception = assertThrows(BizException.class,
                () -> categoryService.update(2L, request(3L, "BOLT", "螺栓")));

        assertEquals(ErrorCode.PARAM_ERROR.getCode(), exception.getCode());
        verify(categoryMapper, never()).updateById(any(PdmPartCategory.class));
    }

    /**
     * 构造分类请求。
     *
     * @param parentId 上级ID
     * @param code     编码
     * @param name     名称
     * @return 请求
     */
    private PdmPartCategorySaveRequest request(Long parentId, String code, String name) {
        PdmPartCategorySaveRequest request = new PdmPartCategorySaveRequest();
        request.setParentId(parentId);
        request.setCode(code);
        request.setName(name);
        request.setSortNo(1);
        return request;
    }

    /**
     * 构造分类实体。
     *
     * @param id       主键
     * @param parentId 上级
     * @param code     编码
     * @param name     名称
     * @param path     路径
     * @param sortNo   排序
     * @return 分类
     */
    private PdmPartCategory category(Long id, Long parentId, String code, String name,
                                     String path, int sortNo) {
        PdmPartCategory category = new PdmPartCategory();
        category.setId(id);
        category.setParentId(parentId);
        category.setCode(code);
        category.setName(name);
        category.setPath(path);
        category.setSortNo(sortNo);
        return category;
    }
}
