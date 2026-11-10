package com.bluewhale.doc.application.service;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.doc.interfaces.vo.DocInternalChangeVO;
import com.bluewhale.doc.interfaces.vo.DocInternalPageVO;

import java.time.LocalDateTime;

/**
 * 内网知识源同步应用服务。
 *
 * <p>仅供 {@code /doc/internal/**} 调用，租户来自 {@code X-Tenant-Id}，不校验用户权限。</p>
 *
 * @author BlueWhale
 */
public interface DocInternalService {

    /**
     * 查询公开已发布页面的增量变更（含删除/归档墓碑）。
     *
     * @param since   更新时间游标（不含），null 表示全量
     * @param current 当前页码
     * @param size    每页条数
     * @return 变更分页
     */
    PageResult<DocInternalChangeVO> changes(LocalDateTime since, long current, long size);

    /**
     * 读取公开已发布页面快照。
     *
     * @param pageId 页面ID
     * @return 页面快照
     */
    DocInternalPageVO detail(Long pageId);
}
