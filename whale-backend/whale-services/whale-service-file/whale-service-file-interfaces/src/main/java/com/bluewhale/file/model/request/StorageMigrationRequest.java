package com.bluewhale.file.model.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serializable;

/**
 * 存储迁移请求。
 *
 * @author BlueWhale
 */
@Data
public class StorageMigrationRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 源落盘存储类型 */
    @NotBlank(message = "源存储类型不能为空")
    private String sourceStorageType;

    /** 桶过滤条件；为空表示不限桶 */
    private String bucket;

    /** 单批最大处理条数 */
    @Min(value = 1, message = "单批条数必须大于 0")
    @Max(value = 500, message = "单批条数不能超过 500")
    private long limit = 100L;

    /** 是否只统计不执行 */
    private boolean dryRun;

    /** 迁移成功后是否删除源对象 */
    private boolean deleteSource;

    /** 执行确认：非 dry-run 时必须为 true */
    private boolean confirm;
}
