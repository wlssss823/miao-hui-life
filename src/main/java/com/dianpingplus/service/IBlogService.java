package com.dianpingplus.service;

import com.dianpingplus.dto.Result;
import com.dianpingplus.entity.Blog;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IBlogService extends IService<Blog> {
    /**
     * 点赞
     * @param id
     * @return
     */
    Result like(Long id);

    /**
     *
     * @param id
     */
    Blog getBlogById(Integer id);

    /**
     * 查询最热博客
     * @param current
     * @return
     */
    Result queryHotBlog(Integer current);

    /**
     * 查询博客最早点赞人
     * @param id
     * @return
     */
    Result queryBlogLikes(Long id);

    /**
     * 查询其他用户的博客
     * @param id
     * @param current
     * @return
     */
    Result queryOtherBlog(Long id, Integer current);

    /**
     * 保存博客
     * @param blog
     * @return
     */
    Result saveBlog(Blog blog);

    /**
     * 查询关注人的博客
     * @param lastId
     * @param offset
     * @return
     */
    Result querySubBlog(Long lastId, Integer offset);
}
