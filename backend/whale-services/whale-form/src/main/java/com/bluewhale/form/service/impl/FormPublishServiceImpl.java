package com.bluewhale.form.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.form.entity.FormDefinition;
import com.bluewhale.form.entity.FormTableRegistry;
import com.bluewhale.form.mapper.FormDefinitionMapper;
import com.bluewhale.form.mapper.FormTableRegistryMapper;
import com.bluewhale.form.service.FormPublishService;
import com.bluewhale.form.support.FormDataEngine;
import com.bluewhale.form.support.FormField;
import com.bluewhale.form.support.FormSchemaParser;
import com.bluewhale.form.support.FormTableDdlGenerator;
import com.bluewhale.form.vo.FormTableRegistryVO;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 表单发布服务实现。
 *
 * <p>发布 = 校验编码 → 校验版本未发布 → 解析 schema → 生成 DDL 建物理表 → 登记 registry。
 * 物理表名与列名均来自受校验的 schema 白名单（spec §三.3.3、§三.3.9）。</p>
 *
 * @author BlueWhale
 */
@Service
@RequiredArgsConstructor
public class FormPublishServiceImpl implements FormPublishService {

    /** 登记状态：生效 */
    private static final int STATUS_NORMAL = 1;

    /** 版本号默认值 */
    private static final int DEFAULT_VERSION = 1;

    private final FormDefinitionMapper formDefinitionMapper;

    private final FormTableRegistryMapper formTableRegistryMapper;

    private final FormDataEngine formDataEngine;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FormTableRegistryVO publish(Long definitionId) {
        FormDefinition definition = requireDefinition(definitionId);
        FormTableDdlGenerator.assertPublishableCode(definition.getCode());
        Integer version = definition.getVersion() == null ? DEFAULT_VERSION : definition.getVersion();
        assertNotPublished(definition.getCode(), version);
        List<FormField> fields = FormSchemaParser.parse(definition.getSchemaJson());
        String table = FormTableDdlGenerator.tableName(definition.getCode(), version);
        formDataEngine.executeDdl(FormTableDdlGenerator.generate(table, definition.getCode(), version, fields));
        FormTableRegistry registry = new FormTableRegistry();
        registry.setDefinitionId(definition.getId());
        registry.setDefinitionCode(definition.getCode());
        registry.setDefinitionVersion(version);
        registry.setPhysicalTable(table);
        registry.setFieldCount(fields.size());
        registry.setSchemaJson(definition.getSchemaJson());
        registry.setStatus(STATUS_NORMAL);
        formTableRegistryMapper.insert(registry);
        return toVo(registry);
    }

    @Override
    public List<FormTableRegistryVO> listTables(Long definitionId) {
        requireDefinition(definitionId);
        LambdaQueryWrapper<FormTableRegistry> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FormTableRegistry::getDefinitionId, definitionId);
        wrapper.orderByDesc(FormTableRegistry::getDefinitionVersion);
        return formTableRegistryMapper.selectList(wrapper).stream().map(FormPublishServiceImpl::toVo).toList();
    }

    /**
     * 查询表单定义，不存在则抛出 {@code NOT_FOUND}
     *
     * @param definitionId 表单定义ID
     * @return 表单定义实体
     */
    private FormDefinition requireDefinition(Long definitionId) {
        FormDefinition definition = formDefinitionMapper.selectById(definitionId);
        if (definition == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        return definition;
    }

    /**
     * 校验该（编码 + 版本）尚未发布
     *
     * @param code 表单定义编码
     * @param version 表单定义版本
     * @throws BizException 已发布时抛出 {@code PARAM_ERROR}
     */
    private void assertNotPublished(String code, Integer version) {
        LambdaQueryWrapper<FormTableRegistry> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FormTableRegistry::getDefinitionCode, code);
        wrapper.eq(FormTableRegistry::getDefinitionVersion, version);
        Long count = formTableRegistryMapper.selectCount(wrapper);
        if (count != null && count > 0) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "该表单版本已发布，如需变更请修改版本号后重新发布");
        }
    }

    /**
     * 登记实体转视图对象
     *
     * @param entity 登记实体
     * @return 登记视图对象
     */
    private static FormTableRegistryVO toVo(FormTableRegistry entity) {
        FormTableRegistryVO vo = new FormTableRegistryVO();
        BeanUtils.copyProperties(entity, vo);
        return vo;
    }
}
