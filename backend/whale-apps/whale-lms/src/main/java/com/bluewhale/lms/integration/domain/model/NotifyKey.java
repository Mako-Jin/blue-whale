package com.bluewhale.lms.integration.domain.model;

import com.bluewhale.lms.common.LmsErrorCode;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * 通知幂等键拼装。
 *
 * <p>幂等键只表达「同一件事」，不含接收人——接收人由
 * {@code lr_notify_log} 唯一键的 {@code user_id} 一起裁定。把 userId 拼进键里
 * 也能做到「一人一次」，但那样键就无法用来回答「这件事发了多少轮」，
 * 而运维排查恰恰更需要后者。</p>
 *
 * <p>键里禁止出现随机数与时间戳以外的可变因素：同一业务对象在任意时刻、
 * 任意入口重算都必须得到同一个键，否则幂等形同虚设。</p>
 *
 * @author BlueWhale
 */
public final class NotifyKey {

    /** 日期段落格式（催学按天去重） */
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyyMMdd");

    /** 私有构造：工具类不允许实例化 */
    private NotifyKey() {
    }

    /**
     * 报名成功通知键。
     *
     * @param courseId 课程 ID
     * @return 幂等键
     */
    public static String enroll(Long courseId) {
        return "ENROLL:" + required(courseId, "课程");
    }

    /**
     * 开班提醒通知键。
     *
     * @param classId 培训班 ID
     * @return 幂等键
     */
    public static String classStart(Long classId) {
        return "CLASS_START:" + required(classId, "培训班");
    }

    /**
     * 催学通知键（按天去重：同一培训班同一学员一天最多一条）。
     *
     * @param classId 培训班 ID
     * @param day 催办日期
     * @return 幂等键
     */
    public static String urge(Long classId, LocalDate day) {
        Long id = required(classId, "培训班");
        LocalDate date = day == null ? LocalDate.now() : day;
        return "AUTO_URGE:" + id + ":" + DAY.format(date);
    }

    /**
     * 发证通知键。
     *
     * @param certificateId 证书 ID
     * @return 幂等键
     */
    public static String certIssue(Long certificateId) {
        return "CERT_ISSUE:" + required(certificateId, "证书");
    }

    /**
     * 校验业务 ID 非空。
     *
     * @param value 业务 ID
     * @param label 业务名称（用于报错文案）
     * @return 原值
     */
    private static Long required(Long value, String label) {
        if (value == null) {
            throw LmsErrorCode.exception(LmsErrorCode.NOTIFY_INVALID,
                    label + " ID 不能为空，无法生成通知幂等键");
        }
        return value;
    }
}
