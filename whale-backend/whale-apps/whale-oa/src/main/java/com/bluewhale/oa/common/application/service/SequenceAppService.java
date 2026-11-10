package com.bluewhale.oa.common.application.service;

/**
 * 业务序列号应用服务。
 *
 * @author BlueWhale
 */
public interface SequenceAppService {

    /**
     * 生成业务单号。
     *
     * <p>格式：{@code 前缀 + yyyyMM + "-" + 三位序号}，如 {@code OA-LV-202611-001}。</p>
     *
     * @param bizType 业务类型
     * @param prefix 单号前缀
     * @return 业务单号
     */
    String nextCode(String bizType, String prefix);
}
