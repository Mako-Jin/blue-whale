package com.bluewhale.doc.application.service;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.doc.infrastructure.persistence.entity.DocPage;
import com.bluewhale.doc.interfaces.dto.DocVersionManualRequest;
import com.bluewhale.doc.interfaces.vo.DocPageSaveVO;
import com.bluewhale.doc.interfaces.vo.DocPageVersionVO;

/**
 * 页面版本快照应用服务。
 *
 * @author BlueWhale
 */
public interface DocVersionService {

    /**
     * 分页查询版本历史。
     *
     * @param pageId  页面ID
     * @param current 当前页码
     * @param size    每页条数
     * @param userId  当前用户ID
     * @return 版本分页
     */
    PageResult<DocPageVersionVO> page(Long pageId, long current, long size, Long userId);

    /**
     * 查询指定 revision 的版本详情。
     *
     * @param pageId     页面ID
     * @param revisionNo 快照 revision
     * @param userId     当前用户ID
     * @return 版本详情
     */
    DocPageVersionVO detail(Long pageId, Long revisionNo, Long userId);

    /**
     * 手动保存当前 revision 快照。
     *
     * @param pageId  页面ID
     * @param request 变更说明
     * @param userId  当前用户ID
     * @return 版本详情
     */
    DocPageVersionVO manual(Long pageId, DocVersionManualRequest request, Long userId);

    /**
     * 以历史版本正文生成新的 revision。
     *
     * @param pageId     页面ID
     * @param revisionNo 历史 revision
     * @param userId     当前用户ID
     * @return 保存结果
     */
    DocPageSaveVO restore(Long pageId, Long revisionNo, Long userId);

    /**
     * 写入指定类型的快照（同 revision 已存在时跳过）。
     *
     * @param page          页面
     * @param versionKind   快照类型
     * @param changeSummary 变更说明
     * @param editorId      编辑人ID
     */
    void snapshot(DocPage page, String versionKind, String changeSummary, Long editorId);

    /**
     * 按 revision 间隔或时间间隔判断是否需要自动快照。
     *
     * @param page     保存后的页面
     * @param editorId 编辑人ID
     */
    void maybeAutoSnapshot(DocPage page, Long editorId);

    /**
     * 读取最近一条版本快照的正文哈希，供协作快照去重。
     *
     * @param pageId 页面ID
     * @return SHA-256 哈希；无快照返回 {@code null}
     */
    String latestContentHash(Long pageId);
}
