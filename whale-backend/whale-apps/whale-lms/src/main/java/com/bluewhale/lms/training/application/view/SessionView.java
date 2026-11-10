package com.bluewhale.lms.training.application.view;

import com.bluewhale.lms.training.domain.model.ClassSession;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 培训班场次视图。
 *
 * @author BlueWhale
 */
@Data
public class SessionView {

    /** 场次 ID */
    private Long id;

    /** 场次序号 */
    private Integer sessionNo;

    /** 场次主题 */
    private String title;

    /** 开始时间 */
    private LocalDateTime startTime;

    /** 结束时间 */
    private LocalDateTime endTime;

    /** 场次地点 */
    private String location;

    /** 场次讲师 */
    private String lecturer;

    /** 本节课时 */
    private BigDecimal hours;

    /**
     * 从领域模型转换。
     *
     * @param session 场次
     * @return 视图
     */
    public static SessionView from(ClassSession session) {
        SessionView view = new SessionView();
        view.setId(session.getId());
        view.setSessionNo(session.getSessionNo());
        view.setTitle(session.getTitle());
        view.setStartTime(session.getStartTime());
        view.setEndTime(session.getEndTime());
        view.setLocation(session.getLocation());
        view.setLecturer(session.getLecturer());
        view.setHours(session.getHours());
        return view;
    }
}
