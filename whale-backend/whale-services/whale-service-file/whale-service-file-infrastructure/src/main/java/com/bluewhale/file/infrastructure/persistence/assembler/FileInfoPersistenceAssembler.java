package com.bluewhale.file.infrastructure.persistence.assembler;

import com.bluewhale.file.domain.file.aggregate.FileAssetAggregate;
import com.bluewhale.file.domain.model.FileRecycleItem;
import com.bluewhale.file.domain.model.vo.FileStatus;
import com.bluewhale.file.po.FileInfoPo;

/**
 * FileAsset 聚合与其持久化对象（PO，{@link FileInfoPo}）的双向转换器（手写 Assembler）。
 *
 * <p>选择手写而非 MapStruct：需对文件大小、状态码与逻辑删除标记做空值归一化，
 * 显式转换更可控、可读。</p>
 *
 * @author BlueWhale
 */
public final class FileInfoPersistenceAssembler {

    /** 逻辑删除标记：已删除 */
    private static final int DELETED_FLAG = 1;

    /**
     * 私有构造器：工具类，禁止实例化
     */
    private FileInfoPersistenceAssembler() {
    }

    /**
     * PO → 领域对象：文件大小为空时按 0 归一化（兼容存量缺失元数据；0 字节文件本身合法），
     * 状态码经 {@link FileStatus#fromCode(Integer)} 还原
     *
     * @param po 文件持久化对象
     * @return 文件资产聚合根
     */
    public static FileAssetAggregate toDomain(FileInfoPo po) {
        return FileAssetAggregate.reconstitute(
                po.getId(),
                po.getFileName(),
                po.getOriginalName(),
                po.getBucket(),
                po.getObjectKey(),
                po.getFileSize() == null ? 0L : po.getFileSize(),
                po.getContentType(),
                po.getMd5(),
                FileStatus.fromCode(po.getStatus()),
                po.getUploaderId(),
                po.getDeleted() != null && po.getDeleted() == DELETED_FLAG,
                po.getCreateTime(),
                po.getUpdateTime(),
                po.getStorageType());
    }

    /**
     * PO → 回收站投影：保留删除时间与存储定位，供列表展示与到期物理清理使用
     *
     * @param po 文件持久化对象（含逻辑删除行）
     * @return 回收站投影
     */
    public static FileRecycleItem toRecycleItem(FileInfoPo po) {
        return new FileRecycleItem(po.getId(), po.getFileName(), po.getOriginalName(), po.getBucket(),
                po.getStorageType(), po.getObjectKey(), po.getFileSize(), po.getContentType(), po.getStatus(),
                po.getUploaderId(), po.getCreateTime(), po.getUpdateTime(), po.getDeleteTime());
    }

    /**
     * 领域对象 → PO：仅转换 FileAsset 聚合所承载的字段
     *
     * @param asset 文件资产聚合根
     * @return 文件持久化对象
     */
    public static FileInfoPo toPo(FileAssetAggregate asset) {
        FileInfoPo po = new FileInfoPo();
        po.setId(asset.getId());
        po.setFileName(asset.getFileName());
        po.setOriginalName(asset.getOriginalName());
        po.setBucket(asset.getBucket());
        po.setStorageType(asset.getStorageType());
        po.setObjectKey(asset.getObjectKey());
        po.setFileSize(asset.getFileSize());
        po.setContentType(asset.getContentType());
        po.setMd5(asset.getMd5());
        po.setStatus(asset.getStatus().getCode());
        po.setUploaderId(asset.getUploaderId());
        po.setDeleted(asset.isDeleted() ? DELETED_FLAG : 0);
        po.setCreateTime(asset.getCreateTime());
        po.setUpdateTime(asset.getUpdateTime());
        return po;
    }
}
