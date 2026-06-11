package com.dianpingplus.service;

import com.dianpingplus.dto.Result;
import com.dianpingplus.entity.BlogComments;
import com.baomidou.mybatisplus.extension.service.IService;

public interface IBlogCommentsService extends IService<BlogComments> {

    /**
     * 查询博客的评论列表（分页，时间倒序）
     */
    Result queryByBlog(Long blogId, Integer current);

    /**
     * 添加评论（同时通知博客作者）
     */
    Result addComment(BlogComments comment);

    /**
     * 删除评论（校验当前用户为评论者或博客作者）
     */
    Result deleteComment(Long id);
}
