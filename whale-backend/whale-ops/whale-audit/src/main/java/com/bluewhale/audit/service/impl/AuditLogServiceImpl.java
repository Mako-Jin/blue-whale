package com.bluewhale.audit.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.audit.config.AuditExportProperties;
import com.bluewhale.audit.converter.AuditLogConverter;
import com.bluewhale.audit.dto.AuditLogQueryRequest;
import com.bluewhale.audit.service.AuditLogService;
import com.bluewhale.audit.support.AuditArchiveClient;
import com.bluewhale.audit.support.AuditCsvWriter;
import com.bluewhale.audit.vo.AuditLogVO;
import com.bluewhale.common.audit.entity.AuditLogRecord;
import com.bluewhale.common.audit.mapper.AuditLogMapper;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.core.result.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 审计日志查询服务实现（查询只读，不做任何写入，保持表 append-only 语义；导出仅生成 CSV 并上传归档；
 * 另提供内网摄入入口用于跨服务审计汇聚，摄入仅追加、不更新）
 *
 * @author BlueWhale
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditLogServiceImpl implements AuditLogService {

    /** 单页最大条数：防止深度分页拖垮数据库 */
    private static final long MAX_PAGE_SIZE = 200L;

    /** 单页最小条数 */
    private static final long MIN_PAGE_SIZE = 1L;

    /** 单次摄入最大条数（防刷量） */
    public static final int MAX_INGEST_SIZE = 50;

    /** 单条链路最多返回的审计记录数（批量操作/长事务的 traceId 可能关联上万条，防撑爆响应） */
    private static final int MAX_TRACE_RECORDS = 1000;

    /** 上传结果中对象存储键字段名（与 whale-file FileUploadVO 对齐） */
    private static final String FIELD_OBJECT_KEY = "objectKey";

    /** 导出结果中的行数字段（异步任务据此展示导出规模） */
    private static final String FIELD_ROWS = "rows";

    private final AuditLogMapper auditLogMapper;

    private final AuditArchiveClient archiveClient;

    private final AuditExportProperties exportProperties;

    @Override
    public PageResult<AuditLogVO> page(AuditLogQueryRequest request) {
        LambdaQueryWrapper<AuditLogRecord> wrapper = buildWrapper(request);
        wrapper.orderByDesc(AuditLogRecord::getCreateTime);
        Page<AuditLogRecord> page = new Page<>(request.getCurrent(), clampPageSize(request.getSize()));
        IPage<AuditLogRecord> result = auditLogMapper.selectPage(page, wrapper);
        List<AuditLogVO> records = result.getRecords().stream().map(AuditLogServiceImpl::toVo).toList();
        return PageResult.of(records, result.getTotal(), result.getCurrent(), result.getSize());
    }

    @Override
    public AuditLogVO getDetail(Long id) {
        AuditLogRecord record = auditLogMapper.selectById(id);
        if (record == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        return toVo(record);
    }

    @Override
    public List<AuditLogVO> listByTraceId(String traceId) {
        if (!StringUtils.hasText(traceId)) {
            return List.of();
        }
        List<AuditLogRecord> records = auditLogMapper.selectList(new LambdaQueryWrapper<AuditLogRecord>()
                .eq(AuditLogRecord::getTraceId, traceId)
                .orderByAsc(AuditLogRecord::getCreateTime)
                .last("LIMIT " + MAX_TRACE_RECORDS));
        if (records.size() >= MAX_TRACE_RECORDS) {
            log.warn("按链路查询命中上限，仅返回前 {} 条: traceId={}", MAX_TRACE_RECORDS, traceId);
        }
        return records.stream().map(AuditLogServiceImpl::toVo).toList();
    }

    @Override
    public Result<Map<String, Object>> exportCsv(AuditLogQueryRequest request) {
        List<AuditLogRecord> records = queryForExport(request);
        if (records.isEmpty()) {
            log.info("审计日志导出：条件无匹配记录，跳过上传");
            return Result.ok(Map.of());
        }
        try {
            byte[] csv = AuditCsvWriter.toCsvBytes(records);
            Map<String, Object> uploaded = archiveClient.upload(
                    csv, AuditCsvWriter.exportFileName(), exportProperties.getBucket());
            Map<String, Object> data = new HashMap<>(uploaded);
            data.put(FIELD_ROWS, records.size());
            log.info("审计日志导出完成: 行数={}, objectKey={}", records.size(), uploaded.get(FIELD_OBJECT_KEY));
            return Result.ok(data);
        } catch (Exception e) {
            // 详细原因仅记日志：响应文案不外泄内网地址、SQL 等实现细节
            log.warn("审计日志导出失败（受控失败，不外抛原始异常）: reason={}", e.getMessage(), e);
            return Result.fail(ErrorCode.SYSTEM_ERROR.getCode(), "审计日志导出失败，请稍后重试");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int ingest(List<AuditLogRecord> records) {
        if (records == null || records.isEmpty()) {
            return 0;
        }
        if (records.size() > MAX_INGEST_SIZE) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        int inserted = 0;
        for (AuditLogRecord record : records) {
            if (record == null) {
                continue;
            }
            // 主键由本库重新生成，避免与来源库雪花 ID 冲突
            record.setId(null);
            auditLogMapper.insert(record);
            inserted++;
        }
        log.info("审计日志内网摄入完成: count={}", inserted);
        return inserted;
    }

    /**
     * 按条件查询待导出的审计记录：复用查询条件，按时间升序，最多取「上限 + 1」行以判断是否超限
     *
     * @param request 查询条件
     * @return 待导出记录；超上限时已截断为上限行
     */
    private List<AuditLogRecord> queryForExport(AuditLogQueryRequest request) {
        int maxRows = exportProperties.getMaxRows();
        LambdaQueryWrapper<AuditLogRecord> wrapper = buildWrapper(request);
        wrapper.orderByAsc(AuditLogRecord::getCreateTime);
        wrapper.last("LIMIT " + (maxRows + 1L));
        List<AuditLogRecord> records = auditLogMapper.selectList(wrapper);
        if (records.size() > maxRows) {
            log.warn("审计日志导出行数超上限，仅取前 {} 行（原命中 {} 行）", maxRows, records.size());
            return records.subList(0, maxRows);
        }
        return records;
    }

    /**
     * 按查询条件构造 wrapper：等值条件 + 时间范围；username 支持模糊匹配
     * <p>条件均带「是否提供」开关，避免生成恒真/冗余条件导致索引失效。</p>
     */
    private static LambdaQueryWrapper<AuditLogRecord> buildWrapper(AuditLogQueryRequest request) {
        LambdaQueryWrapper<AuditLogRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(request.getUserId() != null, AuditLogRecord::getUserId, request.getUserId());
        wrapper.like(StringUtils.hasText(request.getUsername()), AuditLogRecord::getUsername, request.getUsername());
        wrapper.eq(StringUtils.hasText(request.getModule()), AuditLogRecord::getModule, request.getModule());
        wrapper.eq(StringUtils.hasText(request.getOperateType()),
                AuditLogRecord::getOperateType, request.getOperateType());
        wrapper.eq(request.getBizStatus() != null, AuditLogRecord::getBizStatus, request.getBizStatus());
        wrapper.eq(StringUtils.hasText(request.getTraceId()), AuditLogRecord::getTraceId, request.getTraceId());
        wrapper.ge(request.getStartTime() != null, AuditLogRecord::getCreateTime, request.getStartTime());
        wrapper.le(request.getEndTime() != null, AuditLogRecord::getCreateTime, request.getEndTime());
        return wrapper;
    }

    /** 单页条数收敛到 [1, 200] */
    private static long clampPageSize(long size) {
        return Math.min(Math.max(size, MIN_PAGE_SIZE), MAX_PAGE_SIZE);
    }

    /**
     * 审计日志实体转视图对象
     *
     * @param record 审计日志实体
     * @return 审计日志视图对象
     */
    private static AuditLogVO toVo(AuditLogRecord record) {
        return AuditLogConverter.INSTANCE.toAuditLogVo(record);
    }
}
