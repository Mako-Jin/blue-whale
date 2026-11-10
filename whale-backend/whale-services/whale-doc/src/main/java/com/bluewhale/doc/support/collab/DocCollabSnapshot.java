package com.bluewhale.doc.support.collab;

import java.time.LocalDateTime;

/**
 * 协作快照落库结果（M2）。
 *
 * @param revision 快照后的页面 revision
 * @param savedAt  快照时间
 * @param skipped  正文与标题均未变化时为 {@code true}（不写库）
 * @author BlueWhale
 */
public record DocCollabSnapshot(Long revision, LocalDateTime savedAt, boolean skipped) {
}
