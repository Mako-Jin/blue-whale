package com.bluewhale.auth.application.model.query;

import java.time.LocalDateTime;

/**
 * 登录历史分页查询（权限中心扩展 M3）
 *
 * @param current 当前页码（从 1 开始）
 * @param size 每页条数
 * @param userId 用户 ID（可空表示不限）
 * @param result 结果过滤（SUCCESS / FAILURE / LOGOUT，可空表示不限）
 * @param startTime 起始时间（可空）
 * @param endTime 截止时间（可空）
 * @author BlueWhale
 */
public record LoginHistoryPageQuery(long current, long size, Long userId, String result,
                                    LocalDateTime startTime, LocalDateTime endTime) {
}
