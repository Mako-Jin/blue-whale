package com.bluewhale.mes.trace.service;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.mes.trace.dto.MesBarcodeGenerateRequest;
import com.bluewhale.mes.trace.dto.MesBarcodeQueryRequest;
import com.bluewhale.mes.trace.dto.MesBarcodeRuleSaveRequest;
import com.bluewhale.mes.trace.vo.MesBarcodeRuleVO;
import com.bluewhale.mes.trace.vo.MesBarcodeVO;

import java.util.List;

/**
 * MES 条码应用服务
 *
 * <p>条码规则维护、按批次批量生成条码、条码作废与分页查询。</p>
 *
 * @author BlueWhale
 */
public interface MesBarcodeService {

    /**
     * 按规则批量生成条码并绑定批次
     *
     * @param request 生成请求
     * @return 生成的条码列表
     */
    List<MesBarcodeVO> generate(MesBarcodeGenerateRequest request);

    /**
     * 作废条码
     *
     * @param id 条码 ID
     */
    void voidBarcode(Long id);

    /**
     * 分页查询条码
     *
     * @param request 查询条件
     * @return 分页结果
     */
    PageResult<MesBarcodeVO> page(MesBarcodeQueryRequest request);

    /**
     * 保存条码规则（无 id 新增，有 id 编辑）
     *
     * @param request 规则请求
     * @return 规则视图
     */
    MesBarcodeRuleVO saveRule(MesBarcodeRuleSaveRequest request);

    /**
     * 分页查询条码规则
     *
     * @param ruleCode 规则编码（模糊）
     * @param status   状态
     * @param current  当前页
     * @param size     每页条数
     * @return 分页结果
     */
    PageResult<MesBarcodeRuleVO> pageRules(String ruleCode, String status, Long current, Long size);
}
