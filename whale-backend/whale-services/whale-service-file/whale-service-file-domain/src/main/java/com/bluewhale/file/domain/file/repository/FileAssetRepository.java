package com.bluewhale.file.domain.file.repository;

import com.bluewhale.file.domain.file.aggregate.FileAssetAggregate;
import com.bluewhale.file.domain.model.FileAssetPage;
import com.bluewhale.file.domain.model.FileAssetQuery;
import com.bluewhale.file.domain.model.FileObjectRef;
import com.bluewhale.file.domain.model.FileRecycleItem;
import com.bluewhale.file.domain.model.FileRecyclePage;
import com.bluewhale.file.domain.model.FileRecycleQuery;
import com.bluewhale.file.domain.model.vo.StorageQuota;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * FileAsset 聚合仓储端口：契约定义于领域层，由 {@code infrastructure} 以 MyBatis-Plus 适配实现（依赖倒置）。
 *
 * <p>读路径默认过滤逻辑删除行；仅变更流与变更详情需要感知逻辑删除，以支撑知识库增量采集。</p>
 *
 * @author BlueWhale
 */
public interface FileAssetRepository {

    /**
     * 保存文件资产聚合（无主键时新增，有主键时按主键更新）
     *
     * <p>聚合在构造时已将 MD5 归一化为十六进制小写，实现按聚合值原样落库即可。</p>
     *
     * @param asset 文件资产聚合根
     * @return 回填主键后的文件资产聚合根
     */
    FileAssetAggregate save(FileAssetAggregate asset);

    /**
     * 在配额内原子保存文件资产：配额校验与写入在同一临界区完成，避免并发上传穿透配额。
     *
     * <p>个人、租户、桶三个维度中只有配置了正数上限的维度参与校验与加锁；
     * 全部不限时退化为普通保存。多维命名锁按「个人 → 租户 → 桶」固定顺序获取，避免死锁。</p>
     *
     * @param asset 文件资产聚合根
     * @param quota 生效的配额上限
     * @return 回填主键后的文件资产聚合根
     * @throws com.bluewhale.common.domain.exception.DomainException 超出任一维度配额时抛出 {@code PARAM_ERROR}
     */
    FileAssetAggregate saveWithQuotaCheck(FileAssetAggregate asset, StorageQuota quota);

    /**
     * 按主键查询未逻辑删除的文件资产
     *
     * @param id 文件ID
     * @return 文件资产；不存在或已逻辑删除时返回空
     */
    Optional<FileAssetAggregate> findById(Long id);

    /**
     * 按主键查询文件资产（包含已逻辑删除的行，供变更详情使用）
     *
     * @param id 文件ID
     * @return 文件资产；不存在时返回空
     */
    Optional<FileAssetAggregate> findByIdIncludingDeleted(Long id);

    /**
     * 查询版本组内版本号最大的一行（包含逻辑删除行）
     *
     * <p>包含逻辑删除行保证版本号单调递增：已删除版本占用的序号不再复用。</p>
     *
     * @param rootId 版本组根文件ID
     * @return 版本号最大的文件资产；不存在时返回空
     */
    Optional<FileAssetAggregate> findLatestVersion(Long rootId);

    /**
     * 查询版本组内全部未删除版本（包含根版本）
     *
     * <p>排序固定为 {@code version DESC, id DESC}。</p>
     *
     * @param rootId 版本组根文件ID
     * @return 版本组文件资产
     */
    List<FileAssetAggregate> findVersions(Long rootId);

    /**
     * 查询版本组内已逻辑删除的版本行（不含根文件）
     *
     * <p>供根文件恢复后发布版本恢复事件使用。</p>
     *
     * @param rootId 版本组根文件ID
     * @return 已删除版本；无记录时返回空列表
     */
    List<FileAssetAggregate> findDeletedVersions(Long rootId);

    /**
     * 物理删除版本行（版本保留策略清理使用）
     *
     * <p>实现必须带 {@code parent_id IS NOT NULL} 条件，保证根版本不会被误删。</p>
     *
     * @param id 版本行ID
     * @return 是否实际删除（{@code false} 表示目标不存在或不是版本行）
     */
    boolean purgeVersionById(Long id);

    /**
     * 按 MD5 + 大小查询可复用的正常文件（秒传去重依据，按主键升序取第一条）
     *
     * <p>入参摘要大小写不敏感：实现内部统一归一化为小写后查询；空白摘要直接视为未命中。</p>
     *
     * @param md5 MD5 摘要（十六进制，大小写不敏感）
     * @param fileSize 文件大小（字节）
     * @return 可复用的文件资产；未命中时返回空
     */
    Optional<FileAssetAggregate> findReusableByDigest(String md5, long fileSize);

