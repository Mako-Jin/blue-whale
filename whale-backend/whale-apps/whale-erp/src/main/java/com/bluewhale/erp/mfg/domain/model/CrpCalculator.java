package com.bluewhale.erp.mfg.domain.model;

import com.bluewhale.erp.common.ErpErrorCode;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * CRP 计算器（纯领域计算，无外部依赖）。
 *
 * <p>负荷口径：工序工时 = 准备工时 + 单件标准工时 × 待加工数量，在订单计划窗口内按工作日平均分摊，
 * 与工作中心日产能比较并标记超载。排程口径：按订单计划开工日、工序号顺序做有限前推，
 * 逐日占用剩余产能，占满顺延，用于甘特展示。</p>
 *
 * @author BlueWhale
 */
public final class CrpCalculator {

    /** 工时/负荷小数位 */
    private static final int HOUR_SCALE = 4;

    /** 利用率小数位 */
    private static final int RATE_SCALE = 2;

    /** 查询区间最大天数（含首尾） */
    private static final int MAX_RANGE_DAYS = 62;

    /** 百分比基数 */
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    /**
     * 私有构造器：工具类，禁止实例化。
     */
    private CrpCalculator() {
    }

    /**
     * 计算产能负荷与工序排程。
     *
     * @param orders  活动生产订单（状态由调用方筛选）
     * @param centers 工作中心
     * @param from    开始日期
     * @param to      结束日期
     * @return CRP 结果
     */
    public static CrpResult calculate(List<ProductionOrder> orders, List<WorkCenter> centers,
                                      LocalDate from, LocalDate to) {
        validateRange(from, to);
        Map<String, WorkCenter> centerMap = new LinkedHashMap<>();
        centers.stream()
                .sorted(Comparator.comparing(WorkCenter::getCode))
                .forEach(center -> centerMap.put(center.getCode(), center));

        Map<String, Map<LocalDate, BigDecimal>> loadMap = new HashMap<>();
        Map<String, Map<LocalDate, BigDecimal>> usedCapacity = new HashMap<>();
        List<CrpScheduleSlot> schedule = new ArrayList<>();
        BigDecimal beyond = BigDecimal.ZERO;
        int excluded = 0;

        List<ProductionOrder> sortedOrders = new ArrayList<>(orders);
        sortedOrders.sort(Comparator.comparing(ProductionOrder::getPlanStartDate,
                        Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(ProductionOrder::getOrderNo));

        for (ProductionOrder order : sortedOrders) {
            List<ProductionOperation> operations = operationsOf(order);
            if (operations.isEmpty() || missingCenter(order, centerMap)) {
                excluded++;
                continue;
            }
            if (pendingQuantity(order).signum() <= 0) {
                continue;
            }
            OrderSchedule outcome = scheduleOrder(order, operations, centerMap, loadMap, usedCapacity, from, to);
            schedule.addAll(outcome.slots);
            beyond = beyond.add(outcome.beyond);
        }

        CrpResult result = new CrpResult();
        result.setLoads(buildLoads(centerMap, loadMap, from, to));
        result.setSchedule(schedule);
        result.setExcludedOrderCount(excluded);
        result.setBeyondHorizonHours(beyond.setScale(HOUR_SCALE, RoundingMode.HALF_UP));
        return result;
    }

    /**
     * 排程单个订单的全部工序。
     *
     * @param order         订单
     * @param operations    工序
     * @param centerMap     工作中心
     * @param loadMap       无限负荷
     * @param usedCapacity  已占用产能
     * @param from          区间开始
     * @param to            区间结束
     * @return 排程结果
     */
    private static OrderSchedule scheduleOrder(ProductionOrder order, List<ProductionOperation> operations,
                                               Map<String, WorkCenter> centerMap,
                                               Map<String, Map<LocalDate, BigDecimal>> loadMap,
                                               Map<String, Map<LocalDate, BigDecimal>> usedCapacity,
                                               LocalDate from, LocalDate to) {
        OrderSchedule outcome = new OrderSchedule();
        LocalDate cursor = order.getPlanStartDate().isBefore(from) ? from : order.getPlanStartDate();
        for (ProductionOperation operation : operations) {
            BigDecimal quantity = operation.remainingQuantity();
            if (quantity.signum() <= 0) {
                continue;
            }
            WorkCenter center = centerMap.get(operation.getWorkCenterCode());
            BigDecimal hours = hoursOf(operation, quantity);
            outcome.beyond = outcome.beyond
                    .add(addInfiniteLoad(loadMap, order, operation, center, hours, from, to));
            Allocation allocation = allocate(center, hours, cursor, to, usedCapacity);
            outcome.beyond = outcome.beyond.add(allocation.remaining);
            if (allocation.start != null) {
                outcome.slots.add(slot(order, operation, quantity, hours, allocation.start, allocation.end));
                cursor = allocation.end.plusDays(1);
            } else {
                cursor = to.plusDays(1);
            }
        }
        return outcome;
    }

    /**
     * 按剩余产能逐日分配工时。
     *
     * @param center       工作中心
     * @param hours        工时
     * @param cursor       起始日
     * @param to           区间结束
     * @param usedCapacity 已占用产能
     * @return 分配结果（开始/结束日与未排程工时）
     */
    private static Allocation allocate(WorkCenter center, BigDecimal hours, LocalDate cursor, LocalDate to,
                                       Map<String, Map<LocalDate, BigDecimal>> usedCapacity) {
        LocalDate day = cursor;
        BigDecimal remainingHours = hours;
        LocalDate start = null;
        LocalDate end = null;
        while (remainingHours.signum() > 0 && !day.isAfter(to)) {
            BigDecimal capacity = center.capacityOn(day);
            if (capacity.signum() <= 0) {
                day = day.plusDays(1);
                continue;
            }
            Map<LocalDate, BigDecimal> used = usedCapacity
                    .computeIfAbsent(center.getCode(), key -> new HashMap<>());
            BigDecimal usedHours = used.getOrDefault(day, BigDecimal.ZERO);
            BigDecimal available = capacity.subtract(usedHours);
            if (available.signum() <= 0) {
                day = day.plusDays(1);
                continue;
            }
            BigDecimal consumed = available.min(remainingHours);
            used.put(day, usedHours.add(consumed));
            if (start == null) {
                start = day;
            }
            end = day;
            remainingHours = remainingHours.subtract(consumed);
            if (remainingHours.signum() > 0) {
                day = day.plusDays(1);
            }
        }
        Allocation allocation = new Allocation();
        allocation.start = start;
        allocation.end = end;
        allocation.remaining = remainingHours;
        return allocation;
    }

    /**
     * 单次产能分配结果。
     */
    private static final class Allocation {

        /** 开始日（未排程时为 null） */
        private LocalDate start;

        /** 结束日 */
        private LocalDate end;

        /** 区间内未排程工时 */
        private BigDecimal remaining = BigDecimal.ZERO;
    }

    /**
     * 单订单排程结果。
     */
    private static final class OrderSchedule {

        /** 排程条带 */
        private final List<CrpScheduleSlot> slots = new ArrayList<>();

        /** 区间外工时 */
        private BigDecimal beyond = BigDecimal.ZERO;
    }

    /**
     * 累加无限负荷，返回区间外工时。
     *
     * @param loadMap   工作中心日负荷
     * @param order     生产订单
     * @param operation 工序
     * @param center    工作中心
     * @param hours     工序工时
     * @param from      区间开始
     * @param to        区间结束
     * @return 区间外工时
     */
    private static BigDecimal addInfiniteLoad(Map<String, Map<LocalDate, BigDecimal>> loadMap,
                                              ProductionOrder order, ProductionOperation operation,
                                              WorkCenter center, BigDecimal hours,
                                              LocalDate from, LocalDate to) {
        LocalDate windowStart = order.getPlanStartDate();
        LocalDate windowEnd = order.getPlanEndDate() == null ? windowStart : order.getPlanEndDate();
        if (windowEnd.isBefore(windowStart)) {
            windowEnd = windowStart;
        }
        List<LocalDate> workDays = new ArrayList<>();
        LocalDate cursor = windowStart;
        while (!cursor.isAfter(windowEnd)) {
            if (center.worksOn(cursor) && center.capacityOn(cursor).signum() > 0) {
                workDays.add(cursor);
            }
            cursor = cursor.plusDays(1);
        }
        if (workDays.isEmpty()) {
            return hours;
        }
        List<LocalDate> included = workDays.stream()
                .filter(day -> !day.isBefore(from) && !day.isAfter(to))
                .toList();
        BigDecimal outside = BigDecimal.ZERO;
        if (!included.isEmpty()) {
            Map<LocalDate, BigDecimal> dailyLoad = loadMap
                    .computeIfAbsent(center.getCode(), key -> new HashMap<>());
            BigDecimal perDay = hours.divide(BigDecimal.valueOf(included.size()), HOUR_SCALE, RoundingMode.HALF_UP);
            BigDecimal allocated = BigDecimal.ZERO;
            for (int index = 0; index < included.size(); index++) {
                BigDecimal amount = index == included.size() - 1
                        ? hours.subtract(allocated)
                        : perDay;
                LocalDate day = included.get(index);
                dailyLoad.put(day, dailyLoad.getOrDefault(day, BigDecimal.ZERO).add(amount));
                allocated = allocated.add(amount);
            }
            long outsideDays = workDays.size() - included.size();
            if (outsideDays > 0) {
                outside = hours.multiply(BigDecimal.valueOf(outsideDays))
                        .divide(BigDecimal.valueOf(workDays.size()), HOUR_SCALE, RoundingMode.HALF_UP);
            }
        } else {
            outside = hours;
        }
        return outside;
    }

    /**
     * 生成每日负荷视图。
     *
     * @param centerMap 工作中心
     * @param loadMap   负荷
     * @param from      开始日期
     * @param to        结束日期
     * @return 日负荷列表
     */
    private static List<CrpLoadBucket> buildLoads(Map<String, WorkCenter> centerMap,
                                                  Map<String, Map<LocalDate, BigDecimal>> loadMap,
                                                  LocalDate from, LocalDate to) {
        List<CrpLoadBucket> buckets = new ArrayList<>();
        for (WorkCenter center : centerMap.values()) {
            Map<LocalDate, BigDecimal> dailyLoad = loadMap.getOrDefault(center.getCode(), Map.of());
            LocalDate day = from;
            while (!day.isAfter(to)) {
                BigDecimal capacity = center.capacityOn(day);
                BigDecimal load = dailyLoad.getOrDefault(day, BigDecimal.ZERO);
                CrpLoadBucket bucket = new CrpLoadBucket();
                bucket.setWorkCenterCode(center.getCode());
                bucket.setDate(day);
                bucket.setCapacityHours(capacity.setScale(HOUR_SCALE, RoundingMode.HALF_UP));
                bucket.setLoadHours(load.setScale(HOUR_SCALE, RoundingMode.HALF_UP));
                if (capacity.signum() == 0) {
                    bucket.setUtilizationPercent(load.signum() > 0 ? HUNDRED.setScale(RATE_SCALE) : BigDecimal.ZERO);
                } else {
                    bucket.setUtilizationPercent(load.multiply(HUNDRED)
                            .divide(capacity, RATE_SCALE, RoundingMode.HALF_UP));
                }
                bucket.setOverload(capacity.signum() == 0 ? load.signum() > 0 : load.compareTo(capacity) > 0);
                buckets.add(bucket);
                day = day.plusDays(1);
            }
        }
        return buckets;
    }

    /**
     * 生成排程条带。
     *
     * @param order     订单
     * @param operation 工序
     * @param quantity  待加工数量
     * @param hours     工时
     * @param start     开始日
     * @param end       结束日
     * @return 排程条带
     */
    private static CrpScheduleSlot slot(ProductionOrder order, ProductionOperation operation,
                                        BigDecimal quantity, BigDecimal hours,
                                        LocalDate start, LocalDate end) {
        CrpScheduleSlot slot = new CrpScheduleSlot();
        slot.setOrderNo(order.getOrderNo());
        slot.setItemCode(order.getItemCode());
        slot.setStepNo(operation.getStepNo());
        slot.setName(operation.getName());
        slot.setWorkCenterCode(operation.getWorkCenterCode());
        slot.setQuantity(quantity);
        slot.setHours(hours.setScale(HOUR_SCALE, RoundingMode.HALF_UP));
        slot.setStartDate(start);
        slot.setEndDate(end);
        slot.setStatus(operation.getStatus());
        LocalDate planEnd = order.getPlanEndDate() == null ? order.getPlanStartDate() : order.getPlanEndDate();
        slot.setDelayed(end.isAfter(planEnd));
        return slot;
    }

    /**
     * 工序工时。
     *
     * @param operation 工序
     * @param quantity  待加工数量
     * @return 工时
     */
    private static BigDecimal hoursOf(ProductionOperation operation, BigDecimal quantity) {
        BigDecimal setup = operation.getSetupHours() == null ? BigDecimal.ZERO : operation.getSetupHours();
        BigDecimal standard = operation.getStandardHours() == null ? BigDecimal.ZERO : operation.getStandardHours();
        return setup.add(standard.multiply(quantity)).setScale(HOUR_SCALE, RoundingMode.HALF_UP);
    }

    /**
     * 订单待加工数量。
     *
     * @param order 订单
     * @return 计划数量减已完工数量，不为负
     */
    private static BigDecimal pendingQuantity(ProductionOrder order) {
        BigDecimal completed = order.getCompletedQuantity() == null ? BigDecimal.ZERO : order.getCompletedQuantity();
        BigDecimal pending = order.getPlannedQuantity().subtract(completed);
        return pending.signum() < 0 ? BigDecimal.ZERO : pending;
    }

    /**
     * 工序按序号升序。
     *
     * @param order 订单
     * @return 工序列表
     */
    private static List<ProductionOperation> operationsOf(ProductionOrder order) {
        if (order.getOperations() == null) {
            return List.of();
        }
        return order.getOperations().stream()
                .sorted(Comparator.comparing(ProductionOperation::getStepNo))
                .toList();
    }

    /**
     * 判断是否存在未维护的工作中心。
     *
     * @param order     订单
     * @param centerMap 工作中心
     * @return 缺工作中心返回 true
     */
    private static boolean missingCenter(ProductionOrder order, Map<String, WorkCenter> centerMap) {
        return order.getOperations().stream()
                .anyMatch(operation -> !centerMap.containsKey(operation.getWorkCenterCode()));
    }

    /**
     * 校验查询区间。
     *
     * @param from 开始日期
     * @param to   结束日期
     */
    private static void validateRange(LocalDate from, LocalDate to) {
        if (from == null || to == null) {
            throw ErpErrorCode.exception(ErpErrorCode.CRP_INVALID, "CRP 查询日期不能为空");
        }
        if (from.isAfter(to)) {
            throw ErpErrorCode.exception(ErpErrorCode.CRP_INVALID, "CRP 开始日期不能晚于结束日期");
        }
        if (ChronoUnit.DAYS.between(from, to) + 1 > MAX_RANGE_DAYS) {
            throw ErpErrorCode.exception(ErpErrorCode.CRP_INVALID, "CRP 查询区间最多 " + MAX_RANGE_DAYS + " 天");
        }
    }
}
