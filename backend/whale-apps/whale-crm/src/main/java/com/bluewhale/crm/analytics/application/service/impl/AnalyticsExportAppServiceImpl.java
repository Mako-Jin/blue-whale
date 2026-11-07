package com.bluewhale.crm.analytics.application.service.impl;

import com.bluewhale.crm.analytics.application.service.AnalyticsExportAppService;
import com.bluewhale.crm.analytics.domain.model.AnalyticsExport;
import com.bluewhale.crm.analytics.domain.model.ExportStatus;
import com.bluewhale.crm.analytics.domain.model.ExportTable;
import com.bluewhale.crm.analytics.domain.model.ExportType;
import com.bluewhale.crm.analytics.domain.port.AnalyticsSourcePort;
import com.bluewhale.crm.analytics.domain.repository.AnalyticsExportRepository;
import com.bluewhale.crm.common.CrmActor;
import com.bluewhale.crm.common.CrmErrorCode;
import com.bluewhale.crm.common.CrmMasking;
import com.bluewhale.crm.config.CrmAnalyticsProperties;
import com.bluewhale.crm.config.CrmProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 分析导出应用服务实现。
 *
 * <p>同步生成 UTF-8 CSV（可选 BOM），手机号与邮箱无条件脱敏；超过
 * {@code whale.crm.analytics.export-max-rows} 直接拒绝。CSV 内容暂时落库，
 * 后续接入 whale-file 后改为文件 ID。</p>
 *
 * @author BlueWhale
 */
@Service
@RequiredArgsConstructor
public class AnalyticsExportAppServiceImpl implements AnalyticsExportAppService {

    /** 默认租户 */
    private static final long DEFAULT_TENANT_ID = 1L;

    /** 默认单次导出上限 */
    private static final int DEFAULT_MAX_ROWS = 5000;

    /** 导出单号前缀 */
    private static final String EXPORT_NO_PREFIX = "EXP-";

    /** CSV BOM */
    private static final String BOM = "\uFEFF";

    /** 手机号表头 */
    private static final String PHONE_HEADER = "手机号";

    /** 邮箱表头 */
    private static final String EMAIL_HEADER = "邮箱";

    private final AnalyticsExportRepository analyticsExportRepository;
    private final AnalyticsSourcePort analyticsSourcePort;
    private final CrmProperties crmProperties;
    private final CrmAnalyticsProperties analyticsProperties;

