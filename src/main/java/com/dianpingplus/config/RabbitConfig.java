package com.dianpingplus.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class RabbitConfig {

    @Bean("directExchange")
    public Exchange exchange(){
        return ExchangeBuilder.directExchange("amq.direct").build();
    }

    @Bean("orderQueue")
    public Queue queue(){
        return QueueBuilder.durable("order_queue")
                .build();
    }

    @Bean("orderBinding")
    public Binding binding(@Qualifier("orderQueue") Queue queue,
                           @Qualifier("directExchange") Exchange exchange){
        return BindingBuilder.bind(queue)
                .to(exchange)
                .with("dianping.order")
                .noargs();
    }

    // ========== 超时未支付取消（死信队列） ==========

    /**
     * 延迟队列：消息 30 分钟未被消费 → 进入死信
     */
    @Bean("orderDelayQueue")
    public Queue orderDelayQueue() {
        Map<String, Object> args = new HashMap<>();
        args.put("x-dead-letter-exchange", "amq.direct");
        args.put("x-dead-letter-routing-key", "dianping.order.cancel");
        args.put("x-message-ttl", 30 * 60 * 1000); // 30 分钟
        return QueueBuilder.durable("order_delay_queue").withArguments(args).build();
    }

    @Bean("delayBinding")
    public Binding delayBinding(@Qualifier("orderDelayQueue") Queue queue,
                                @Qualifier("directExchange") Exchange exchange) {
        return BindingBuilder.bind(queue)
                .to(exchange)
                .with("dianping.order.delay")
                .noargs();
    }

    /**
     * 取消订单队列：接收从死信转过来的超时订单
     */
    @Bean("orderCancelQueue")
    public Queue orderCancelQueue() {
        return QueueBuilder.durable("order_cancel_queue").build();
    }

    @Bean("cancelBinding")
    public Binding cancelBinding(@Qualifier("orderCancelQueue") Queue queue,
                                 @Qualifier("directExchange") Exchange exchange) {
        return BindingBuilder.bind(queue)
                .to(exchange)
                .with("dianping.order.cancel")
                .noargs();
    }

    @Bean
    public MessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
