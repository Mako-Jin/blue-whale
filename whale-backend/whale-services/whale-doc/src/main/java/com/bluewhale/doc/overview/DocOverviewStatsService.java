package com.bluewhale.doc.overview;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bluewhale.doc.infrastructure.persistence.entity.DocPage;
import com.bluewhale.doc.infrastructure.persistence.entity.DocSpace;
import com.bluewhale.doc.infrastructure.persistence.mapper.DocPageMapper;
import com.bluewhale.doc.infrastructure.persistence.mapper.DocSpaceMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 翰林坊（DOC）统计口径：空间、页面、审批、回收站四本账。
 *
 * <p><b>口径只在这里定义一次。</b>"正式页面"是 {@code doc_page.deleted = 0}——
 * 逻辑删除插件在常规查询里自动带上这个条件，这里不重复写；"回收站一棵树"
 * 是 {@code trash_root_id = id}（删除时整棵子树的根指向自己），恢复与彻底清除
 * 都按根节点操作，所以按棵数不按页数——子树里 30 篇页面在屏风上也是一棵。</p>
 *
 * <p><b>不写 GROUP BY，按状态各 count 一次。</b>页面状态只有四种且码值固定，
 * 四次索引 count 比一次聚合直观；更重要的是不走自定义 SQL 就不用自己拼
 * {@code tenant_id} 条件——租户隔离由 MyBatis-Plus 插件保证，一旦漏了就是
 * 跨租户串数，且不会报错。唯一例外是回收站：逻辑删除挡住常规查询，
 * 只能走手工 SQL 并显式带租户条件（见 {@code DocPageMapper#countTrashRootsByTenant}）。</p>
 *
 * <p><b>租户 ID 必须显式传入。</b>概览 provider 在独立线程里执行，拿不到
 * 请求线程的 {@code TenantContext}；回收站那条手工 SQL 的租户条件全靠这个参数。
 * 传 {@code null} 直接抛——总控聚合时会显式传租户，缺租户说明调用方配错了，
 * 早点降级比在屏风上显示一个错误的 0 强。</p>
 *
 * <p>统计只读，不写库：概览被前端按 30s 轮询，任何写操作都会变成"每 30 秒写一次"。</p>
 *
 * @author BlueWhale
 */
@Service
@RequiredArgsConstructor
public class DocOverviewStatsService {

    /** 页面状态：草稿 */
    private static final String PAGE_DRAFT = "DRAFT";

    /** 页面状态：已发布 */
    private static final String PAGE_PUBLISHED = "PUBLISHED";

    /** 页面状态：待发布审批 */
    private static final String PAGE_PENDING_APPROVAL = "PENDING_APPROVAL";

    /** 页面状态：已归档 */
    private static final String PAGE_ARCHIVED = "ARCHIVED";

    /** 空间状态：正常 */
    private static final int SPACE_ACTIVE = 1;

    /** 空间状态：归档 */
    private static final int SPACE_ARCHIVED = 0;

    private final DocPageMapper pageMapper;

    private final DocSpaceMapper spaceMapper;

    /**
     * 取一次统计快照。
     *
     * @param tenantId 租户 ID（回收站手工 SQL 的租户条件，必填）
     * @return 统计快照
     */
    public DocOverviewStats snapshot(Long tenantId) {
        if (tenantId == null) {
            throw new IllegalArgumentException("tenantId is required for doc overview stats");
        }
        long spaceActive = count(spaceMapper.selectCount(new LambdaQueryWrapper<DocSpace>()
                .eq(DocSpace::getStatus, SPACE_ACTIVE)));
        long spaceArchived = count(spaceMapper.selectCount(new LambdaQueryWrapper<DocSpace>()
                .eq(DocSpace::getStatus, SPACE_ARCHIVED)));
        long pageTotal = count(pageMapper.selectCount(null));
        long pageDraft = count(pageMapper.selectCount(new LambdaQueryWrapper<DocPage>()
                .eq(DocPage::getStatus, PAGE_DRAFT)));
        long pagePublished = count(pageMapper.selectCount(new LambdaQueryWrapper<DocPage>()
                .eq(DocPage::getStatus, PAGE_PUBLISHED)));
        long pagePendingApproval = count(pageMapper.selectCount(new LambdaQueryWrapper<DocPage>()
                .eq(DocPage::getStatus, PAGE_PENDING_APPROVAL)));
        long pageArchived = count(pageMapper.selectCount(new LambdaQueryWrapper<DocPage>()
                .eq(DocPage::getStatus, PAGE_ARCHIVED)));
        long trashRoots = pageMapper.countTrashRootsByTenant(tenantId);
        return new DocOverviewStats(spaceActive, spaceArchived, pageTotal,
                pageDraft, pagePublished, pagePendingApproval, pageArchived, trashRoots);
    }

    /**
     * 计数兜底：{@code selectCount} 在极端情况下返回 {@code null}，直接拆箱就是 NPE。
     *
     * @param value 计数值，可空
     * @return 非空计数
     */
    private static long count(Long value) {
        return value == null ? 0L : value;
    }
}
