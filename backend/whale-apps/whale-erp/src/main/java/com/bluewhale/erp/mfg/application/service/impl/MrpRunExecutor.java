package com.bluewhale.erp.mfg.application.service.impl;

import com.bluewhale.erp.common.ErpErrorCode;
import com.bluewhale.erp.masterdata.domain.model.MasterDataStatus;
import com.bluewhale.erp.mfg.domain.model.BomLine;
import com.bluewhale.erp.mfg.domain.model.BomSnapshot;
import com.bluewhale.erp.mfg.domain.model.MfgStatus;
import com.bluewhale.erp.mfg.domain.model.MrpContribution;
import com.bluewhale.erp.mfg.domain.model.MrpExpansionCalculator;
import com.bluewhale.erp.mfg.domain.model.MrpPlannedOrder;
import com.bluewhale.erp.mfg.domain.model.MrpRun;
import com.bluewhale.erp.mfg.domain.model.MrpRunLine;
import com.bluewhale.erp.mfg.domain.model.MrpTrace;
import com.bluewhale.erp.mfg.domain.port.BomCatalog;
import com.bluewhale.erp.mfg.domain.port.StockAvailability;
import com.bluewhale.erp.mfg.domain.repository.MrpRepository;
import com.bluewhale.erp.scm.domain.port.ItemCatalog;
import com.bluewhale.erp.scm.domain.port.ItemSnapshot;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * MRP 运算执行器：BOM 展开、净需求、计划订单与追溯落库。
 *
 * <p>在独立事务内执行；任何校验或 PDM 拉取失败都会抛出业务异常并回滚本次写入，
 * 由应用服务把运行记录置为 FAILED。</p>
 *
 * @author BlueWhale
 */
@Service
@RequiredArgsConstructor
public class MrpRunExecutor {

    /** 数量精度 */
    private static final BigDecimal ZERO = BigDecimal.ZERO;

    /** 计划订单序号宽度 */
    private static final String ORDER_SEQUENCE_FORMAT = "%03d";

    private final BomCatalog bomCatalog;

    private final StockAvailability stockAvailability;

    private final ItemCatalog itemCatalog;

    private final MrpRepository mrpRepository;

    /**
     * 执行一次 MRP 运算。
     *
     * @param run 运行记录（含输入行）
     */
    @Transactional(rollbackFor = Exception.class)
    public void execute(MrpRun run) {
        Map<String, BomSnapshot> snapshotCache = new HashMap<>();
        Map<String, ItemSnapshot> itemCache = new HashMap<>();
        Set<String> manufacturedParts = new HashSet<>();
        Map<String, AggregatedDemand> aggregated = new LinkedHashMap<>();
        int snapshotLineCount = 0;
        for (MrpRunLine line : run.getLines()) {
            BomSnapshot snapshot = loadSnapshot(line, snapshotCache);
            if (snapshot.getProductCode() != null && !snapshot.getProductCode().equals(line.getItemCode())) {
                throw ErpErrorCode.exception(ErpErrorCode.MRP_INVALID,
                        "BOM " + line.getBomNo() + " / " + line.getBomVersion() + " 的产品编码 "
                                + snapshot.getProductCode() + " 与需求物料 " + line.getItemCode() + " 不一致");
            }
            snapshotLineCount += snapshot.getLines().size();
            for (BomLine bomLine : snapshot.getLines()) {
                if (bomLine.getParentPartNo() != null) {
                    manufacturedParts.add(bomLine.getParentPartNo());
                }
            }
            List<MrpContribution> contributions = MrpExpansionCalculator.expand(snapshot, line.getQuantity());
            for (MrpContribution contribution : contributions) {
                ItemSnapshot item = requireItem(contribution.getChildPartNo(), itemCache);
                AggregatedDemand demand = aggregated.computeIfAbsent(contribution.getChildPartNo(),
                        code -> new AggregatedDemand(item, line, contribution));
                demand.accumulate(line, contribution);
            }
        }
        List<MrpPlannedOrder> orders = new ArrayList<>();
        int sequence = 1;
        for (Map.Entry<String, AggregatedDemand> entry : aggregated.entrySet()) {
            AggregatedDemand demand = entry.getValue();
            BigDecimal available = stockAvailability.availableQuantity(demand.item.getId());
            if (available == null) {
                available = ZERO;
            }
            BigDecimal net = demand.gross.subtract(available);
            if (net.compareTo(ZERO) <= 0) {
                continue;
            }
            MrpPlannedOrder order = buildOrder(run, demand, manufacturedParts.contains(entry.getKey()),
                    available, net, sequence++);
            orders.add(order);
            demand.order = order;
        }
        mrpRepository.insertPlannedOrders(orders);
        List<MrpTrace> traces = new ArrayList<>();
        for (AggregatedDemand demand : aggregated.values()) {
            if (demand.order == null) {
                continue;
            }
            for (TraceDraft draft : demand.traces) {
                traces.add(buildTrace(run, demand.order, draft));
            }
        }
        mrpRepository.insertTraces(traces);
        run.setSnapshotLineCount(snapshotLineCount);
        run.setPlannedOrderCount(orders.size());
        run.setStatus(MfgStatus.COMPLETED);
        run.setEndTime(LocalDateTime.now());
        mrpRepository.updateRun(run);
    }

