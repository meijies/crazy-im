package com.meijie.domain;

import lombok.Data;

@Data
public class Message {
    private long userId1;
    private long userId2;
    private long version;
    private String content;
    private long owner;
}
