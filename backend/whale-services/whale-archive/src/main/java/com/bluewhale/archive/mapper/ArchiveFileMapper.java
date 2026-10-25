package com.bluewhale.archive.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bluewhale.archive.entity.ArchiveFile;
import com.bluewhale.archive.vo.ArchiveFileSecurityVO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 档案文件引用 Mapper。
 *
 * @author BlueWhale
 */
public interface ArchiveFileMapper extends BaseMapper<ArchiveFile> {

    /**
     * 按文件ID查询所属档案条目的密级（同一文件被多个条目引用时取最严）。
     *
     * @param fileId 文件ID
     * @return 密级视图；未被档案引用返回 {@code null}
     */
    @Select("SELECT af.file_id AS fileId, af.item_id AS itemId, ai.security_level AS securityLevel "
            + "FROM archive_file af JOIN archive_item ai ON ai.id = af.item_id "
            + "WHERE af.file_id = #{fileId} AND af.deleted = 0 AND ai.deleted = 0 "
            + "ORDER BY ai.security_level DESC LIMIT 1")
    ArchiveFileSecurityVO selectSecurityByFileId(@Param("fileId") Long fileId);
}
