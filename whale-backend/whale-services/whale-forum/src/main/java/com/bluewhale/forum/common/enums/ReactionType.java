package com.bluewhale.forum.common.enums;

/**
 * 互动类型（对应 {@code fm_reaction.reaction_type}）。
 *
 * <p>幂等由唯一键
 * {@code uk_fm_reaction(tenant_id, user_id, target_type, target_id, reaction_type)}
 * 保证，<b>不靠计数判重</b>；取消即物理删除（关联表不挂逻辑删除，
 * 否则「取消后再点」会撞唯一键）。</p>
 *
 * @author BlueWhale
 */
public enum ReactionType {

    /** 投花（点赞） */
    LIKE,

    /** 藏帖（收藏） */
    COLLECT
}
