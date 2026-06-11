package com.dianpingplus.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dianpingplus.dto.Result;
import com.dianpingplus.dto.UserDTO;
import com.dianpingplus.entity.Blog;
import com.dianpingplus.entity.Follow;
import com.dianpingplus.entity.Scroll;
import com.dianpingplus.entity.User;
import com.dianpingplus.mapper.BlogMapper;
import com.dianpingplus.service.IBlogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dianpingplus.service.IFollowService;
import com.dianpingplus.service.INoticeService;
import com.dianpingplus.service.IUserService;
import com.dianpingplus.utils.SystemConstants;
import com.dianpingplus.utils.ThreadLocalUserUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static com.dianpingplus.utils.RedisConstants.BLOG_LIKED_KEY;
import static com.dianpingplus.utils.RedisConstants.FEED_KEY;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private IUserService userService;

    @Resource
    private IFollowService followService;

    @Resource
    private INoticeService noticeService;

    @Override
    public Result queryHotBlog(Integer current) {
        // 根据用户查询
        Page<Blog> page = query()
                .orderByDesc("liked")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        // 批量查询用户信息，避免 N+1
        if (!records.isEmpty()) {
            Map<Long, User> userMap = userService.listByIds(
                    records.stream().map(Blog::getUserId).collect(Collectors.toList())
            ).stream().collect(Collectors.toMap(User::getId, u -> u));

            records.forEach(blog -> {
                User u = userMap.get(blog.getUserId());
                if (u != null) {
                    blog.setName(u.getNickName());
                    blog.setIcon(u.getIcon());
                }
                setBlogIsLiked(blog);
            });
        }
        return Result.ok(records);
    }

    @Override
    public Result queryOtherBlog(Long id, Integer current) {
        // 根据用户查询
        Page<Blog> page = query()
                .eq("user_id", id).page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        return Result.ok(records);
    }

    @Override
    public Result like(Long id) {
        Long userId = ThreadLocalUserUtils.getUser().getId();
        String key = BLOG_LIKED_KEY + id;
        boolean res;
//        //sismember
//        Boolean isMember = stringRedisTemplate.opsForSet().isMember(key, userId.toString());
        // ~= isMember Zset替换
        Double score = stringRedisTemplate.opsForZSet().score(key, userId.toString());
        //isMember 为true时，已点赞，不能重复点赞
        if(score == null){
            //未点赞，点赞加1
            res = update()
                    .eq("id", id)
                    .setSql("liked = liked + 1")
                    .update();
            if(BooleanUtil.isFalse(res))return Result.fail("点赞失败");

            stringRedisTemplate.opsForZSet().add(key, userId.toString(), System.currentTimeMillis());

            // 通知博客作者有人点赞
            Blog blog = getById(id);
            if (blog != null && !blog.getUserId().equals(userId)) {
                UserDTO curUser = ThreadLocalUserUtils.getUser();
                noticeService.addNotice(blog.getUserId(), 4, "新点赞",
                        curUser != null ? curUser.getNickName() + " 赞了你的博客" : "有人赞了你的博客", id);
            }
        }else{
            //已点赞
            res = update()
                    .eq("id", id)
                    .setSql("liked = liked - 1")
                    .update();
            if(BooleanUtil.isFalse(res))return Result.fail("取消点赞失败");

            stringRedisTemplate.opsForZSet().remove(key, userId.toString());
        }

        return Result.ok();
    }

    @Override
    public Blog getBlogById(Integer id) {
        Blog blog = this.getById(id);
        if(blog == null)return null;
        queryBlogUser(blog);
        setBlogIsLiked(blog);

        return blog;
    }

    private void queryBlogUser(Blog blog) {
        Long blogUserId = blog.getUserId();
        User user = userService.getById(blogUserId);
        //设置用户信息
        blog.setIcon(user.getIcon());
        blog.setName(user.getNickName());
    }

    @Override
    public Result queryBlogLikes(Long id) {
        String key = BLOG_LIKED_KEY + id;
        //获取blog的Zset,提取score最高的几个
        Set<String> userSets = stringRedisTemplate.opsForZSet().range(key, 0, 4);
        if(userSets == null || userSets.isEmpty())return Result.ok(Collections.emptyList());

        // 批量查询用户信息，避免 N+1
        List<Long> userIds = userSets.stream().map(Long::valueOf).collect(Collectors.toList());
        Map<Long, User> userMap = userService.listByIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u));

        List<UserDTO> userDTOList = new ArrayList<>(userIds.size());
        for (Long uid : userIds) {
            User user = userMap.get(uid);
            if (user != null) {
                UserDTO dto = new UserDTO();
                BeanUtil.copyProperties(user, dto);
                userDTOList.add(dto);
            }
        }
        return Result.ok(userDTOList);
    }

    private void setBlogIsLiked(Blog blog) {
        UserDTO user = ThreadLocalUserUtils.getUser();
        if(user == null)return;

        Long userId = user.getId();
        String key = BLOG_LIKED_KEY + blog.getId();
        //查询是否点赞
        Double score = stringRedisTemplate.opsForZSet().score(key, userId.toString());
        blog.setIsLike(score != null);
    }

    @Override
    public Result saveBlog(Blog blog) {
        // 获取登录用户
        UserDTO user = ThreadLocalUserUtils.getUser();
        blog.setUserId(user.getId());
        //判断必选项（为什么不是前端做
        if(blog.getShopId() == null)return Result.fail("请选择商店");
        if(StringUtils.isBlank(blog.getImages()) || blog.getImages() == null)return Result.fail("请上传图片");
        // 保存探店博文
        boolean save = save(blog);
        if(!save)return Result.fail("保存失败");
        //查询所有粉丝，推送
        List<Follow> follows = followService.query().eq("follow_user_id", user.getId()).list();
        for (Follow follow : follows) {
            //推送到粉丝ID
            Long userId = follow.getUserId();
            String key = FEED_KEY + userId;
            stringRedisTemplate.opsForZSet().add(key, blog.getId().toString(), System.currentTimeMillis());
        }
        // 返回id
        return Result.ok(blog.getId());
    }

    @Override
    public Result querySubBlog(Long lastId, Integer offset) {
        //获取自己信箱 ZREVRANGEBYSCORE key Max Min LIMIT offset count
        // score 值介于 max 和 min 之间 (默认包括等于 max 或 min )的所有的成员
        String key = FEED_KEY + ThreadLocalUserUtils.getUser().getId();
        //获取当前用户信箱中lastId之前最新的2篇博文降序排列
        Set<ZSetOperations.TypedTuple<String>> typedTuples = stringRedisTemplate.opsForZSet().
                reverseRangeByScoreWithScores(key, 0, lastId, offset, 2);
        //如果空说明没新博文
        if(typedTuples == null || typedTuples.isEmpty())return Result.ok();

        //解析获取到的数据
        List<Long> ids = new ArrayList<>(typedTuples.size());
        //确保
        long minTime = 0;
        //记录与 minTime 时间戳相同的元素的数量，防止下次请求获取重复数据
        int os = 1;
        for(ZSetOperations.TypedTuple<String> tuple : typedTuples){
            ids.add(Long.valueOf(tuple.getValue()));
            long time = tuple.getScore().longValue();
            if(time == minTime){
                //确保下次查询跳过所有已看过博客
                os++;
            }else{
                minTime = time;
                //如果时间戳不一致重置offset
                os = 1;
            }
        }


        //根据id查blog
        String idStr = StrUtil.join(",", ids);
        List<Blog> blogs = query().in("id", ids).last("ORDER BY FIELD(id," + idStr + ")").list();

        // 批量查询用户信息，避免 N+1
        Map<Long, User> userMap = userService.listByIds(
                blogs.stream().map(Blog::getUserId).collect(Collectors.toList())
        ).stream().collect(Collectors.toMap(User::getId, u -> u));

        UserDTO curUser = ThreadLocalUserUtils.getUser();

        for (Blog blog : blogs) {
            // 批量映射用户信息
            User u = userMap.get(blog.getUserId());
            if (u != null) {
                blog.setIcon(u.getIcon());
                blog.setName(u.getNickName());
            }
            // 查询blog是否被点赞
            if (curUser != null) {
                String likedKey = BLOG_LIKED_KEY + blog.getId();
                Double score = stringRedisTemplate.opsForZSet().score(likedKey, curUser.getId().toString());
                blog.setIsLike(score != null);
            }
        }

        Scroll s = new Scroll();
        s.setList(blogs);
        s.setMinTime(minTime);
        //下次请求就会跳过前os个元素
        s.setOffset(os);

        return Result.ok(s);
    }
}
