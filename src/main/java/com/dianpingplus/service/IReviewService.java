package com.dianpingplus.service;

import com.dianpingplus.dto.Result;
import com.dianpingplus.entity.Review;
import com.baomidou.mybatisplus.extension.service.IService;

public interface IReviewService extends IService<Review> {

    Result addReview(Review review);

    Result queryByShop(Long shopId, Integer current);
}
