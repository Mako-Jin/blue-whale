package com.bluewhale.audit.service;

import com.bluewhale.audit.dto.AuditLogQueryRequest;
import com.bluewhale.audit.vo.AuditLogVO;
import com.bluewhale.common.audit.entity.AuditLogRecord;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.core.result.Result;

import java.util.List;
import java.util.Map;

/**
 * 审计日志服务（查询 / 导出归档 + 内网摄入）
 *
 * @author BlueWhale
 */
public interface AuditLogService {

    /**
     * 分页条件查询
     *
     * @param request 查询条件（单页条数上限见实现类）
     * @return 分页结果
     */
    PageResult<AuditLogVO> page(AuditLogQueryRequest request);

    /**
     * 单条详情
     *
     * @param id 审计记录ID
     * @return 审计记录视图；不存在时抛 {@code NOT_FOUND}
     */
    AuditLogVO getDetail(Long id);

    /**
     * 按链路追踪ID聚合一次请求在各服务产生的全部审计记录
     *
     * @param traceId 链路追踪ID
     * @return 按操作时间升序排列的审计记录
     */
    List<AuditLogVO> listByTraceId(String traceId);

    /**
     * 按条件导出审计日志为 CSV 并上传 whale-file 归档（AUTH-08）。
     *
     * <p>复用 {@link AuditLogQueryRequest} 作为过滤条件；行数上限由
     * {@code whale.audit.export.max-rows} 控制，超限仅取上限行并告警。查询本身不改动 {@code audit_log}（append-only）。</p>
     *
     * <p>返回 {@code data} 字段与 whale-file 的 {@code FileUploadVO} 对齐（{@code objectKey} 等）；
     * 条件无匹配记录时不执行上传、{@code data} 为空 Map；上传失败时返回<b>受控失败</b>
     * （{@code code != 0}），绝不抛出原始网络异常。</p>
     *
     * @param request 查询条件（复用分页查询 DTO）
     * @return 上传结果响应；无数据时 {@code data} 为空 Map；上传失败时 {@code code} 非 0
     */
    Result<Map<String, Object>> exportCsv(AuditLogQueryRequest request);

    /**
     * 接收其他服务转发的审计记录（内网摄入，P1-3 跨库汇聚）。
     *
     * <p>记录主键一律重置为 {@code null}，由本库雪花算法重新生成，避免跨库主键冲突；
     * 单次最多 50 条，超限抛 {@code PARAM_ERROR}。</p>
     *
     * @param records 待入库的审计记录
     * @return 实际入库条数
     */
    int ingest(List<AuditLogRecord> records);
}
