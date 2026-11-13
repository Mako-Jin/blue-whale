package com.bluewhale.file.domain.file.repository;

import com.bluewhale.file.domain.file.aggregate.FileAsset;

import java.time.LocalDateTime;
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
    FileAsset save(FileAsset asset);

    /**
     * 在个人配额内原子保存文件资产：配额校验与写入在同一临界区完成，避免并发上传穿透配额。
     *
     * <p>不限配额（{@code quotaBytes <= 0}）或无上传用户时退化为普通保存。</p>
     *
     * @param asset 文件资产聚合根
     * @param quotaBytes 单用户配额字节数；{@code <=0} 表示不限
     * @return 回填主键后的文件资产聚合根
     * @throws com.bluewhale.common.domain.exception.DomainException 超出配额时抛出 {@code PARAM_ERROR}
     */
    FileAsset saveWithQuotaCheck(FileAsset asset, long quotaBytes);

    /**
     * 按主键查询未逻辑删除的文件资产
     *
     * @param id 文件ID
     * @return 文件资产；不存在或已逻辑删除时返回空
     */
    Optional<FileAsset> findById(Long id);

    /**
     * 按主键查询文件资产（包含已逻辑删除的行，供变更详情使用）
     *
     * @param id 文件ID
     * @return 文件资产；不存在时返回空
     */
    Optional<FileAsset> findByIdIncludingDeleted(Long id);

    /**
     * 按 MD5 + 大小查询可复用的正常文件（秒传去重依据，按主键升序取第一条）
     *
     * <p>入参摘要大小写不敏感：实现内部统一归一化为小写后查询；空白摘要直接视为未命中。</p>
     *
     * @param md5 MD5 摘要（十六进制，大小写不敏感）
     * @param fileSize 文件大小（字节）
     * @return 可复用的文件资产；未命中时返回空
     */
    Optional<FileAsset> findReusableByDigest(String md5, long fileSize);

    /**
     * 逻辑删除文件资产
     *
     * @param id 文件ID
     */
    void deleteById(Long id);

    /**
     * 统计指定上传者的已用存储字节数（仅统计状态正常且未逻辑删除的文件）
     *
     * @param uploaderId 上传用户 ID
     * @return 已用字节数；无记录时返回 0
     */
    long sumNormalSizeByUploader(Long uploaderId);

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
}
