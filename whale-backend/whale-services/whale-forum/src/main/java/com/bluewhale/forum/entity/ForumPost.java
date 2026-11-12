package com.bluewhale.forum.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 楼层实体，对应表 {@code fm_post}。
 *
 * <p><b>楼层号语义</b>：{@code floor} 为 {@code null} 表示二级回复（楼中楼），
 * 二级回复只记 {@code parentPostId}、不占楼层号；一级楼层 1..N 由服务端顺序分配。
 * {@code uk_fm_post_floor} 是并发兜底——唯一键冲突即代表两个请求抢到同一楼层号，
 * 由调用方重试，绝不能改成 {@code MAX(floor)+1}。</p>
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("fm_post")
public class ForumPost extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 租户ID */
    private Long tenantId;

    /** 帖子ID */
    private Long threadId;

    /** 楼层号（二级回复为 NULL） */
    private Integer floor;

    /** 父回复ID（NULL 为一级楼层） */
    private Long parentPostId;

    /** 引用楼层ID */
    private Long replyToPostId;

    /** 作者用户ID（匿名也恒存） */
    private Long authorId;

    /** 是否匿名展示：0 否 / 1 是 */
    private Integer anonymous;

    /** 匿名编号 */
    private String anonymousNo;

    /** 正文（已消毒） */
    private String content;

    /** 投花数 */
    private Integer likeCount;

    /** 状态：PUBLISHED / REMOVED */
    private String status;
}
