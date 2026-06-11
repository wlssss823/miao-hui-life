package com.dianpingplus.controller;


import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dianpingplus.dto.Result;
import com.dianpingplus.dto.UserDTO;
import com.dianpingplus.entity.Blog;
import com.dianpingplus.entity.User;
import com.dianpingplus.service.IBlogService;
import com.dianpingplus.service.IUserService;
import com.dianpingplus.utils.SystemConstants;
import com.dianpingplus.utils.ThreadLocalUserUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;
import java.util.List;

/**
 * blog 前端
 */
@RestController
@RequestMapping("/blog")
public class BlogController {

    @Resource
    private IBlogService blogService;
    @Resource
    private IUserService userService;
    @Qualifier("redisTemplate")
    @Autowired
    private RedisTemplate redisTemplate;

    @PostMapping
    public Result saveBlog(@RequestBody Blog blog) {
        return blogService.saveBlog(blog);
    }

    @PutMapping("/like/{id}")
    public Result likeBlog(@PathVariable("id") Long id) {
//        // 修改点赞数量
//        blogService.update()
//                .setSql("liked = liked + 1").eq("id", id).update();
        return blogService.like(id);
    }

    @GetMapping("/likes/{id}")
    public Result queryBlogLikes(@PathVariable("id") Long id){
        return blogService.queryBlogLikes(id);
    }

    @GetMapping("/of/me")
    public Result queryMyBlog(@RequestParam(value = "current", defaultValue = "1") Integer current) {
        // 获取登录用户
        UserDTO user = ThreadLocalUserUtils.getUser();
        // 根据用户查询
        Page<Blog> page = blogService.query()
                .eq("user_id", user.getId()).page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        return Result.ok(records);
    }

    @GetMapping("/of/user")
    public Result queryOtherBlog(@RequestParam(value = "id")Long id ,
                                 @RequestParam(value = "current", defaultValue = "1") Integer current){
        return blogService.queryOtherBlog(id,current);
    }

    @GetMapping("/hot")
    public Result queryHotBlog(@RequestParam(value = "current", defaultValue = "1") Integer current) {
        return blogService.queryHotBlog(current);
    }

    @GetMapping("/{id}")
    public Result getBlogById(@PathVariable Integer id){
        Blog blog = blogService.getBlogById(id);
        if(blog == null)return Result.fail("结果为空");
        return Result.ok(blog);
    }

    @GetMapping("of/follow")
    public Result querySubBlog(@RequestParam("lastId") Long lastId,
                               @RequestParam(value = "offset",defaultValue = "0") Integer offset){
        return blogService.querySubBlog(lastId,offset);
    }

}
