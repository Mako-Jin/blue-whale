package com.bluewhale.form.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bluewhale.common.audit.annotation.AuditLog;
import com.bluewhale.common.audit.enums.OperateType;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.form.converter.FormConverter;
import com.bluewhale.form.po.FormColumnMetaPo;
import com.bluewhale.form.po.FormDefinitionPo;
import com.bluewhale.form.po.FormTableDefinitionPo;
import com.bluewhale.form.po.FormTableRegistryPo;
import com.bluewhale.form.mapper.FormColumnMetaMapper;
import com.bluewhale.form.mapper.FormDefinitionMapper;
import com.bluewhale.form.mapper.FormTableDefinitionMapper;
import com.bluewhale.form.mapper.FormTableRegistryMapper;
import com.bluewhale.form.service.FormPublishService;
import com.bluewhale.form.support.FormDataEngine;
import com.bluewhale.form.support.FormColumnNarrowingDetector;
import com.bluewhale.form.support.FormDataType;
import com.bluewhale.form.support.FormDdlAuditor;
import com.bluewhale.form.support.FormField;
import com.bluewhale.form.support.FormMetadataCache;
import com.bluewhale.form.support.FormMetadataCompiler;
import com.bluewhale.form.support.FormMetrics;
import com.bluewhale.form.support.FormRegistryStatusMarker;
import com.bluewhale.form.support.FormSchemaParser;
import com.bluewhale.form.support.FormTableDdlGenerator;
import com.bluewhale.form.model.vo.FormTableRegistryVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 表单发布服务实现。
 *
 * <p>旧模型发布 = 校验编码 → 校验版本未发布 → 解析 schema → 生成 DDL 建物理表 → 登记 registry。
 * 元数据模型发布 = 校验编码 → 比对列元数据与物理表现有列 → 建表或执行增量 ALTER → 登记/刷新 registry。
 * 物理表名与列名均来自受校验的 schema 白名单（spec §三.3.3、§三.3.9）。</p>
 *
 * @author BlueWhale
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class FormPublishServiceImpl implements FormPublishService {

    /** 登记状态：生效 */
    private static final int STATUS_NORMAL = 1;

    /** 定义类型：旧模型（表单 schema 直接建表） */
    private static final String DEFINITION_TYPE_FORM = "FORM";

    /** 定义类型：元数据模型（表定义 + 列元数据） */
    private static final String DEFINITION_TYPE_TABLE = "TABLE";

    /** 版本号默认值 */
    private static final int DEFAULT_VERSION = 1;

    /** 列状态：正常 */
    private static final int COLUMN_STATUS_NORMAL = 1;

    private final FormDefinitionMapper formDefinitionMapper;

    private final FormTableRegistryMapper formTableRegistryMapper;

    private final FormTableDefinitionMapper formTableDefinitionMapper;

    private final FormColumnMetaMapper formColumnMetaMapper;

    private final FormDataEngine formDataEngine;

    private final FormDdlAuditor formDdlAuditor;

    private final FormRegistryStatusMarker formRegistryStatusMarker;

    private final FormMetrics formMetrics;

    private final FormMetadataCache metadataCache;

    /**
     * 发布表单物理表。
     *
     * <p>此处刻意<b>不加声明式事务</b>：MySQL 的 CREATE/ALTER 会隐式提交，事务注解无法保护
     * 「DDL 已生效、登记写入失败」的场景。可靠性由 DDL 审计（独立事务逐条留痕）与
     * 失败状态标记（独立事务写 registry）共同保证，异常不再依赖回滚。</p>
     *
     * @param definitionId 表单定义ID
     * @return 物理表登记视图对象
     */
    @Override
    @AuditLog(module = "form", operation = "发布表单物理表", type = OperateType.UPDATE,
            targetType = "form_definition", targetId = "#definitionId")
    public FormTableRegistryVO publish(Long definitionId) {
        return publishMeasured(definitionId, false);
    }

    @Override
    @AuditLog(module = "form", operation = "发布表单物理表", type = OperateType.UPDATE,
            targetType = "form_definition", targetId = "#definitionId")
    public FormTableRegistryVO publish(Long definitionId, boolean force) {
        return publishMeasured(definitionId, force);
    }

    /**
     * 计时并上报发布指标：成功记录耗时与成功计数，失败记录失败计数后原样抛出。
     *
     * @param definitionId 表单定义ID
     * @param force 存在列类型收窄时是否强制发布
     * @return 物理表登记视图对象
     * @throws RuntimeException 发布失败时原样抛出
     */
    private FormTableRegistryVO publishMeasured(Long definitionId, boolean force) {
        long start = System.nanoTime();
        try {
            FormTableRegistryVO vo = doPublish(definitionId, force);
            formMetrics.publishSucceeded(System.nanoTime() - start);
            return vo;
        } catch (RuntimeException e) {
            formMetrics.publishFailed();
            throw e;
        }
    }

    /**
     * 发布表单物理表本体（不含指标埋点）
     *
     * @param definitionId 表单定义ID
     * @param force 存在列类型收窄时是否强制发布
     * @return 物理表登记视图对象
     */
    private FormTableRegistryVO doPublish(Long definitionId, boolean force) {
        FormDefinitionPo definition = requireDefinition(definitionId);
        if (definition.getTableDefinitionId() == null) {
            return publishLegacy(definition);
        }
        return publishMetadata(definition, force);
    }

    /**
     * 旧模型发布：解析 {@code schema_json} → 建物理表 → 登记（同版本重复发布抛 {@code PARAM_ERROR}）
     *
     * @param definition 表单定义实体
     * @return 物理表登记视图对象
     */
    private FormTableRegistryVO publishLegacy(FormDefinitionPo definition) {
        FormTableDdlGenerator.assertPublishableCode(definition.getCode());
        Integer version = definition.getVersion() == null ? DEFAULT_VERSION : definition.getVersion();
        assertNotPublished(definition.getCode(), version);
        List<FormField> fields = FormSchemaParser.parse(definition.getSchemaJson());
        String table = FormTableDdlGenerator.tableName(definition.getCode(), version);
        executeDdl(DEFINITION_TYPE_FORM, definition.getId(), table,
                FormTableDdlGenerator.generate(table, definition.getCode(), version, fields));
        FormTableRegistryPo registry = new FormTableRegistryPo();
        registry.setDefinitionId(definition.getId());
        registry.setDefinitionCode(definition.getCode());
        registry.setDefinitionVersion(version);
        registry.setPhysicalTable(table);
        registry.setFieldCount(fields.size());
        registry.setSchemaJson(definition.getSchemaJson());
        registry.setStatus(STATUS_NORMAL);
        try {
            formTableRegistryMapper.insert(registry);
        } catch (DuplicateKeyException e) {
            throw duplicatePublishError();
        }
        metadataCache.invalidateRegistry(definition.getCode(), version);
        return toVo(registry);
    }

    /**
     * 元数据模型发布：确保所属表定义物理表已发布（无表则建表，已有表则按列元数据同步结构）
     *
     * @param definition 表单定义实体（已含 {@code tableDefinitionId}）
     * @return 所属表定义的物理表登记视图对象
     */
    private FormTableRegistryVO publishMetadata(FormDefinitionPo definition, boolean force) {
        return ensurePublished(requireTableDefinition(definition.getTableDefinitionId()), force);
    }

    @Override
    public List<FormTableRegistryVO> listTables(Long definitionId) {
        FormDefinitionPo definition = requireDefinition(definitionId);
        if (definition.getTableDefinitionId() != null) {
            return listTablesByTableDefinition(definition.getTableDefinitionId());
        }
        LambdaQueryWrapper<FormTableRegistryPo> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FormTableRegistryPo::getDefinitionId, definitionId);
        wrapper.orderByDesc(FormTableRegistryPo::getDefinitionVersion);
        return formTableRegistryMapper.selectList(wrapper).stream().map(FormPublishServiceImpl::toVo).toList();
    }

    @Override
    @AuditLog(module = "form", operation = "发布表定义物理表", type = OperateType.UPDATE,
            targetType = "form_table_definition", targetId = "#tableDefinitionId")
    public FormTableRegistryVO publishTableDefinition(Long tableDefinitionId) {
        return publishTableDefinitionMeasured(tableDefinitionId, false);
    }

    @Override
    @AuditLog(module = "form", operation = "发布表定义物理表", type = OperateType.UPDATE,
            targetType = "form_table_definition", targetId = "#tableDefinitionId")
    public FormTableRegistryVO publishTableDefinition(Long tableDefinitionId, boolean force) {
        return publishTableDefinitionMeasured(tableDefinitionId, force);
    }

    /**
     * 计时并上报表定义发布指标：成功记录耗时与成功计数，失败记录失败计数后原样抛出。
     *
     * @param tableDefinitionId 表定义ID
     * @param force 存在列类型收窄时是否强制发布
     * @return 物理表登记视图对象
     * @throws RuntimeException 发布失败时原样抛出
     */
    private FormTableRegistryVO publishTableDefinitionMeasured(Long tableDefinitionId, boolean force) {
        long start = System.nanoTime();
        try {
            FormTableRegistryVO vo = doPublishTableDefinition(tableDefinitionId, force);
            formMetrics.publishSucceeded(System.nanoTime() - start);
            return vo;
        } catch (RuntimeException e) {
            formMetrics.publishFailed();
            throw e;
        }
    }

    /**
     * 发布表定义物理表本体（不含指标埋点）
     *
     * @param tableDefinitionId 表定义ID
     * @param force 存在列类型收窄时是否强制发布
     * @return 物理表登记视图对象
     */
    private FormTableRegistryVO doPublishTableDefinition(Long tableDefinitionId, boolean force) {
        return ensurePublished(requireTableDefinition(tableDefinitionId), force);
    }

    /**
     * 发布（幂等 + 增量迁移）：物理表不存在则按列元数据建表；已存在则对比结构差异并执行 ALTER。
     *
     * <p><b>列变更只在发布时落到物理表</b>：编辑/删除列仅改 {@code form_column_meta} 元数据，
     * 本方法比对「目标列元数据」与「物理表现有列」，产出 {@code ADD COLUMN}（新增）、
     * {@code MODIFY COLUMN}（类型/长度变更）、{@code DROP COLUMN}（元数据已删且未被表单视图引用）。
     * 物理表公共列永不变更。</p>
     *
     * @param definition 表定义实体
     * @param force 存在列类型收窄时是否强制发布
     * @return 物理表登记视图对象（新建时 insert，已存在时刷新列数）
     * @throws BizException 编码非法、无可发布列、待删列被表单视图引用或存在未确认的列类型收窄时
     *         抛出 {@code PARAM_ERROR}
     */
    private FormTableRegistryVO ensurePublished(FormTableDefinitionPo definition, boolean force) {
        FormTableDdlGenerator.assertPublishableCode(definition.getTableCode());
        Integer version = definition.getVersion() == null ? DEFAULT_VERSION : definition.getVersion();
        List<FormColumnMetaPo> columns = listPublishColumns(definition.getId(), version);
        if (columns.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "form.table-definition.no-publishable-column",
                    "表定义无可发布的列，请先维护列元数据");
        }
        String table = FormTableDdlGenerator.tableName(definition.getTableCode(), version);
        String schemaSnapshot = FormMetadataCompiler.compileTable(columns);
        FormTableRegistryPo registry = findRegistry(definition.getTableCode(), version);
        Map<String, String> existing = formDataEngine.describeColumns(table);
        assertNoNarrowing(table, columns, existing, force);
        try {
            if (existing.isEmpty()) {
                executeDdl(DEFINITION_TYPE_TABLE, definition.getId(), table,
                        FormTableDdlGenerator.generateFromColumns(table, definition.getTableCode(), version, columns));
            } else {
                applyAlter(definition.getId(), table, columns, existing,
                        listMetadataCodes(definition.getId(), version));
                syncIndexes(definition.getId(), table, columns);
            }
        } catch (RuntimeException e) {
            markPublishedFailed(registry, e.getMessage());
            log.error("[ALERT] 表单物理表发布失败，需人工介入：table={}, definitionId={}",
                    table, definition.getId(), e);
            throw e;
        }
        if (registry == null) {
            registry = new FormTableRegistryPo();
            registry.setTableDefinitionId(definition.getId());
            registry.setTableCode(definition.getTableCode());
            registry.setTableVersion(version);
            registry.setPhysicalTable(table);
            registry.setFieldCount(columns.size());
            registry.setSchemaJson(schemaSnapshot);
            registry.setStatus(STATUS_NORMAL);
            try {
                formTableRegistryMapper.insert(registry);
            } catch (DuplicateKeyException e) {
                throw duplicatePublishError();
            }
        } else {
            registry.setFieldCount(columns.size());
            registry.setSchemaJson(schemaSnapshot);
            registry.setStatus(STATUS_NORMAL);
            registry.setLastError(null);
            formTableRegistryMapper.updateById(registry);
        }
        metadataCache.invalidateRegistry(definition.getTableCode(), version);
        return toVo(registry);
    }

    /**
     * 发布前置预检：列类型「收窄」一次性列出并要求二次确认
     *
     * <p>预检必须在执行任何 DDL 之前完成，否则可能出现「前几列已改、后面的收窄才报错」的半迁移状态。
     * 收窄本身不禁止（业务可能确实要缩短字段），但必须由调用方显式传 {@code force=true} 确认。</p>
     *
     * @param table 物理表名
     * @param columns 目标列元数据
     * @param existing 物理表现有列（列名 → 归一化类型）；表不存在时为空映射
     * @param force 是否已显式确认
     * @throws BizException 存在收窄且未确认时抛出 {@code PARAM_ERROR}
     */
    private void assertNoNarrowing(String table, List<FormColumnMetaPo> columns,
                                   Map<String, String> existing, boolean force) {
        if (existing.isEmpty()) {
            return;
        }
        List<String> narrowed = new ArrayList<>();
        for (FormColumnMetaPo column : columns) {
            String currentType = existing.get(column.getColumnCode());
            if (currentType == null) {
                continue;
            }
            String wantedType = FormTableDdlGenerator.normalizeType(FormDataType.mysqlType(
                    column.getDataType(), column.getLength(), column.getPrecision(), column.getScale()));
            String reason = FormColumnNarrowingDetector.detect(currentType, wantedType);
            if (reason != null) {
                narrowed.add(column.getColumnCode() + "：" + reason);
            }
        }
        if (narrowed.isEmpty()) {
            return;
        }
        if (!force) {
            throw new BizException(ErrorCode.PARAM_ERROR, "form.publish.narrowed-columns",
                    "以下列类型收窄可能导致存量数据被截断，请确认后以 force=true 重新发布："
                            + String.join("；", narrowed), String.join("；", narrowed));
        }
        log.warn("表单发布存在列类型收窄，已按 force=true 确认执行：table={}, 收窄列={}", table, narrowed);
    }

    /**
     * 对比目标列元数据与物理表现有列，逐条执行增量 DDL
     *
     * @param definitionId 表定义ID（审计用）
     * @param table 物理表名
     * @param columns 目标列元数据（已按排序号升序）
     * @param existing 物理表现有列（列名 → 归一化列类型）
     * @param metadataCodes 该表定义指定版本下**仍存在**的列编码集合（不限状态）：
     *                      仅「元数据已逻辑删除」的列才允许 DROP，「停用（status=0）」列保留物理列
     */
    private void applyAlter(Long definitionId, String table, List<FormColumnMetaPo> columns,
                            Map<String, String> existing, Set<String> metadataCodes) {
        Set<String> target = new LinkedHashSet<>();
        for (FormColumnMetaPo column : columns) {
            String code = column.getColumnCode();
            target.add(code);
            String currentType = existing.get(code);
            if (currentType == null) {
                executeDdlTolerant(DEFINITION_TYPE_TABLE, definitionId, table,
                        FormTableDdlGenerator.alterAdd(table, column));
                continue;
            }
            String wantedType = FormTableDdlGenerator.normalizeType(FormDataType.mysqlType(
                    column.getDataType(), column.getLength(), column.getPrecision(), column.getScale()));
            if (!wantedType.equals(currentType)) {
                executeDdlTolerant(DEFINITION_TYPE_TABLE, definitionId, table,
                        FormTableDdlGenerator.alterModify(table, column));
            }
        }
        for (String code : existing.keySet()) {
            if (target.contains(code) || FormTableDdlGenerator.reservedColumns().contains(code)
                    || metadataCodes.contains(code)) {
                continue;
            }
            executeDdlTolerant(DEFINITION_TYPE_TABLE, definitionId, table,
                    FormTableDdlGenerator.alterDrop(table, code));
        }
    }

    /**
     * 对齐存量物理表的字段级自动索引（幂等）：补齐缺失索引、回收已不合格（列已删/类型变化）的自动索引
     *
     * <p>仅管理 {@code idx_f_} 前缀的索引，公共索引与人工索引不动；新建表时索引已随 DDL 生成，无需再对齐。</p>
     *
     * @param definitionId 表定义ID（审计用）
     * @param table 物理表名
     * @param columns 目标列元数据（已按排序号升序）
     */
    private void syncIndexes(Long definitionId, String table, List<FormColumnMetaPo> columns) {
        Map<String, String> desired = FormTableDdlGenerator.autoIndexes(columns);
        Set<String> existing = formDataEngine.describeIndexes(table);
        for (Map.Entry<String, String> entry : desired.entrySet()) {
            if (!existing.contains(entry.getKey())) {
                executeDdlTolerant(DEFINITION_TYPE_TABLE, definitionId, table,
                        FormTableDdlGenerator.alterAddIndex(table, entry.getValue()));
            }
        }
        for (String name : existing) {
            if (name.startsWith(FormTableDdlGenerator.AUTO_INDEX_PREFIX) && !desired.containsKey(name)) {
                executeDdlTolerant(DEFINITION_TYPE_TABLE, definitionId, table,
                        FormTableDdlGenerator.alterDropIndex(table, name));
            }
        }
    }

    /**
     * 执行增量 DDL：并发发布时另一实例可能已完成同一变更，重复列/索引/已不存在等错误视为幂等成功。
     *
     * <p>MySQL DDL 无法用事务串行化，多实例同时发布同一表版本时，后到者会收到
     * {@code Duplicate column name} / {@code Duplicate key name} / {@code check that column/key exists}。
     * 这些错误说明目标结构已达成，吞掉即可收敛；其他错误仍原样抛出。</p>
     *
     * @param definitionType 定义类型（FORM / TABLE）
     * @param definitionId 定义ID
     * @param physicalTable 物理表名
     * @param ddl DDL 语句
     */
    private void executeDdlTolerant(String definitionType, Long definitionId, String physicalTable, String ddl) {
        try {
            executeDdl(definitionType, definitionId, physicalTable, ddl);
        } catch (RuntimeException e) {
            if (isConvergentDdlError(e)) {
                log.warn("并发发布已由其他实例完成，忽略重复 DDL: table={}, ddl={}", physicalTable, ddl);
                return;
            }
            throw e;
        }
    }

    /**
     * 判断 DDL 错误是否属于「目标结构已达成」的并发收敛场景
     *
     * @param e DDL 执行异常
     * @return 可忽略返回 {@code true}
     */
    private static boolean isConvergentDdlError(RuntimeException e) {
        String message = e.getMessage() == null ? "" : e.getMessage().toLowerCase(Locale.ROOT);
        return message.contains("duplicate column name")
                || message.contains("duplicate key name")
                || message.contains("check that column/key exists");
    }

    /**
     * 执行一条 DDL 并写入审计（成功/失败均留痕）
     *
     * @param definitionType 定义类型（FORM / TABLE）
     * @param definitionId 定义ID
     * @param physicalTable 物理表名
     * @param ddl DDL 语句
     * @throws RuntimeException DDL 执行失败时原样抛出
     */
    private void executeDdl(String definitionType, Long definitionId, String physicalTable, String ddl) {
        try {
            formDataEngine.executeDdl(ddl);
        } catch (RuntimeException e) {
            recordDdlFailure(definitionType, definitionId, physicalTable, ddl, e);
            throw e;
        }
        recordDdlSuccess(definitionType, definitionId, physicalTable, ddl);
    }

    /**
     * 记录 DDL 成功审计（审计失败不影响发布主流程）
     *
     * @param definitionType 定义类型
     * @param definitionId 定义ID
     * @param physicalTable 物理表名
     * @param ddl DDL 语句
     */
    private void recordDdlSuccess(String definitionType, Long definitionId, String physicalTable, String ddl) {
        try {
            formDdlAuditor.recordSuccess(definitionType, definitionId, physicalTable, ddl);
        } catch (RuntimeException e) {
            log.warn("表单 DDL 审计写入失败：{}", e.getMessage());
        }
    }

    /**
     * 记录 DDL 失败审计（审计失败不影响异常抛出）
     *
     * @param definitionType 定义类型
     * @param definitionId 定义ID
     * @param physicalTable 物理表名
     * @param ddl DDL 语句
     * @param error 执行异常
     */
    private void recordDdlFailure(String definitionType, Long definitionId, String physicalTable, String ddl,
                                  RuntimeException error) {
        try {
            formDdlAuditor.recordFailure(definitionType, definitionId, physicalTable, ddl, error.getMessage());
        } catch (RuntimeException e) {
            log.warn("表单 DDL 审计写入失败：{}", e.getMessage());
        }
    }

    /**
     * 发布失败时把已有登记标记为失败（独立事务，避免被回滚吞掉）
     *
     * @param registry 已有登记（新建登记尚未落库时为空）
     * @param errorMessage 失败原因
     */
    private void markPublishedFailed(FormTableRegistryPo registry, String errorMessage) {
        if (registry == null || registry.getId() == null) {
            return;
        }
        try {
            formRegistryStatusMarker.markFailed(registry.getId(), errorMessage);
            invalidateRegistry(registry);
        } catch (RuntimeException e) {
            log.warn("表单发布失败状态标记写入失败：{}", e.getMessage());
        }
    }

    /**
     * 失效发布失败登记对应的本地缓存（其状态已不再是「生效」，不能继续被读取路径命中）
     *
     * @param registry 已有登记
     */
    private void invalidateRegistry(FormTableRegistryPo registry) {
        if (registry.getTableCode() != null) {
            metadataCache.invalidateRegistry(registry.getTableCode(), registry.getTableVersion());
        }
        if (registry.getDefinitionCode() != null) {
            metadataCache.invalidateRegistry(registry.getDefinitionCode(), registry.getDefinitionVersion());
        }
    }

    /**
     * 查询该表定义指定版本下**全部**列元数据的列编码集合（不限状态）
     *
     * <p>用于区分「已逻辑删除的列」（可 DROP）与「停用（{@code status=0}）的列」（保留物理列）。
     * 被表单视图引用的列无法从元数据中删除（编辑期护栏），故此处只需按元数据是否存在判定。</p>
     *
     * @param tableDefinitionId 表定义ID
     * @param version 表版本
     * @return 列编码集合（按查询顺序）
     */
    private Set<String> listMetadataCodes(Long tableDefinitionId, Integer version) {
        LambdaQueryWrapper<FormColumnMetaPo> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FormColumnMetaPo::getTableDefinitionId, tableDefinitionId);
        wrapper.eq(FormColumnMetaPo::getTableVersion, version);
        return formColumnMetaMapper.selectList(wrapper).stream()
                .map(FormColumnMetaPo::getColumnCode)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    @Override
    public List<FormTableRegistryVO> listTablesByTableDefinition(Long tableDefinitionId) {
        requireTableDefinition(tableDefinitionId);
        LambdaQueryWrapper<FormTableRegistryPo> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FormTableRegistryPo::getTableDefinitionId, tableDefinitionId);
        wrapper.orderByDesc(FormTableRegistryPo::getTableVersion);
        return formTableRegistryMapper.selectList(wrapper).stream().map(FormPublishServiceImpl::toVo).toList();
    }

    /**
     * 查询表定义指定版本下状态正常的列元数据（按排序号升序）
     *
     * @param tableDefinitionId 表定义ID
     * @param version 表版本
     * @return 可发布列元数据列表
     */
    private List<FormColumnMetaPo> listPublishColumns(Long tableDefinitionId, Integer version) {
        LambdaQueryWrapper<FormColumnMetaPo> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FormColumnMetaPo::getTableDefinitionId, tableDefinitionId);
        wrapper.eq(FormColumnMetaPo::getTableVersion, version);
        wrapper.eq(FormColumnMetaPo::getStatus, COLUMN_STATUS_NORMAL);
        wrapper.orderByAsc(FormColumnMetaPo::getSortNo);
        return formColumnMetaMapper.selectList(wrapper);
    }

    /**
     * 查询表定义，不存在则抛出 {@code NOT_FOUND}
     *
     * @param tableDefinitionId 表定义ID
     * @return 表定义实体
     */
    private FormTableDefinitionPo requireTableDefinition(Long tableDefinitionId) {
        FormTableDefinitionPo definition = formTableDefinitionMapper.selectById(tableDefinitionId);
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
    private FormTableRegistryPo findRegistry(String tableCode, Integer version) {
        LambdaQueryWrapper<FormTableRegistryPo> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FormTableRegistryPo::getTableCode, tableCode);
        wrapper.eq(FormTableRegistryPo::getTableVersion, version);
        return formTableRegistryMapper.selectOne(wrapper);
    }

    /**
     * 查询表单定义，不存在则抛出 {@code NOT_FOUND}
     *
     * @param definitionId 表单定义ID
     * @return 表单定义实体
     */
    private FormDefinitionPo requireDefinition(Long definitionId) {
        FormDefinitionPo definition = formDefinitionMapper.selectById(definitionId);
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
        LambdaQueryWrapper<FormTableRegistryPo> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FormTableRegistryPo::getDefinitionCode, code);
        wrapper.eq(FormTableRegistryPo::getDefinitionVersion, version);
        wrapper.ne(FormTableRegistryPo::getStatus, FormRegistryStatusMarker.STATUS_FAILED);
        Long count = formTableRegistryMapper.selectCount(wrapper);
        if (count != null && count > 0) {
            throw new BizException(ErrorCode.PARAM_ERROR, "form.definition.version-already-published",
                    "该表单版本已发布，如需变更请修改版本号后重新发布");
        }
    }

    /**
     * 构造并发发布冲突异常（唯一约束兜底，避免重复登记）
     *
     * @return 参数错误业务异常
     */
    private static BizException duplicatePublishError() {
        return new BizException(ErrorCode.PARAM_ERROR, "form.definition.publish-conflict",
                "该表单版本已发布或正在发布，请刷新后重试");
    }

    /**
     * 登记实体转视图对象
     *
     * @param entity 登记实体
     * @return 登记视图对象
     */
    private static FormTableRegistryVO toVo(FormTableRegistryPo entity) {
        return FormConverter.INSTANCE.toRegistryVo(entity);
    }
}
