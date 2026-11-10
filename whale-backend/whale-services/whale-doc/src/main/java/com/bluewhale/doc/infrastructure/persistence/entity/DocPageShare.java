package com.bluewhale.doc.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 页面登录内分享实体，对应表 {@code doc_page_share}。
 *
 * <p>分享不做匿名公开：访问者必须已登录，且仍需通过页面读取权限校验。</p>
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("doc_page_share")
public class DocPageShare extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 页面ID */
    private Long pageId;

    /** 分享令牌 */
    private String shareToken;

    /** 分享角色：READER */
    private String shareRole;

    /** 过期时间，{@code null} 表示不限期 */
    private LocalDateTime expiresAt;

    /** 状态：1 有效 / 0 已撤销 */
    private Integer status;

    /** 创建人ID */
    private Long createdBy;
}
