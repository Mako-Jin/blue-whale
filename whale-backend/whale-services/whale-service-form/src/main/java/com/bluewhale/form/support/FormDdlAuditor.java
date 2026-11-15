package com.bluewhale.form.support;

import com.bluewhale.common.context.RequestContextHolder;
import com.bluewhale.common.tenant.TenantContext;
import com.bluewhale.form.po.FormDdlAuditPo;
import com.bluewhale.form.mapper.FormDdlAuditMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 表单物理表 DDL 审计记录器。
 *
 * <p>每条实际执行的 DDL（成功或失败）写一行 {@code form_ddl_audit}，使用独立事务提交：
 * MySQL 的 DDL 会隐式提交当前事务，审计不能与发布事务同生共死，否则失败记录会被回滚掉，
 * 孤儿表与半迁移将无从追溯。</p>
 *
 * @author BlueWhale
 */
@Component
@RequiredArgsConstructor
public class FormDdlAuditor {

    /** 审计失败原因最大长度 */
    private static final int MAX_ERROR_LENGTH = 500;

    /** 成功标记 */
    private static final int SUCCESS = 1;

    /** 失败标记 */
    private static final int FAILURE = 0;

    private final FormDdlAuditMapper formDdlAuditMapper;

    private final FormMetrics formMetrics;

    /**
     * 记录一条 DDL 执行审计（独立事务，调用方负责兜底异常）
     *
     * @param definitionType 定义类型（FORM / TABLE）
     * @param definitionId 定义ID
     * @param physicalTable 物理表名
     * @param ddl 执行的 DDL 语句
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void recordSuccess(String definitionType, Long definitionId, String physicalTable, String ddl) {
        doRecord(definitionType, definitionId, physicalTable, ddl, SUCCESS, null);
    }

    /**
     * 记录一条 DDL 失败审计（独立事务，调用方负责兜底异常）
     *
     * @param definitionType 定义类型（FORM / TABLE）
     * @param definitionId 定义ID
     * @param physicalTable 物理表名
     * @param ddl 执行的 DDL 语句
     * @param errorMessage 失败原因
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void recordFailure(String definitionType, Long definitionId, String physicalTable, String ddl,
                              String errorMessage) {
        doRecord(definitionType, definitionId, physicalTable, ddl, FAILURE, errorMessage);
        formMetrics.ddlAuditFailed();
    }

    /**
     * 写入审计记录
     *
     * @param definitionType 定义类型
     * @param definitionId 定义ID
     * @param physicalTable 物理表名
     * @param ddl DDL 语句
     * @param success 成功标记
     * @param errorMessage 失败原因（成功时为 {@code null}）
     */
    private void doRecord(String definitionType, Long definitionId, String physicalTable, String ddl,
                          int success, String errorMessage) {
        FormDdlAuditPo audit = new FormDdlAuditPo();
        audit.setTenantId(TenantContext.get());
        audit.setDefinitionType(definitionType);
        audit.setDefinitionId(definitionId);
        audit.setPhysicalTable(physicalTable);
        audit.setDdlStatement(ddl);
        audit.setSuccess(success);
        audit.setErrorMessage(truncate(errorMessage));
        audit.setOperator(RequestContextHolder.getUsername());
        audit.setCreateTime(LocalDateTime.now());
        formDdlAuditMapper.insert(audit);
    }

    /**
     * 截断失败原因，避免超出列宽
     *
     * @param errorMessage 原始失败原因
     * @return 截断后的文本
     */
    private static String truncate(String errorMessage) {
        if (errorMessage == null || errorMessage.length() <= MAX_ERROR_LENGTH) {
            return errorMessage;
        }
        return errorMessage.substring(0, MAX_ERROR_LENGTH);
    }
}
