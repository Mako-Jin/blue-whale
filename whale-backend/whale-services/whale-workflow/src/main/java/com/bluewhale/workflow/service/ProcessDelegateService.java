package com.bluewhale.workflow.service;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.workflow.dto.DelegateRuleSaveRequest;
import com.bluewhale.workflow.vo.DelegateRuleVO;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * 流程代理规则服务。
 *
 * <p>代理在任务创建时解析一次并改派，不判断代理人自身是否还有代理，
 * 从机制上禁止链式代理。</p>
 *
 * @author BlueWhale
 */
public interface ProcessDelegateService {

    /**
     * 分页查询代理规则。
     *
     * @param fromAccount 委托人账号（可选）
     * @param current 页码
     * @param size 每页条数
     * @return 分页结果
     */
    PageResult<DelegateRuleVO> page(String fromAccount, long current, long size);

    /**
     * 新建代理规则。
     *
     * @param request 保存请求
     * @return 新建后的规则
     */
    DelegateRuleVO create(DelegateRuleSaveRequest request);

    /**
     * 更新代理规则。
     *
     * @param id 规则ID
     * @param request 保存请求
     * @return 更新后的规则
     */
    DelegateRuleVO update(Long id, DelegateRuleSaveRequest request);

    /**
     * 删除代理规则（逻辑删除）。
     *
     * @param id 规则ID
     */
    void delete(Long id);

    /**
     * 解析当前生效的代理人（按流程编码 + 委托人 + 时间窗口）。
     *
     * @param definitionCode 流程编码
     * @param account 委托人账号
     * @param now 判定时间
     * @return 代理人账号；无生效规则返回空
     */
    Optional<String> resolveActive(String definitionCode, String account, LocalDateTime now);
}
