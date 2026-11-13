package com.bluewhale.file.model.response;

import lombok.Data;

import java.io.Serializable;

/**
 * 预签名下载响应。
 *
 * @author BlueWhale
 */
@Data
public class FilePresignedResponse implements Serializable {

    private static final long serialVersionUID = 1L;

    private String url;
    private Integer expiresSeconds;
}
