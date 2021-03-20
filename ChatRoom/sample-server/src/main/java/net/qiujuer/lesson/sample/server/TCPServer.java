package net.qiujuer.lesson.sample.server;

import net.qiujuer.lesson.sample.foo.Foo;
import net.qiujuer.lesson.sample.foo.audioroom.privateRoom;
import net.qiujuer.lesson.sample.foo.audioroom.publicRoom;
import net.qiujuer.lesson.sample.foo.handle.ConnectorCloseChain;
import net.qiujuer.lesson.sample.foo.handle.ConnectorHandler;
import net.qiujuer.lesson.sample.foo.handle.ConnectorStringPacketChain;
import net.qiujuer.library.clink.box.StringReceivePacket;
import net.qiujuer.library.clink.core.Connector;
import net.qiujuer.library.clink.utils.CloseUtils;
import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class TCPServer implements ServerAcceptor.AcceptListener,
        Group.GroupMessageAdapter {
    private final int port;
    private final File cachePath;
    private final List<ConnectorHandler> connectorHandlerList = new ArrayList<>();
    private ServerAcceptor acceptor;
    private ServerSocketChannel server;
    private final ServerStatistics statistics = new ServerStatistics();
    private final Map<String, Group> groups = new HashMap<>();
    private final Map<String, privateRoom> privateRooms = new HashMap<>();
    private final Map<String, publicRoom> publicRooms = new HashMap<>();

    TCPServer(int port, File cachePath) {
        this.port = port;
        this.cachePath = cachePath;
        this.groups.put(Foo.DEFAULT_GROUP_NAME, new Group(Foo.DEFAULT_GROUP_NAME, this));
    }


    boolean start() {
        try {
            // 启动Acceptor线程
            ServerAcceptor acceptor = new ServerAcceptor(this);
            ServerSocketChannel server = ServerSocketChannel.open();
            // 设置为非阻塞
            server.configureBlocking(false);
            // 绑定本地端口
            server.socket().bind(new InetSocketAddress(port));
            // 注册客户端连接到达监听
            server.register(acceptor.getSelector(), SelectionKey.OP_ACCEPT);

            this.server = server;
            this.acceptor = acceptor;

            // 线程需要启动
            acceptor.start();
            if (acceptor.awaitRunning()) {
                System.out.println("服务器准备就绪～");
                System.out.println("服务器信息：" + server.getLocalAddress().toString());
                return true;
            } else {
                System.out.println("启动异常！");
                return false;
            }
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }



    /**
     * 关闭操作
     */
    void stop() {
        if (acceptor != null) { acceptor.exit(); }

        ConnectorHandler[] connectorHandlers;
        synchronized (connectorHandlerList) {
            connectorHandlers = connectorHandlerList.toArray(new ConnectorHandler[0]);
            connectorHandlerList.clear();
        }
        for (ConnectorHandler connectorHandler : connectorHandlers) {
            connectorHandler.exit();
        }

        CloseUtils.close(server);
    }


    /**
     * 进行广播发送，发给所有客户端
     *
     * @param str 消息
     */
    void broadcast(String str) {
        str = "系统通知：" + str;
        ConnectorHandler[] connectorHandlers;
        synchronized (connectorHandlerList) {
            connectorHandlers = connectorHandlerList.toArray(new ConnectorHandler[0]);
        }
        for (ConnectorHandler connectorHandler : connectorHandlers) {
            sendMessageToClient(connectorHandler, str);
        }
    }


    /**
     * 发送消息给某个客户端
     *
     * @param handler 客户端
     * @param msg     消息
     */
    @Override
    public void sendMessageToClient(ConnectorHandler handler, String msg) {
        handler.send(msg);
        statistics.sendSize++;
    }


    /**
     * 获取当前的状态信息
     */
    Object[] getStatusString() {
        return new String[]{
                "客户端数量：" + connectorHandlerList.size(),
                "发送数量：" + statistics.sendSize,
                "接收数量：" + statistics.receiveSize
        };
    }


    /**
     * 新客户端链接时回调
     *
     * @param channel 新客户端
     */
    @Override
    public void onNewSocketArrived(SocketChannel channel) {
        try {
            ConnectorHandler connectorHandler = new ConnectorHandler(channel, cachePath);
            System.out.println(connectorHandler.getClientInfo() + ":Connected!");

            // 收到消息的处理责任链
            connectorHandler.getStringPacketChain()
                    .appendLast(statistics.statisticsChain())
                    .appendLast(new ParseCommandConnectorStringPacketChain());

            // 关闭链接的责任链
            connectorHandler.getCloseChain().appendLast(new RemoveQueueOnConnectorClosedChain());

            // 空闲任务: 心跳包
//            ScheduleJob scheduleJob = new IdleTimeoutScheduleJob(20, TimeUnit.SECONDS, connectorHandler);
//            connectorHandler.schedule(scheduleJob);

            synchronized (connectorHandlerList) {
                connectorHandlerList.add(connectorHandler);
                System.out.println("当前客户端数量：" + connectorHandlerList.size());
            }
        } catch (IOException e) {
            e.printStackTrace();
            System.out.println("客户端链接异常：" + e.getMessage());
        }
    }


    /**
     * 移除队列，在链接关闭回调时
     */
    private class RemoveQueueOnConnectorClosedChain extends ConnectorCloseChain {

        @Override
        protected boolean consume(ConnectorHandler handler, Connector connector) {
            synchronized (connectorHandlerList) {
                connectorHandlerList.remove(handler);
            }
            // 移除群聊的客户端
            Group group = groups.get(Foo.DEFAULT_GROUP_NAME);
            group.removeMember(handler);
            return true;
        }
    }


    /**
     * 解析收到的消息，当前节点主要做命令的解析，
     * 如果子节点也未进行数据消费，那么则进行二次消费，直接返回收到的数据
     */
    private class ParseCommandConnectorStringPacketChain extends ConnectorStringPacketChain {
        @Override
        protected boolean consume(ConnectorHandler handler, StringReceivePacket stringReceivePacket) {
            String str = stringReceivePacket.entity();

            if (str.startsWith(Foo.COMMAND_ENTER_ROOM)) {
                //进入单聊房间
                String roomname = str.substring(Foo.COMMAND_ENTER_ROOM.length());
                privateRoom privateRoom = privateRooms.get(roomname);
                if(privateRoom == null) {
                    privateRoom = new privateRoom(roomname);
                    privateRooms.put(roomname, privateRoom);
                }

                if(!privateRoom.enterRoom(handler)) {
                    System.out.println("不能加入此单聊房间!");
                    return true;
                }

                if(privateRoom.getConnectors().length == 2){
                    privateRoom.changetoBridge();
                }
                return true;

            } else if (str.startsWith(Foo.COMMAND_LEAVE_ROOM)) {
                //离开单聊房间
                String roomname = str.substring(Foo.COMMAND_ENTER_ROOM.length());
                privateRoom privateRoom = privateRooms.remove(roomname);
                if(privateRoom == null || !privateRoom.exitRoom(handler)) {
                    System.out.println("单聊房间不存在, 或者你不在房间");
                }
                return true;
            }

            return false;
        }

        @Override
        protected boolean consumeAgain(ConnectorHandler handler, StringReceivePacket stringReceivePacket) {
            // 捡漏的模式，当我们第一遍未消费，然后又没有加入到群，自然没有后续的节点消费, 此时我们进行二次消费，返回发送过来的消息
            sendMessageToClient(handler, stringReceivePacket.entity());
            return true;
        }
    }
}
