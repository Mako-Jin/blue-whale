package com.bluewhale.archive.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.bluewhale.archive.dto.ArchiveCategorySaveRequest;
import com.bluewhale.archive.entity.ArchiveCategory;
import com.bluewhale.archive.entity.ArchiveRule;
import com.bluewhale.archive.mapper.ArchiveCategoryMapper;
import com.bluewhale.archive.mapper.ArchiveRuleMapper;
import com.bluewhale.archive.vo.ArchiveCategoryVO;
import com.bluewhale.common.core.exception.BizException;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link ArchiveCategoryService} 单元测试：门类树、编码唯一、层级计算、删除前置校验。
 *
 * @author BlueWhale
 */
class ArchiveCategoryServiceTest {

    static {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                ArchiveCategory.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                ArchiveRule.class);
    }

    private final ArchiveCategoryMapper categoryMapper = mock(ArchiveCategoryMapper.class);

    private final ArchiveRuleMapper ruleMapper = mock(ArchiveRuleMapper.class);

    private final ArchiveCategoryService service = new ArchiveCategoryService(categoryMapper, ruleMapper);

    /**
     * 扁平列表应按 parentId 组装为两级树，并保留排序顺序。
     */
    @Test
    void treeShouldBuildHierarchyByParentId() {
        ArchiveCategory root = category(1L, 0L, "GL", "公文", 1);
        ArchiveCategory child = category(2L, 1L, "GL_WJ", "文件", 1);
        ArchiveCategory secondRoot = category(3L, 0L, "CW", "财务", 2);
        when(categoryMapper.selectList(any())).thenReturn(List.of(root, child, secondRoot));

        List<ArchiveCategoryVO> roots = service.tree();

        assertThat(roots).hasSize(2);
        assertThat(roots.get(0).getCode()).isEqualTo("GL");
        assertThat(roots.get(0).getChildren()).hasSize(1);
        assertThat(roots.get(0).getChildren().get(0).getCode()).isEqualTo("GL_WJ");
        assertThat(roots.get(0).getChildren().get(0).getParentId()).isEqualTo("1");
        assertThat(roots.get(1).getCode()).isEqualTo("CW");
    }

    /**
     * 新增时编码重复应拒绝，且提示「已存在」。
     */
    @Test
    void createShouldRejectDuplicateCode() {
        when(categoryMapper.selectCount(any())).thenReturn(1L);

        assertThatThrownBy(() -> service.create(request("gl", "公文", 0L)))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("门类编码已存在");
    }

    /**
     * 新增根节点应把编码规范为大写、层级置 1、父ID置 0。
     */
    @Test
    void createShouldNormalizeCodeAndComputeRootLevel() {
        when(categoryMapper.selectCount(any())).thenReturn(0L);
        when(categoryMapper.insert(any(ArchiveCategory.class))).thenAnswer(invocation -> {
            ArchiveCategory entity = invocation.getArgument(0);
            entity.setId(100L);
            return 1;
        });

        ArchiveCategoryVO vo = service.create(request(" gl ", "公文", 0L));

        assertThat(vo.getId()).isEqualTo("100");
        assertThat(vo.getCode()).isEqualTo("GL");
        assertThat(vo.getLevel()).isEqualTo(1);
        assertThat(vo.getParentId()).isEqualTo("0");
    }

    /**
     * 修改时把上级指向自身应被拒绝。
     */
    @Test
    void updateShouldRejectSelfAsParent() {
        when(categoryMapper.selectById(10L)).thenReturn(category(10L, 0L, "GL", "公文", 1));
        when(categoryMapper.selectCount(any())).thenReturn(0L);

        assertThatThrownBy(() -> service.update(10L, request("GL", "公文", 10L)))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("上级门类不能是自身");
    }

    /**
     * 存在下级门类时删除应被拒绝。
     */
    @Test
    void deleteShouldRejectWhenChildrenExist() {
        when(categoryMapper.selectById(1L)).thenReturn(category(1L, 0L, "GL", "公文", 1));
        when(categoryMapper.selectCount(any())).thenReturn(1L);

        assertThatThrownBy(() -> service.delete(1L))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("存在下级门类");
    }

    /**
     * 被归档规则引用时删除应被拒绝。
     */
    @Test
    void deleteShouldRejectWhenReferencedByRule() {
        when(categoryMapper.selectById(1L)).thenReturn(category(1L, 0L, "GL", "公文", 1));
        when(categoryMapper.selectCount(any())).thenReturn(0L);
        when(ruleMapper.selectCount(any())).thenReturn(1L);

        assertThatThrownBy(() -> service.delete(1L))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("归档规则引用");
    }

    /**
     * 构造门类实体。
     *
     * @param id 主键
     * @param parentId 父ID
     * @param code 编码
     * @param name 名称
     * @param sort 排序号
     * @return 门类实体
     */
    private static ArchiveCategory category(Long id, Long parentId, String code, String name, int sort) {
        ArchiveCategory entity = new ArchiveCategory();
        entity.setId(id);
        entity.setParentId(parentId);
        entity.setCode(code);
        entity.setName(name);
        entity.setLevel(parentId == 0L ? 1 : 2);
        entity.setSort(sort);
        entity.setSecurityLevel(2);
        entity.setStatus(1);
        return entity;
    }

    /**
     * 构造门类保存请求。
     *
     * @param code 编码
     * @param name 名称
     * @param parentId 父ID
     * @return 保存请求
     */
    private static ArchiveCategorySaveRequest request(String code, String name, Long parentId) {
        ArchiveCategorySaveRequest request = new ArchiveCategorySaveRequest();
        request.setCode(code);
        request.setName(name);
        request.setParentId(parentId);
        return request;
    }
}
