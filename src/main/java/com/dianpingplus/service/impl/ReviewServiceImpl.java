package com.dianpingplus.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dianpingplus.dto.Result;
import com.dianpingplus.entity.Review;
import com.dianpingplus.entity.User;
import com.dianpingplus.entity.VoucherOrder;
import com.dianpingplus.mapper.ReviewMapper;
import com.dianpingplus.service.INoticeService;
import com.dianpingplus.service.IReviewService;
import com.dianpingplus.service.IUserService;
import com.dianpingplus.service.IVoucherOrderService;
import com.dianpingplus.utils.ThreadLocalUserUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.Resource;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ReviewServiceImpl extends ServiceImpl<ReviewMapper, Review> implements IReviewService {

    @Resource
    private IVoucherOrderService voucherOrderService;

    @Resource
    private IUserService userService;

    @Resource
    private INoticeService noticeService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    @Transactional
    public Result addReview(Review review) {
        Long userId = ThreadLocalUserUtils.getUser().getId();

        // 校验订单：已核销、属于当前用户
        VoucherOrder order = voucherOrderService.getById(review.getOrderId());
        if (order == null || !order.getUserId().equals(userId) || order.getStatus() != 3) {
            return Result.fail("订单状态异常，无法评价");
        }

        // 校验评分范围
        if (review.getRating() == null || review.getRating() < 1 || review.getRating() > 5) {
            return Result.fail("评分必须在 1-5 之间");
        }

        // 校验图片数量（最多 9 张）
        if (review.getImages() != null) {
            String[] imgs = review.getImages().split(",");
            if (imgs.length > 9) {
                return Result.fail("最多上传 9 张图片");
            }
        }

        review.setUserId(userId);
        review.setShopId(order.getVoucherId());
        save(review);

        // 写入 Redis ZSet：店铺评价列表（按时间排序）
        String reviewKey = "review:shop:" + review.getShopId();
        stringRedisTemplate.opsForZSet().add(reviewKey, review.getId().toString(), System.currentTimeMillis());

        // 发送评价成功通知
        noticeService.addNotice(userId, 5, "评价成功",
                "您的评价已提交，感谢您的分享", review.getId());

        return Result.ok();
    }

    @Override
    public Result queryByShop(Long shopId, Integer current) {
        Page<Review> page = page(new Page<>(current, 10),
                new QueryWrapper<Review>().eq("shop_id", shopId).orderByDesc("create_time"));

        List<Review> reviews = page.getRecords();
        if (!reviews.isEmpty()) {
            // 批量查询用户信息
            List<Long> userIds = reviews.stream().map(Review::getUserId).collect(Collectors.toList());
            List<User> users = userService.listByIds(userIds);
            Map<Long, User> userMap = users.stream().collect(Collectors.toMap(User::getId, u -> u));
            // 填充用户信息
            reviews.forEach(r -> {
                User u = userMap.get(r.getUserId());
                if (u != null) {
                    r.setName(u.getNickName());
                    r.setIcon(u.getIcon());
                }
            });
        }

        return Result.ok(reviews, page.getTotal());
    }
}
