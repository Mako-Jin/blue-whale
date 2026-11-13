package com.bluewhale.auth.interfaces.dto.response;

/**
 * 导入提交响应（权限中心扩展 M4）。
 *
 * @param totalRows    预检通过的行数
 * @param successCount 新建成功行数
 * @param skippedCount 同名已存在跳过行数（幂等）
 * @param failedCount  行级失败行数（并发唯一键冲突等）
 * @param tempPassword 统一临时口令（一次性展示，导入用户强制首登改密）
 * @author BlueWhale
 */
public record ImportCommitResponse(int totalRows, int successCount, int skippedCount, int failedCount,
                                   String tempPassword) {
}
