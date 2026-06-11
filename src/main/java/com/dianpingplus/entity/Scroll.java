package com.dianpingplus.entity;


import lombok.Data;

import java.util.List;

@Data
public class Scroll {
    private List<?> list;
    private Long minTime;
    private Integer offset;
}
