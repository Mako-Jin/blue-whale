package com.bluewhale.message.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 消息模板视图。
 *
 * @author BlueWhale
 */
@Data
public class MessageTemplateVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 模板ID */
    private Long id;

    /** 模板编码 */
    private String templateCode;

    /** 模板名称 */
    private String templateName;

    /** 标题模板 */
    private String titleTemplate;

    /** 内容模板 */
    private String contentTemplate;

    /** 状态：1 启用 / 0 停用 */
    private Integer status;

    /** 备注 */
    private String remark;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
