package com.bluewhale.alert.service;

import com.bluewhale.alert.dto.AlertRuleQueryRequest;
import com.bluewhale.alert.dto.AlertRuleSaveRequest;
import com.bluewhale.alert.vo.AlertRuleVO;
import com.bluewhale.common.core.result.PageResult;

/**
 * 告警规则服务
 *
 * @author BlueWhale
 */
public interface AlertRuleService {

    /**
     * 分页查询告警规则
     *
     * @param request 查询条件
     * @return 分页结果
     */
    PageResult<AlertRuleVO> page(AlertRuleQueryRequest request);

    /**
     * 告警规则详情
     *
     * @param id 规则ID
     * @return 规则视图对象
     */
    AlertRuleVO getDetail(Long id);

    /**
     * 新增告警规则
     *
     * @param request 新增请求
     * @return 新增后的规则视图对象
     */
    AlertRuleVO create(AlertRuleSaveRequest request);

    /**
     * 更新告警规则
     *
     * @param id      规则ID
     * @param request 更新请求
     * @return 更新后的规则视图对象
     */
    AlertRuleVO update(Long id, AlertRuleSaveRequest request);

    /**
     * 删除告警规则（逻辑删除）
     *
     * @param id 规则ID
     */
    void delete(Long id);

    /**
     * 启停告警规则
     *
     * @param id     规则ID
     * @param status 状态：1 启用 / 0 停用
     */
    void updateStatus(Long id, Integer status);
}
