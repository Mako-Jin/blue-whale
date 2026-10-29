package com.bluewhale.doc.interfaces.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 空间成员视图。
 *
 * @author BlueWhale
 */
@Data
public class DocMemberVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 成员记录ID */
    private Long id;

    /** 空间ID */
    private Long spaceId;

    /** 用户ID */
    private Long userId;

    /** 空间角色 */
    private String memberRole;

    /** 加入时间 */
    private LocalDateTime createTime;
}