    /**
     * 加载并缓存 BOM 快照。
     *
     * @param line 需求行
     * @param cache 快照缓存
     * @return 快照
     */
    private BomSnapshot loadSnapshot(MrpRunLine line, Map<String, BomSnapshot> cache) {
        String key = line.getBomNo() + "@" + line.getBomVersion();
        BomSnapshot snapshot = cache.get(key);
        if (snapshot == null) {
            snapshot = bomCatalog.fetch(line.getBomNo(), line.getBomVersion());
            cache.put(key, snapshot);
        }
        return snapshot;
    }

    /**
     * 校验物料存在且启用。
     *
     * @param itemCode 物料编码
     * @param cache 物料缓存
     * @return 物料快照
     */
    private ItemSnapshot requireItem(String itemCode, Map<String, ItemSnapshot> cache) {
        ItemSnapshot cached = cache.get(itemCode);
        if (cached != null) {
            return cached;
        }
        ItemSnapshot item = itemCatalog.findByCode(itemCode).orElseThrow(
                () -> ErpErrorCode.exception(ErpErrorCode.ITEM_INVALID, "BOM 子件物料不存在：" + itemCode));
        if (!MasterDataStatus.ENABLED.equals(item.getStatus())) {
            throw ErpErrorCode.exception(ErpErrorCode.ITEM_INVALID, "BOM 子件物料已停用：" + itemCode);
        }
        cache.put(itemCode, item);
        return item;
    }

    /**
     * 构建计划订单。
     *
     * @param run 运行记录
     * @param demand 聚合需求
     * @param manufactured 是否为生产建议
     * @param available 可用量
     * @param net 净需求
     * @param sequence 序号
     * @return 计划订单
     */
    private MrpPlannedOrder buildOrder(MrpRun run, AggregatedDemand demand, boolean manufactured,
                                       BigDecimal available, BigDecimal net, int sequence) {
        MrpPlannedOrder order = new MrpPlannedOrder();
        order.setRunId(run.getId());
        order.setOrderNo(run.getRunNo() + "-" + String.format(ORDER_SEQUENCE_FORMAT, sequence));
        order.setOrderType(manufactured ? MfgStatus.ORDER_PRODUCTION : MfgStatus.ORDER_PURCHASE);
        order.setItemId(demand.item.getId());
        order.setItemCode(demand.item.getCode());
        order.setItemName(demand.item.getName());
        order.setUom(demand.uom);
        order.setGrossQuantity(demand.gross);
        order.setAvailableQuantity(available);
        order.setNetQuantity(net);
        order.setItemLevel(demand.minLevel);
        order.setBomNo(demand.bomNo);
        order.setBomVersion(demand.bomVersion);
        order.setNeedDate(demand.needDate);
        order.setStatus(MfgStatus.PLANNED);
        return order;
    }

