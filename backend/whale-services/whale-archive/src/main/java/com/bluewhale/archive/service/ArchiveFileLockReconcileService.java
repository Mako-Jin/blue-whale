package com.bluewhale.archive.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bluewhale.archive.client.FileArchiveClient;
import com.bluewhale.archive.entity.ArchiveFile;
import com.bluewhale.archive.entity.ArchiveItem;
import com.bluewhale.archive.mapper.ArchiveFileMapper;
import com.bluewhale.archive.mapper.ArchiveItemMapper;
import com.bluewhale.archive.remote.RemoteFileMeta;
import com.bluewhale.archive.support.ArchiveConstants;
import com.bluewhale.archive.vo.ArchiveFileLockReconcileVO;
import com.bluewhale.common.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 历史归档文件锁定补扫服务。
 *
 * <p>修复「合同归档原件未锁定」上线前已归档的条目：按当前租户、按条目 ID 游标分批扫描
 * 已归档条目，读取文件元数据，仅对未锁定文件调用文件服务锁定接口。已锁定文件跳过，
 * 单个文件失败不阻断整批，结果以计数返回，便于运维重复执行。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ArchiveFileLockReconcileService {

    private final ArchiveItemMapper itemMapper;

    private final ArchiveFileMapper fileMapper;

    private final FileArchiveClient fileClient;

    /**
     * 补扫当前租户的一批已归档条目并锁定未锁定文件。
     *
     * @param limit 单批条目数（自动限制在 1..500）
     * @param afterId 游标：只处理 ID 大于该值的条目，可为 {@code null}
     * @return 补扫结果
     */
    public ArchiveFileLockReconcileVO reconcile(int limit, Long afterId) {
        TenantContext.requireId();
        int size = Math.min(Math.max(limit, 1), ArchiveConstants.MAX_LOCK_RECONCILE_LIMIT);
        List<ArchiveItem> items = itemMapper.selectList(new LambdaQueryWrapper<ArchiveItem>()
                .eq(ArchiveItem::getItemStatus, ArchiveConstants.ITEM_STATUS_ARCHIVED)
                .gt(afterId != null, ArchiveItem::getId, afterId)
                .orderByAsc(ArchiveItem::getId)
                .last("LIMIT " + size));
        ArchiveFileLockReconcileVO result = new ArchiveFileLockReconcileVO();
        int locked = 0;
        int alreadyLocked = 0;
        int missing = 0;
        int failed = 0;
        for (ArchiveItem item : items) {
            List<ArchiveFile> files = fileMapper.selectList(new LambdaQueryWrapper<ArchiveFile>()
                    .eq(ArchiveFile::getItemId, item.getId()));
            for (ArchiveFile file : files) {
                if (file.getFileId() == null) {
                    continue;
                }
                try {
                    RemoteFileMeta meta = fileClient.detail(file.getFileId());
                    if (meta == null) {
                        missing++;
                        log.warn("补锁跳过：文件不存在, itemId={}, fileId={}", item.getId(), file.getFileId());
                        continue;
                    }
                    if (meta.getStatus() != null && meta.getStatus() == ArchiveConstants.FILE_STATUS_LOCKED) {
                        alreadyLocked++;
                        continue;
                    }
                    fileClient.lock(file.getFileId());
                    locked++;
                } catch (Exception ex) {
                    failed++;
                    log.warn("补锁失败: itemId={}, fileId={}, error={}", item.getId(), file.getFileId(),
                            ex.getMessage());
                }
            }
        }
        result.setScannedItems(items.size());
        result.setLockedFiles(locked);
        result.setAlreadyLockedFiles(alreadyLocked);
        result.setMissingFiles(missing);
        result.setFailedFiles(failed);
        result.setNextAfterId(items.isEmpty() ? afterId : items.get(items.size() - 1).getId());
        result.setHasMore(items.size() == size);
        log.info("归档文件锁定补扫完成: tenantId={}, afterId={}, limit={}, scanned={}, locked={}, "
                        + "alreadyLocked={}, missing={}, failed={}",
                TenantContext.get(), afterId, size, items.size(), locked, alreadyLocked, missing, failed);
        return result;
    }
}
