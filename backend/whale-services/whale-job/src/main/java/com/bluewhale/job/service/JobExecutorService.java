package com.bluewhale.job.service;

import com.bluewhale.job.dto.ExecutorBeatRequest;
import com.bluewhale.job.dto.ExecutorRegisterRequest;
import com.bluewhale.job.vo.JobExecutorVO;

import java.util.List;

/**
 * 执行器注册表服务。
 *
 * <p>承载「谁在线、谁支持哪些任务」这一层信息，供调度侧寻址使用。
 * 在线判定为「状态在线 + 心跳未超时」，因此实例异常退出后无需人工摘除。</p>
 *
 * @author BlueWhale
 */
public interface JobExecutorService {

    /**
     * 注册（或刷新）一个执行器实例。
     *
     * @param request 注册请求
     */
    void register(ExecutorRegisterRequest request);

    /**
     * 心跳续约。
     *
     * @param request 心跳请求
     * @return 实例存在并续约成功返回 {@code true}；返回 {@code false} 时调用方应重新注册
     */
    boolean beat(ExecutorBeatRequest request);

    /**
     * 注销一个执行器实例（优雅停机时调用）。
     *
     * @param request 注销请求
     */
    void deregister(ExecutorBeatRequest request);

    /**
     * 查询指定应用的在线实例地址（按地址升序，便于调用方稳定轮询）。
     *
     * @param tenantId 租户ID
     * @param appName  执行器应用名
     * @return 在线实例地址列表，无在线实例返回空列表
     */
    List<String> onlineAddresses(Long tenantId, String appName);

    /**
     * 查询指定应用中「支持指定任务编码」的在线实例地址。
     *
     * <p>注册表记录了每个实例上报的任务编码，据此可在派发前判定能力，
     * 避免把请求打到没有该任务的实例（灰度版本不一致时尤其重要）。</p>
     *
     * @param tenantId 租户ID
     * @param appName  执行器应用名
     * @param jobCode  任务编码，为空时等价于不过滤
     * @return 在线且支持该任务的实例地址列表
     */
    List<String> onlineAddresses(Long tenantId, String appName, String jobCode);

    /**
     * 查询真正可派发的实例地址：注册表在线实例 ∩ 服务发现实例。
     *
     * <p>两个来源都可能滞后：注册表里留着已下线容器的行（心跳未超时的窗口内），
     * 服务发现里可能有尚未注册执行器的实例。取交集后才是既活着又接了任务的一批。
     * 交集为空时回退注册表并告警——宁可多一次告警，也不要因为服务发现配置问题
     * 让全部任务直接失败。</p>
     *
     * <p>分片数与寻址都基于该方法，保证「按几个分片跑」与「实际打到几个实例」
     * 不会出现两套口径。</p>
     *
     * @param tenantId 租户ID
     * @param appName  执行器应用名
     * @param jobCode  任务编码，为空时不过滤能力
     * @return 可派发地址列表；无任何在线实例返回空列表
     */
    List<String> dispatchableAddresses(Long tenantId, String appName, String jobCode);

    /**
     * 查询指定应用的执行器详情（含离线实例，供管理端展示）。
     *
     * @param tenantId 租户ID
     * @param appName  执行器应用名，为空时查询全部
     * @return 执行器列表
     */
    List<JobExecutorVO> list(Long tenantId, String appName);

    /**
     * 物理清理心跳长期过期的执行器行。
     *
     * <p>在线判定本身不依赖清理（查询时按「状态 + 心跳」双条件过滤），本方法只解决
     * 表内残留问题：容器重建后旧地址会一直留在注册表里，管理端看着像「一堆离线实例」，
     * 且地址复用时会被唯一键挡住注册。</p>
     *
     * @return 删除行数
     */
    int purgeStale();
}
