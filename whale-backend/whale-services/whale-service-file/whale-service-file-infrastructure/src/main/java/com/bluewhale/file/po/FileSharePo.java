package com.bluewhale.file.po;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 文件分享链接实体，对应表 {@code file_share}。
 *
 * <p>公共字段（id / create_time / update_time / deleted）继承自 {@link BaseEntity}；
 * {@code tenant_id} 由租户拦截器自动写入与过滤，PO 不重复声明。</p>
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("file_share")
public class FileSharePo extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 被分享的文件ID */
    private Long fileId;

    /** 创建时的原始文件名快照 */
    private String fileName;

    /** 分享令牌 */
    private String token;

    /** 密码哈希；为空表示无密码 */
    private String passwordHash;

    /** 失效时间；为空表示长期有效 */
    private LocalDateTime expireTime;

    /** 下载次数上限；为空表示不限 */
    private Integer maxDownloads;

    /** 已下载次数 */
    private Integer downloadCount;

    /** 状态：1 有效 / 0 已撤销 */
    private Integer status;

    /** 创建人用户 ID */
    private Long creatorId;
}
