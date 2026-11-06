package com.bluewhale.lms.training.domain.repository;

import com.bluewhale.lms.training.domain.model.LecturerHours;

import java.util.List;

/**
 * 讲师课时台账仓储。
 *
 * @author BlueWhale
 */
public interface LecturerHoursRepository {

    /**
     * 用给定台账覆盖某场次的课时记录（先逻辑删除旧记录再写入，保证讲师变更后不残留）。
     *
     * @param sessionId 场次 ID
     * @param entries 新台账记录（可为空，表示该场次不计课时）
     */
    void replaceBySession(Long sessionId, List<LecturerHours> entries);

    /**
     * 逻辑删除某场次的课时记录。
     *
     * @param sessionId 场次 ID
     */
    void deleteBySession(Long sessionId);

    /**
     * 列出某班全部课时台账。
     *
     * @param classId 培训班 ID
     * @return 台账记录列表
     */
    List<LecturerHours> listByClass(Long classId);
}
