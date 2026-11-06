package com.bluewhale.party.content.domain.repository;

import com.bluewhale.party.content.domain.model.ContentAudit;
import com.bluewhale.party.content.domain.model.ContentPortalQuery;
import com.bluewhale.party.content.domain.model.ContentQuery;
import com.bluewhale.party.content.domain.model.ContentRead;
import com.bluewhale.party.content.domain.model.ContentScope;
import com.bluewhale.party.content.domain.model.PartyContent;

import java.util.List;
import java.util.Optional;

/**
 * 党建内容仓储端口。
 *
 * @author BlueWhale
 */
public interface ContentRepository {

    /**
     * 管理列表分页（含全部状态）。
     *
     * @param query 查询条件
     * @param offset 偏移量
     * @param size 条数
     * @return 内容列表
     */
    List<PartyContent> page(ContentQuery query, long offset, int size);

    /**
     * 管理列表总数。
     *
     * @param query 查询条件
     * @return 总数
     */
    long count(ContentQuery query);

    /**
     * 阅读列表分页（仅已发布且范围内可见）。
     *
     * @param query 查询条件
     * @param scope 阅读范围
     * @param offset 偏移量
     * @param size 条数
     * @return 内容列表
     */
    List<PartyContent> portalPage(ContentPortalQuery query, ContentScope scope, long offset, int size);

    /**
     * 阅读列表总数。
     *
     * @param query 查询条件
     * @param scope 阅读范围
     * @return 总数
     */
    long portalCount(ContentPortalQuery query, ContentScope scope);

    /**
     * 按主键查询。
     *
     * @param id 内容 ID
     * @return 内容
     */
    Optional<PartyContent> findById(Long id);

    /**
     * 保存内容（主键为空时插入，否则更新）。
     *
     * @param content 内容
     * @return 主键
     */
    Long save(PartyContent content);

    /**
     * 查询审核轨迹。
     *
     * @param contentId 内容 ID
     * @return 轨迹列表（按时间正序）
     */
    List<ContentAudit> listAudits(Long contentId);

    /**
     * 写入一条审核轨迹。
     *
     * @param audit 轨迹
     * @return 主键
     */
    Long saveAudit(ContentAudit audit);

    /**
     * 记录阅读（同一人同一内容只记一次）。
     *
     * @param read 阅读记录
     * @return 新增返回 true；已存在仅刷新阅读时间返回 false
     */
    boolean recordRead(ContentRead read);

    /**
     * 查询阅读记录。
     *
     * @param contentId 内容 ID
     * @return 阅读记录列表
     */
    List<ContentRead> listReads(Long contentId);

    /**
     * 阅读数 +1（按人数去重后的计数）。
     *
     * @param contentId 内容 ID
     */
    void incrementReadCount(Long contentId);
}
