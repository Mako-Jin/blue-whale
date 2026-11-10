package com.bluewhale.oa.administrative.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 用章文件实体，对应表 {@code oa_seal_use_file}。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("oa_seal_use_file")
public class SealUseFileEntity extends BaseEntity {

    private Long sealUseId;

    private Long fileId;

    private String fileName;
}
