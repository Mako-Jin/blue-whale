package com.bluewhale.form.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.form.converter.FormConverter;
import com.bluewhale.form.model.dto.FormColumnMetaDTO;
import com.bluewhale.form.model.request.FormQueryRequest;
import com.bluewhale.form.model.request.FormTableDefinitionSaveRequest;
import com.bluewhale.form.po.FormColumnMetaPo;
import com.bluewhale.form.po.FormDefinitionPo;
import com.bluewhale.form.po.FormFieldConfigPo;
import com.bluewhale.form.po.FormTableDefinitionPo;
import com.bluewhale.form.mapper.FormColumnMetaMapper;
import com.bluewhale.form.mapper.FormDefinitionMapper;
import com.bluewhale.form.mapper.FormFieldConfigMapper;
import com.bluewhale.form.mapper.FormTableDefinitionMapper;
import com.bluewhale.form.service.FormTableDefinitionService;
import com.bluewhale.form.support.FormDataType;
import com.bluewhale.form.support.FormSchemaParser;
import com.bluewhale.form.support.FormTableDdlGenerator;
import com.bluewhale.form.model.vo.FormColumnMetaVO;
import com.bluewhale.form.model.vo.FormTableDefinitionVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

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

    private final FormTableDefinitionMapper formTableDefinitionMapper;

    private final FormColumnMetaMapper formColumnMetaMapper;

    private final FormFieldConfigMapper formFieldConfigMapper;

    private final FormDefinitionMapper formDefinitionMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FormTableDefinitionVO create(FormTableDefinitionSaveRequest request) {
        FormTableDdlGenerator.assertPublishableCode(request.getTableCode());
        assertTableCodeUnique(request.getTableCode(), null);
        FormTableDefinitionPo entity = new FormTableDefinitionPo();
        entity.setTableCode(request.getTableCode());
        entity.setTableName(request.getTableName());
        entity.setVersion(request.getVersion() == null ? DEFAULT_VERSION : request.getVersion());
        entity.setRemark(request.getRemark());
        entity.setStatus(request.getStatus() == null ? STATUS_NORMAL : request.getStatus());
        formTableDefinitionMapper.insert(entity);
        List<FormColumnMetaPo> columns = persistColumns(entity.getId(), entity.getVersion(), request.getColumns());
        return toVo(entity, columns);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FormTableDefinitionVO update(Long id, FormTableDefinitionSaveRequest request) {
        FormTableDefinitionPo entity = requireTableDefinition(id);
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
        List<FormColumnMetaPo> columns = mergeColumns(entity.getId(), entity.getVersion(), request.getColumns());
        return toVo(entity, columns);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        requireTableDefinition(id);
        Long referenced = formDefinitionMapper.selectCount(new LambdaQueryWrapper<FormDefinitionPo>()
                .eq(FormDefinitionPo::getTableDefinitionId, id));
        if (referenced != null && referenced > 0) {
            throw paramError("表定义正被 " + referenced + " 个表单引用，请先解除引用后再删除");
        }
        formTableDefinitionMapper.deleteById(id);
        LambdaQueryWrapper<FormColumnMetaPo> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FormColumnMetaPo::getTableDefinitionId, id);
        formColumnMetaMapper.delete(wrapper);
    }

    @Override
    public FormTableDefinitionVO getDetail(Long id) {
        FormTableDefinitionPo entity = requireTableDefinition(id);
        return toVo(entity, listColumns(entity.getId()));
    }

    @Override
    public PageResult<FormTableDefinitionVO> page(FormQueryRequest request) {
        LambdaQueryWrapper<FormTableDefinitionPo> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(StringUtils.hasText(request.getName()), FormTableDefinitionPo::getTableName, request.getName());
        wrapper.eq(StringUtils.hasText(request.getCode()), FormTableDefinitionPo::getTableCode, request.getCode());
        wrapper.eq(request.getStatus() != null, FormTableDefinitionPo::getStatus, request.getStatus());
        wrapper.orderByDesc(FormTableDefinitionPo::getCreateTime);
        Page<FormTableDefinitionPo> page =
                formTableDefinitionMapper.selectPage(request.toPage(), wrapper);
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
    private List<FormColumnMetaPo> listColumns(Long tableDefinitionId) {
        LambdaQueryWrapper<FormColumnMetaPo> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FormColumnMetaPo::getTableDefinitionId, tableDefinitionId);
        wrapper.orderByAsc(FormColumnMetaPo::getSortNo);
        return formColumnMetaMapper.selectList(wrapper);
    }

    /**
     * 按列编码就地合并列集合（保留既有列主键，缺失列逻辑删除）
     *
     * <p><b>列变更只改元数据</b>：本方法不触碰物理表；物理表结构在「发布」时由
     * {@code FormPublishService} 比对列元数据与现有列后执行 ALTER。就地合并而非「删除重建」
     * 是关键——重建会生成新的列主键，使既有表单视图的 {@code form_field_config.column_meta_id} 悬空。</p>
     *
     * @param tableDefinitionId 表定义ID
     * @param version 表版本
     * @param dtos 列请求列表（可空，空表示清空全部列）
     * @return 合并后的列实体列表（入参为空时返回空列表）
     * @throws BizException 列编码/存储类型/参数非法、重复，或待删列被表单视图引用时抛出 {@code PARAM_ERROR}
     */
    private List<FormColumnMetaPo> mergeColumns(Long tableDefinitionId, Integer version,
                                                List<FormColumnMetaDTO> dtos) {
        List<FormColumnMetaPo> existing = listColumns(tableDefinitionId);
        Map<String, FormColumnMetaPo> existingByCode = existing.stream().collect(Collectors.toMap(
                FormColumnMetaPo::getColumnCode, Function.identity(), (first, second) -> first, LinkedHashMap::new));
        List<FormColumnMetaPo> result = new ArrayList<>();
        Set<String> kept = new HashSet<>();
        if (dtos != null) {
            Set<String> codes = new HashSet<>();
            for (FormColumnMetaDTO dto : dtos) {
                validateColumn(dto);
                if (!codes.add(dto.getColumnCode())) {
                    throw paramError("列编码重复：" + dto.getColumnCode());
                }
                FormColumnMetaPo column = existingByCode.get(dto.getColumnCode());
                if (column == null) {
                    column = applyColumn(new FormColumnMetaPo(), dto, tableDefinitionId, version);
                    formColumnMetaMapper.insert(column);
                } else {
                    applyColumn(column, dto, tableDefinitionId, version);
                    formColumnMetaMapper.updateById(column);
                }
                kept.add(dto.getColumnCode());
                result.add(column);
            }
        }
        for (FormColumnMetaPo column : existing) {
            if (!kept.contains(column.getColumnCode())) {
                assertColumnNotReferenced(column, tableDefinitionId);
                formColumnMetaMapper.deleteById(column.getId());
            }
        }
        return result;
    }

    /**
     * 校验待删除列未被本表定义下任一表单视图的字段配置引用
     *
     * <p>列元数据一旦逻辑删除，其物理列将在下次发布时被 DROP；若仍被表单视图引用，
     * 会导致该视图编译 {@code schema_json} 失败并使历史提交数据无列可落。故在此前置拦截。</p>
     *
     * @param column 待删除列元数据
     * @param tableDefinitionId 表定义ID
     * @throws BizException 存在引用时抛出 {@code PARAM_ERROR}
     */
    private void assertColumnNotReferenced(FormColumnMetaPo column, Long tableDefinitionId) {
        LambdaQueryWrapper<FormFieldConfigPo> configWrapper = new LambdaQueryWrapper<>();
        configWrapper.eq(FormFieldConfigPo::getColumnMetaId, column.getId());
        List<FormFieldConfigPo> configs = formFieldConfigMapper.selectList(configWrapper);
        if (configs.isEmpty()) {
            return;
        }
        Set<Long> formIds = configs.stream().map(FormFieldConfigPo::getFormId).collect(Collectors.toSet());
        LambdaQueryWrapper<FormDefinitionPo> formWrapper = new LambdaQueryWrapper<>();
        formWrapper.in(FormDefinitionPo::getId, formIds);
        formWrapper.eq(FormDefinitionPo::getTableDefinitionId, tableDefinitionId);
        List<FormDefinitionPo> forms = formDefinitionMapper.selectList(formWrapper);
        if (forms.isEmpty()) {
            return;
        }
        String names = forms.stream()
                .map(form -> StringUtils.hasText(form.getName()) ? form.getName() : form.getCode())
                .collect(Collectors.joining("、"));
        throw paramError("列 " + column.getColumnCode() + " 正被表单视图「" + names
                + "」引用，请先在设计器中移除该字段后再删除");
    }

    /**
     * 列请求统一校验：编码白名单、存储类型/长度参数、必填与非空语义
     *
     * @param dto 列请求
     * @throws BizException 任一校验不通过时抛出 {@code PARAM_ERROR}
     */
    private static void validateColumn(FormColumnMetaDTO dto) {
        FormSchemaParser.assertColumnCode(dto.getColumnCode());
        FormDataType.assertAllowed(dto.getDataType(), dto.getColumnCode());
        FormDataType.mysqlType(dto.getDataType(), dto.getLength(), dto.getPrecision(), dto.getScale());
        assertRequiredNullable(dto);
    }

    /**
     * 校验「必填 ⇔ 非空」一致性：{@code required=1} 时 {@code nullable} 必须为 0；
     * {@code nullable=0} 时也必须声明为必填，避免物理列 {@code NOT NULL} 但服务层不校验导致写入失败。
     *
     * <p>{@code nullable} 缺省时由 {@code required} 推导（必填列物理表为 {@code NOT NULL}），
     * 避免元数据声明必填但物理列允许 NULL 的双重语义。</p>
     *
     * @param dto 列请求
     * @throws BizException 必填与非空语义冲突时抛出 {@code PARAM_ERROR}
     */
    private static void assertRequiredNullable(FormColumnMetaDTO dto) {
        int required = dto.getRequired() == null ? 0 : dto.getRequired();
        if (dto.getNullable() == null) {
            return;
        }
        if (required == STATUS_NORMAL && dto.getNullable() != 0) {
            throw paramError("列 " + dto.getColumnCode() + " 为必填时必须设置为非空（nullable=0）");
        }
        if (required != STATUS_NORMAL && dto.getNullable() == 0) {
            throw paramError("列 " + dto.getColumnCode() + " 为非空时必须同时设置为必填（required=1）");
        }
    }

    /**
     * 逐列校验并落库（新建表定义时使用），返回新列实体列表
     *
     * @param tableDefinitionId 表定义ID
     * @param version 表版本
     * @param dtos 列请求列表（可空）
     * @return 新列实体列表（入参为空时返回空列表）
     * @throws BizException 列编码/存储类型/参数非法或重复时抛出 {@code PARAM_ERROR}
     */
    private List<FormColumnMetaPo> persistColumns(Long tableDefinitionId, Integer version,
                                                  List<FormColumnMetaDTO> dtos) {
        List<FormColumnMetaPo> result = new ArrayList<>();
        if (dtos == null) {
            return result;
        }
        Set<String> codes = new HashSet<>();
        for (FormColumnMetaDTO dto : dtos) {
            validateColumn(dto);
            if (!codes.add(dto.getColumnCode())) {
                throw paramError("列编码重复：" + dto.getColumnCode());
            }
            FormColumnMetaPo column = applyColumn(new FormColumnMetaPo(), dto, tableDefinitionId, version);
            formColumnMetaMapper.insert(column);
            result.add(column);
        }
        return result;
    }

    /**
     * 列请求 → 列实体（就地写入，保留调用方传入实体的主键）
     *
     * @param column 目标列实体（新建传 {@code new FormColumnMeta()}，更新传已落库实体）
     * @param dto 列请求
     * @param tableDefinitionId 表定义ID
     * @param version 表版本
     * @return 写入后的列实体
     */
    private static FormColumnMetaPo applyColumn(FormColumnMetaPo column, FormColumnMetaDTO dto,
                                                Long tableDefinitionId, Integer version) {
        column.setTableDefinitionId(tableDefinitionId);
        column.setTableVersion(version);
        column.setColumnCode(dto.getColumnCode());
        column.setColumnName(StringUtils.hasText(dto.getColumnName()) ? dto.getColumnName() : dto.getColumnCode());
        column.setDataType(dto.getDataType());
        column.setLength(dto.getLength());
        column.setPrecision(dto.getPrecision());
        column.setScale(dto.getScale());
        int required = dto.getRequired() == null ? 0 : dto.getRequired();
        column.setRequired(required);
        column.setNullable(dto.getNullable() == null ? (required == STATUS_NORMAL ? 0 : 1) : dto.getNullable());
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
    private FormTableDefinitionPo requireTableDefinition(Long id) {
        FormTableDefinitionPo entity = formTableDefinitionMapper.selectById(id);
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
        LambdaQueryWrapper<FormTableDefinitionPo> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FormTableDefinitionPo::getTableCode, tableCode);
        wrapper.ne(excludeId != null, FormTableDefinitionPo::getId, excludeId);
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
    private static FormTableDefinitionVO toVo(FormTableDefinitionPo entity, List<FormColumnMetaPo> columns) {
        FormTableDefinitionVO vo = FormConverter.INSTANCE.toTableDefinitionVo(entity);
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
    private static FormColumnMetaVO toColumnVo(FormColumnMetaPo entity) {
        return FormConverter.INSTANCE.toColumnMetaVo(entity);
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
