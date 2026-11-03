package com.bluewhale.video.application.support;

import com.bluewhale.video.application.view.MovieDetailView;
import com.bluewhale.video.application.view.MovieView;
import com.bluewhale.video.domain.model.Movie;

/**
 * 电影视图组装工具：写入电影元数据与海报 / 背景图代理地址。
 *
 * <p>用户侧视图不暴露任何文件 ID，海报与背景图统一通过
 * {@code /video/movies/{id}/poster} 与 {@code /video/movies/{id}/backdrop} 代理。</p>
 *
 * @author BlueWhale
 */
public final class MovieViewAssembler {

    /** 电影图片代理地址前缀 */
    private static final String PATH_PREFIX = "/video/movies/";

    /** 海报代理地址后缀 */
    private static final String POSTER_SUFFIX = "/poster";

    /** 背景图代理地址后缀 */
    private static final String BACKDROP_SUFFIX = "/backdrop";

    /**
     * 私有构造器：工具类，禁止实例化。
     */
    private MovieViewAssembler() {
    }

    /**
     * 写入电影公开元数据与图片代理地址。
     *
     * @param view 目标视图
     * @param movie 电影元数据
     * @param contentId 内容 ID
     */
    public static void applyPublic(MovieView view, Movie movie, Long contentId) {
        view.setOriginalTitle(movie.getOriginalTitle());
        view.setDirector(movie.getDirector());
        view.setActors(movie.getActors());
        view.setRegion(movie.getRegion());
        view.setLanguage(movie.getLanguage());
        view.setReleaseDate(movie.getReleaseDate());
        view.setRating(movie.getRating());
        view.setPosterUrl(posterUrl(contentId, movie.getPosterFileId()));
        view.setBackdropUrl(backdropUrl(contentId, movie.getBackdropFileId()));
    }

    /**
     * 写入电影详情公开元数据与图片代理地址。
     *
     * @param view 目标详情视图
     * @param movie 电影元数据
     * @param contentId 内容 ID
     */
    public static void applyDetail(MovieDetailView view, Movie movie, Long contentId) {
        view.setOriginalTitle(movie.getOriginalTitle());
        view.setDirector(movie.getDirector());
        view.setActors(movie.getActors());
        view.setRegion(movie.getRegion());
        view.setLanguage(movie.getLanguage());
        view.setReleaseDate(movie.getReleaseDate());
        view.setRating(movie.getRating());
        view.setPosterUrl(posterUrl(contentId, movie.getPosterFileId()));
        view.setBackdropUrl(backdropUrl(contentId, movie.getBackdropFileId()));
    }

    /**
     * 构造海报代理地址。
     *
     * @param contentId 内容 ID
     * @param fileId 海报文件 ID
     * @return 代理地址；无海报返回 {@code null}
     */
    public static String posterUrl(Long contentId, Long fileId) {
        return imageUrl(contentId, fileId, POSTER_SUFFIX);
    }

    /**
     * 构造背景图代理地址。
     *
     * @param contentId 内容 ID
     * @param fileId 背景图文件 ID
     * @return 代理地址；无背景图返回 {@code null}
     */
    public static String backdropUrl(Long contentId, Long fileId) {
        return imageUrl(contentId, fileId, BACKDROP_SUFFIX);
    }

    /**
     * 构造图片代理地址。
     *
     * @param contentId 内容 ID
     * @param fileId 图片文件 ID
     * @param suffix 代理地址后缀
     * @return 代理地址；无图片返回 {@code null}
     */
    private static String imageUrl(Long contentId, Long fileId, String suffix) {
        if (contentId == null || fileId == null || fileId <= 0) {
            return null;
        }
        return PATH_PREFIX + contentId + suffix;
    }
}