    /**
     * 逻辑删除文件资产（同时记录删除时间），并级联删除其版本行
     *
     * <p>仅对未删除行生效，重复调用不改变结果（幂等）。传入根文件时级联逻辑删除
     * {@code parent_id = id} 的全部版本；传入版本行时没有子行，等同于单行删除。</p>
     *
     * @param id 文件ID
     */
    void deleteById(Long id);

    /**
     * 更新原始文件名（不改变对象键与存储文件名）
     *
     * @param id 文件ID
     * @param originalName 新的原始文件名
     */
    void updateOriginalName(Long id, String originalName);

    /**
     * 更新桶名称（物理对象已迁移到目标桶后调用）
     *
     * @param id 文件ID
     * @param bucket 目标桶名称
     */
    void updateBucket(Long id, String bucket);

    /**
     * 统计指定上传者的已用存储字节数（仅统计状态正常且未逻辑删除的文件）
     *
     * @param uploaderId 上传用户 ID
     * @return 已用字节数；无记录时返回 0
     */
    long sumNormalSizeByUploader(Long uploaderId);

    /**
     * 统计当前租户的已用存储字节数（仅统计状态正常且未逻辑删除的文件）
     *
     * <p>租户条件由 MyBatis 租户拦截器追加；无租户上下文时拦截器不追加条件，
     * 调用方不得在无租户态下使用本方法做租户维度校验。</p>
     *
     * @return 已用字节数；无记录时返回 0
     */
    long sumNormalSizeByTenant();

    /**
     * 统计当前租户指定桶的已用存储字节数（仅统计状态正常且未逻辑删除的文件）
     *
     * @param bucket 桶名称
     * @return 已用字节数；无记录时返回 0
     */
    long sumNormalSizeByBucket(String bucket);

    /**
     * 按条件分页查询文件资产（过滤逻辑删除行）
     *
     * @param query 分页查询条件
     * @return 文件资产分页结果
     */
    FileAssetPage page(FileAssetQuery query);

    /**
     * 按更新时间游标分页查询文件变更（包含逻辑删除行）
     *
     * <p>排序固定为 {@code update_time ASC, id ASC}。</p>
     *
     * @param since 游标下界（不含），可为 {@code null} 表示不限
     * @param current 页码（从 1 开始）
     * @param size 每页条数
     * @return 文件资产分页结果
     */
    FileAssetPage findChanges(LocalDateTime since, long current, long size);

    /**
     * 分页查询回收站文件（仅逻辑删除行）
     *
     * <p>排序固定为 {@code delete_time DESC, id DESC}；历史数据删除时间为空时排在其后。</p>
     *
     * @param query 回收站查询条件
     * @return 回收站分页结果
     */
    FileRecyclePage pageRecycle(FileRecycleQuery query);

    /**
     * 查询删除时间早于截止时间的回收站记录（供到期物理清理分批处理）
     *
     * @param cutoff 截止时间（不含）
     * @param limit 单批最大条数
     * @return 到期记录（按删除时间、ID 升序）
     */
    List<FileRecycleItem> findExpiredRecycle(LocalDateTime cutoff, long limit);

    /**
     * 物理删除已逻辑删除的元数据行（到期清理使用）
     *
     * <p>条件带 {@code deleted = 1}，并发恢复后不会误删；返回 {@code false} 表示已被恢复或已清理。</p>
     *
     * @param id 文件ID
     * @return 是否实际删除
     */
    boolean purgeById(Long id);

    /**
     * 查询全部元数据对象引用（包含逻辑删除行，供孤儿对象对账）
     *
     * @return 存储类型、桶名、对象键三元组
     */
    List<FileObjectRef> findAllObjectRefs();

    /**
     * 按落盘存储类型查询待迁移文件（主键升序）
     *
     * @param storageType 源落盘类型
     * @param limit 单批最大条数
     * @return 待迁移文件资产
     */
    List<FileAssetAggregate> findByStorageType(String storageType, long limit);

    /**
     * 更新文件的落盘存储类型（迁移成功且校验通过后调用）
     *
     * @param id 文件ID
     * @param storageType 目标存储类型
     */
    void updateStorageType(Long id, String storageType);

    /**
     * 在配额内恢复逻辑删除的文件：配额校验与 {@code deleted} 置 0 在同一命名锁临界区完成。
     *
     * <p>配额维度与顺序同 {@link #saveWithQuotaCheck}；待恢复行及其版本组的已删除版本
     * 字节数合计作为本次恢复量，恢复时同时还原组内版本行。幂等：目标不存在或已恢复时
     * 返回 {@code false}，不抛异常。</p>
     *
     * @param id 文件ID（根文件或任一版本行）
     * @param quota 生效的配额上限
     * @return 是否实际恢复（{@code false} 表示已恢复或目标不存在）
     * @throws com.bluewhale.common.domain.exception.DomainException 超出任一维度配额时抛出 {@code PARAM_ERROR}
     */
    boolean restoreWithQuotaCheck(Long id, StorageQuota quota);
}
