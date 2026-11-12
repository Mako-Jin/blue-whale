package com.bluewhale.forum.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bluewhale.forum.entity.ForumSensitiveWord;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 敏感词 Mapper。
 *
 * @author BlueWhale
 */
@Mapper
public interface ForumSensitiveWordMapper extends BaseMapper<ForumSensitiveWord> {

    /**
     * 物理删除词条。
     *
     * <p>词库是配置数据，不能逻辑删：唯一键 {@code uk_fm_word(tenant_id, word)}
     * 不认 {@code deleted}，软删后同词永远加不回来（与 thread_no 同类坑）。
     * 历史命中记录在 {@code fm_sensitive_hit} 里独立留痕，不依赖本行。</p>
     *
     * @param id 词条ID
     * @return 影响行数
     */
    @Delete("DELETE FROM fm_sensitive_word WHERE id = #{id}")
    int hardDelete(@Param("id") Long id);
}
