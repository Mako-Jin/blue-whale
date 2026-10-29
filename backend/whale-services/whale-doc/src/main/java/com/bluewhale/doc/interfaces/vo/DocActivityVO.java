package com.bluewhale.doc.interfaces.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 页面动态视图。
 *
 * @author BlueWhale
 */
@Data
public class DocActivityVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 动态ID */
    private Long id;

    /** 动作 */
    private String action;

    /** 操作人ID */
    private Long actorId;

    /** 动作摘要 */
    private String detail;

    /** 发生时间 */
    private LocalDateTime createTime;
}
