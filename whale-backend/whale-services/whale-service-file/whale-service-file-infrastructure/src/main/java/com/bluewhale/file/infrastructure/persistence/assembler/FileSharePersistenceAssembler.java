package com.bluewhale.file.infrastructure.persistence.assembler;

import com.bluewhale.file.domain.share.aggregate.FileShareAggregate;
import com.bluewhale.file.domain.share.model.FileShareItem;
import com.bluewhale.file.domain.share.model.vo.ShareStatus;
import com.bluewhale.file.po.FileSharePo;

/**
 * FileShare 聚合与其持久化对象（{@link FileSharePo}）的双向转换器（手写 Assembler）。
 *
 * @author BlueWhale
 */
public final class FileSharePersistenceAssembler {

    /**
     * 私有构造器：工具类，禁止实例化
     */
    private FileSharePersistenceAssembler() {
    }

    /**
     * PO → 领域对象
     *
     * @param po 分享持久化对象
     * @return 分享聚合根
     */
    public static FileShareAggregate toDomain(FileSharePo po) {
        return FileShareAggregate.reconstitute(
                po.getId(),
                po.getFileId(),
                po.getFileName(),
                po.getToken(),
                po.getPasswordHash(),
                po.getExpireTime(),
                po.getMaxDownloads(),
                po.getDownloadCount() == null ? 0 : po.getDownloadCount(),
                ShareStatus.fromCode(po.getStatus()),
                po.getCreatorId(),
                po.getCreateTime(),
                po.getUpdateTime());
    }

    /**
     * PO → 列表投影
     *
     * @param po 分享持久化对象
     * @return 分享列表投影
     */
    public static FileShareItem toItem(FileSharePo po) {
        return new FileShareItem(po.getId(), po.getFileId(), po.getFileName(), po.getToken(),
                po.getExpireTime(), po.getMaxDownloads(),
                po.getDownloadCount() == null ? 0 : po.getDownloadCount(),
                ShareStatus.fromCode(po.getStatus()).getCode(), po.getCreatorId(), po.getCreateTime());
    }

    /**
     * 领域对象 → PO：仅插入场景使用
     *
     * @param share 分享聚合根
     * @return 分享持久化对象
     */
    public static FileSharePo toPo(FileShareAggregate share) {
        FileSharePo po = new FileSharePo();
        po.setId(share.getId());
        po.setFileId(share.getFileId());
        po.setFileName(share.getFileName());
        po.setToken(share.getToken());
        po.setPasswordHash(share.getPasswordHash());
        po.setExpireTime(share.getExpireTime());
        po.setMaxDownloads(share.getMaxDownloads());
        po.setDownloadCount(share.getDownloadCount());
        po.setStatus(share.getStatus().getCode());
        po.setCreatorId(share.getCreatorId());
        po.setCreateTime(share.getCreateTime());
        po.setUpdateTime(share.getUpdateTime());
        return po;
    }
}
