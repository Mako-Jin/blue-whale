package com.bluewhale.music.application.service;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.music.application.command.AppealQueryCommand;
import com.bluewhale.music.application.command.AppealReviewCommand;
import com.bluewhale.music.application.command.AppealSubmitCommand;
import com.bluewhale.music.application.view.SongAppealView;

/**
 * 版权下架申诉应用服务（用户提交 / 运营复核）。
 *
 * @author BlueWhale
 */
public interface SongAppealAppService {

    /**
     * 上传者提交版权下架申诉：仅本人作品且处于版权下架状态可提交，
     * 同一作品同一时刻只允许一条待复核申诉。
     *
     * @param songId 歌曲 ID
     * @param command 申诉命令
     * @param userId 申诉人用户 ID
     * @return 申诉视图
     */
    SongAppealView submit(Long songId, AppealSubmitCommand command, Long userId);

    /**
     * 运营复核分页：按歌曲 / 状态过滤，按提交时间倒序。
     *
     * @param command 查询命令
     * @return 申诉分页
     */
    PageResult<SongAppealView> pageAudit(AppealQueryCommand command);

    /**
     * 申诉通过：申诉置为 APPROVED，歌曲版权恢复为有效并转为下架（需运营重新上架）。
     *
     * @param id 申诉 ID
     * @param command 复核命令
     * @param reviewerId 复核人用户 ID
     * @return 申诉视图
     */
    SongAppealView approve(Long id, AppealReviewCommand command, Long reviewerId);

    /**
     * 申诉驳回：申诉置为 REJECTED，歌曲保持版权下架。
     *
     * @param id 申诉 ID
     * @param command 复核命令
     * @param reviewerId 复核人用户 ID
     * @return 申诉视图
     */
    SongAppealView reject(Long id, AppealReviewCommand command, Long reviewerId);
}
