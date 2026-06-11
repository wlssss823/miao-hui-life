package com.dianpingplus.controller;

import com.dianpingplus.dto.Result;
import com.dianpingplus.entity.Review;
import com.dianpingplus.service.IReviewService;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;

@RestController
@RequestMapping("/review")
public class ReviewController {

    @Resource
    private IReviewService reviewService;

    @PostMapping
    public Result addReview(@RequestBody Review review) {
        return reviewService.addReview(review);
    }

    @GetMapping("/list/{shopId}")
    public Result queryByShop(@PathVariable("shopId") Long shopId,
                              @RequestParam(value = "current", defaultValue = "1") Integer current) {
        return reviewService.queryByShop(shopId, current);
    }
}
