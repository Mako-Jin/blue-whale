package com.bluewhale.contract.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 印章视图对象。
 *
 * @author BlueWhale
 */
@Data
public class SealVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 印章ID（字符串返回，避免雪花精度丢失） */
    private String id;

    /** 印章名称 */
    private String name;

    /** 印章类型 */
    private String sealType;

    /** 印章图片文件ID */
    private String imageFileId;

    /** 印章保管人用户ID */
    private String keeperUserId;

    /** 有效期起 */
    private LocalDate validFrom;

    /** 有效期止 */
    private LocalDate validTo;

    /** 状态 */
    private String status;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
