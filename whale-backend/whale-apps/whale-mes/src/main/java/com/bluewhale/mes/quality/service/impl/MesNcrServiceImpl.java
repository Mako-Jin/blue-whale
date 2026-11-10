package com.bluewhale.mes.quality.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.mes.common.support.MesCodeGenerator;
import com.bluewhale.mes.quality.dto.MesDispositionCreateRequest;
import com.bluewhale.mes.quality.dto.MesDispositionQueryRequest;
import com.bluewhale.mes.quality.dto.MesNcrCloseRequest;
import com.bluewhale.mes.quality.dto.MesNcrCreateRequest;
import com.bluewhale.mes.quality.dto.MesNcrQueryRequest;
import com.bluewhale.mes.quality.entity.MesDisposition;
import com.bluewhale.mes.quality.entity.MesInspectionDefect;
import com.bluewhale.mes.quality.entity.MesInspectionTask;
import com.bluewhale.mes.quality.entity.MesNonconformingRecord;
import com.bluewhale.mes.quality.entity.MesReworkOrder;
import com.bluewhale.mes.quality.mapper.MesDispositionMapper;
import com.bluewhale.mes.quality.mapper.MesInspectionDefectMapper;
import com.bluewhale.mes.quality.mapper.MesNonconformingRecordMapper;
import com.bluewhale.mes.quality.mapper.MesReworkOrderMapper;
import com.bluewhale.mes.quality.service.MesNcrService;
import com.bluewhale.mes.quality.vo.MesDispositionVO;
import com.bluewhale.mes.quality.vo.MesNcrVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/**
 * MES 不合格品记录与处置应用服务实现
 *
 * <p>NCR 状态机严格按 {@code OPEN → DISPOSITION → REWORK/REPAIR/SCRAP/CONCESSION → CLOSED} 推进；
 * 一个 NCR 只允许一条处置，处置数量必须等于不良数量；返工处置自动生成返工单，返工未完成不可关闭。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MesNcrServiceImpl implements MesNcrService {

    /** 单页最大条数 */
    private static final long MAX_PAGE_SIZE = 200L;

    /** 状态：打开 */
    private static final String STATUS_OPEN = "OPEN";

    /** 状态：已处置 */
    private static final String STATUS_DISPOSITION = "DISPOSITION";

    /** 状态：返工 */
    private static final String STATUS_REWORK = "REWORK";

    /** 状态：关闭 */
    private static final String STATUS_CLOSED = "CLOSED";

    /** 处置类型：返工 */
    private static final String TYPE_REWORK = "REWORK";

    /** 处置状态：已确认 */
    private static final String DISPOSITION_CONFIRMED = "CONFIRMED";

    /** 处置状态：已执行 */
    private static final String DISPOSITION_EXECUTED = "EXECUTED";

    /** 返工状态：已完工 */
    private static final String REWORK_COMPLETED = "COMPLETED";

    /** 可关闭的 NCR 状态 */
    private static final Set<String> CLOSABLE_STATUS = Set.of("REWORK", "REPAIR", "SCRAP", "CONCESSION");

    private final MesNonconformingRecordMapper ncrMapper;

    private final MesDispositionMapper dispositionMapper;

    private final MesReworkOrderMapper reworkMapper;

    private final MesInspectionDefectMapper defectMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createFromInspectionTask(MesInspectionTask task) {
        List<MesInspectionDefect> defects = defectMapper.selectList(new LambdaQueryWrapper<MesInspectionDefect>()
                .eq(MesInspectionDefect::getTaskId, task.getId()));
        BigDecimal quantity = defects.stream()
                .map(MesInspectionDefect::getQuantity)
                .filter(value -> value != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (quantity.compareTo(BigDecimal.ZERO) <= 0) {
            quantity = BigDecimal.ONE;
        }
        MesNonconformingRecord ncr = new MesNonconformingRecord();
        ncr.setNcrNo(MesCodeGenerator.nextNo("NCR"));
        ncr.setSourceType("INSPECTION");
        ncr.setInspectionTaskId(task.getId());
        ncr.setInspectionTaskNo(task.getTaskNo());
        ncr.setWorkOrderId(task.getWorkOrderId());
        ncr.setWorkOrderCode(task.getWorkOrderCode());
        ncr.setExecTaskId(task.getExecTaskId());
        ncr.setExecTaskNo(task.getExecTaskNo());
        ncr.setBatchNo(task.getBatchNo());
        ncr.setOperationCode(task.getOperationCode());
        if (!defects.isEmpty()) {
            ncr.setDefectCode(defects.get(0).getDefectCode());
            ncr.setDefectName(defects.get(0).getDefectName());
        }
        ncr.setDefectQuantity(quantity);
        ncr.setStatus(STATUS_OPEN);
        ncr.setRemark("检验任务 " + task.getTaskNo() + " 判定不合格自动生成");
        ncrMapper.insert(ncr);
        log.info("NCR 自动生成: ncrNo={}, taskNo={}, quantity={}",
                ncr.getNcrNo(), task.getTaskNo(), quantity);
        return ncr.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MesNcrVO create(MesNcrCreateRequest request) {
        BigDecimal quantity = request.getDefectQuantity();
        if (quantity == null || quantity.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BizException(ErrorCode.PARAM_ERROR, "不良数量必须大于 0");
        }
        MesNonconformingRecord ncr = new MesNonconformingRecord();
        ncr.setNcrNo(MesCodeGenerator.nextNo("NCR"));
        ncr.setSourceType(StringUtils.hasText(request.getSourceType()) ? request.getSourceType() : "MANUAL");
        ncr.setWorkOrderId(request.getWorkOrderId());
        ncr.setWorkOrderCode(request.getWorkOrderCode());
        ncr.setExecTaskId(request.getExecTaskId());
        ncr.setExecTaskNo(request.getExecTaskNo());
        ncr.setItemCode(request.getItemCode());
        ncr.setItemName(request.getItemName());
        ncr.setBatchNo(request.getBatchNo());
        ncr.setOperationCode(request.getOperationCode());
        ncr.setDefectCode(request.getDefectCode());
        ncr.setDefectName(request.getDefectName());
        ncr.setDefectQuantity(quantity);
        ncr.setStatus(STATUS_OPEN);
        ncr.setRemark(request.getRemark());
        ncrMapper.insert(ncr);
        log.info("NCR 手工创建: ncrNo={}, quantity={}", ncr.getNcrNo(), quantity);
        return toNcrVo(ncr);
    }

    @Override
    public PageResult<MesNcrVO> page(MesNcrQueryRequest request) {
        LambdaQueryWrapper<MesNonconformingRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(StringUtils.hasText(request.getNcrNo()),
                MesNonconformingRecord::getNcrNo, request.getNcrNo());
        wrapper.eq(request.getWorkOrderId() != null,
                MesNonconformingRecord::getWorkOrderId, request.getWorkOrderId());
        wrapper.eq(StringUtils.hasText(request.getBatchNo()),
                MesNonconformingRecord::getBatchNo, request.getBatchNo());
        wrapper.eq(StringUtils.hasText(request.getStatus()), MesNonconformingRecord::getStatus, request.getStatus());
        wrapper.orderByDesc(MesNonconformingRecord::getCreateTime);
        Page<MesNonconformingRecord> page = ncrMapper.selectPage(
                pageOf(request.getCurrent(), request.getSize()), wrapper);
        return PageResult.of(page.getRecords().stream().map(this::toNcrVo).toList(),
                page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Override
    public MesNcrVO get(Long id) {
        return toNcrVo(loadNcr(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MesDispositionVO createDisposition(MesDispositionCreateRequest request) {
        MesNonconformingRecord ncr = loadNcr(request.getNcrId());
        if (!STATUS_OPEN.equals(ncr.getStatus())) {
            throw new BizException(ErrorCode.PARAM_ERROR, "NCR 当前状态不可处置：" + ncr.getStatus());
        }
        if (dispositionMapper.selectCount(new LambdaQueryWrapper<MesDisposition>()
                .eq(MesDisposition::getNcrId, ncr.getId())) > 0) {
            throw new BizException(ErrorCode.PARAM_ERROR, "NCR 已存在处置记录：" + ncr.getNcrNo());
        }
        BigDecimal quantity = request.getQuantity() == null ? ncr.getDefectQuantity() : request.getQuantity();
        if (quantity.compareTo(ncr.getDefectQuantity()) != 0) {
            throw new BizException(ErrorCode.PARAM_ERROR,
                    "处置数量必须等于 NCR 不良数量：" + ncr.getDefectQuantity().toPlainString());
        }
        MesDisposition disposition = new MesDisposition();
        disposition.setDispositionNo(MesCodeGenerator.nextNo("QDP"));
        disposition.setNcrId(ncr.getId());
        disposition.setNcrNo(ncr.getNcrNo());
        disposition.setDispositionType(request.getDispositionType());
        disposition.setQuantity(quantity);
        disposition.setReason(request.getReason());
        disposition.setApprovedBy(request.getApprovedBy());
        disposition.setStatus(DISPOSITION_CONFIRMED);
        disposition.setRemark(request.getRemark());
        dispositionMapper.insert(disposition);
        ncr.setStatus(STATUS_DISPOSITION);
        updateNcr(ncr);
        log.info("NCR 处置创建: ncrNo={}, dispositionNo={}, type={}",
                ncr.getNcrNo(), disposition.getDispositionNo(), disposition.getDispositionType());
        return toDispositionVo(disposition);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MesDispositionVO executeDisposition(Long dispositionId) {
        MesDisposition disposition = dispositionMapper.selectById(dispositionId);
        if (disposition == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "处置记录不存在：" + dispositionId);
        }
        if (!DISPOSITION_CONFIRMED.equals(disposition.getStatus())) {
            throw new BizException(ErrorCode.PARAM_ERROR, "处置记录当前状态不可执行：" + disposition.getStatus());
        }
        MesNonconformingRecord ncr = loadNcr(disposition.getNcrId());
        if (!STATUS_DISPOSITION.equals(ncr.getStatus())) {
            throw new BizException(ErrorCode.PARAM_ERROR, "NCR 当前状态不可执行处置：" + ncr.getStatus());
        }
        disposition.setStatus(DISPOSITION_EXECUTED);
        disposition.setExecutedAt(LocalDateTime.now());
        if (dispositionMapper.updateById(disposition) == 0) {
            throw new BizException(ErrorCode.PARAM_ERROR, "处置记录已被其他用户修改，请刷新后重试");
        }
        ncr.setStatus(disposition.getDispositionType());
        updateNcr(ncr);
        if (TYPE_REWORK.equals(disposition.getDispositionType())) {
            createReworkOrder(ncr, disposition);
        }
        log.info("处置执行: dispositionNo={}, type={}, ncrNo={}",
                disposition.getDispositionNo(), disposition.getDispositionType(), ncr.getNcrNo());
        return toDispositionVo(disposition);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MesNcrVO close(Long id, MesNcrCloseRequest request) {
        MesNonconformingRecord ncr = loadNcr(id);
        if (!CLOSABLE_STATUS.contains(ncr.getStatus())) {
            throw new BizException(ErrorCode.PARAM_ERROR,
                    "NCR 当前状态不可关闭（需完成处置并执行）：" + ncr.getStatus());
        }
        if (!StringUtils.hasText(request.getConclusion())) {
            throw new BizException(ErrorCode.PARAM_ERROR, "关闭 NCR 必须填写结论");
        }
        if (STATUS_REWORK.equals(ncr.getStatus())) {
            assertReworkCompleted(ncr);
        }
        ncr.setStatus(STATUS_CLOSED);
        ncr.setConclusion(request.getConclusion());
        ncr.setClosedBy(request.getClosedBy());
        ncr.setClosedAt(LocalDateTime.now());
        updateNcr(ncr);
        log.info("NCR 关闭: ncrNo={}, conclusion={}", ncr.getNcrNo(), request.getConclusion());
        return toNcrVo(ncr);
    }

    @Override
    public PageResult<MesDispositionVO> pageDispositions(MesDispositionQueryRequest request) {
        LambdaQueryWrapper<MesDisposition> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(StringUtils.hasText(request.getDispositionNo()),
                MesDisposition::getDispositionNo, request.getDispositionNo());
        wrapper.eq(StringUtils.hasText(request.getNcrNo()), MesDisposition::getNcrNo, request.getNcrNo());
        wrapper.eq(StringUtils.hasText(request.getDispositionType()),
                MesDisposition::getDispositionType, request.getDispositionType());
        wrapper.eq(StringUtils.hasText(request.getStatus()), MesDisposition::getStatus, request.getStatus());
        wrapper.orderByDesc(MesDisposition::getCreateTime);
        Page<MesDisposition> page = dispositionMapper.selectPage(
                pageOf(request.getCurrent(), request.getSize()), wrapper);
        return PageResult.of(page.getRecords().stream().map(this::toDispositionVo).toList(),
                page.getTotal(), page.getCurrent(), page.getSize());
    }

    /**
     * 创建返工单
     *
     * @param ncr         NCR
     * @param disposition 处置
     */
    private void createReworkOrder(MesNonconformingRecord ncr, MesDisposition disposition) {
        MesReworkOrder rework = new MesReworkOrder();
        rework.setReworkNo(MesCodeGenerator.nextNo("RWO"));
        rework.setNcrId(ncr.getId());
        rework.setNcrNo(ncr.getNcrNo());
        rework.setDispositionId(disposition.getId());
        rework.setWorkOrderId(ncr.getWorkOrderId());
        rework.setWorkOrderCode(ncr.getWorkOrderCode());
        rework.setExecTaskId(ncr.getExecTaskId());
        rework.setExecTaskNo(ncr.getExecTaskNo());
        rework.setItemCode(ncr.getItemCode());
        rework.setItemName(ncr.getItemName());
        rework.setBatchNo(ncr.getBatchNo());
        rework.setQuantity(disposition.getQuantity());
        rework.setStatus("CREATED");
        rework.setReason(disposition.getReason());
        reworkMapper.insert(rework);
        log.info("返工单生成: reworkNo={}, ncrNo={}, quantity={}",
                rework.getReworkNo(), ncr.getNcrNo(), rework.getQuantity());
    }

    /**
     * 校验返工单是否全部完工
     *
     * @param ncr NCR
     */
    private void assertReworkCompleted(MesNonconformingRecord ncr) {
        List<MesReworkOrder> reworks = reworkMapper.selectList(new LambdaQueryWrapper<MesReworkOrder>()
                .eq(MesReworkOrder::getNcrId, ncr.getId()));
        if (reworks.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "返工处置未生成返工单，不可关闭");
        }
        boolean allCompleted = reworks.stream().allMatch(rework -> REWORK_COMPLETED.equals(rework.getStatus()));
        if (!allCompleted) {
            throw new BizException(ErrorCode.PARAM_ERROR, "返工单未完成，不可关闭 NCR");
        }
    }

    /**
     * 加载 NCR
     *
     * @param id NCR ID
     * @return NCR
     */
    private MesNonconformingRecord loadNcr(Long id) {
        MesNonconformingRecord ncr = ncrMapper.selectById(id);
        if (ncr == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "NCR 不存在：" + id);
        }
        return ncr;
    }

    /**
     * 更新 NCR 并校验乐观锁
     *
     * @param ncr NCR
     */
    private void updateNcr(MesNonconformingRecord ncr) {
        if (ncrMapper.updateById(ncr) == 0) {
            throw new BizException(ErrorCode.PARAM_ERROR, "NCR 已被其他用户修改，请刷新后重试");
        }
    }

    /**
     * 构建分页对象
     *
     * @param current 当前页
     * @param size    每页条数
     * @return 分页对象
     */
    private <T> Page<T> pageOf(Long current, Long size) {
        long pageSize = Math.min(Math.max(size == null ? 20L : size, 1L), MAX_PAGE_SIZE);
        long pageCurrent = Math.max(current == null ? 1L : current, 1L);
        return new Page<>(pageCurrent, pageSize);
    }

    /**
     * NCR 实体转视图
     *
     * @param ncr NCR
     * @return NCR 视图
     */
    private MesNcrVO toNcrVo(MesNonconformingRecord ncr) {
        MesNcrVO vo = new MesNcrVO();
        vo.setId(ncr.getId());
        vo.setNcrNo(ncr.getNcrNo());
        vo.setSourceType(ncr.getSourceType());
        vo.setInspectionTaskId(ncr.getInspectionTaskId());
        vo.setInspectionTaskNo(ncr.getInspectionTaskNo());
        vo.setReportId(ncr.getReportId());
        vo.setWorkOrderId(ncr.getWorkOrderId());
        vo.setWorkOrderCode(ncr.getWorkOrderCode());
        vo.setExecTaskId(ncr.getExecTaskId());
        vo.setExecTaskNo(ncr.getExecTaskNo());
        vo.setItemCode(ncr.getItemCode());
        vo.setItemName(ncr.getItemName());
        vo.setBatchNo(ncr.getBatchNo());
        vo.setOperationCode(ncr.getOperationCode());
        vo.setDefectCode(ncr.getDefectCode());
        vo.setDefectName(ncr.getDefectName());
        vo.setDefectQuantity(ncr.getDefectQuantity());
        vo.setStatus(ncr.getStatus());
        vo.setConclusion(ncr.getConclusion());
        vo.setClosedBy(ncr.getClosedBy());
        vo.setClosedAt(ncr.getClosedAt());
        vo.setRemark(ncr.getRemark());
        vo.setCreateTime(ncr.getCreateTime());
        return vo;
    }

    /**
     * 处置实体转视图
     *
     * @param disposition 处置
     * @return 处置视图
     */
    private MesDispositionVO toDispositionVo(MesDisposition disposition) {
        MesDispositionVO vo = new MesDispositionVO();
        vo.setId(disposition.getId());
        vo.setDispositionNo(disposition.getDispositionNo());
        vo.setNcrId(disposition.getNcrId());
        vo.setNcrNo(disposition.getNcrNo());
        vo.setDispositionType(disposition.getDispositionType());
        vo.setQuantity(disposition.getQuantity());
        vo.setReason(disposition.getReason());
        vo.setApprovedBy(disposition.getApprovedBy());
        vo.setStatus(disposition.getStatus());
        vo.setExecutedAt(disposition.getExecutedAt());
        vo.setRemark(disposition.getRemark());
        vo.setCreateTime(disposition.getCreateTime());
        return vo;
    }
}