    /**
     * 构建追溯行。
     *
     * @param run 运行记录
     * @param order 计划订单
     * @param draft 追溯草稿
     * @return 追溯行
     */
    private MrpTrace buildTrace(MrpRun run, MrpPlannedOrder order, TraceDraft draft) {
        MrpContribution contribution = draft.contribution;
        MrpTrace trace = new MrpTrace();
        trace.setRunId(run.getId());
        trace.setPlannedOrderId(order.getId());
        trace.setDemandLineNo(draft.demandLineNo);
        trace.setBomNo(draft.bomNo);
        trace.setBomVersion(draft.bomVersion);
        trace.setBomPath(contribution.getBomPath());
        trace.setDepth(contribution.getDepth());
        trace.setUnitQty(contribution.getUnitQty());
        trace.setLossRate(contribution.getLossRate());
        trace.setGrossQuantity(contribution.getGrossQuantity());
        return trace;
    }

    /**
     * 聚合需求：同一物料在一次运行内的毛需求、最早需求日期与最小层级。
     */
    private static final class AggregatedDemand {

        /** 物料快照 */
        private final ItemSnapshot item;

        /** 毛需求 */
        private BigDecimal gross = BigDecimal.ZERO;

        /** 最小层级 */
        private Integer minLevel;

        /** 需求日期 */
        private LocalDate needDate;

        /** 单位 */
        private String uom;

        /** BOM 编号 */
        private String bomNo;

        /** BOM 版本 */
        private String bomVersion;

        /** 追溯草稿 */
        private final List<TraceDraft> traces = new ArrayList<>();

        /** 生成的计划订单（净需求大于 0 时回填） */
        private MrpPlannedOrder order;

        /**
         * 构造聚合需求。
         *
         * @param item 物料
         * @param line 首个来源需求行
         * @param contribution 首个贡献行
         */
        private AggregatedDemand(ItemSnapshot item, MrpRunLine line, MrpContribution contribution) {
            this.item = item;
            this.uom = contribution.getUom();
            this.bomNo = line.getBomNo();
            this.bomVersion = line.getBomVersion();
        }

        /**
         * 累加一条贡献。
         *
         * @param line 需求行
         * @param contribution 贡献行
         */
        private void accumulate(MrpRunLine line, MrpContribution contribution) {
            gross = gross.add(contribution.getGrossQuantity());
            if (contribution.getDepth() != null && (minLevel == null || contribution.getDepth() < minLevel)) {
                minLevel = contribution.getDepth();
            }
            if (needDate == null || line.getDueDate().isBefore(needDate)) {
                needDate = line.getDueDate();
            }
            traces.add(new TraceDraft(line.getLineNo(), line.getBomNo(), line.getBomVersion(), contribution));
        }
    }

    /**
     * 追溯草稿：在计划订单落库前暂存来源信息。
     */
    private static final class TraceDraft {

        /** 来源需求行号 */
        private final Integer demandLineNo;

        /** BOM 编号 */
        private final String bomNo;

        /** BOM 版本 */
        private final String bomVersion;

        /** 贡献行 */
        private final MrpContribution contribution;

        /**
         * 构造追溯草稿。
         *
         * @param demandLineNo 来源需求行号
         * @param bomNo BOM 编号
         * @param bomVersion BOM 版本
         * @param contribution 贡献行
         */
        private TraceDraft(Integer demandLineNo, String bomNo, String bomVersion, MrpContribution contribution) {
            this.demandLineNo = demandLineNo;
            this.bomNo = bomNo;
            this.bomVersion = bomVersion;
            this.contribution = contribution;
        }
    }
}
