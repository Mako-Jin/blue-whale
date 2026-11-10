package com.bluewhale.doc.interfaces.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 页面分享视图（M3）。
 *
 * @author BlueWhale
 */
@Data
public class DocPageShareVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 分享记录ID */
    private Long id;

    /** 页面ID */
    private Long pageId;

    /** 分享令牌 */
    private String shareToken;

    /** 访问路径（登录内可读） */
    private String sharePath;

    /** 分享角色 */
    private String shareRole;

    /** 过期时间，null 表示不限期 */
    private LocalDateTime expiresAt;

    /** 状态：1 有效 / 0 已撤销 */
    private Integer status;

    /** 创建人ID */
    private Long createdBy;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 是否已过期 */
    private boolean expired;
}
