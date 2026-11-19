package com.bluewhale.message.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 消息模板实体，对应表 {@code msg_template}。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("msg_template")
public class MessageTemplate extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 模板编码（租户内逻辑唯一） */
    private String templateCode;

    /** 模板名称 */
    private String templateName;

    /** 标题模板，支持 ${key} 占位符 */
    private String titleTemplate;

    /** 内容模板，支持 ${key} 占位符 */
    private String contentTemplate;

    /** 状态：1 启用 / 0 停用 */
    private Integer status;

    /** 备注 */
    private String remark;
}
