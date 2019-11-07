# Crazy IM
Crazy IM的目标是实现一个最基础的分布式即时通信系统，当前实现了一个简陋的单机版本，当前正开始设计并实现一个分布式版本

## 分布式Crazy IM架构设计(讨论阶段)

TODO 配图。

Crazy IM是CS架构，服务端将会成为性能瓶颈，在设计过程中，我们尽量让客户端做更多的事。单独拿服务端来说，它又是主从架构。
Master管理所有Work Node, 并定时收集Work Node负载信息，从而进行负载均衡。客户端在发起通信之前，先访问Master来获得整个集群的元数据
并缓存到客户端，客户端会为不同的通信会话选择特定的Work Node进行通信，直到该Work Node失效（特别是群聊，如何保证某个群聊的消息都发送到同
一个Work Node？，该Work Node失效如何选举新的Work Node呢？这个选举的时间要多久呢？）

当某个Work Node失效时，我们需要防止短时间内，大量与该Work Node通信的客户端将负载迁移到某个负载低的Work Node，
从而导致该Work Node负载过高。

客户端主要发送两类消息，一种是文本消息，另一种是大文件消息，服务端将分别存储它们。发送大文件的时候，首先将大文件上传到分布式文件系统或者
放到K-V存储系统中，并返回该文件的唯一标识；然后客户端会再将该文件的元数据以及获得的唯一标识组织成文本消息发送到服务端，以便于后续搜索。
在存储会话消息的时候，会给每个消息加上一个唯一的版本号。客户端会带着这个版本号来请求新消息，服务端将返回大于这个版本号的消息。前面提到，
我们会保证某个私聊或群聊的消息总是发送到同一个Work Node，那么我们只要保证version在单机上的增顺。（如果这种方式不行，将采用来redis等其他
手段来确保version的唯一和有序，不过导致获取version的成本增加了),

## 短期目标
* 完善架构设计
* 进行接口设计
* 实现简单的私聊和群聊

## 文件存储
由于发送的文件大小可能差异很大，需要存储系统在大文件和小文件的情况下都有很好的性能，如果对于特别小的文本文件，
直接把它转换成文本消息可能也是一种很好的选择

### 当前可供选择的文件存储系统：
+ [linkedin ambry](https://github.com/linkedin/ambry)

## 协议
Apache License 2.0
