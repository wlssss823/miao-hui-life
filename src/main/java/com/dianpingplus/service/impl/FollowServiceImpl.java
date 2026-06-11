package com.dianpingplus.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.dianpingplus.dto.Result;
import com.dianpingplus.dto.UserDTO;
import com.dianpingplus.entity.Follow;
import com.dianpingplus.entity.User;
import com.dianpingplus.mapper.FollowMapper;
import com.dianpingplus.service.IFollowService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dianpingplus.service.INoticeService;
import com.dianpingplus.service.IUserService;
import com.dianpingplus.utils.ThreadLocalUserUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements IFollowService {
    private final StringRedisTemplate stringRedisTemplate;

    public FollowServiceImpl(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Resource
    private IUserService userService;

    @Resource
    private INoticeService noticeService;

    @Override
    public Result follow(Long id, Boolean op) {
        //table : id user_id follow_user_id create_time
        Long userId = ThreadLocalUserUtils.getUser().getId();
        String key = "follow:" + userId;
        //op == true是关注
        if(op){
            if(userId.equals(id))return Result.fail("不能关注自己");

            Follow follow = new Follow();
            follow.setUserId(userId);
            follow.setFollowUserId(id);
            boolean save = save(follow);
            if(!save)return Result.fail("关注失败");
            stringRedisTemplate.opsForSet().add(key, id.toString());

            // 通知被关注者
            UserDTO curUser = ThreadLocalUserUtils.getUser();
            noticeService.addNotice(id, 5, "新粉丝",
                    curUser != null ? curUser.getNickName() + " 关注了你" : "有人关注了你", userId);

        }else{
            boolean remove = remove(new QueryWrapper<Follow>()
                    .eq("user_id", userId).eq("follow_user_id", id));
            if(!remove)return Result.fail("取消关注失败");
            stringRedisTemplate.opsForSet().remove(key, id.toString());
        }
        return Result.ok();
    }

    @Override
    public Result isFollow(Long id) {
        Long userId = ThreadLocalUserUtils.getUser().getId();
        //select count(*) from follow where user_id = ? and follow_user_id = ?
        Long count = query().eq("user_id", userId).eq("follow_user_id", id).count();
        return count > 0 ? Result.ok(true) : Result.ok(false);
    }

    @Override
    public Result followCommons(Long id) {
        Long userId = ThreadLocalUserUtils.getUser().getId();
        String curKey = "follow:" + userId;
        String aimKey = "follow:" + id;
        //求交集
        Set<String> intersect = stringRedisTemplate.opsForSet().intersect(curKey, aimKey);
        //解析
        if(intersect == null || intersect.isEmpty())return Result.ok();

        List<UserDTO> userDTOList = new ArrayList<>(intersect.size());
        intersect.forEach(userIdStr -> {
            UserDTO userDTO = new UserDTO();
            User user = userService.getById(Long.getLong(userIdStr));
            BeanUtils.copyProperties(user, userDTO);
            userDTOList.add(userDTO);
        });

        return Result.ok(userDTOList);
    }
}
