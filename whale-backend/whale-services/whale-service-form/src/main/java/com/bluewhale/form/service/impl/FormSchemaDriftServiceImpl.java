package com.bluewhale.form.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.form.mapper.FormColumnMetaMapper;
import com.bluewhale.form.mapper.FormTableRegistryMapper;
import com.bluewhale.form.model.vo.FormSchemaDriftVO;
import com.bluewhale.form.po.FormColumnMetaPo;
import com.bluewhale.form.po.FormTableRegistryPo;
import com.bluewhale.form.service.FormSchemaDriftService;
import com.bluewhale.form.support.FormDataEngine;
import com.bluewhale.form.support.FormDataType;
import com.bluewhale.form.support.FormField;
import com.bluewhale.form.support.FormSchemaParser;
import com.bluewhale.form.support.FormTableDdlGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * 物理表结构漂移检测实现。
 *
 * <p>期望结构来源：元数据模型取 {@code form_column_meta}（按登记的表版本、仅状态正常列），
 * 旧模型取登记上的 {@code schema_json} 发布快照；实际结构取 {@code information_schema}。
 * 两边列类型统一归一化后逐列比对。</p>
 *
 * @author BlueWhale
 */
@Service
@RequiredArgsConstructor
public class FormSchemaDriftServiceImpl implements FormSchemaDriftService {

    /** 列状态：正常 */
    private static final int COLUMN_STATUS_NORMAL = 1;

    private final FormTableRegistryMapper formTableRegistryMapper;

    private final FormColumnMetaMapper formColumnMetaMapper;

    private final FormDataEngine formDataEngine;

    @Override
    public FormSchemaDriftVO inspect(Long registryId) {
        FormTableRegistryPo registry = formTableRegistryMapper.selectById(registryId);
        if (registry == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        Map<String, String> expected = expectedColumns(registry);
        Map<String, String> actual = formDataEngine.describeColumns(registry.getPhysicalTable());
        return buildResult(registry, expected, actual);
    }

    /**
     * 组装期望结构：元数据模型按列元数据，旧模型按 schema 快照
     *
     * @param registry 登记实体
     * @return 列名 → 归一化 MySQL 列类型（保持列顺序）
     */
    private Map<String, String> expectedColumns(FormTableRegistryPo registry) {
        Map<String, String> expected = new LinkedHashMap<>();
        if (registry.getTableDefinitionId() != null) {
            LambdaQueryWrapper<FormColumnMetaPo> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(FormColumnMetaPo::getTableDefinitionId, registry.getTableDefinitionId());
            wrapper.eq(FormColumnMetaPo::getTableVersion, registry.getTableVersion());
            wrapper.eq(FormColumnMetaPo::getStatus, COLUMN_STATUS_NORMAL);
            wrapper.orderByAsc(FormColumnMetaPo::getSortNo);
            for (FormColumnMetaPo column : formColumnMetaMapper.selectList(wrapper)) {
                expected.put(column.getColumnCode(), FormTableDdlGenerator.normalizeType(FormDataType.mysqlType(
                        column.getDataType(), column.getLength(), column.getPrecision(), column.getScale())));
            }
            return expected;
        }
        List<FormField> fields = FormSchemaParser.parseStorage(registry.getSchemaJson());
        for (FormField field : fields) {
            expected.put(field.getKey(), FormTableDdlGenerator.normalizeType(FormTableDdlGenerator.columnType(field)));
        }
        return expected;
    }

    /**
     * 比对期望结构与物理表实际结构并组装检测结果
     *
     * @param registry 登记实体
     * @param expected 期望结构
     * @param actual 实际结构
     * @return 漂移检测结果
     */
    private static FormSchemaDriftVO buildResult(FormTableRegistryPo registry, Map<String, String> expected,
                                                 Map<String, String> actual) {
        FormSchemaDriftVO vo = new FormSchemaDriftVO();
        vo.setRegistryId(registry.getId());
        vo.setPhysicalTable(registry.getPhysicalTable());
        vo.setCheckedAt(LocalDateTime.now());
        vo.setExpectedColumnCount(expected.size());
        vo.setPhysicalColumnCount(actual.size());
        if (actual.isEmpty()) {
            vo.setStatus(FormSchemaDriftVO.TABLE_MISSING);
            vo.setMissingInPhysical(new ArrayList<>(expected.keySet()));
            vo.setMissingInMetadata(new ArrayList<>());
            vo.setTypeMismatches(new ArrayList<>());
            return vo;
        }
        Set<String> reserved = FormTableDdlGenerator.reservedColumns();
        List<String> missingInPhysical = new ArrayList<>();
        for (String column : expected.keySet()) {
            if (!actual.containsKey(column)) {
                missingInPhysical.add(column);
            }
        }
        List<String> missingInMetadata = new ArrayList<>(new TreeSet<>(actual.keySet()));
        missingInMetadata.removeAll(expected.keySet());
        missingInMetadata.removeAll(reserved);
        List<FormSchemaDriftVO.TypeMismatch> mismatches = new ArrayList<>();
        for (Map.Entry<String, String> entry : expected.entrySet()) {
            String current = actual.get(entry.getKey());
            if (current != null && !current.equals(entry.getValue())) {
                mismatches.add(new FormSchemaDriftVO.TypeMismatch(
                        entry.getKey(), entry.getValue(), FormTableDdlGenerator.normalizeType(current)));
            }
        }
        vo.setMissingInPhysical(missingInPhysical);
        vo.setMissingInMetadata(missingInMetadata);
        vo.setTypeMismatches(mismatches);
        vo.setStatus(missingInPhysical.isEmpty() && missingInMetadata.isEmpty() && mismatches.isEmpty()
                ? FormSchemaDriftVO.OK : FormSchemaDriftVO.DRIFT);
        return vo;
    }
}
