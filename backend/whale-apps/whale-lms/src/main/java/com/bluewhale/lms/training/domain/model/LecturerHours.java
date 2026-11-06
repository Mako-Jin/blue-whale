package com.bluewhale.lms.training.domain.model;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 讲师课时台账（场次 × 讲师一条）。
 *
 * <p>入账口径：每保存一个场次，就按「场次有效讲师 × 场次课时」写一条台账；
 * 因此某班级某讲师的课时合计恒等于其名下各场次课时之和（不含已删除场次）。</p>
 *
 * @author BlueWhale
 */
@Data
public class LecturerHours {

    /** 主键 */
    private Long id;

    /** 培训班 ID */
    private Long classId;

    /** 场次 ID */
    private Long sessionId;

    /** 讲师 */
    private String lecturer;

    /** 本节课时（与场次一致） */
    private BigDecimal hours;

    /** 入账时间 */
    private LocalDateTime accumulatedAt;

    /**
     * 按场次与有效讲师构造台账记录。
     *
     * @param session 场次
     * @param effectiveLecturer 有效讲师（场次优先，其次班级）
     * @return 台账记录；讲师为空时返回 null（不产生课时）
     */
    public static LecturerHours of(ClassSession session, String effectiveLecturer) {
        if (effectiveLecturer == null || effectiveLecturer.isBlank()) {
            return null;
        }
        LecturerHours hours = new LecturerHours();
        hours.setClassId(session.getClassId());
        hours.setSessionId(session.getId());
        hours.setLecturer(effectiveLecturer.trim());
        hours.setHours(session.getHours());
        hours.setAccumulatedAt(LocalDateTime.now());
        return hours;
    }
}
