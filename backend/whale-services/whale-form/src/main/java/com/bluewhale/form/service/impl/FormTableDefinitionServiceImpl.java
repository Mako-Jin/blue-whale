package com.bluewhale.form.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.form.dto.FormColumnMetaDTO;
import com.bluewhale.form.dto.FormQueryRequest;
import com.bluewhale.form.dto.FormTableDefinitionSaveRequest;
import com.bluewhale.form.entity.FormColumnMeta;
import com.bluewhale.form.entity.FormTableDefinition;
import com.bluewhale.form.mapper.FormColumnMetaMapper;
import com.bluewhale.form.mapper.FormTableDefinitionMapper;
import com.bluewhale.form.service.FormTableDefinitionService;
import com.bluewhale.form.support.FormDataType;
import com.bluewhale.form.support.FormSchemaParser;
import com.bluewhale.form.support.FormTableDdlGenerator;
import com.bluewhale.form.vo.FormColumnMetaVO;
import com.bluewhale.form.vo.FormTableDefinitionVO;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 表定义服务实现（表定义层：表 + 列聚合 CRUD）。
 *
 * <p>列编码沿用 {@link FormSchemaParser#assertColumnCode(String)} 白名单校验（正则 + 保留列），
 * 存储类型与长度/精度由 {@link FormDataType} 校验；列集合整体替换（旧列逻辑删除，新列重建）。</p>
 *
 * @author BlueWhale
 */
@Service
@RequiredArgsConstructor
public class FormTableDefinitionServiceImpl implements FormTableDefinitionService {

    /** 状态：正常 */
    private static final int STATUS_NORMAL = 1;

    /** 版本号默认值 */
    private static final int DEFAULT_VERSION = 1;

    /** 单页最大条数 */
    private static final long MAX_PAGE_SIZE = 200L;

    private final FormTableDefinitionMapper formTableDefinitionMapper;

    private final FormColumnMetaMapper formColumnMetaMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FormTableDefinitionVO create(FormTableDefinitionSaveRequest request) {
        FormTableDdlGenerator.assertPublishableCode(request.getTableCode());
        assertTableCodeUnique(request.getTableCode(), null);
        FormTableDefinition entity = new FormTableDefinition();
        entity.setTableCode(request.getTableCode());
        entity.setTableName(request.getTableName());
        entity.setVersion(request.getVersion() == null ? DEFAULT_VERSION : request.getVersion());
        entity.setRemark(request.getRemark());
        entity.setStatus(request.getStatus() == null ? STATUS_NORMAL : request.getStatus());
        formTableDefinitionMapper.insert(entity);
        List<FormColumnMeta> columns = persistColumns(entity.getId(), entity.getVersion(), request.getColumns());
        return toVo(entity, columns);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FormTableDefinitionVO update(Long id, FormTableDefinitionSaveRequest request) {
        FormTableDefinition entity = requireTableDefinition(id);
        FormTableDdlGenerator.assertPublishableCode(request.getTableCode());
        if (!entity.getTableCode().equals(request.getTableCode())) {
            assertTableCodeUnique(request.getTableCode(), id);
            entity.setTableCode(request.getTableCode());
        }
        entity.setTableName(request.getTableName());
        if (request.getVersion() != null) {
            entity.setVersion(request.getVersion());
        }
        entity.setRemark(request.getRemark());
        if (request.getStatus() != null) {
            entity.setStatus(request.getStatus());
        }
        formTableDefinitionMapper.updateById(entity);
        List<FormColumnMeta> columns = replaceColumns(entity.getId(), entity.getVersion(), request.getColumns());
        return toVo(entity, columns);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        requireTableDefinition(id);
        formTableDefinitionMapper.deleteById(id);
        LambdaQueryWrapper<FormColumnMeta> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FormColumnMeta::getTableDefinitionId, id);
        formColumnMetaMapper.delete(wrapper);
    }

    @Override
    public FormTableDefinitionVO getDetail(Long id) {
        FormTableDefinition entity = requireTableDefinition(id);
        return toVo(entity, listColumns(entity.getId()));
    }

    @Override
    public PageResult<FormTableDefinitionVO> page(FormQueryRequest request) {
        LambdaQueryWrapper<FormTableDefinition> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(StringUtils.hasText(request.getName()), FormTableDefinition::getTableName, request.getName());
        wrapper.eq(StringUtils.hasText(request.getCode()), FormTableDefinition::getTableCode, request.getCode());
        wrapper.eq(request.getStatus() != null, FormTableDefinition::getStatus, request.getStatus());
        wrapper.orderByDesc(FormTableDefinition::getCreateTime);
        long size = Math.min(Math.max(request.getSize(), 1L), MAX_PAGE_SIZE);
        Page<FormTableDefinition> page =
                formTableDefinitionMapper.selectPage(new Page<>(request.getCurrent(), size), wrapper);
        List<FormTableDefinitionVO> records =
                page.getRecords().stream().map(entity -> toVo(entity, null)).toList();
        return PageResult.of(records, page.getTotal(), page.getCurrent(), page.getSize());
    }

    /**
     * 查询表定义（含列，按排序号升序）
     *
     * @param tableDefinitionId 表定义ID
     * @return 列元数据列表
     */
    private List<FormColumnMeta> listColumns(Long tableDefinitionId) {
        LambdaQueryWrapper<FormColumnMeta> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FormColumnMeta::getTableDefinitionId, tableDefinitionId);
        wrapper.orderByAsc(FormColumnMeta::getSortNo);
        return formColumnMetaMapper.selectList(wrapper);
    }

    /**
     * 全量替换列集合（旧列逻辑删除，新列重建）
     *
     * @param tableDefinitionId 表定义ID
     * @param version 表版本
     * @param dtos 列请求列表
     * @return 新列实体列表
     */
    private List<FormColumnMeta> replaceColumns(Long tableDefinitionId, Integer version,
                                                List<FormColumnMetaDTO> dtos) {
        LambdaQueryWrapper<FormColumnMeta> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FormColumnMeta::getTableDefinitionId, tableDefinitionId);
        formColumnMetaMapper.delete(wrapper);
        return persistColumns(tableDefinitionId, version, dtos);
    }

    /**
     * 逐列校验并落库，返回新列实体列表
     *
     * @param tableDefinitionId 表定义ID
     * @param version 表版本
     * @param dtos 列请求列表（可空）
     * @return 新列实体列表（入参为空时返回空列表）
     * @throws BizException 列编码/存储类型/参数非法或重复时抛出 {@code PARAM_ERROR}
     */
    private List<FormColumnMeta> persistColumns(Long tableDefinitionId, Integer version,
                                                List<FormColumnMetaDTO> dtos) {
        List<FormColumnMeta> result = new ArrayList<>();
        if (dtos == null) {
            return result;
        }
        Set<String> codes = new HashSet<>();
        for (FormColumnMetaDTO dto : dtos) {
            FormSchemaParser.assertColumnCode(dto.getColumnCode());
            if (!codes.add(dto.getColumnCode())) {
                throw paramError("列编码重复：" + dto.getColumnCode());
            }
            FormDataType.assertAllowed(dto.getDataType(), dto.getColumnCode());
            FormDataType.mysqlType(dto.getDataType(), dto.getLength(), dto.getPrecision(), dto.getScale());
            FormColumnMeta column = toColumn(dto, tableDefinitionId, version);
            formColumnMetaMapper.insert(column);
            result.add(column);
        }
        return result;
    }

    /**
     * 列请求 → 列实体（补齐默认值）
     *
     * @param dto 列请求
     * @param tableDefinitionId 表定义ID
     * @param version 表版本
     * @return 列实体
     */
    private static FormColumnMeta toColumn(FormColumnMetaDTO dto, Long tableDefinitionId, Integer version) {
        FormColumnMeta column = new FormColumnMeta();
        column.setTableDefinitionId(tableDefinitionId);
        column.setTableVersion(version);
        column.setColumnCode(dto.getColumnCode());
        column.setColumnName(StringUtils.hasText(dto.getColumnName()) ? dto.getColumnName() : dto.getColumnCode());
        column.setDataType(dto.getDataType());
        column.setLength(dto.getLength());
        column.setPrecision(dto.getPrecision());
        column.setScale(dto.getScale());
        column.setNullable(dto.getNullable() == null ? 1 : dto.getNullable());
        column.setRequired(dto.getRequired() == null ? 0 : dto.getRequired());
        column.setDefaultValue(dto.getDefaultValue());
        column.setRemark(dto.getRemark());
        column.setSortNo(dto.getSortNo() == null ? 0 : dto.getSortNo());
        column.setStatus(dto.getStatus() == null ? STATUS_NORMAL : dto.getStatus());
        return column;
    }

    /**
     * 查询表定义，不存在则抛出 {@code NOT_FOUND}
     *
     * @param id 表定义ID
     * @return 表定义实体
     */
    private FormTableDefinition requireTableDefinition(Long id) {
        FormTableDefinition entity = formTableDefinitionMapper.selectById(id);
        if (entity == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        return entity;
    }

    /**
     * 校验表编码唯一性
     *
     * @param tableCode 表编码
     * @param excludeId 需排除的当前记录ID（新增传 {@code null}）
     */
    private void assertTableCodeUnique(String tableCode, Long excludeId) {
        LambdaQueryWrapper<FormTableDefinition> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FormTableDefinition::getTableCode, tableCode);
        wrapper.ne(excludeId != null, FormTableDefinition::getId, excludeId);
        Long count = formTableDefinitionMapper.selectCount(wrapper);
        if (count != null && count > 0) {
            throw paramError("表编码已存在");
        }
    }

    /**
     * 表定义实体 + 列 → 视图对象
     *
     * @param entity 表定义实体
     * @param columns 列实体列表（列表查询可传 {@code null}）
     * @return 表定义视图对象
     */
    private static FormTableDefinitionVO toVo(FormTableDefinition entity, List<FormColumnMeta> columns) {
        FormTableDefinitionVO vo = new FormTableDefinitionVO();
        BeanUtils.copyProperties(entity, vo);
        if (columns != null) {
            vo.setColumns(columns.stream().map(FormTableDefinitionServiceImpl::toColumnVo).toList());
        }
        return vo;
    }

    /**
     * 列实体 → 列视图对象
     *
     * @param entity 列实体
     * @return 列视图对象
     */
    private static FormColumnMetaVO toColumnVo(FormColumnMeta entity) {
        FormColumnMetaVO vo = new FormColumnMetaVO();
        BeanUtils.copyProperties(entity, vo);
        return vo;
    }

    /**
     * 构造参数错误异常
     *
     * @param message 错误消息
     * @return 参数错误业务异常
     */
    private static BizException paramError(String message) {
        return new BizException(ErrorCode.PARAM_ERROR.getCode(), message);
    }
}
