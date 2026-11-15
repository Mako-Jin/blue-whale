package com.bluewhale.form.service;

import com.bluewhale.form.model.vo.FormSchemaDriftVO;

/**
 * 物理表结构漂移检测服务（运维巡检接口，不做启动阻塞自检）。
 *
 * <p>比对「列元数据（元数据模型）或发布快照 schema（旧模型）」与
 * {@code information_schema} 中物理表的实际列，输出缺失列、多余列与类型不一致，
 * 用于发现人工改库、改实体漏改脚本、发布中途失败等导致的 schema 漂移。</p>
 *
 * @author BlueWhale
 */
public interface FormSchemaDriftService {

    /**
     * 检测单个物理表登记的结构漂移
     *
     * @param registryId 物理表登记ID
     * @return 漂移检测结果
     */
    FormSchemaDriftVO inspect(Long registryId);
}
