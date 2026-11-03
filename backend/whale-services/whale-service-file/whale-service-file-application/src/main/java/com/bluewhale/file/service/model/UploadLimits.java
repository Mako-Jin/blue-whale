package com.bluewhale.file.service.model;

/**
 * 一次上传请求生效的上传上限与个人配额（G1 按权限码大文件上传策略）。
 *
 * <p>由接口层按网关可信注入的 {@code X-Permission-Codes} 解析后传入应用层，
 * 应用层不再感知权限码头与服务配置；{@code null} 入参一律按全局默认值处理。</p>
 *
 * @param maxSizeBytes 单文件最大字节数（必须为正）
 * @param quotaBytes 单用户存储配额字节数；{@code <=0} 表示不限
 * @author BlueWhale
 */
public record UploadLimits(long maxSizeBytes, long quotaBytes) {

    /**
     * 判断个人配额是否不限
     *
     * @return 配额不限时返回 {@code true}
     */
    public boolean quotaUnlimited() {
        return quotaBytes <= 0;
    }

    /**
     * 取两条上限中更严格的一条：单文件上限取较小值，配额取较小值（{@code <=0} 视为不限/正数优先）。
     *
     * <p>用于分片上传 complete 阶段同时校验「初始化时登记的生效上限」与「完成请求的当前上限」，
     * 任一阶段权限被回收或降低都无法放宽上限，防止 init / complete 两阶段绕过。</p>
     *
     * @param first 第一条上限，可为 {@code null}
     * @param second 第二条上限，可为 {@code null}
     * @return 合并后的上限；两条均为 {@code null} 时返回 {@code null}
     */
    public static UploadLimits tighten(UploadLimits first, UploadLimits second) {
        if (first == null) {
            return second;
        }
        if (second == null) {
            return first;
        }
        return new UploadLimits(Math.min(first.maxSizeBytes, second.maxSizeBytes),
                stricterQuota(first.quotaBytes, second.quotaBytes));
    }

    /**
     * 取更严格的配额：不限（{@code <=0}）被正数配额替代，其余取较小值
     *
     * @param left 第一条配额
     * @param right 第二条配额
     * @return 生效配额
     */
    private static long stricterQuota(long left, long right) {
        if (left <= 0) {
            return right;
        }
        if (right <= 0) {
            return left;
        }
        return Math.min(left, right);
    }
}
