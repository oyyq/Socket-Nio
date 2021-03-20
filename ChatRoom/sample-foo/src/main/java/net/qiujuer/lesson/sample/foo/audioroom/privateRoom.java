package net.qiujuer.lesson.sample.foo.audioroom;

import net.qiujuer.lesson.sample.foo.handle.ConnectorHandler;
import net.qiujuer.library.clink.core.Sender;
import net.qiujuer.library.clink.impl.bridge.BridgeSocketDispatcher;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 2人聊天
 */
public class privateRoom {
    private final String roomCode;
    private volatile ConnectorHandler handler1;
    private volatile ConnectorHandler handler2;
    private List<ConnectorHandler> handlers = new ArrayList<>(2);
    private BridgeSocketDispatcher[] bridges = new BridgeSocketDispatcher[2];    //2个桥接

    public privateRoom(String roomCode) {
        this.roomCode = roomCode;
    }

    public String getRoomCode() {
        return roomCode;
    }

    public ConnectorHandler[] getConnectors() { return handlers.toArray(new ConnectorHandler[0]); }

    /**
     * 获取对方
     */
    public ConnectorHandler getTheOtherHandler(ConnectorHandler handler) {
        return (handler1 == handler || handler1 == null) ? handler2 : handler1;
    }

    /**
     * 房间是否可聊天，是否两个客户端都具有
     */
    public synchronized boolean isEnable() {
        return handler1 != null && handler2 != null;
    }

    /**
     * 加入房间
     * @return 加入是否成功
     */
    public synchronized boolean enterRoom(ConnectorHandler handler) {
        if (handler1 == null) {
            handler1 = handler;
        } else if (handler2 == null) {
            handler2 = handler;
        } else {
            return false;
        }
        return true;
    }


    public void changetoBridge(){
        bridges[0] = new BridgeSocketDispatcher();
        bridges[1] = new BridgeSocketDispatcher();

        handler1.changeToBridge(bridges[0]);
        handler2.bindToBridge(bridges[0]);

        handler2.changeToBridge(bridges[1]);
        handler1.bindToBridge(bridges[1]);
    }


    /**
     * 退出房间, 只要有一人退出房间, 另一人自动退出房间
     * @return
     */
    public synchronized boolean exitRoom(ConnectorHandler handler) {
        if(handler1 != handler && handler2 != handler) return false;

        //数据清理, 解注册
        bridges[0].stop();
        bridges[0].stop();

        //2个handler重新绑定服务器数据调度器, 重新注册
        handler1.rebind();
        handler1 = null;
        handler2.rebind();
        handler2 = null;

        return true;
    }


    /**
     * 生成一个简单的随机字符串
     */
    private static String getRandomString(final int length) {
        final String str = "123456789";
        final Random random = new Random();
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < length; ++i) {
            int number = random.nextInt(str.length());
            sb.append(str.charAt(number));
        }
        return sb.toString();
    }


}
