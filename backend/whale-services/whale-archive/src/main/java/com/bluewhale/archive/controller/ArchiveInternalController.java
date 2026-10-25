package com.bluewhale.archive.controller;

import com.bluewhale.archive.mapper.ArchiveFileMapper;
import com.bluewhale.archive.vo.ArchiveFileSecurityVO;
import com.bluewhale.common.core.result.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 档案服务内网接口（只读）。
 *
 * <p>服务内路径前缀 {@code /archive/internal}，供 whale-agent 知识库采集查询文件密级；
 * 网关 {@code internal-deny} 对 {@code /archive/internal/**} 统一返回 403，外部不可达，
 * 故不加权限码注解。未被档案引用的文件返回 {@code data=null}，调用方按拒绝索引处理。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping("/archive/internal")
@RequiredArgsConstructor
public class ArchiveInternalController {

    private final ArchiveFileMapper archiveFileMapper;

    /**
     * 查询文件所属档案条目的密级。
     *
     * @param fileId 文件ID
     * @return 密级视图；未归档返回 {@code data=null}
     */
    @GetMapping("/files/{fileId}/security")
    public Result<ArchiveFileSecurityVO> security(@PathVariable Long fileId) {
        return Result.ok(archiveFileMapper.selectSecurityByFileId(fileId));
    }
}
