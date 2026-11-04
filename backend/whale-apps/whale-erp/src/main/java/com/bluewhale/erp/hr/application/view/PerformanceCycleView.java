package com.bluewhale.erp.hr.application.view;

import com.bluewhale.erp.hr.domain.model.Appraisal;
import com.bluewhale.erp.hr.domain.model.Kpi;
import com.bluewhale.erp.hr.domain.model.PerformanceCycle;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 绩效周期视图。
 *
 * @author BlueWhale
 */
@Data
public class PerformanceCycleView {

    /** 主键 */
    private Long id;

    /** 周期编号 */
    private String cycleNo;

    /** 周期名称 */
    private String name;

    /** 考核期间 */
    private String period;

    /** 开始日期 */
    private LocalDate startDate;

    /** 结束日期 */
    private LocalDate endDate;

    /** 状态 */
    private String status;

    /** 备注 */
    private String remark;

    /** 指标 */
    private List<KpiView> kpis;

    /** 考核结果 */
    private List<AppraisalView> appraisals;

    /**
     * 由领域对象构建视图。
     *
     * @param cycle 周期
     * @param kpis 指标
     * @param appraisals 考核结果
     * @return 视图
     */
    public static PerformanceCycleView of(PerformanceCycle cycle, List<Kpi> kpis, List<Appraisal> appraisals) {
        PerformanceCycleView view = new PerformanceCycleView();
        view.setId(cycle.getId());
        view.setCycleNo(cycle.getCycleNo());
        view.setName(cycle.getName());
        view.setPeriod(cycle.getPeriod());
        view.setStartDate(cycle.getStartDate());
        view.setEndDate(cycle.getEndDate());
        view.setStatus(cycle.getStatus());
        view.setRemark(cycle.getRemark());
        if (kpis != null) {
            view.setKpis(kpis.stream().map(KpiView::of).toList());
        }
        if (appraisals != null) {
            view.setAppraisals(appraisals.stream().map(AppraisalView::of).toList());
        }
        return view;
    }

    /**
     * 绩效指标视图。
     */
    @Data
    public static class KpiView {

        /** 主键 */
        private Long id;

        /** 指标名称 */
        private String name;

        /** 权重 */
        private BigDecimal weight;

        /** 目标值 */
        private BigDecimal targetValue;

        /** 单位 */
        private String unit;

        /** 备注 */
        private String remark;

        /**
         * 由领域对象构建视图。
         *
         * @param kpi 指标
         * @return 视图
         */
        public static KpiView of(Kpi kpi) {
            KpiView view = new KpiView();
            view.setId(kpi.getId());
            view.setName(kpi.getName());
            view.setWeight(kpi.getWeight());
            view.setTargetValue(kpi.getTargetValue());
            view.setUnit(kpi.getUnit());
            view.setRemark(kpi.getRemark());
            return view;
        }
    }

    /**
     * 考核结果视图。
     */
    @Data
    public static class AppraisalView {

        /** 主键 */
        private Long id;

        /** 指标主键 */
        private Long kpiId;

        /** 指标名称 */
        private String kpiName;

        /** 员工编号 */
        private String employeeNo;

        /** 员工姓名 */
        private String employeeName;

        /** 得分 */
        private BigDecimal score;

        /** 等级 */
        private String grade;

        /** 评语 */
        private String remark;

        /**
         * 由领域对象构建视图。
         *
         * @param appraisal 考核结果
         * @return 视图
         */
        public static AppraisalView of(Appraisal appraisal) {
            AppraisalView view = new AppraisalView();
            view.setId(appraisal.getId());
            view.setKpiId(appraisal.getKpiId());
            view.setKpiName(appraisal.getKpiName());
            view.setEmployeeNo(appraisal.getEmployeeNo());
            view.setEmployeeName(appraisal.getEmployeeName());
            view.setScore(appraisal.getScore());
            view.setGrade(appraisal.getGrade());
            view.setRemark(appraisal.getRemark());
            return view;
        }
    }
}
