package com.dianpingplus.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.dianpingplus.dto.Result;
import com.dianpingplus.entity.Notice;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

public interface INoticeService extends IService<Notice> {

    /**
     * 添加通知（同时推送 SSE）
     */
    void addNotice(Long userId, Integer type, String title, String content, Long relatedId);

    /**
     * 查询用户通知列表（分页，最新在前）
     */
    Result queryNoticeList(Integer current);

    /**
     * 未读数量
     */
    Result unreadCount();

    /**
     * 标记单条已读
     */
    Result markRead(Long id);

    /**
     * 标记全部已读
     */
    Result markAllRead();

    /**
     * 订阅 SSE 实时推送
     */
    SseEmitter subscribe();
}
