package com.bluewhale.im.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bluewhale.im.entity.ImConversationMember;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;

/**
 * 会话成员 Mapper。
 *
 * @author BlueWhale
 */
public interface ImConversationMemberMapper extends BaseMapper<ImConversationMember> {

    /**
     * 物理删除成员记录（退群 / 被移出群聊）。
     *
     * <p>成员表唯一索引 {@code uk_conversation_user} 覆盖全部行（含逻辑删除行），
     * 若走逻辑删除会导致同一用户无法再次入群，故退群场景使用物理删除。</p>
     *
     * @param conversationId 会话ID
     * @param userId         用户ID
     * @return 删除行数
     */
    @Delete("DELETE FROM im_conversation_member WHERE conversation_id = #{conversationId} AND user_id = #{userId}")
    int deleteMember(@Param("conversationId") Long conversationId, @Param("userId") Long userId);
}
