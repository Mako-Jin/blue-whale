package com.bluewhale.archive.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.archive.dto.ArchiveRuleQueryRequest;
import com.bluewhale.archive.dto.ArchiveRuleSaveRequest;
import com.bluewhale.archive.entity.ArchiveCategory;
import com.bluewhale.archive.entity.ArchiveRule;
import com.bluewhale.archive.mapper.ArchiveCategoryMapper;
import com.bluewhale.archive.mapper.ArchiveRuleMapper;
import com.bluewhale.archive.vo.ArchiveRuleVO;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.result.PageResult;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link ArchiveRuleService} 单元测试：分页联表、编码唯一、门类存在性。
 *
 * @author BlueWhale
 */
class ArchiveRuleServiceTest {

    static {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                ArchiveRule.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                ArchiveCategory.class);
    }

    private final ArchiveRuleMapper ruleMapper = mock(ArchiveRuleMapper.class);

    private final ArchiveCategoryMapper categoryMapper = mock(ArchiveCategoryMapper.class);

    private final ArchiveRuleService service = new ArchiveRuleService(ruleMapper, categoryMapper);

    /**
     * 分页结果应补齐门类名称，并把雪花ID转字符串。
     */
    @Test
    void pageShouldFillCategoryName() {
        ArchiveRule rule = rule(1L, "leave_apply", 5L);
        Page<ArchiveRule> page = new Page<>(1, 10);
        page.setRecords(List.of(rule));
        page.setTotal(1L);
        when(ruleMapper.selectPage(any(), any())).thenReturn(page);
        when(categoryMapper.selectBatchIds(any())).thenReturn(List.of(category(5L, "GL", "公文")));

        PageResult<ArchiveRuleVO> result = service.page(new ArchiveRuleQueryRequest());

        assertThat(result.getRecords()).hasSize(1);
        assertThat(result.getRecords().get(0).getCategoryId()).isEqualTo("5");
        assertThat(result.getRecords().get(0).getCategoryName()).isEqualTo("公文");
        assertThat(result.getTotal()).isEqualTo(1L);
    }

    /**
     * 流程定义编码重复时新增应被拒绝。
     */
    @Test
    void createShouldRejectDuplicateDefinitionCode() {
        when(ruleMapper.selectCount(any())).thenReturn(1L);

        assertThatThrownBy(() -> service.create(request("leave_apply", 5L)))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("已存在归档规则");
    }

    /**
     * 流程定义编码含非法字符时应拒绝，且不落库。
     */
    @Test
    void createShouldRejectInvalidDefinitionCode() {
        assertThatThrownBy(() -> service.create(request("<script>", 5L)))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("流程定义编码");
    }

    /**
     * 门类不存在时新增应返回「未找到」。
     */
    @Test
    void createShouldRejectMissingCategory() {
        when(ruleMapper.selectCount(any())).thenReturn(0L);
        when(categoryMapper.selectById(5L)).thenReturn(null);

        assertThatThrownBy(() -> service.create(request("leave_apply", 5L)))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("归档门类不存在");
    }

    /**
     * 新增规则成功时应默认启用并回填门类名称。
     */
    @Test
    void createShouldPersistWithDefaults() {
        when(ruleMapper.selectCount(any())).thenReturn(0L);
        when(categoryMapper.selectById(5L)).thenReturn(category(5L, "GL", "公文"));
        when(ruleMapper.insert(any(ArchiveRule.class))).thenAnswer(invocation -> {
            ArchiveRule entity = invocation.getArgument(0);
            entity.setId(9L);
            return 1;
        });

        ArchiveRuleVO vo = service.create(request(" leave_apply ", 5L));

        assertThat(vo.getId()).isEqualTo("9");
        assertThat(vo.getDefinitionCode()).isEqualTo("leave_apply");
        assertThat(vo.getEnabled()).isEqualTo(1);
        assertThat(vo.getAutoArchive()).isEqualTo(0);
        assertThat(vo.getCategoryName()).isEqualTo("公文");
    }

    /**
     * 删除不存在的规则应返回「未找到」。
     */
    @Test
    void deleteShouldRejectMissingRule() {
        when(ruleMapper.selectById(99L)).thenReturn(null);

        assertThatThrownBy(() -> service.delete(99L))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("归档规则不存在");
    }

    /**
     * 构造规则实体。
     *
     * @param id 主键
     * @param definitionCode 流程定义编码
     * @param categoryId 门类ID
     * @return 规则实体
     */
    private static ArchiveRule rule(Long id, String definitionCode, Long categoryId) {
        ArchiveRule entity = new ArchiveRule();
        entity.setId(id);
        entity.setDefinitionCode(definitionCode);
        entity.setCategoryId(categoryId);
        entity.setSecurityLevel(2);
        entity.setAutoArchive(0);
        entity.setEnabled(1);
        return entity;
    }

    /**
     * 构造门类实体。
     *
     * @param id 主键
     * @param code 编码
     * @param name 名称
     * @return 门类实体
     */
    private static ArchiveCategory category(Long id, String code, String name) {
        ArchiveCategory entity = new ArchiveCategory();
        entity.setId(id);
        entity.setCode(code);
        entity.setName(name);
        entity.setParentId(0L);
        entity.setLevel(1);
        entity.setStatus(1);
        return entity;
    }

    /**
     * 构造规则保存请求。
     *
     * @param definitionCode 流程定义编码
     * @param categoryId 门类ID
     * @return 保存请求
     */
    private static ArchiveRuleSaveRequest request(String definitionCode, Long categoryId) {
        ArchiveRuleSaveRequest request = new ArchiveRuleSaveRequest();
        request.setDefinitionCode(definitionCode);
        request.setCategoryId(categoryId);
        return request;
    }
}
