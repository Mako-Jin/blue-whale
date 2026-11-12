package com.bluewhale.forum.service;

import com.bluewhale.forum.common.ForumActor;
import com.bluewhale.forum.dto.ReactionStateVO;

import java.util.Set;

/**
 * 互动服务：投花（LIKE）与藏帖（COLLECT）。
 *
 * <p>幂等语义：投花/取消投花都是「把状态置为目标值」，重复调用不报错、不重复计数。
 * 判重靠唯一键 {@code uk_fm_reaction}，不靠先查后写——先查后写在并发下会给同一人记两次分。</p>
 *
 * @author BlueWhale
 */
public interface ReactionService {

    /**
     * 投花 / 取消投花（藏帖同理）。
     *
     * @param threadId 帖子ID
     * @param reactionType 互动类型（LIKE / COLLECT，大小写不敏感）
     * @param enable {@code true} 表示投，{@code false} 表示取消
     * @param actor 当前操作者
     * @return 操作后的选中态与最新计数
     */
    ReactionStateVO toggle(Long threadId, String reactionType, boolean enable, ForumActor actor);

    /**
     * 当前用户对某帖的互动状态（按钮选中态 + 计数）。
     *
     * @param threadId 帖子ID
     * @param actor 当前操作者
     * @return 互动状态
     */
    ReactionStateVO state(Long threadId, ForumActor actor);

    /**
     * 当前用户对某帖已生效的互动类型集合（详情页装填按钮态用）。
     *
     * @param threadId 帖子ID
     * @param userId 用户ID
     * @return 类型集合（无互动时为空集）
     */
    Set<String> myReactionTypes(Long threadId, Long userId);
}
