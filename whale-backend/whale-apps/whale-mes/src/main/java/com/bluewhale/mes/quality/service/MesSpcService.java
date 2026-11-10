package com.bluewhale.mes.quality.service;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.mes.quality.dto.MesSpcRuleQueryRequest;
import com.bluewhale.mes.quality.dto.MesSpcRuleSaveRequest;
import com.bluewhale.mes.quality.dto.MesSpcSampleQueryRequest;
import com.bluewhale.mes.quality.dto.MesSpcSampleRequest;
import com.bluewhale.mes.quality.vo.MesSpcChartPointVO;
import com.bluewhale.mes.quality.vo.MesSpcRuleVO;
import com.bluewhale.mes.quality.vo.MesSpcSampleVO;

import java.util.List;

/**
 * MES SPC 应用服务
 *
 * <p>默认 Xbar-R 控制图：样本必须关联有效检验项；子组满额后计算均值与极差并按控制限判定，
 * 命中规则在样本上留痕（{@code alertFlag} + {@code alertRule}）。</p>
 *
 * @author BlueWhale
 */
public interface MesSpcService {

    /**
     * 保存 SPC 规则
     *
     * @param request 规则请求
     * @return 规则视图
     */
    MesSpcRuleVO saveRule(MesSpcRuleSaveRequest request);

    /**
     * 分页查询 SPC 规则
     *
     * @param request 查询条件
     * @return 分页结果
     */
    PageResult<MesSpcRuleVO> pageRules(MesSpcRuleQueryRequest request);

    /**
     * 录入 SPC 样本（子组满额后自动计算并判定）
     *
     * @param request 样本请求
     * @return 样本视图
     */
    MesSpcSampleVO submitSample(MesSpcSampleRequest request);

    /**
     * 分页查询 SPC 样本
     *
     * @param request 查询条件
     * @return 分页结果
     */
    PageResult<MesSpcSampleVO> pageSamples(MesSpcSampleQueryRequest request);

    /**
     * 查询控制图表点（按子组聚合）
     *
     * @param planItemId 检验计划项 ID
     * @param ruleId     规则 ID（空则不限制）
     * @param limit      最大子组数
     * @return 图表点
     */
    List<MesSpcChartPointVO> chart(Long planItemId, Long ruleId, Integer limit);
}
