package com.bluewhale.mail.service;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.mail.dto.MailDraftSaveRequest;
import com.bluewhale.mail.vo.MailDraftVO;

/**
 * 草稿服务。
 *
 * @author BlueWhale
 */
public interface MailDraftService {

    /**
     * 保存草稿（带 ID 时更新，否则新建）
     *
     * @param id 草稿ID，可为 {@code null}
     * @param request 草稿内容
     * @param userId 当前用户ID
     * @return 草稿视图
     */
    MailDraftVO save(Long id, MailDraftSaveRequest request, Long userId);

    /**
     * 草稿分页
     *
     * @param current 页码
     * @param size 每页条数
     * @param accountId 账号ID，可为 {@code null} 表示当前用户默认账号
     * @param userId 当前用户ID
     * @return 分页结果
     */
    PageResult<MailDraftVO> page(long current, long size, Long accountId, Long userId);

    /**
     * 删除草稿
     *
     * @param id 草稿ID
     * @param userId 当前用户ID
     */
    void remove(Long id, Long userId);
}
