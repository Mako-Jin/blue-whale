package com.bluewhale.mes.trace.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.mes.trace.dto.MesBarcodeGenerateRequest;
import com.bluewhale.mes.trace.dto.MesBarcodeQueryRequest;
import com.bluewhale.mes.trace.dto.MesBarcodeRuleSaveRequest;
import com.bluewhale.mes.trace.entity.MesBarcode;
import com.bluewhale.mes.trace.entity.MesBarcodeRule;
import com.bluewhale.mes.trace.entity.MesMaterialBatch;
import com.bluewhale.mes.trace.mapper.MesBarcodeMapper;
import com.bluewhale.mes.trace.mapper.MesBarcodeRuleMapper;
import com.bluewhale.mes.trace.mapper.MesMaterialBatchMapper;
import com.bluewhale.mes.trace.service.MesBarcodeService;
import com.bluewhale.mes.trace.vo.MesBarcodeRuleVO;
import com.bluewhale.mes.trace.vo.MesBarcodeVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * MES 条码应用服务实现
 *
 * <p>条码值 = 规则前缀 + 日期 + 流水号，租户内唯一（数据库唯一索引兜底）；
 * 一个条码只绑定一个批次，批次封批后不再生成新条码。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MesBarcodeServiceImpl implements MesBarcodeService {

    /** 单页最大条数 */
    private static final long MAX_PAGE_SIZE = 200L;

    /** 状态：启用 / 正常 */
    private static final String STATUS_ENABLED = "ENABLED";

    /** 条码状态：正常 */
    private static final String BARCODE_ACTIVE = "ACTIVE";

    /** 条码状态：作废 */
    private static final String BARCODE_VOID = "VOID";

    /** 批次状态：已封批 */
    private static final String BATCH_CLOSED = "CLOSED";

    /** 绑定对象：批次 */
    private static final String OBJECT_BATCH = "BATCH";

    /** 默认流水号长度 */
    private static final int DEFAULT_SERIAL_LENGTH = 6;

    /** 日期片段格式 */
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final MesBarcodeMapper barcodeMapper;

    private final MesBarcodeRuleMapper ruleMapper;

    private final MesMaterialBatchMapper batchMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<MesBarcodeVO> generate(MesBarcodeGenerateRequest request) {
        MesBarcodeRule rule = ruleMapper.selectById(request.getRuleId());
        if (rule == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "biz.mes.trace.barcode-rule-found", "条码规则不存在：{0}", request.getRuleId());
        }
        if (!STATUS_ENABLED.equals(rule.getStatus())) {
            throw new BizException(ErrorCode.PARAM_ERROR, "biz.mes.trace.barcode-rule-disabled", "条码规则已停用：{0}", rule.getRuleCode());
        }
        MesMaterialBatch batch = batchMapper.selectById(request.getBatchId());
        if (batch == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "biz.mes.trace.batch-found", "批次不存在：{0}", request.getBatchId());
        }
        if (BATCH_CLOSED.equals(batch.getStatus())) {
            throw new BizException(ErrorCode.PARAM_ERROR, "biz.mes.trace.batch-batch-closure-cannot-generate-barcode", "批次已封批，不能生成条码：{0}",
                          batch.getBatchNo());
        }
        long existed = barcodeMapper.selectCount(new LambdaQueryWrapper<MesBarcode>()
                .eq(MesBarcode::getRuleId, rule.getId()));
        int serialLength = rule.getSerialLength() == null ? DEFAULT_SERIAL_LENGTH : rule.getSerialLength();
        String datePart = LocalDate.now().format(DATE_FORMAT);
        List<MesBarcodeVO> result = new ArrayList<>(request.getQuantity());
        for (int index = 0; index < request.getQuantity(); index++) {
            MesBarcode barcode = new MesBarcode();
            barcode.setBarcodeValue(rule.getPrefix() + datePart
                    + String.format("%0" + serialLength + "d", existed + index + 1));
            barcode.setRuleId(rule.getId());
            barcode.setBatchId(batch.getId());
            barcode.setBatchNo(batch.getBatchNo());
            barcode.setObjectType(OBJECT_BATCH);
            barcode.setItemCode(batch.getItemCode());
            barcode.setStatus(BARCODE_ACTIVE);
            barcode.setScanCount(0);
            barcode.setPrintPayload(buildPrintPayload(barcode.getBarcodeValue(), batch.getBatchNo(),
                    batch.getItemCode(), rule.getCodeType()));
            barcode.setRemark(request.getRemark());
            barcodeMapper.insert(barcode);
            result.add(toBarcodeVo(barcode, rule.getCodeType()));
        }
        log.info("条码批量生成: rule={}, batch={}, quantity={}",
                rule.getRuleCode(), batch.getBatchNo(), request.getQuantity());
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void voidBarcode(Long id) {
        MesBarcode barcode = barcodeMapper.selectById(id);
        if (barcode == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "biz.mes.trace.barcode-found", "条码不存在：{0}", id);
        }
        if (BARCODE_VOID.equals(barcode.getStatus())) {
            return;
        }
        barcode.setStatus(BARCODE_VOID);
        barcodeMapper.updateById(barcode);
    }

    @Override
    public PageResult<MesBarcodeVO> page(MesBarcodeQueryRequest request) {
        LambdaQueryWrapper<MesBarcode> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(StringUtils.hasText(request.getBarcodeValue()),
                MesBarcode::getBarcodeValue, request.getBarcodeValue());
        wrapper.eq(StringUtils.hasText(request.getBatchNo()), MesBarcode::getBatchNo, request.getBatchNo());
        wrapper.eq(StringUtils.hasText(request.getItemCode()), MesBarcode::getItemCode, request.getItemCode());
        wrapper.eq(StringUtils.hasText(request.getStatus()), MesBarcode::getStatus, request.getStatus());
        wrapper.orderByDesc(MesBarcode::getCreateTime);
        long size = Math.min(Math.max(request.getSize() == null ? 20L : request.getSize(), 1L), MAX_PAGE_SIZE);
        long current = Math.max(request.getCurrent() == null ? 1L : request.getCurrent(), 1L);
        Page<MesBarcode> page = barcodeMapper.selectPage(new Page<>(current, size), wrapper);
        List<MesBarcodeVO> records = page.getRecords().stream()
                .map(barcode -> toBarcodeVo(barcode, null))
                .toList();
        return PageResult.of(records, page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MesBarcodeRuleVO saveRule(MesBarcodeRuleSaveRequest request) {
        LambdaQueryWrapper<MesBarcodeRule> duplicate = new LambdaQueryWrapper<>();
        duplicate.eq(MesBarcodeRule::getRuleCode, request.getRuleCode());
        duplicate.ne(request.getId() != null, MesBarcodeRule::getId, request.getId());
        if (ruleMapper.selectCount(duplicate) > 0) {
            throw new BizException(ErrorCode.PARAM_ERROR, "biz.mes.trace.barcode-rule-code-already-exists", "条码规则编码已存在：{0}", request.getRuleCode());
        }
        MesBarcodeRule rule = request.getId() == null ? new MesBarcodeRule() : ruleMapper.selectById(request.getId());
        if (rule == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "biz.mes.trace.barcode-rule-found-2", "条码规则不存在：{0}", request.getId());
        }
        rule.setRuleCode(request.getRuleCode());
        rule.setRuleName(request.getRuleName());
        rule.setPrefix(request.getPrefix());
        rule.setCodeType(StringUtils.hasText(request.getCodeType()) ? request.getCodeType() : "CODE128");
        rule.setSerialLength(request.getSerialLength() == null ? DEFAULT_SERIAL_LENGTH : request.getSerialLength());
        rule.setStatus(StringUtils.hasText(request.getStatus()) ? request.getStatus() : STATUS_ENABLED);
        rule.setRemark(request.getRemark());
        if (rule.getId() == null) {
            ruleMapper.insert(rule);
        } else if (ruleMapper.updateById(rule) == 0) {
            throw new BizException(ErrorCode.PARAM_ERROR, "biz.mes.trace.barcode-rule-was-modified-refresh-retry", "条码规则已被其他用户修改，请刷新后重试");
        }
        return toRuleVo(rule);
    }

    @Override
    public PageResult<MesBarcodeRuleVO> pageRules(String ruleCode, String status, Long current, Long size) {
        LambdaQueryWrapper<MesBarcodeRule> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(StringUtils.hasText(ruleCode), MesBarcodeRule::getRuleCode, ruleCode);
        wrapper.eq(StringUtils.hasText(status), MesBarcodeRule::getStatus, status);
        wrapper.orderByDesc(MesBarcodeRule::getCreateTime);
        long pageSize = Math.min(Math.max(size == null ? 20L : size, 1L), MAX_PAGE_SIZE);
        long pageNo = Math.max(current == null ? 1L : current, 1L);
        Page<MesBarcodeRule> page = ruleMapper.selectPage(new Page<>(pageNo, pageSize), wrapper);
        List<MesBarcodeRuleVO> records = page.getRecords().stream().map(this::toRuleVo).toList();
        return PageResult.of(records, page.getTotal(), page.getCurrent(), page.getSize());
    }

    /**
     * 构建标签打印载荷（由现场标签系统消费）
     *
     * @param barcode  条码值
     * @param batchNo  批次号
     * @param itemCode 物料编码
     * @param codeType 码制
     * @return 打印载荷
     */
    private String buildPrintPayload(String barcode, String batchNo, String itemCode, String codeType) {
        return "{\"barcode\":\"" + barcode + "\",\"batchNo\":\"" + batchNo
                + "\",\"itemCode\":\"" + itemCode + "\",\"codeType\":\"" + codeType + "\"}";
    }

    /**
     * 条码实体转视图
     *
     * @param barcode  条码实体
     * @param codeType 码制（可空）
     * @return 条码视图
     */
    private MesBarcodeVO toBarcodeVo(MesBarcode barcode, String codeType) {
        MesBarcodeVO vo = new MesBarcodeVO();
        vo.setId(barcode.getId());
        vo.setBarcodeValue(barcode.getBarcodeValue());
        vo.setCodeType(codeType);
        vo.setBatchId(barcode.getBatchId());
        vo.setBatchNo(barcode.getBatchNo());
        vo.setItemCode(barcode.getItemCode());
        vo.setObjectType(barcode.getObjectType());
        vo.setStatus(barcode.getStatus());
        vo.setPrintPayload(barcode.getPrintPayload());
        vo.setLastScanTime(barcode.getLastScanTime());
        vo.setScanCount(barcode.getScanCount());
        vo.setCreateTime(barcode.getCreateTime());
        return vo;
    }

    /**
     * 条码规则实体转视图
     *
     * @param rule 规则实体
     * @return 规则视图
     */
    private MesBarcodeRuleVO toRuleVo(MesBarcodeRule rule) {
        MesBarcodeRuleVO vo = new MesBarcodeRuleVO();
        vo.setId(rule.getId());
        vo.setRuleCode(rule.getRuleCode());
        vo.setRuleName(rule.getRuleName());
        vo.setPrefix(rule.getPrefix());
        vo.setCodeType(rule.getCodeType());
        vo.setSerialLength(rule.getSerialLength());
        vo.setStatus(rule.getStatus());
        vo.setRemark(rule.getRemark());
        return vo;
    }
}
