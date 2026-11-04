package com.bluewhale.oa.common.domain.repository;

/**
 * 业务序列号仓储。
 *
 * @author BlueWhale
 */
public interface SequenceRepository {

    /**
     * 原子递增并返回最新序号。
     *
     * @param bizType 业务类型
     * @param period 期间 yyyyMM
     * @return 最新序号（从 1 开始）
     */
    long nextValue(String bizType, String period);
}
