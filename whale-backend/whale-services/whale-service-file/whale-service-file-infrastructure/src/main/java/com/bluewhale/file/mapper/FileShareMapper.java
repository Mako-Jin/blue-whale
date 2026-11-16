package com.bluewhale.file.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bluewhale.file.po.FileSharePo;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

/**
 * 文件分享链接 Mapper。
 *
 * <p>撤销与下载次数占用使用条件更新：把状态、有效期与次数上限放进同一条 SQL，
 * 避免「先查后改」在并发下超额发放下载次数。</p>
 *
 * @author BlueWhale
 */
public interface FileShareMapper extends BaseMapper<FileSharePo> {

    /**
     * 撤销分享：仅有效分享置为已撤销（幂等）。
     *
     * @param id 分享ID
     * @return 受影响行数（已撤销或不存在时为 0）
     */
    @Update("UPDATE file_share SET status = 0, update_time = NOW() WHERE id = #{id} AND deleted = 0 AND status = 1")
    int revokeById(@Param("id") Long id);

    /**
     * 原子占用一次下载次数：状态、有效期与次数上限在同一 SQL 内校验。
     *
     * @param id 分享ID
     * @return 受影响行数（失效或已达上限时为 0）
     */
    @Update("UPDATE file_share SET download_count = download_count + 1, update_time = NOW() "
            + "WHERE id = #{id} AND deleted = 0 AND status = 1 "
            + "AND (expire_time IS NULL OR expire_time > NOW()) "
            + "AND (max_downloads IS NULL OR download_count < max_downloads)")
    int consumeDownload(@Param("id") Long id);

    /**
     * 物理删除到期时间早于阈值的分享（单批）。
     *
     * <p>使用 {@code LIMIT} 控制单批删除量，避免一次清理过多行造成长事务与主从延迟；
     * 只删除有到期时间的记录，撤销但未到期的分享保留至自然到期。</p>
     *
     * @param threshold 到期时间阈值
     * @param limit 单批最多删除条数
     * @return 受影响行数
     */
    @Delete("DELETE FROM file_share WHERE expire_time IS NOT NULL AND expire_time < #{threshold} "
            + "LIMIT #{limit}")
    int purgeExpiredBefore(@Param("threshold") LocalDateTime threshold, @Param("limit") int limit);
}
