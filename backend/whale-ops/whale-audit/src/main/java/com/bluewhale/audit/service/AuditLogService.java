package com.bluewhale.audit.service;

import com.bluewhale.audit.dto.AuditLogQueryRequest;
import com.bluewhale.audit.vo.AuditLogVO;
import com.bluewhale.common.core.result.PageResult;

import java.util.List;

/**
 * 审计日志查询服务（只读）
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
}
