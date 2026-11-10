package com.bluewhale.i18n.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 国际化消息实体（对齐 sys_i18n_message DDL）
 *
 * @author BlueWhale
 */
@Data
@TableName("sys_i18n_message")
public class SysI18nMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键 ID（雪花算法） */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 消息编码，如 login.success */
    private String messageKey;

    /** 语言环境，如 zh_CN、en_US */
    private String locale;

    /** 消息内容（对应语言环境的文案） */
    private String messageContent;

    /** 所属业务模块 */
    private String module;

    /** 备注 */
    private String remark;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /** 逻辑删除：0 未删除 / 1 已删除 */
    @TableLogic
    private Integer deleted;
}
