package com.bluewhale.crm.service.domain.repository;

import com.bluewhale.crm.service.domain.model.SlaPolicy;

import java.util.List;

/**
 * SLA 策略仓储端口。
 *
 * @author BlueWhale
 */
public interface SlaPolicyRepository {

    /**
     * 查询全部策略。
     *
     * @return 策略列表
     */
    List<SlaPolicy> findAll();

    /**
     * 按优先级查询。
     *
     * @param priority 优先级
     * @return 策略；不存在返回 null
     */
    SlaPolicy findByPriority(String priority);

    /**
     * 新增或覆盖策略。
     *
     * @param policy 策略
     */
    void upsert(SlaPolicy policy);
}
