package com.dianpingplus.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dianpingplus.dto.Result;
import com.dianpingplus.dto.UserDTO;
import com.dianpingplus.entity.Blog;
import com.dianpingplus.entity.BlogComments;
import com.dianpingplus.entity.User;
import com.dianpingplus.mapper.BlogCommentsMapper;
import com.dianpingplus.service.IBlogCommentsService;
import com.dianpingplus.service.IBlogService;
import com.dianpingplus.service.INoticeService;
import com.dianpingplus.service.IUserService;
import com.dianpingplus.utils.ThreadLocalUserUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.Resource;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class BlogCommentsServiceImpl extends ServiceImpl<BlogCommentsMapper, BlogComments> implements IBlogCommentsService {

    @Resource
    private IBlogService blogService;

    @Resource
    private IUserService userService;

    @Resource
    private INoticeService noticeService;

    @Override
    public Result queryByBlog(Long blogId, Integer current) {
        // 分页查询评论
        Page<BlogComments> page = page(
                new Page<>(current, 20),
                new QueryWrapper<BlogComments>()
                        .eq("blog_id", blogId)
                        .orderByDesc("create_time"));

        List<BlogComments> records = page.getRecords();
        if (!records.isEmpty()) {
            // 批量查询评论用户信息
            Map<Long, User> userMap = userService.listByIds(
                    records.stream().map(BlogComments::getUserId).collect(Collectors.toList())
            ).stream().collect(Collectors.toMap(User::getId, u -> u));

            for (BlogComments comment : records) {
                User u = userMap.get(comment.getUserId());
                if (u != null) {
                    comment.setContent("[" + u.getNickName() + "]: " + comment.getContent());
                }
            }
        }

        return Result.ok(records, page.getTotal());
    }

    @Override
    @Transactional
    public Result addComment(BlogComments comment) {
        UserDTO curUser = ThreadLocalUserUtils.getUser();
        if (curUser == null) return Result.fail("请先登录");

        // 校验博客存在
        Blog blog = blogService.getById(comment.getBlogId());
        if (blog == null) return Result.fail("博客不存在");

        comment.setUserId(curUser.getId());
        comment.setLiked(0);
        comment.setStatus(true);
        save(comment);

        // 通知博客作者（自己评论自己不发通知）
        if (!blog.getUserId().equals(curUser.getId())) {
            noticeService.addNotice(blog.getUserId(), 4, "新评论",
                    curUser.getNickName() + " 评论了你的博客：" + comment.getContent(), comment.getBlogId());
        }

        return Result.ok();
    }

    @Override
    @Transactional
    public Result deleteComment(Long id) {
        UserDTO curUser = ThreadLocalUserUtils.getUser();
        if (curUser == null) return Result.fail("请先登录");

        BlogComments comment = getById(id);
        if (comment == null) return Result.fail("评论不存在");

        // 仅评论作者或博客作者可删除
        Blog blog = blogService.getById(comment.getBlogId());
        boolean isCommentOwner = comment.getUserId().equals(curUser.getId());
        boolean isBlogOwner = blog != null && blog.getUserId().equals(curUser.getId());

        if (!isCommentOwner && !isBlogOwner) {
            return Result.fail("无权删除该评论");
        }

        removeById(id);
        return Result.ok();
    }
}
