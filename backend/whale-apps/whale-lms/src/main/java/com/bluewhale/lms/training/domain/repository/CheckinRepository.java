package com.bluewhale.lms.training.domain.repository;

import com.bluewhale.lms.training.domain.model.Checkin;

import java.util.List;
import java.util.Optional;

/**
 * 签到仓储。
 *
 * @author BlueWhale
 */
public interface CheckinRepository {

    /**
     * 按场次与学员查询签到（幂等判定用）。
     *
     * @param sessionId 场次 ID
     * @param userId 学员用户 ID
     * @return 签到记录
     */
    Optional<Checkin> findBySessionAndUser(Long sessionId, Long userId);

    /**
     * 保存签到记录。
     *
     * @param checkin 签到记录
     * @return 主键
     */
    Long save(Checkin checkin);

    /**
     * 列出某班的签到记录（可按场次过滤）。
     *
     * @param classId 培训班 ID
     * @param sessionId 场次 ID，为空则不过滤
     * @return 签到记录列表
     */
    List<Checkin> listByClass(Long classId, Long sessionId);
}
