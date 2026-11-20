package com.bluewhale.alert.service;

import com.bluewhale.alert.dto.AlertSilenceCreateRequest;
import com.bluewhale.alert.dto.AlertSilenceQueryRequest;
import com.bluewhale.alert.vo.AlertSilenceVO;
import com.bluewhale.common.core.result.PageResult;

/**
 * 告警静默服务。
 *
 * @author BlueWhale
 */
public interface AlertSilenceService {

    /**
     * 创建静默规则
     *
     * @param request 创建请求
     * @return 静默视图对象
     */
    AlertSilenceVO create(AlertSilenceCreateRequest request);

    /**
     * 分页查询静默规则
     *
     * @param request 查询条件
     * @return 分页结果
     */
    PageResult<AlertSilenceVO> page(AlertSilenceQueryRequest request);

    /**
     * 删除静默规则（逻辑删除）
     *
     * @param id 静默ID
     */
    void delete(Long id);

    /**
     * 判断告警是否命中当前生效的静默规则。
     *
     * @param ruleCode 规则编码
     * @param level    告警级别
     * @param content  告警内容
     * @return 命中返回 {@code true}
     */
    boolean isSilenced(String ruleCode, String level, String content);
}
