本地监视网络IO速度
https://user-images.githubusercontent.com/26019986/111859883-0acd2300-897f-11eb-99bf-dc3832339217.mp4

Java VisuaVM监视线程利用效率
https://user-images.githubusercontent.com/26019986/111860187-d8bcc080-8980-11eb-8611-1c2fa8dad16f.mp4





# Socket-Nio

在此项目中:

设计开发了一套基于NIO Reactor模式, 利用Socket进行通讯的Client/Server推送框架。在这个模型中， 设计了自定义协议， 三层缓冲区解决了消息传输的半包，粘包的问题。通过对不同文本类型设计不同的协议(数据装包，分帧)，实现了文件快传；文件混传(SocketChannel交替传输不同文件的数据片实现多文件并发传输, 仿http2 Multiplexing)；心跳机制；服务端桥接2个客户端实现1v1单点语音聊天等实际应用场景。

性能优化: 

在旧版本中，每个SocketChannel的读事件，写事件就绪后，以读满 / 全部写出自定义数据缓冲区为事件处理单元，导致了线程大量时间浪费在Blocked状态。优化过程中，我设计了一个类生产者-消费者模型，将读写事件处理单元粗化为将Socket缓冲区中到达数据全部读出，在Socket缓冲区容量范围内将可写数据全部写出，极大提高单线程工作效率 (Running状态占比80%以上)，而后将高并发SocketChannel读写任务从线程池执行换成单线程执行，同时将同样压测条件下的网络I/O峰值从 ~ 8MB/s 提升到 ~ 38MB/s。
