package com.bluewhale.music.infrastructure.persistence.mapper;

import com.bluewhale.music.infrastructure.persistence.query.SongTagRow;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Collection;
import java.util.List;

/**
 * 歌曲-标签关系 Mapper（复合主键表，使用注解 SQL）。
 *
 * <p>{@code tenant_id} 由 starter 的 {@code TenantLineInnerInterceptor} 自动追加，
 * 此处不显式写租户列。</p>
 *
 * @author BlueWhale
 */
public interface SongTagMapper {

    /**
     * 新增一条歌曲标签关系。
     *
     * @param songId 歌曲 ID
     * @param tagId 标签 ID
     * @return 影响行数
     */
    @Insert("INSERT INTO mu_song_tag (song_id, tag_id) VALUES (#{songId}, #{tagId})")
    int insertOne(@Param("songId") Long songId, @Param("tagId") Long tagId);

    /**
     * 删除歌曲的全部标签关系。
     *
     * @param songId 歌曲 ID
     * @return 影响行数
     */
    @Delete("DELETE FROM mu_song_tag WHERE song_id = #{songId}")
    int deleteBySongId(@Param("songId") Long songId);

    /**
     * 删除某标签的全部歌曲关系。
     *
     * @param tagId 标签 ID
     * @return 影响行数
     */
    @Delete("DELETE FROM mu_song_tag WHERE tag_id = #{tagId}")
    int deleteByTagId(@Param("tagId") Long tagId);

    /**
     * 查询歌曲的标签 ID。
     *
     * @param songId 歌曲 ID
     * @return 标签 ID 列表
     */
    @Select("SELECT tag_id FROM mu_song_tag WHERE song_id = #{songId}")
    List<Long> selectTagIdsBySongId(@Param("songId") Long songId);

    /**
     * 按标签查询歌曲 ID。
     *
     * @param tagId 标签 ID
     * @return 歌曲 ID 列表
     */
    @Select("SELECT song_id FROM mu_song_tag WHERE tag_id = #{tagId}")
    List<Long> selectSongIdsByTagId(@Param("tagId") Long tagId);

    /**
     * 批量查询歌曲标签关系。
     *
     * @param songIds 歌曲 ID 集合
     * @return 关系行列表
     */
    @Select("<script>SELECT song_id AS songId, tag_id AS tagId FROM mu_song_tag "
            + "WHERE song_id IN <foreach collection='songIds' item='id' open='(' separator=',' close=')'>#{id}</foreach>"
            + "</script>")
    List<SongTagRow> selectRowsBySongIds(@Param("songIds") Collection<Long> songIds);
}
