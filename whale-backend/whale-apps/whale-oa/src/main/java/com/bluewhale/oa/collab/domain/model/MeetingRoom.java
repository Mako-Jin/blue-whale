package com.bluewhale.oa.collab.domain.model;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 会议室聚合根。
 *
 * @author BlueWhale
 */
@Data
public class MeetingRoom {

    /** 主键ID */
    private Long id;

    /** 会议室名称 */
    private String name;

    /** 位置 */
    private String location;

    /** 容纳人数 */
    private Integer capacity;

    /** 状态：ENABLED / DISABLED */
    private String status;

    /** 备注 */
    private String remark;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
