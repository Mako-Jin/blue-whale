package com.bluewhale.form.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.form.mapper.FormDataMapper;
import com.bluewhale.form.mapper.FormReconcileDiffMapper;
import com.bluewhale.form.mapper.FormTableRegistryMapper;
import com.bluewhale.form.model.request.FormReconcileDiffQueryRequest;
import com.bluewhale.form.model.vo.FormReconcileDiffVO;
import com.bluewhale.form.po.FormDataPo;
import com.bluewhale.form.po.FormReconcileDiffPo;
import com.bluewhale.form.po.FormTableRegistryPo;
import com.bluewhale.form.service.FormReconcileService;
import com.bluewhale.form.support.FormDataEngine;
import com.bluewhale.form.support.FormField;
import com.bluewhale.form.support.FormMetrics;
import com.bluewhale.form.support.FormSchemaParser;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 表单主从数据对账服务实现。
 *
 * <p>对账以物理表登记为范围：逐表比对「物理表活跃记录ID」与「主表按该表路由条件命中的记录ID」，
 * 单向缺失即登记一条差异。对账方法不加事务（跨表循环、可重入、逐条登记，唯一键保证幂等）；
 * 修复方法加事务，保证物理表操作与差异状态一起成功或失败。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FormReconcileServiceImpl implements FormReconcileService {

    /** 物理表登记状态：生效 */
    private static final int REGISTRY_STATUS_ENABLED = 1;

    /** 差异状态：待处理 */
    private static final int DIFF_STATUS_PENDING = 0;

    /** 差异状态：已修复 */
    private static final int DIFF_STATUS_REPAIRED = 1;

    /** 差异类型：主表有物理表无 */
    private static final int DIFF_TYPE_MAIN_ONLY = 1;

    /** 差异类型：物理表有主表无 */
    private static final int DIFF_TYPE_PHYSICAL_ONLY = 2;

    /** 单表参与比对的物理表记录上限（超出即截断） */
    private static final int PHYSICAL_SCAN_LIMIT = 20000;

    /** 单批次差异登记上限 */
    private static final int MAX_DIFFS_PER_BATCH = 5000;

    /** 对账批次号格式 */
    private static final DateTimeFormatter BATCH_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    /** JSON 解析器（仅读取，线程安全） */
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final FormReconcileDiffMapper formReconcileDiffMapper;

    private final FormTableRegistryMapper formTableRegistryMapper;

    private final FormDataMapper formDataMapper;

    private final FormDataEngine formDataEngine;

    private final FormMetrics formMetrics;

    /**
     * 对指定租户执行一次主从数据对账并逐表登记差异（不加事务：跨表循环、可重入、逐条登记）。
     *
     * @param tenantId 租户ID
     * @return 本次对账执行摘要
     * @throws BizException 租户ID为空时抛出 {@code PARAM_ERROR}
     */
    @Override
    public ReconcileSummary reconcile(Long tenantId) {
        if (tenantId == null) {
            throw paramError("租户ID不能为空");
        }
        String batchNo = LocalDateTime.now().format(BATCH_FORMATTER);
        List<FormTableRegistryPo> registries = listEnabledRegistries();
        int tableCount = 0;
        int diffCount = 0;
        boolean truncated = false;
        boolean limited = false;
        for (FormTableRegistryPo registry : registries) {
            tableCount++;
            List<Long> physicalIds = formDataEngine.listIds(registry.getPhysicalTable(), tenantId,
                    PHYSICAL_SCAN_LIMIT + 1);
            boolean physicalTruncated = physicalIds.size() > PHYSICAL_SCAN_LIMIT;
            if (physicalTruncated) {
                truncated = true;
                log.warn("物理表记录数超过对账扫描上限，仅比对前 {} 条 tenantId={} table={}",
                        PHYSICAL_SCAN_LIMIT, tenantId, registry.getPhysicalTable());
                physicalIds = new ArrayList<>(physicalIds.subList(0, PHYSICAL_SCAN_LIMIT));
            }
            Set<Long> physicalIdSet = new HashSet<>(physicalIds);
            List<Long> mainIds = listMainIds(tenantId, registry);
            Set<Long> mainIdSet = new HashSet<>(mainIds);
            if (physicalTruncated) {
                // 物理表只扫到一部分，任何「主表有、物理表无」的判定都可能是误报，本表跳过该方向
                log.warn("物理表扫描被截断，跳过「主表有物理表无」比对 tenantId={} table={}",
                        tenantId, registry.getPhysicalTable());
            } else {
                for (Long dataId : mainIds) {
                    if (physicalIdSet.contains(dataId)) {
                        continue;
                    }
                    if (diffCount >= MAX_DIFFS_PER_BATCH) {
                        limited = true;
                        break;
                    }
                    registerDiff(batchNo, tenantId, registry.getPhysicalTable(), dataId, DIFF_TYPE_MAIN_ONLY,
                            "主表存在记录，物理表缺失");
                    diffCount++;
                }
            }
            if (limited) {
                break;
            }
            for (Long dataId : physicalIds) {
                if (mainIdSet.contains(dataId)) {
                    continue;
                }
                if (diffCount >= MAX_DIFFS_PER_BATCH) {
                    limited = true;
                    break;
                }
                registerDiff(batchNo, tenantId, registry.getPhysicalTable(), dataId, DIFF_TYPE_PHYSICAL_ONLY,
                        "物理表存在记录，主表缺失或已删除");
                diffCount++;
            }
            if (limited) {
                break;
            }
        }
        if (limited) {
            truncated = true;
            log.warn("单批次差异登记达到上限 {} 条，本次停止登记 tenantId={} batch={}",
                    MAX_DIFFS_PER_BATCH, tenantId, batchNo);
        }
        formMetrics.dualWriteMismatch(diffCount);
        return new ReconcileSummary(batchNo, tableCount, diffCount, truncated);
    }

    /**
     * 分页查询对账差异记录（按租户过滤，创建时间倒序）。
     *
     * @param request 查询条件：batchNo / status / physicalTable
     * @param tenantId 租户ID
     * @return 差异记录分页结果
     * @throws BizException 查询条件或租户ID为空时抛出 {@code PARAM_ERROR}
     */
    @Override
    public PageResult<FormReconcileDiffVO> page(FormReconcileDiffQueryRequest request, Long tenantId) {
        if (request == null) {
            throw paramError("查询条件不能为空");
        }
        if (tenantId == null) {
            throw paramError("租户ID不能为空");
        }
        LambdaQueryWrapper<FormReconcileDiffPo> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FormReconcileDiffPo::getTenantId, tenantId);
        wrapper.eq(StringUtils.hasText(request.getBatchNo()), FormReconcileDiffPo::getBatchNo, request.getBatchNo());
        wrapper.eq(request.getStatus() != null, FormReconcileDiffPo::getStatus, request.getStatus());
        wrapper.eq(StringUtils.hasText(request.getPhysicalTable()), FormReconcileDiffPo::getPhysicalTable,
                request.getPhysicalTable());
        wrapper.orderByDesc(FormReconcileDiffPo::getCreateTime);
        wrapper.orderByDesc(FormReconcileDiffPo::getId);
        Page<FormReconcileDiffPo> query = request.toPage();
        Page<FormReconcileDiffPo> page = formReconcileDiffMapper.selectPage(query, wrapper);
        List<FormReconcileDiffVO> records = page.getRecords().stream()
                .map(FormReconcileServiceImpl::toVo).toList();
        return PageResult.of(records, page.getTotal(), page.getCurrent(), page.getSize());
    }

    /**
     * 修复一条对账差异（事务内完成物理表操作与差异状态更新，仅处理待处理状态）。
     *
     * @param diffId 差异记录ID
     * @param tenantId 租户ID
     * @throws BizException 记录不存在或跨租户时抛出 {@code NOT_FOUND}；
     *         主记录缺失、结构快照缺失或差异类型非法时抛出 {@code PARAM_ERROR}
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void repair(Long diffId, Long tenantId) {
        if (diffId == null) {
            throw paramError("差异记录ID不能为空");
        }
        if (tenantId == null) {
            throw paramError("租户ID不能为空");
        }
        FormReconcileDiffPo diff = formReconcileDiffMapper.selectById(diffId);
        if (diff == null || !tenantId.equals(diff.getTenantId())) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        if (!Integer.valueOf(DIFF_STATUS_PENDING).equals(diff.getStatus())) {
            return;
        }
        if (Integer.valueOf(DIFF_TYPE_MAIN_ONLY).equals(diff.getDiffType())) {
            restorePhysical(diff);
        } else if (Integer.valueOf(DIFF_TYPE_PHYSICAL_ONLY).equals(diff.getDiffType())) {
            formDataEngine.logicDelete(diff.getPhysicalTable(), diff.getDataId(), tenantId);
        } else {
            throw paramError("未知的差异类型：" + diff.getDiffType());
        }
        diff.setStatus(DIFF_STATUS_REPAIRED);
        formReconcileDiffMapper.updateById(diff);
    }

    /**
     * 差异类型 1 修复：按主记录路由物理表登记，用 schema 快照与 {@code dataJson} 补写物理表。
     *
     * @param diff 差异记录
     * @throws BizException 主记录不存在、结构快照缺失或数据 JSON 非法时抛出 {@code PARAM_ERROR}
     */
    private void restorePhysical(FormReconcileDiffPo diff) {
        FormDataPo data = formDataMapper.selectById(diff.getDataId());
        if (data == null || !diff.getTenantId().equals(data.getTenantId())) {
            throw paramError("主表记录已不存在或不属于当前租户，无法修复差异");
        }
        FormTableRegistryPo registry = requireRegistry(routeCodeOf(data), routeVersionOf(data));
        if (!StringUtils.hasText(registry.getSchemaJson())) {
            throw paramError("物理表结构快照缺失，无法修复差异");
        }
        List<FormField> fields = FormSchemaParser.parseStorage(registry.getSchemaJson());
        Map<String, Object> dataMap = parseData(data.getDataJson());
        formDataEngine.insert(diff.getPhysicalTable(), data, fields, dataMap);
    }

    /**
     * 查询该租户下状态生效的物理表登记（租户隔离由 MyBatis-Plus 租户插件按上下文追加）
     *
     * @return 物理表登记列表
     */
    private List<FormTableRegistryPo> listEnabledRegistries() {
        LambdaQueryWrapper<FormTableRegistryPo> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FormTableRegistryPo::getStatus, REGISTRY_STATUS_ENABLED);
        wrapper.orderByAsc(FormTableRegistryPo::getId);
        return formTableRegistryMapper.selectList(wrapper);
    }

    /**
     * 查询主表中归属指定物理表登记的记录ID。
     *
     * <p>路由口径与 {@code FormDataServiceImpl#requireRegistry} 一致：优先按 {@code tableCode + tableVersion}
     * （元数据模型），无表编码时回落 {@code definitionCode + definitionVersion}（旧模型）；
     * 逻辑删除由 MyBatis-Plus 自动追加。</p>
     *
     * @param tenantId 租户ID
     * @param registry 物理表登记
     * @return 主表记录ID列表
     */
    private List<Long> listMainIds(Long tenantId, FormTableRegistryPo registry) {
        String routeCode = StringUtils.hasText(registry.getTableCode())
                ? registry.getTableCode() : registry.getDefinitionCode();
        Integer routeVersion = StringUtils.hasText(registry.getTableCode())
                ? registry.getTableVersion() : registry.getDefinitionVersion();
        if (!StringUtils.hasText(routeCode)) {
            log.warn("物理表登记缺少路由编码，跳过主表比对 registryId={} table={}",
                    registry.getId(), registry.getPhysicalTable());
            return List.of();
        }
        boolean metadataModel = StringUtils.hasText(registry.getTableCode());
        LambdaQueryWrapper<FormDataPo> wrapper = new LambdaQueryWrapper<>();
        wrapper.select(FormDataPo::getId);
        wrapper.eq(FormDataPo::getTenantId, tenantId);
        if (metadataModel) {
            wrapper.eq(FormDataPo::getTableCode, routeCode);
        } else {
            wrapper.eq(FormDataPo::getDefinitionCode, routeCode);
        }
        if (routeVersion == null) {
            if (metadataModel) {
                wrapper.isNull(FormDataPo::getTableVersion);
            } else {
                wrapper.isNull(FormDataPo::getDefinitionVersion);
            }
        } else if (metadataModel) {
            wrapper.eq(FormDataPo::getTableVersion, routeVersion);
        } else {
            wrapper.eq(FormDataPo::getDefinitionVersion, routeVersion);
        }
        return formDataMapper.selectList(wrapper).stream().map(FormDataPo::getId).toList();
    }

    /**
     * 登记一条差异（唯一键冲突视为同批次重复登记，忽略以保证幂等）
     *
     * @param batchNo 对账批次号
     * @param tenantId 租户ID
     * @param physicalTable 物理表名
     * @param dataId 表单数据ID
     * @param diffType 差异类型
     * @param detail 差异说明
     */
    private void registerDiff(String batchNo, Long tenantId, String physicalTable, Long dataId,
                              int diffType, String detail) {
        FormReconcileDiffPo entity = new FormReconcileDiffPo();
        entity.setTenantId(tenantId);
        entity.setBatchNo(batchNo);
        entity.setPhysicalTable(physicalTable);
        entity.setDataId(dataId);
        entity.setDiffType(diffType);
        entity.setStatus(DIFF_STATUS_PENDING);
        entity.setDetail(detail);
        try {
            formReconcileDiffMapper.insert(entity);
        } catch (DuplicateKeyException e) {
            log.debug("差异记录已存在，忽略重复登记 batch={} table={} dataId={}", batchNo, physicalTable, dataId);
        }
    }

    /**
     * 查询指定（路由编码 + 版本）的物理表登记：先按表维度（元数据模型）查找，
     * 未命中再按表单维度（旧模型）回落；均未命中则抛出 {@code PARAM_ERROR}
     *
     * @param code 路由编码（表编码或表单编码）
     * @param version 路由版本
     * @return 物理表登记实体
     * @throws BizException 登记不存在时抛出 {@code PARAM_ERROR}
     */
    private FormTableRegistryPo requireRegistry(String code, Integer version) {
        LambdaQueryWrapper<FormTableRegistryPo> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FormTableRegistryPo::getTableCode, code);
        wrapper.eq(FormTableRegistryPo::getTableVersion, version);
        wrapper.eq(FormTableRegistryPo::getStatus, REGISTRY_STATUS_ENABLED);
        FormTableRegistryPo registry = formTableRegistryMapper.selectOne(wrapper);
        if (registry == null) {
            LambdaQueryWrapper<FormTableRegistryPo> legacy = new LambdaQueryWrapper<>();
            legacy.eq(FormTableRegistryPo::getDefinitionCode, code);
            legacy.eq(FormTableRegistryPo::getDefinitionVersion, version);
            legacy.eq(FormTableRegistryPo::getStatus, REGISTRY_STATUS_ENABLED);
            registry = formTableRegistryMapper.selectOne(legacy);
        }
        if (registry == null) {
            throw paramError("表单未发布或版本已变更，无法修复差异");
        }
        return registry;
    }

    /**
     * 取表单数据的物理表路由编码：元数据模型用 {@code tableCode}，旧模型回落 {@code definitionCode}
     *
     * @param entity 表单数据实体
     * @return 路由编码
     */
    private static String routeCodeOf(FormDataPo entity) {
        return entity.getTableCode() == null ? entity.getDefinitionCode() : entity.getTableCode();
    }

    /**
     * 取表单数据的物理表路由版本：元数据模型用 {@code tableVersion}，旧模型回落 {@code definitionVersion}
     *
     * @param entity 表单数据实体
     * @return 路由版本
     */
    private static Integer routeVersionOf(FormDataPo entity) {
        return entity.getTableCode() == null ? entity.getDefinitionVersion() : entity.getTableVersion();
    }

    /**
     * 解析表单数据 JSON 为字段值映射
     *
     * @param dataJson 表单数据 JSON
     * @return 字段值映射
     * @throws BizException 非法 JSON 或非 JSON 对象时抛出 {@code PARAM_ERROR}
     */
    private static Map<String, Object> parseData(String dataJson) {
        try {
            return MAPPER.readValue(dataJson, new TypeReference<Map<String, Object>>() {
            });
        } catch (Exception e) {
            throw paramError("表单数据不是合法的 JSON 对象，无法修复差异");
        }
    }

    /**
     * 差异实体转视图对象
     *
     * @param entity 差异实体
     * @return 差异视图对象
     */
    private static FormReconcileDiffVO toVo(FormReconcileDiffPo entity) {
        FormReconcileDiffVO vo = new FormReconcileDiffVO();
        vo.setId(entity.getId());
        vo.setTenantId(entity.getTenantId());
        vo.setBatchNo(entity.getBatchNo());
        vo.setPhysicalTable(entity.getPhysicalTable());
        vo.setDataId(entity.getDataId());
        vo.setDiffType(entity.getDiffType());
        vo.setStatus(entity.getStatus());
        vo.setDetail(entity.getDetail());
        vo.setCreateTime(entity.getCreateTime());
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
