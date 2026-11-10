package com.bluewhale.mes.dashboard.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.mes.dashboard.service.MesDashboardService;
import com.bluewhale.mes.dashboard.vo.MesDashboardVO;
import com.bluewhale.mes.dashboard.vo.MesEquipmentBoardVO;
import com.bluewhale.mes.dashboard.vo.MesWorkforceBoardVO;
import com.bluewhale.mes.dispatch.service.MesDispatchService;
import com.bluewhale.mes.dispatch.vo.MesBoardVO;
import com.bluewhale.mes.equipment.entity.MesEquipment;
import com.bluewhale.mes.equipment.entity.MesEquipmentDowntime;
import com.bluewhale.mes.equipment.entity.MesOeeSnapshot;
import com.bluewhale.mes.equipment.mapper.MesEquipmentDowntimeMapper;
import com.bluewhale.mes.equipment.mapper.MesEquipmentMapper;
import com.bluewhale.mes.equipment.mapper.MesOeeSnapshotMapper;
import com.bluewhale.mes.execution.entity.MesProductionReport;
import com.bluewhale.mes.execution.mapper.MesProductionReportMapper;
import com.bluewhale.mes.quality.service.MesInspectionService;
import com.bluewhale.mes.quality.vo.MesQualityBoardVO;
import com.bluewhale.mes.workforce.entity.MesHandover;
import com.bluewhale.mes.workforce.entity.MesPerformanceStat;
import com.bluewhale.mes.workforce.entity.MesShiftAssignment;
import com.bluewhale.mes.workforce.entity.MesWorkHour;
import com.bluewhale.mes.workforce.mapper.MesHandoverMapper;
import com.bluewhale.mes.workforce.mapper.MesPerformanceStatMapper;
import com.bluewhale.mes.workforce.mapper.MesShiftAssignmentMapper;
import com.bluewhale.mes.workforce.mapper.MesWorkHourMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.StringJoiner;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * MES 综合看板应用服务实现
 *
 * <p>生产域复用 {@link MesDispatchService#board}、质量域复用 {@link MesInspectionService#board}，
 * 保证综合看板与独立看板指标口径一致；设备域读台账当前状态与当日 OEE 快照；
 * 人员域读当日有效工时 / 当日报工 / 当日排班 / 未确认交接单与当月绩效快照。
 * 设备与人员上下文均为只读访问，不写跨上下文状态。</p>
 *
 * @author BlueWhale
 */
@Service
@RequiredArgsConstructor
public class MesDashboardServiceImpl implements MesDashboardService {

    /** 日期参数格式 */
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE;

    /** 比率均值保留位数 */
    private static final int RATE_SCALE = 4;

    private final MesDispatchService dispatchService;
    private final MesInspectionService inspectionService;
    private final MesEquipmentMapper equipmentMapper;
    private final MesEquipmentDowntimeMapper downtimeMapper;
    private final MesOeeSnapshotMapper oeeSnapshotMapper;
    private final MesProductionReportMapper productionReportMapper;
    private final MesWorkHourMapper workHourMapper;
    private final MesShiftAssignmentMapper shiftAssignmentMapper;
    private final MesHandoverMapper handoverMapper;
    private final MesPerformanceStatMapper performanceStatMapper;

    @Override
    public MesDashboardVO dashboard(Long lineId, Long teamId, String boardDate) {
        LocalDate date = parseBoardDate(boardDate);
        MesDashboardVO vo = new MesDashboardVO();
        vo.setBoardDate(date);
        vo.setLineId(lineId);
        vo.setTeamId(teamId);
        vo.setGeneratedAt(LocalDateTime.now());
        vo.setProduction(dispatchService.board(lineId, teamId));
        vo.setQuality(inspectionService.board(date.toString(), lineId));
        vo.setEquipment(buildEquipmentBoard(date));
        vo.setWorkforce(buildWorkforceBoard(date));
        return vo;
    }

    @Override
    public String exportCsv(Long lineId, Long teamId, String boardDate) {
        MesDashboardVO vo = dashboard(lineId, teamId, boardDate);
        StringJoiner csv = new StringJoiner("\n");
        csv.add("MES 综合看板");
        csv.add("统计日期," + vo.getBoardDate());
        csv.add("产线 ID," + (vo.getLineId() == null ? "" : vo.getLineId()));
        csv.add("班组 ID," + (vo.getTeamId() == null ? "" : vo.getTeamId()));
        csv.add("生成时间," + vo.getGeneratedAt());

        csv.add("");
        csv.add("【生产域】");
        appendMetricRows(csv, productionRows(vo.getProduction()));
        csv.add("");
        csv.add("【质量域】");
        appendMetricRows(csv, qualityRows(vo.getQuality()));
        csv.add("");
        csv.add("【设备域】");
        appendMetricRows(csv, equipmentRows(vo.getEquipment()));
        csv.add("");
        csv.add("【人员域】");
        appendMetricRows(csv, workforceRows(vo.getWorkforce()));
        return csv.toString();
    }

    /** 解析统计日期：空则当天，非法抛参数错误 */
    private LocalDate parseBoardDate(String boardDate) {
        if (!StringUtils.hasText(boardDate)) {
            return LocalDate.now();
        }
        try {
            return LocalDate.parse(boardDate, DATE_FORMAT);
        } catch (DateTimeParseException ex) {
            throw new BizException(ErrorCode.PARAM_ERROR, "统计日期格式非法，应为 yyyy-MM-dd：" + boardDate);
        }
    }

    /** 构建设备域聚合：台账状态计数 + 当日停机单数 + 当日 OEE 均值 */
    private MesEquipmentBoardVO buildEquipmentBoard(LocalDate date) {
        MesEquipmentBoardVO vo = new MesEquipmentBoardVO();
        List<MesEquipment> equipments = equipmentMapper.selectList(null);
        vo.setTotalEquipmentCount((long) equipments.size());
        vo.setEnabledEquipmentCount(equipments.stream().filter(e -> Integer.valueOf(1).equals(e.getEnabled())).count());
        vo.setStatusCounts(equipments.stream()
                .collect(Collectors.groupingBy(e -> StringUtils.hasText(e.getStatus()) ? e.getStatus() : "UNKNOWN",
                        LinkedHashMap::new, Collectors.counting())));
        vo.setFaultEquipmentCount(vo.getStatusCounts().getOrDefault("FAULT", 0L));

        LocalDateTime dayStart = date.atStartOfDay();
        LocalDateTime dayEnd = date.plusDays(1).atStartOfDay();
        vo.setTodayDowntimeCount(downtimeMapper.selectCount(new LambdaQueryWrapper<MesEquipmentDowntime>()
                .ge(MesEquipmentDowntime::getStartAt, dayStart)
                .lt(MesEquipmentDowntime::getStartAt, dayEnd)));

        List<MesOeeSnapshot> snapshots = oeeSnapshotMapper.selectList(new LambdaQueryWrapper<MesOeeSnapshot>()
                .eq(MesOeeSnapshot::getShiftDate, date));
        vo.setOeeSnapshotCount((long) snapshots.size());
        vo.setAvgAvailability(averageRate(snapshots, MesOeeSnapshot::getAvailability));
        vo.setAvgPerformance(averageRate(snapshots, MesOeeSnapshot::getPerformance));
        vo.setAvgQuality(averageRate(snapshots, MesOeeSnapshot::getQuality));
        vo.setAvgOee(averageRate(snapshots, MesOeeSnapshot::getOee));
        return vo;
    }

    /** 构建人员域聚合：当日报工 / 工时 / 排班 / 交接班与当月绩效锁定进度 */
    private MesWorkforceBoardVO buildWorkforceBoard(LocalDate date) {
        MesWorkforceBoardVO vo = new MesWorkforceBoardVO();
        LocalDateTime dayStart = date.atStartOfDay();
        LocalDateTime dayEnd = date.plusDays(1).atStartOfDay();

        vo.setTodayReportCount(productionReportMapper.selectCount(new LambdaQueryWrapper<MesProductionReport>()
                .ge(MesProductionReport::getReportedAt, dayStart)
                .lt(MesProductionReport::getReportedAt, dayEnd)));

        List<MesWorkHour> workHours = workHourMapper.selectList(new LambdaQueryWrapper<MesWorkHour>()
                .eq(MesWorkHour::getWorkDate, date)
                .eq(MesWorkHour::getStatus, "VALID"));
        vo.setTodayWorkHourCount((long) workHours.size());
        vo.setTodayNetWorkSeconds(workHours.stream()
                .map(MesWorkHour::getNetSeconds)
                .filter(Objects::nonNull)
                .mapToLong(Long::longValue)
                .sum());

        vo.setActiveShiftCount(shiftAssignmentMapper.selectCount(new LambdaQueryWrapper<MesShiftAssignment>()
                .eq(MesShiftAssignment::getShiftDate, date)
                .eq(MesShiftAssignment::getStatus, "ASSIGNED")));
        vo.setDraftHandoverCount(handoverMapper.selectCount(new LambdaQueryWrapper<MesHandover>()
                .eq(MesHandover::getStatus, "DRAFT")));

        List<MesPerformanceStat> monthly = performanceStatMapper.selectList(
                new LambdaQueryWrapper<MesPerformanceStat>()
                        .eq(MesPerformanceStat::getStatLevel, "MONTHLY")
                        .eq(MesPerformanceStat::getPeriodCode,
                                date.format(DateTimeFormatter.ofPattern("yyyyMM"))));
        vo.setMonthlyStatCount((long) monthly.size());
        vo.setMonthlyLockedCount(monthly.stream().filter(s -> Integer.valueOf(1).equals(s.getLocked())).count());
        vo.setMonthlyProductionQuantity(monthly.stream()
                .map(MesPerformanceStat::getProductionQuantity)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
        vo.setMonthlyAvgEfficiency(averageRate(monthly, MesPerformanceStat::getEfficiency));
        vo.setMonthlyAvgPassRate(averageRate(monthly, MesPerformanceStat::getPassRate));
        return vo;
    }

    /** 比率均值：忽略空值，4 位 HALF_UP，空集返回 null */
    private <T> BigDecimal averageRate(List<T> items, Function<T, BigDecimal> extractor) {
        List<BigDecimal> values = items.stream()
                .map(extractor)
                .filter(Objects::nonNull)
                .toList();
        if (values.isEmpty()) {
            return null;
        }
        return values.stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(values.size()), RATE_SCALE, RoundingMode.HALF_UP);
    }

    /** 追加「指标,数值」两列行 */
    private void appendMetricRows(StringJoiner csv, List<String[]> rows) {
        csv.add("指标,数值");
        rows.forEach(row -> csv.add(escape(row[0]) + "," + escape(row[1])));
    }

    /** 生产域导出行 */
    private List<String[]> productionRows(MesBoardVO board) {
        return List.of(
                new String[]{"今日目标数量", num(board == null ? null : board.getTargetQuantity())},
                new String[]{"今日完工数量", num(board == null ? null : board.getCompletedQuantity())},
                new String[]{"今日不良数量", num(board == null ? null : board.getDefectQuantity())},
                new String[]{"达成率(%)", num(board == null ? null : board.getAchievementRate())},
                new String[]{"未关闭异常数", num(board == null ? null : board.getExceptionCount())},
                new String[]{"执行中任务数", num(board == null ? null : board.getActiveTaskCount())});
    }

    /** 质量域导出行 */
    private List<String[]> qualityRows(MesQualityBoardVO board) {
        return List.of(
                new String[]{"检验任务数", num(board == null ? null : board.getInspectionTaskCount())},
                new String[]{"待检任务数", num(board == null ? null : board.getPendingTaskCount())},
                new String[]{"不合格任务数", num(board == null ? null : board.getFailedTaskCount())},
                new String[]{"首件通过率(%)", num(board == null ? null : board.getFirstArticlePassRate())},
                new String[]{"未关闭 NCR 数", num(board == null ? null : board.getNcrOpenCount())},
                new String[]{"未完成返工单数", num(board == null ? null : board.getReworkOpenCount())},
                new String[]{"SPC 告警样本数", num(board == null ? null : board.getSpcAlertCount())});
    }

    /** 设备域导出行 */
    private List<String[]> equipmentRows(MesEquipmentBoardVO board) {
        return List.of(
                new String[]{"设备总数", num(board.getTotalEquipmentCount())},
                new String[]{"启用设备数", num(board.getEnabledEquipmentCount())},
                new String[]{"故障设备数", num(board.getFaultEquipmentCount())},
                new String[]{"当日停机单数", num(board.getTodayDowntimeCount())},
                new String[]{"当日 OEE 快照数", num(board.getOeeSnapshotCount())},
                new String[]{"平均可用率", num(board.getAvgAvailability())},
                new String[]{"平均性能", num(board.getAvgPerformance())},
                new String[]{"平均质量", num(board.getAvgQuality())},
                new String[]{"平均 OEE", num(board.getAvgOee())});
    }

    /** 人员域导出行 */
    private List<String[]> workforceRows(MesWorkforceBoardVO board) {
        return List.of(
                new String[]{"当日报工次数", num(board.getTodayReportCount())},
                new String[]{"当日有效工时记录数", num(board.getTodayWorkHourCount())},
                new String[]{"当日净工时(秒)", num(board.getTodayNetWorkSeconds())},
                new String[]{"当日有效排班数", num(board.getActiveShiftCount())},
                new String[]{"未确认交接单数", num(board.getDraftHandoverCount())},
                new String[]{"当月绩效统计条数", num(board.getMonthlyStatCount())},
                new String[]{"当月已锁定条数", num(board.getMonthlyLockedCount())},
                new String[]{"当月产量合计", num(board.getMonthlyProductionQuantity())},
                new String[]{"当月平均效率", num(board.getMonthlyAvgEfficiency())},
                new String[]{"当月平均合格率", num(board.getMonthlyAvgPassRate())});
    }

    /** 数值转字符串，空值输出空串 */
    private String num(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    /** CSV 字段转义：含逗号/引号/换行时加引号包裹 */
    private String escape(String value) {
        if (value == null) {
            return "";
        }
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
