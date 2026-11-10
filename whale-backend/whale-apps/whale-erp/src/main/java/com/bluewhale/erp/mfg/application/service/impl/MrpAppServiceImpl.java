package com.bluewhale.erp.mfg.application.service.impl;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.erp.common.ErpErrorCode;
import com.bluewhale.erp.masterdata.domain.model.MasterDataStatus;
import com.bluewhale.erp.mfg.application.command.MrpRunCommand;
import com.bluewhale.erp.mfg.application.service.MrpAppService;
import com.bluewhale.erp.mfg.application.view.MrpPlannedOrderView;
import com.bluewhale.erp.mfg.application.view.MrpRunLineView;
import com.bluewhale.erp.mfg.application.view.MrpRunView;
import com.bluewhale.erp.mfg.application.view.MrpTraceView;
import com.bluewhale.erp.mfg.domain.model.MfgStatus;
import com.bluewhale.erp.mfg.domain.model.MrpPlannedOrder;
import com.bluewhale.erp.mfg.domain.model.MrpQuery;
import com.bluewhale.erp.mfg.domain.model.MrpRun;
import com.bluewhale.erp.mfg.domain.model.MrpRunLine;
import com.bluewhale.erp.mfg.domain.model.MrpTrace;
import com.bluewhale.erp.mfg.domain.repository.MrpRepository;
import com.bluewhale.erp.scm.domain.port.ItemCatalog;
import com.bluewhale.erp.scm.domain.port.ItemSnapshot;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * MRP 应用服务实现。
 *
 * @author BlueWhale
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MrpAppServiceImpl implements MrpAppService {

    /** 失败原因最大长度 */
    private static final int ERROR_MESSAGE_MAX_LENGTH = 500;

    /** 数量最大精度 */
    private static final int QUANTITY_SCALE = 4;

    private final MrpRepository mrpRepository;

    private final MrpRunExecutor mrpRunExecutor;

    private final ItemCatalog itemCatalog;

    @Override
    public MrpRunView run(MrpRunCommand command) {
        MrpRun run = createRun(command);
        mrpRepository.insertRun(run);
        try {
            mrpRunExecutor.execute(run);
        } catch (BizException ex) {
            markFailed(run, ex.getMessage());
        } catch (RuntimeException ex) {
            log.error("MRP 运算异常: runNo={}", run.getRunNo(), ex);
            markFailed(run, "系统异常：" + ex.getMessage());
        }
        return detailRun(run.getId());
    }

    @Override
    public MrpRunView rerun(Long id) {
        MrpRun original = mrpRepository.findRunById(id)
                .orElseThrow(() -> ErpErrorCode.exception(ErpErrorCode.MRP_RUN_NOT_FOUND));
        if (original.getLines() == null || original.getLines().isEmpty()) {
            throw ErpErrorCode.exception(ErpErrorCode.MRP_INVALID, "原运行没有可重跑的需求行");
        }
        MrpRunCommand command = new MrpRunCommand();
        command.setRemark("重跑 " + original.getRunNo());
        for (MrpRunLine line : original.getLines()) {
            MrpRunCommand.Line target = new MrpRunCommand.Line();
            target.setItemCode(line.getItemCode());
            target.setBomNo(line.getBomNo());
            target.setVersionNo(line.getBomVersion());
            target.setQuantity(line.getQuantity());
            target.setDueDate(line.getDueDate());
            command.getLines().add(target);
        }
        return run(command);
    }

    @Override
    public PageResult<MrpRunView> pageRuns(MrpQuery query) {
        List<MrpRun> runs = mrpRepository.pageRuns(query);
        long total = mrpRepository.countRuns(query);
        return PageResult.of(runs.stream().map(this::toRunView).toList(), total,
                query.getCurrent(), query.getSize());
    }

    @Override
    public MrpRunView detailRun(Long id) {
        MrpRun run = mrpRepository.findRunById(id)
                .orElseThrow(() -> ErpErrorCode.exception(ErpErrorCode.MRP_RUN_NOT_FOUND));
        MrpRunView view = toRunView(run);
        view.setLines(run.getLines().stream().map(this::toLineView).toList());
        view.setOrders(mrpRepository.listOrdersByRunId(id).stream().map(this::toOrderView).toList());
        return view;
    }

    @Override
    public PageResult<MrpPlannedOrderView> pageOrders(MrpQuery query) {
        List<MrpPlannedOrder> orders = mrpRepository.pageOrders(query);
        long total = mrpRepository.countOrders(query);
        return PageResult.of(orders.stream().map(this::toOrderView).toList(), total,
                query.getCurrent(), query.getSize());
    }

    @Override
    public MrpPlannedOrderView detailOrder(Long id) {
        MrpPlannedOrder order = mrpRepository.findOrderById(id)
                .orElseThrow(() -> ErpErrorCode.exception(ErpErrorCode.MRP_PLANNED_ORDER_NOT_FOUND));
        MrpPlannedOrderView view = toOrderView(order);
        view.setTraces(mrpRepository.listTracesByOrderId(id).stream().map(this::toTraceView).toList());
        return view;
    }

    /**
     * 校验命令并构建运行记录。
     *
     * @param command 运算命令
     * @return 运行记录
     */
    private MrpRun createRun(MrpRunCommand command) {
        if (command == null || command.getLines() == null || command.getLines().isEmpty()) {
            throw ErpErrorCode.exception(ErpErrorCode.MRP_INVALID, "至少需要一条需求行");
        }
        List<MrpRunLine> lines = new ArrayList<>();
        int lineNo = 1;
        for (MrpRunCommand.Line source : command.getLines()) {
            String itemCode = requireItem(source.getItemCode());
            BigDecimal quantity = source.getQuantity();
            if (quantity == null || quantity.compareTo(BigDecimal.ZERO) <= 0) {
                throw ErpErrorCode.exception(ErpErrorCode.QUANTITY_INVALID);
            }
            if (quantity.stripTrailingZeros().scale() > QUANTITY_SCALE) {
                throw ErpErrorCode.exception(ErpErrorCode.MRP_INVALID,
                        "需求数量最多保留 " + QUANTITY_SCALE + " 位小数");
            }
            if (source.getDueDate() == null) {
                throw ErpErrorCode.exception(ErpErrorCode.MRP_INVALID, "需求日期不能为空");
            }
            if (source.getBomNo() == null || source.getBomNo().isBlank()
                    || source.getVersionNo() == null || source.getVersionNo().isBlank()) {
                throw ErpErrorCode.exception(ErpErrorCode.MRP_INVALID, "BOM 编号与版本不能为空");
            }
            MrpRunLine line = new MrpRunLine();
            line.setLineNo(lineNo++);
            line.setItemCode(itemCode);
            line.setBomNo(source.getBomNo().trim());
            line.setBomVersion(source.getVersionNo().trim());
            line.setQuantity(quantity);
            line.setDueDate(source.getDueDate());
            lines.add(line);
        }
        MrpRun run = new MrpRun();
        run.setRunNo(mrpRepository.nextRunNo(LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE)));
        run.setStatus(MfgStatus.RUNNING);
        run.setDemandLineCount(lines.size());
        run.setSnapshotLineCount(0);
        run.setPlannedOrderCount(0);
        run.setRemark(command.getRemark());
        run.setStartTime(LocalDateTime.now());
        run.setLines(lines);
        return run;
    }

    /**
     * 校验需求产品物料存在且启用，返回标准编码。
     *
     * @param itemCode 物料编码
     * @return 标准物料编码
     */
    private String requireItem(String itemCode) {
        if (itemCode == null || itemCode.isBlank()) {
            throw ErpErrorCode.exception(ErpErrorCode.MRP_INVALID, "需求物料编码不能为空");
        }
        ItemSnapshot item = itemCatalog.findByCode(itemCode.trim())
                .orElseThrow(() -> ErpErrorCode.exception(ErpErrorCode.ITEM_INVALID,
                        "需求物料不存在：" + itemCode));
        if (!MasterDataStatus.ENABLED.equals(item.getStatus())) {
            throw ErpErrorCode.exception(ErpErrorCode.ITEM_INVALID, "需求物料已停用：" + itemCode);
        }
        return item.getCode();
    }

    /**
     * 标记运行失败。
     *
     * @param run 运行记录
     * @param message 失败原因
     */
    private void markFailed(MrpRun run, String message) {
        run.setStatus(MfgStatus.FAILED);
        run.setEndTime(LocalDateTime.now());
        String error = message == null || message.isBlank() ? "MRP 运算失败" : message;
        run.setErrorMessage(error.length() > ERROR_MESSAGE_MAX_LENGTH
                ? error.substring(0, ERROR_MESSAGE_MAX_LENGTH) : error);
        mrpRepository.updateRun(run);
    }

    /**
     * 运行记录转视图（不含明细）。
     *
     * @param run 运行记录
     * @return 视图
     */
    private MrpRunView toRunView(MrpRun run) {
        MrpRunView view = new MrpRunView();
        view.setId(run.getId());
        view.setRunNo(run.getRunNo());
        view.setStatus(run.getStatus());
        view.setDemandLineCount(run.getDemandLineCount());
        view.setSnapshotLineCount(run.getSnapshotLineCount());
        view.setPlannedOrderCount(run.getPlannedOrderCount());
        view.setErrorMessage(run.getErrorMessage());
        view.setRemark(run.getRemark());
        view.setStartTime(run.getStartTime());
        view.setEndTime(run.getEndTime());
        return view;
    }

    /**
     * 输入行转视图。
     *
     * @param line 输入行
     * @return 视图
     */
    private MrpRunLineView toLineView(MrpRunLine line) {
        MrpRunLineView view = new MrpRunLineView();
        view.setLineNo(line.getLineNo());
        view.setItemCode(line.getItemCode());
        view.setBomNo(line.getBomNo());
        view.setBomVersion(line.getBomVersion());
        view.setQuantity(line.getQuantity());
        view.setDueDate(line.getDueDate());
        return view;
    }

    /**
     * 计划订单转视图（不含追溯）。
     *
     * @param order 计划订单
     * @return 视图
     */
    private MrpPlannedOrderView toOrderView(MrpPlannedOrder order) {
        MrpPlannedOrderView view = new MrpPlannedOrderView();
        view.setId(order.getId());
        view.setRunId(order.getRunId());
        view.setOrderNo(order.getOrderNo());
        view.setOrderType(order.getOrderType());
        view.setItemId(order.getItemId());
        view.setItemCode(order.getItemCode());
        view.setItemName(order.getItemName());
        view.setUom(order.getUom());
        view.setGrossQuantity(order.getGrossQuantity());
        view.setAvailableQuantity(order.getAvailableQuantity());
        view.setNetQuantity(order.getNetQuantity());
        view.setItemLevel(order.getItemLevel());
        view.setBomNo(order.getBomNo());
        view.setBomVersion(order.getBomVersion());
        view.setNeedDate(order.getNeedDate());
        view.setStatus(order.getStatus());
        return view;
    }

    /**
     * 追溯行转视图。
     *
     * @param trace 追溯行
     * @return 视图
     */
    private MrpTraceView toTraceView(MrpTrace trace) {
        MrpTraceView view = new MrpTraceView();
        view.setDemandLineNo(trace.getDemandLineNo());
        view.setBomPath(trace.getBomPath());
        view.setDepth(trace.getDepth());
        view.setUnitQty(trace.getUnitQty());
        view.setLossRate(trace.getLossRate());
        view.setGrossQuantity(trace.getGrossQuantity());
        return view;
    }
}
