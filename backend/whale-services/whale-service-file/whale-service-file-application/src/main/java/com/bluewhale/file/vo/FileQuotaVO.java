package com.bluewhale.file.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 个人存储配额视图对象。
 *
 * @author BlueWhale
 */
@Data
public class FileQuotaVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 已用字节数（仅统计状态正常且未逻辑删除的文件） */
    private long usedBytes;

    /** 配额字节数；{@code <=0} 表示不限 */
    private long quotaBytes;

    /** 剩余字节数；不限或无法确定用户时为 0 */
    private long remainingBytes;

    /** 是否不限配额（未配置配额或无法确定用户） */
    private boolean unlimited;
}
