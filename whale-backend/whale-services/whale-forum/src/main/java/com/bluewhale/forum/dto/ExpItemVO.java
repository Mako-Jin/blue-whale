package com.bluewhale.forum.dto;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 论坛经验流水条目。
 *
 * @author BlueWhale
 */
@Data
public class ExpItemVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 事件 */
    private String event;

    /** 经验值 */
    private Integer amount;

    /** 关联帖子ID */
    private String refThreadId;

    /** 时间 */
    private LocalDateTime createTime;
}
