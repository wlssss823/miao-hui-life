package com.dianpingplus.controller;

import com.dianpingplus.dto.Result;
import com.dianpingplus.entity.BlogComments;
import com.dianpingplus.service.IBlogCommentsService;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;

@RestController
@RequestMapping("/blog-comments")
public class BlogCommentsController {

    @Resource
    private IBlogCommentsService blogCommentsService;

    /**
     * 查询博客评论列表
     */
    @GetMapping("/{blogId}")
    public Result list(@PathVariable("blogId") Long blogId,
                       @RequestParam(defaultValue = "1") Integer current) {
        return blogCommentsService.queryByBlog(blogId, current);
    }

    /**
     * 添加评论
     */
    @PostMapping
    public Result add(@RequestBody BlogComments comment) {
        return blogCommentsService.addComment(comment);
    }

    /**
     * 删除评论
     */
    @DeleteMapping("/{id}")
    public Result delete(@PathVariable("id") Long id) {
        return blogCommentsService.deleteComment(id);
    }
}
