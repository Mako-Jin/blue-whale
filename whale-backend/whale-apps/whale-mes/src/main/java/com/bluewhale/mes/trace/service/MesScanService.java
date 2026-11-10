package com.bluewhale.mes.trace.service;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.mes.trace.dto.MesScanRuleSaveRequest;
import com.bluewhale.mes.trace.dto.MesScanValidateRequest;
import com.bluewhale.mes.trace.vo.MesScanRuleVO;
import com.bluewhale.mes.trace.vo.MesScanValidateVO;

/**
 * MES 扫码防错应用服务
 *
 * <p>扫码校验拦截错料（BLOCK 阻断 / WARN 告警），重复扫码按幂等键去重；规则可配置。</p>
 *
 * @author BlueWhale
 */
public interface MesScanService {

    /**
     * 扫码校验
     *
     * @param request 校验请求
     * @return 校验结果
     */
    MesScanValidateVO validate(MesScanValidateRequest request);

    /**
     * 保存扫码规则（无 id 新增，有 id 编辑）
     *
     * @param request 规则请求
     * @return 规则视图
     */
    MesScanRuleVO saveRule(MesScanRuleSaveRequest request);

    /**
     * 分页查询扫码规则
     *
     * @param ruleCode    规则编码（模糊）
     * @param stationCode 工位编码
     * @param status      状态
     * @param current     当前页
     * @param size        每页条数
     * @return 分页结果
     */
    PageResult<MesScanRuleVO> pageRules(String ruleCode, String stationCode, String status, Long current, Long size);
}
