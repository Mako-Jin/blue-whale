package com.bluewhale.auth.application.model.importexport;

/**
 * 导入提交结果（权限中心扩展 M4）。
 *
 * <p>提交按每 500 行一个独立事务分批写入；同名用户跳过并计入 {@code skippedCount}（幂等）；
 * 行级并发冲突跳过并计入 {@code failedCount}，不影响其他行。</p>
 *
 * @param totalRows    预检通过的行数
 * @param successCount 新建成功行数
 * @param skippedCount 同名已存在跳过行数
 * @param failedCount  行级失败行数（并发唯一键冲突等）
 * @param tempPassword 统一临时口令（一次性展示，不落库；导入用户强制首登改密）
 * @author BlueWhale
 */
public record ImportCommitResult(int totalRows, int successCount, int skippedCount, int failedCount,
                                 String tempPassword) {
}
