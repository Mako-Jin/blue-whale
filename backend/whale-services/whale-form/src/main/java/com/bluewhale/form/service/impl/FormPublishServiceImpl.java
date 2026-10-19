package com.bluewhale.form.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.form.entity.FormColumnMeta;
import com.bluewhale.form.entity.FormDefinition;
import com.bluewhale.form.entity.FormTableDefinition;
import com.bluewhale.form.entity.FormTableRegistry;
import com.bluewhale.form.mapper.FormColumnMetaMapper;
import com.bluewhale.form.mapper.FormDefinitionMapper;
import com.bluewhale.form.mapper.FormTableDefinitionMapper;
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

    /** 列状态：正常 */
    private static final int COLUMN_STATUS_NORMAL = 1;

    private final FormDefinitionMapper formDefinitionMapper;

    private final FormTableRegistryMapper formTableRegistryMapper;

    private final FormTableDefinitionMapper formTableDefinitionMapper;

    private final FormColumnMetaMapper formColumnMetaMapper;

    private final FormDataEngine formDataEngine;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FormTableRegistryVO publish(Long definitionId) {
        FormDefinition definition = requireDefinition(definitionId);
        if (definition.getTableDefinitionId() == null) {
            return publishLegacy(definition);
        }
        return publishMetadata(definition);
    }

    /**
     * 旧模型发布：解析 {@code schema_json} → 建物理表 → 登记（同版本重复发布抛 {@code PARAM_ERROR}）
     *
     * @param definition 表单定义实体
     * @return 物理表登记视图对象
     */
    private FormTableRegistryVO publishLegacy(FormDefinition definition) {
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

    /**
     * 元数据模型发布：确保其表定义物理表已发布（幂等，未发布则建）
     *
     * @param definition 表单定义实体（已含 {@code tableDefinitionId}）
     * @return 所属表定义的物理表登记视图对象
     */
    private FormTableRegistryVO publishMetadata(FormDefinition definition) {
        FormTableDefinition table = requireTableDefinition(definition.getTableDefinitionId());
        Integer version = table.getVersion() == null ? DEFAULT_VERSION : table.getVersion();
        FormTableRegistry existing = findRegistry(table.getTableCode(), version);
        if (existing != null) {
            return toVo(existing);
        }
        return publishTableDefinition(table.getId());
    }

    @Override
    public List<FormTableRegistryVO> listTables(Long definitionId) {
        FormDefinition definition = requireDefinition(definitionId);
        if (definition.getTableDefinitionId() != null) {
            return listTablesByTableDefinition(definition.getTableDefinitionId());
        }
        LambdaQueryWrapper<FormTableRegistry> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FormTableRegistry::getDefinitionId, definitionId);
        wrapper.orderByDesc(FormTableRegistry::getDefinitionVersion);
        return formTableRegistryMapper.selectList(wrapper).stream().map(FormPublishServiceImpl::toVo).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FormTableRegistryVO publishTableDefinition(Long tableDefinitionId) {
        FormTableDefinition definition = requireTableDefinition(tableDefinitionId);
        FormTableDdlGenerator.assertPublishableCode(definition.getTableCode());
        Integer version = definition.getVersion() == null ? DEFAULT_VERSION : definition.getVersion();
        List<FormColumnMeta> columns = listPublishColumns(tableDefinitionId, version);
        if (columns.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "表定义无可发布的列，请先维护列元数据");
        }
        String table = FormTableDdlGenerator.tableName(definition.getTableCode(), version);
        assertTableVersionNotPublished(definition.getTableCode(), version);
        formDataEngine.executeDdl(
                FormTableDdlGenerator.generateFromColumns(table, definition.getTableCode(), version, columns));
        FormTableRegistry registry = new FormTableRegistry();
        registry.setTableDefinitionId(definition.getId());
        registry.setTableCode(definition.getTableCode());
        registry.setTableVersion(version);
        registry.setPhysicalTable(table);
        registry.setFieldCount(columns.size());
        registry.setStatus(STATUS_NORMAL);
        formTableRegistryMapper.insert(registry);
        return toVo(registry);
    }

    @Override
    public List<FormTableRegistryVO> listTablesByTableDefinition(Long tableDefinitionId) {
        requireTableDefinition(tableDefinitionId);
        LambdaQueryWrapper<FormTableRegistry> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FormTableRegistry::getTableDefinitionId, tableDefinitionId);
        wrapper.orderByDesc(FormTableRegistry::getTableVersion);
        return formTableRegistryMapper.selectList(wrapper).stream().map(FormPublishServiceImpl::toVo).toList();
    }

    /**
     * 查询表定义指定版本下状态正常的列元数据（按排序号升序）
     *
     * @param tableDefinitionId 表定义ID
     * @param version 表版本
     * @return 可发布列元数据列表
     */
    private List<FormColumnMeta> listPublishColumns(Long tableDefinitionId, Integer version) {
        LambdaQueryWrapper<FormColumnMeta> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FormColumnMeta::getTableDefinitionId, tableDefinitionId);
        wrapper.eq(FormColumnMeta::getTableVersion, version);
        wrapper.eq(FormColumnMeta::getStatus, COLUMN_STATUS_NORMAL);
        wrapper.orderByAsc(FormColumnMeta::getSortNo);
        return formColumnMetaMapper.selectList(wrapper);
    }

    /**
     * 查询表定义，不存在则抛出 {@code NOT_FOUND}
     *
     * @param tableDefinitionId 表定义ID
     * @return 表定义实体
     */
    private FormTableDefinition requireTableDefinition(Long tableDefinitionId) {
        FormTableDefinition definition = formTableDefinitionMapper.selectById(tableDefinitionId);
        if (definition == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        return definition;
    }

    /**
     * 查询指定（表编码 + 版本）的物理表登记（不存在返回 {@code null}）
     *
     * @param tableCode 表编码
     * @param version 表版本
     * @return 物理表登记实体；不存在时返回 {@code null}
     */
    private FormTableRegistry findRegistry(String tableCode, Integer version) {
        LambdaQueryWrapper<FormTableRegistry> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FormTableRegistry::getTableCode, tableCode);
        wrapper.eq(FormTableRegistry::getTableVersion, version);
        return formTableRegistryMapper.selectOne(wrapper);
    }

    /**
     * 校验该（表编码 + 版本）尚未发布
     *
     * @param tableCode 表编码
     * @param version 表版本
     * @throws BizException 已发布时抛出 {@code PARAM_ERROR}
     */
    private void assertTableVersionNotPublished(String tableCode, Integer version) {
        if (findRegistry(tableCode, version) != null) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "该表版本已发布，如需变更请修改版本号后重新发布");
        }
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
