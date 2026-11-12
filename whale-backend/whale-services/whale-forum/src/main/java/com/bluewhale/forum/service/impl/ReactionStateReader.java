package com.bluewhale.forum.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.bluewhale.forum.entity.ForumReaction;
import com.bluewhale.forum.mapper.ForumReactionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 互动状态只读查询（详情页按钮态）。
 *
 * <p>单独成 Bean 是为了拆掉一个循环依赖：帖子服务要读「我投过没」，
 * 互动服务要借帖子服务做可见性校验，两者直接互相注入会让容器起不来。
 * 读侧只碰 Mapper，谁都能依赖，依赖方向就回到单向。</p>
 *
 * @author BlueWhale
 */
@Component
@RequiredArgsConstructor
public class ReactionStateReader {

    private final ForumReactionMapper reactionMapper;

    /**
     * 查当前用户对某帖已生效的互动类型。
     *
     * @param threadId 帖子ID
     * @param userId 用户ID
     * @return 类型集合（无互动时为空集）
     */
    public Set<String> typesOf(Long threadId, Long userId) {
        if (threadId == null || userId == null) {
            return Set.of();
        }
        // 字符串列名而非 lambda：lambda 依赖 TableInfo 缓存，纯单测（无 Spring 上下文）会抛
        // "can not find lambda cache for this entity"，业务规则就没法被单测覆盖
        List<ForumReaction> rows = reactionMapper.selectList(new QueryWrapper<ForumReaction>()
                .eq("user_id", userId)
                .eq("target_type", "THREAD")
                .eq("target_id", threadId));
        Set<String> types = new LinkedHashSet<>(rows.size());
        for (ForumReaction row : rows) {
            types.add(row.getReactionType());
        }
        return types;
    }
}
