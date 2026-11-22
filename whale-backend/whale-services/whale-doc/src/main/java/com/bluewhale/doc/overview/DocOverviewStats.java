package com.bluewhale.doc.overview;

/**
 * 翰林坊（DOC）的一次性统计快照。
 *
 * <p><b>为什么单独一组计数，而不是让概览 provider 直接查 Mapper。</b>
 * 概览 provider 属于 3D 城的取数层，一旦它开始自己写查询条件，
 * "正式页面"与"回收站"的判定口径就有两份（2D 回收站按空间逐棵看，3D 屏风自己数），
 * 迟早出现"后台说 3 棵、城里说 5 棵"。这里把所有口径收在
 * {@link DocOverviewStatsService} 一处，provider 只负责排版。</p>
 *
 * <p><b>两本账分两套查询通道。</b>正式页面（{@code doc_page.deleted = 0}）走
 * MyBatis-Plus 的常规查询——逻辑删除插件自动带上 {@code deleted = 0}；
 * 回收站（{@code deleted = 1}）被逻辑删除挡在常规查询之外，必须走
 * 手工 SQL（{@code DocPageMapper#countTrashRootsByTenant}）并显式带租户条件。
 * 想用一条查询把两本账数全，要么绕过逻辑删除要么漏掉一边——分开数才不会错。</p>
 *
 * @param spaceActive        正常空间数（doc_space.status = 1）
 * @param spaceArchived      归档空间数（status = 0，页面还在但不可编辑）
 * @param pageTotal          正式页面总数（未入回收站，含 FOLDER 目录节点）
 * @param pageDraft          草稿页数（DRAFT）
 * @param pagePublished      已发布页数（PUBLISHED）
 * @param pagePendingApproval 卡在发布审批的页数（PENDING_APPROVAL）
 * @param pageArchived       已归档页数（ARCHIVED）
 * @param trashRoots         回收站删除子树的棵数（trash_root_id = id，整棵子树算一棵）
 * @author BlueWhale
 */
public record DocOverviewStats(
        long spaceActive,
        long spaceArchived,
        long pageTotal,
        long pageDraft,
        long pagePublished,
        long pagePendingApproval,
        long pageArchived,
        long trashRoots) {
}
