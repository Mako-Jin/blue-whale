package com.bluewhale.music.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

/**
 * 专辑实体，对应表 {@code mu_album}。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mu_album")
public class AlbumEntity extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 歌手 ID */
    private Long artistId;

    /** 专辑标题 */
    private String title;

    /** 封面文件 ID */
    private Long coverFileId;

    /** 发行日期 */
    private LocalDate releaseDate;

    /** 专辑类型 */
    private String albumType;

    /** 简介 */
    private String intro;

    /** 发布状态：DRAFT / PUBLISHED / OFFLINE */
    private String publishStatus;

    /** 排序 */
    private Integer sortNo;
}
