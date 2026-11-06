package com.bluewhale.map.service;

import com.bluewhale.map.vo.ProviderHealthVO;

/**
 * 供应商配额服务。
 *
 * @author BlueWhale
 */
public interface MapQuotaService {

    /**
     * 尝试占用一次调用配额
     *
     * @param provider 供应商名
     * @return 允许调用返回 {@code true}，超配额返回 {@code false}
     */
    boolean tryAcquire(String provider);

    /**
     * 查询某供应商今日用量
     *
     * @param provider 供应商名
     * @return 健康视图（仅填充用量相关字段）
     */
    ProviderHealthVO quotaOf(String provider);
}
