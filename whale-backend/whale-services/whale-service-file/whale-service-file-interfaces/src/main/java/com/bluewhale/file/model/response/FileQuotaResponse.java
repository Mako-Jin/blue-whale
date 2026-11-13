package com.bluewhale.file.model.response;

import lombok.Data;

import java.io.Serializable;

/**
 * 个人存储配额响应。
 *
 * @author BlueWhale
 */
@Data
public class FileQuotaResponse implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long usedBytes;
    private Long quotaBytes;
    private Long remainingBytes;
    private Boolean unlimited;
}
