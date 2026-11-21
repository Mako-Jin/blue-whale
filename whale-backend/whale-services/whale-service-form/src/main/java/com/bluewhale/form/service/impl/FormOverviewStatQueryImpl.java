package com.bluewhale.form.service.impl;

import com.bluewhale.form.mapper.FormDataMapper;
import com.bluewhale.form.mapper.FormDefinitionMapper;
import com.bluewhale.form.mapper.FormReconcileDiffMapper;
import com.bluewhale.form.po.stat.FormDaySubmitPo;
import com.bluewhale.form.po.stat.FormDefinitionStatPo;
import com.bluewhale.form.po.stat.FormTopPo;
import com.bluewhale.form.service.FormOverviewStatQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * {@link FormOverviewStatQuery} 的 MyBatis 实现。
 *
 * <p>所有查询显式带 {@code tenant_id}，时间一律写成 {@code column >= ? AND column < ?}，
 * 函数不套在条件列上——{@code DATE(create_time) = ?} 会让索引直接失效。</p>
 *
 * <p>空结果折算成 0 而不是 null：概览是给人看的，"今日提交 null 条"比 0 更像故障。</p>
 *
 * @author BlueWhale
 */
@Service
@RequiredArgsConstructor
public class FormOverviewStatQueryImpl implements FormOverviewStatQuery {

    /** 表单名缺失时的兜底展示名（定义已删除、历史遗留数据） */
    private static final String UNNAMED_FORM = "未知表单";

    private final FormDataMapper formDataMapper;

    private final FormDefinitionMapper formDefinitionMapper;

    private final FormReconcileDiffMapper formReconcileDiffMapper;

    @Override
    public DefinitionCount definitionCount(Long tenantId) {
        if (tenantId == null) {
            return new DefinitionCount(0L, 0L);
        }
        FormDefinitionStatPo row = formDefinitionMapper.countDefinitions(tenantId);
        if (row == null) {
            return new DefinitionCount(0L, 0L);
        }
        return new DefinitionCount(valueOf(row.getDefinitions()), valueOf(row.getEnabled()));
    }

    @Override
    public long submittedBetween(Long tenantId, LocalDateTime from, LocalDateTime to) {
        if (invalidWindow(tenantId, from, to)) {
            return 0L;
        }
        return formDataMapper.countSubmittedBetween(tenantId, from, to);
    }

    @Override
    public long countByBizStatus(Long tenantId, int bizStatus) {
        if (tenantId == null) {
            return 0L;
        }
        return formDataMapper.countByBizStatus(tenantId, bizStatus);
    }

    @Override
    public long staleByBizStatus(Long tenantId, int bizStatus, LocalDateTime before) {
        if (tenantId == null || before == null) {
            return 0L;
        }
        return formDataMapper.countStaleByBizStatus(tenantId, bizStatus, before);
    }

    @Override
    public long pendingReconcile(Long tenantId) {
        if (tenantId == null) {
            return 0L;
        }
        return formReconcileDiffMapper.countPending(tenantId);
    }

    @Override
    public List<DaySubmits> dailySubmits(Long tenantId, LocalDateTime from, LocalDateTime to) {
        if (invalidWindow(tenantId, from, to)) {
            return List.of();
        }
        List<FormDaySubmitPo> rows = formDataMapper.countSubmitsByDay(tenantId, from, to);
        List<DaySubmits> result = new ArrayList<>(rows.size());
        for (FormDaySubmitPo row : rows) {
            if (row.getDay() == null) {
                continue;
            }
            result.add(new DaySubmits(row.getDay(), valueOf(row.getSubmits())));
        }
        return List.copyOf(result);
    }

    @Override
    public List<FormTop> topForms(Long tenantId, LocalDateTime from, LocalDateTime to, int limit) {
        if (invalidWindow(tenantId, from, to) || limit <= 0) {
            return List.of();
        }
        List<FormTopPo> rows = formDataMapper.topForms(tenantId, from, to, limit);
        List<FormTop> result = new ArrayList<>(rows.size());
        for (FormTopPo row : rows) {
            String code = row.getDefinitionCode();
            String name = row.getFormName();
            /* 编码与名称都空是不可能的（编码非空约束），但真正不能让它变成空串：
               空字符串在柱图上等同于"没有这个类目"，那一根柱子会连标签一起消失。 */
            if ((code == null || code.isBlank()) && (name == null || name.isBlank())) {
                code = UNNAMED_FORM;
            }
            result.add(new FormTop(code, name == null || name.isBlank() ? null : name,
                    valueOf(row.getSubmits())));
        }
        return List.copyOf(result);
    }

    /**
     * 时间窗参数是否不可用
     *
     * @param tenantId 租户 ID
     * @param from     起始时刻
     * @param to       截止时刻
     * @return 任一缺失或窗口为空时返回 true
     */
    private boolean invalidWindow(Long tenantId, LocalDateTime from, LocalDateTime to) {
        return tenantId == null || from == null || to == null || !from.isBefore(to);
    }

    /**
     * Long 拆箱兜底
     *
     * @param value 可能为 null 的计数
     * @return 非空计数
     */
    private long valueOf(Long value) {
        return value == null ? 0L : value;
    }
}
