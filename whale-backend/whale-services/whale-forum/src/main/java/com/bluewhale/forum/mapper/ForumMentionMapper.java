package com.bluewhale.forum.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bluewhale.forum.entity.ForumMention;
import org.apache.ibatis.annotations.Mapper;

/**
 * @提及记录 Mapper（append-only）。
 *
 * <p>重复提及由唯一键 {@code uk_fm_mention_target(tenant_id, post_id, user_id)}
 * 拦下，调用方捕获 {@code DuplicateKeyException} 后跳过，不做「先查再插」——
 * 并发下先查再插必漏判。</p>
 *
 * @author BlueWhale
 */
@Mapper
public interface ForumMentionMapper extends BaseMapper<ForumMention> {
}
