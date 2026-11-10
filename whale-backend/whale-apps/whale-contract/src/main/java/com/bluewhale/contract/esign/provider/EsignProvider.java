package com.bluewhale.contract.esign.provider;

import java.util.Map;

/**
 * 电子签平台适配接口：本地 PoC 与三方实现同契约。
 *
 * <p>M3 接入方式为「适配层 + 配置切换」：{@code whale.contract.esign.provider} 指定默认渠道，
 * 回调路径按渠道名路由；新增渠道只需新增实现类并注册为 Spring Bean。</p>
 *
 * @author BlueWhale
 */
public interface EsignProvider {

    /**
     * 渠道名称（小写），与配置项及回调路径中的 {@code {provider}} 一致。
     *
     * @return 渠道名，如 local/esignbao/fadada/ssq
     */
    String name();

    /**
     * 创建签署任务。
     *
     * @param command 签署任务上下文
     * @return 三方任务号与签署链接
     */
    EsignTaskResult createTask(EsignTaskCommand command);

    /**
     * 查询签署任务状态（轮询兜底）。
     *
     * @param providerTaskId 三方任务号
     * @return 任务状态
     */
    EsignTaskStatus queryTask(String providerTaskId);

    /**
     * 下载签章件。
     *
     * @param providerTaskId 三方任务号
     * @return 签章件字节
     */
    byte[] downloadSignedFile(String providerTaskId);

    /**
     * 校验回调签名（HMAC/RSA + 时间戳窗口）。
     *
     * @param rawBody 回调原始报文
     * @param headers 请求头（大小写不敏感）
     * @return 验签通过返回 {@code true}
     */
    boolean verifyCallback(String rawBody, Map<String, String> headers);
}
