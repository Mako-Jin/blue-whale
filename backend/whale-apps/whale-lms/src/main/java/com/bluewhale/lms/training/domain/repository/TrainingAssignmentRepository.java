package com.bluewhale.lms.training.domain.repository;

import com.bluewhale.lms.training.domain.model.TrainingAssignment;

import java.util.List;
import java.util.Optional;

/**
 * 必修分配仓储。
 *
 * @author BlueWhale
 */
public interface TrainingAssignmentRepository {

    /**
     * 按班级与学员查询分配（幂等判定用）。
     *
     * @param classId 培训班 ID
     * @param userId 学员用户 ID
     * @return 分配记录
     */
    Optional<TrainingAssignment> findByClassAndUser(Long classId, Long userId);

    /**
     * 保存分配记录。
     *
     * @param assignment 分配记录
     * @return 主键
     */
    Long save(TrainingAssignment assignment);

    /**
     * 列出某班全部学员 ID（签到前的归属校验用）。
     *
     * @param classId 培训班 ID
     * @return 学员用户 ID 列表
     */
    List<Long> listUserIds(Long classId);

    /**
     * 列出某班全部分配记录。
     *
     * @param classId 培训班 ID
     * @return 分配记录列表
     */
    List<TrainingAssignment> listByClass(Long classId);

    /**
     * 统计某班已分配人数。
     *
     * @param classId 培训班 ID
     * @return 人数
     */
    long countByClass(Long classId);
}
