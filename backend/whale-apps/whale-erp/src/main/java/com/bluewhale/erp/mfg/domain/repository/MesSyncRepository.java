package com.bluewhale.erp.mfg.domain.repository;

import com.bluewhale.erp.mfg.domain.model.MesCallbackLog;
import com.bluewhale.erp.mfg.domain.model.MesProgressSnapshot;

import java.util.List;
import java.util.Optional;

/**
 * MES 回调幂等与进度快照仓储。
 *
 * @author BlueWhale
 */
public interface MesSyncRepository {

    /**
     * 判断事件是否已处理。
     *
     * @param sourceEventId MES 事件幂等键
     * @return 已存在返回 true
     */
    boolean existsEvent(String sourceEventId);

    /**
     * 写入回调流水。
     *
     * @param log 回调流水
     */
    void insertCallback(MesCallbackLog log);

    /**
     * 按 ERP 订单号覆盖写入进度快照。
     *
     * @param snapshot 进度快照
     */
    void upsertProgress(MesProgressSnapshot snapshot);

    /**
     * 查询进度快照。
     *
     * @param erpOrderNo ERP 生产订单号
     * @return 快照
     */
    Optional<MesProgressSnapshot> findProgress(String erpOrderNo);

    /**
     * 查询某订单的回调流水（按接收时间倒序）。
     *
     * @param erpOrderNo ERP 生产订单号
     * @return 回调流水
     */
    List<MesCallbackLog> listCallbacks(String erpOrderNo);
}
