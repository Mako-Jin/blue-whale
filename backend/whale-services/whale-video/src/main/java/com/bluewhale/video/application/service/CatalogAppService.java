package com.bluewhale.video.application.service;

import com.bluewhale.video.application.view.CategoryView;
import com.bluewhale.video.application.view.CreatorView;
import com.bluewhale.video.application.view.TagView;

import java.util.List;

/**
 * 目录应用服务：分类 / 标签 / 创作者资料查询。
 *
 * @author BlueWhale
 */
public interface CatalogAppService {

    /**
     * 查询启用分类。
     *
     * @return 分类列表
     */
    List<CategoryView> categories();

    /**
     * 查询全部标签。
     *
     * @return 标签列表
     */
    List<TagView> tags();

    /**
     * 查询创作者资料（不存在按不存在处理）。
     *
     * @param creatorId 创作者 ID
     * @return 创作者视图
     */
    CreatorView creator(Long creatorId);

    /**
     * 读取创作者头像文件 ID（仅接口层静态代理内部使用，不对外暴露）。
     *
     * @param creatorId 创作者 ID
     * @return 头像文件 ID；无头像返回 {@code null}
     */
    Long avatarFileId(Long creatorId);
}
