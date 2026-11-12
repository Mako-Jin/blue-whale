package com.bluewhale.forum.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bluewhale.forum.entity.ForumBoard;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 版块 Mapper。
 *
 * @author BlueWhale
 */
@Mapper
public interface ForumBoardMapper extends BaseMapper<ForumBoard> {

    /**
     * 软删时改写版块编码以释放租户内唯一键（{@code uk_fm_board_code}）。
     *
     * <p>不这么做的话，删除「鲸茶馆」后永远无法再建同名编码版块——
     * 唯一键还挂在墓碑行上，插入直接撞键失败。</p>
     *
     * @param id 版块ID
     * @param newCode 改写后的编码（{@code 原编码#D{id}}）
     * @return 影响行数
     */
    @Update("UPDATE fm_board SET code = #{newCode} WHERE id = #{id} AND deleted = 0")
    int renameCodeForDelete(@Param("id") Long id, @Param("newCode") String newCode);
}
