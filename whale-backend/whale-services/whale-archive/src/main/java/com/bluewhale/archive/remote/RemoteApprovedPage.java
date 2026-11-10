package com.bluewhale.archive.remote;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;

/**
 * 工作流已通过实例分页（跨服务内网响应体，避免泛型反序列化歧义）。
 *
 * @author BlueWhale
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class RemoteApprovedPage {

    /** 当前页记录 */
    private List<RemoteApprovedInstance> records;

    /** 总条数 */
    private long total;

    /** 当前页码 */
    private long current;

    /** 每页条数 */
    private long size;
}