    /**
     * 创建并同步执行分析导出。
     *
     * @param type 导出类型
     * @param filtersJson 筛选条件 JSON
     * @param actor 操作人
     * @return 导出任务
     */
    @Override
    public AnalyticsExport create(ExportType type, String filtersJson, CrmActor actor) {
        assertGate();
        Long userId = actor.requireUserId();
        if (type == null) {
            throw CrmErrorCode.exception(CrmErrorCode.CUSTOMER_EXPORT_INVALID, "导出类型不能为空");
        }
        int maxRows = analyticsProperties.getExportMaxRows() > 0
                ? analyticsProperties.getExportMaxRows() : DEFAULT_MAX_ROWS;
        ExportTable table = analyticsSourcePort.exportTable(type, filtersJson, maxRows);
        if (table == null) {
            throw CrmErrorCode.exception(CrmErrorCode.EXPORT_TASK_FAILED);
        }
        if (table.isOverLimit(maxRows)) {
            throw CrmErrorCode.exception(CrmErrorCode.CUSTOMER_EXPORT_INVALID,
                    "单次导出最多 " + maxRows + " 行，请缩小筛选范围");
        }
        String prefix = EXPORT_NO_PREFIX + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE) + "-";
        long sequence = analyticsExportRepository.countByPrefix(prefix) + 1;
        AnalyticsExport export = new AnalyticsExport();
        export.setTenantId(DEFAULT_TENANT_ID);
        export.setExportNo(prefix + String.format("%04d", sequence));
        export.setExportType(type);
        export.setFiltersJson(filtersJson);
        export.setStatus(ExportStatus.RUNNING);
        export.setRowCount(0);
        export.setRequestedBy(userId);
        try {
            String csv = toCsv(table);
            export.complete(csv, table.getRowCount());
        } catch (RuntimeException exception) {
            export.fail(exception.getMessage());
            analyticsExportRepository.insert(export);
            throw CrmErrorCode.exception(CrmErrorCode.EXPORT_TASK_FAILED);
        }
        analyticsExportRepository.insert(export);
        return export;
    }

    /**
     * 查询导出内容。
     *
     * @param id 导出任务主键
     * @param actor 操作人
     * @return 导出任务
     */
    @Override
    public AnalyticsExport content(Long id, CrmActor actor) {
        assertGate();
        AnalyticsExport export = analyticsExportRepository.findById(id);
        if (export == null) {
            throw CrmErrorCode.exception(CrmErrorCode.EXPORT_TASK_NOT_FOUND);
        }
        if (!actor.allDataScope() && export.getRequestedBy() != null
                && !export.getRequestedBy().equals(actor.requireUserId())) {
            throw CrmErrorCode.exception(CrmErrorCode.EXPORT_TASK_NOT_FOUND);
        }
        if (export.getStatus() != ExportStatus.COMPLETED) {
            throw CrmErrorCode.exception(CrmErrorCode.EXPORT_TASK_FAILED);
        }
        return export;
    }

    /**
     * 构建 CSV 文本。
     *
     * @param table 导出数据
     * @return CSV 文本
     */
    private String toCsv(ExportTable table) {
        List<String> headers = table.getHeaders();
        StringBuilder builder = new StringBuilder(BOM);
        int phoneIndex = indexOf(headers, PHONE_HEADER);
        int emailIndex = indexOf(headers, EMAIL_HEADER);
        builder.append(joinRow(headers, -1, -1)).append("\r\n");
        List<List<String>> rows = table.getRows();
        if (rows != null) {
            for (List<String> row : rows) {
                builder.append(joinRow(row, phoneIndex, emailIndex)).append("\r\n");
            }
        }
        return builder.toString();
    }

    /**
     * 拼装单行 CSV，并按列位置脱敏手机号 / 邮箱。
     *
     * @param row 行数据
     * @param phoneIndex 手机号列下标
     * @param emailIndex 邮箱列下标
     * @return CSV 行
     */
    private String joinRow(List<String> row, int phoneIndex, int emailIndex) {
        if (row == null || row.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < row.size(); index++) {
            if (index > 0) {
                builder.append(',');
            }
            String value = row.get(index);
            if (index == phoneIndex) {
                value = CrmMasking.phone(value);
            } else if (index == emailIndex) {
                value = CrmMasking.email(value);
            }
            builder.append(escape(value));
        }
        return builder.toString();
    }

    /**
     * 查找表头列下标。
     *
     * @param headers 表头
     * @param name 表头名
     * @return 下标；未找到返回 -1
     */
    private int indexOf(List<String> headers, String name) {
        if (headers == null) {
            return -1;
        }
        for (int index = 0; index < headers.size(); index++) {
            if (name.equals(headers.get(index))) {
                return index;
            }
        }
        return -1;
    }

    /**
     * CSV 字段转义。
     *
     * @param value 字段值
     * @return 转义后的字段
     */
    private String escape(String value) {
        if (value == null) {
            return "";
        }
        if (value.indexOf(',') >= 0 || value.indexOf('"') >= 0
                || value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0) {
            return '"' + value.replace("\"", "\"\"") + '"';
        }
        return value;
    }

    /**
     * 门禁校验。
     */
    private void assertGate() {
        if (!crmProperties.isDemandGatePassed()) {
            throw CrmErrorCode.exception(CrmErrorCode.GATE_CLOSED);
        }
        if (!analyticsProperties.isEnabled()) {
            throw CrmErrorCode.exception(CrmErrorCode.GATE_CLOSED, "数据分析模块未启用");
        }
    }
}
