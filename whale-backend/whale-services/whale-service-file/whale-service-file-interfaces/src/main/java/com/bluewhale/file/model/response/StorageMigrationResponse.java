package com.bluewhale.file.model.response;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 存储迁移响应。
 *
 * @author BlueWhale
 */
@Data
public class StorageMigrationResponse implements Serializable {

    private static final long serialVersionUID = 1L;

    private String sourceStorageType;
    private String targetStorageType;
    private Integer scanned;
    private Integer migrated;
    private Integer skipped;
    private Integer failed;
    private Boolean dryRun;
    private List<String> failures;
}
