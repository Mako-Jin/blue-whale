package com.bluewhale.scene.application.command;

import com.bluewhale.scene.domain.model.SceneQuery;
import lombok.Data;

/**
 * 场景分页查询命令。
 *
 * @author BlueWhale
 */
@Data
public class SceneQueryCommand {

    /** 关键字：场景标识 / 名称 */
    private String keyword;

    /** 状态过滤 */
    private String status;

    /** 当前页（从 1 开始） */
    private Long current = 1L;

    /** 每页条数 */
    private Long size = 20L;

    /**
     * 转换为领域查询对象，并收敛分页边界。
     *
     * @return 领域查询对象
     */
    public SceneQuery toQuery() {
        SceneQuery query = new SceneQuery();
        query.setKeyword(keyword == null ? null : keyword.trim());
        query.setStatus(status);
        query.setCurrent(current == null || current < 1 ? 1L : current);
        query.setSize(size == null || size < 1 ? 20L : Math.min(size, 100L));
        return query;
    }
}
