package com.bluewhale.lms.training.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 签到记录实体，对应表 {@code lr_checkin}。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("lr_checkin")
public class CheckinEntity extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 培训班 ID */
    private Long classId;

    /** 场次 ID */
    private Long sessionId;

    /** 学员用户 ID */
    private Long userId;

    /** 学员用户名 */
    private String username;

    /** 签到方式 */
    private String checkinType;

    /** 签到时间 */
    private LocalDateTime checkinAt;

    /** 操作人 ID */
    private Long operatorId;

    /** 备注 */
    private String remark;
}
