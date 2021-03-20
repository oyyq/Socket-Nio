package net.qiujuer.library.clink.impl.bridge;

import net.qiujuer.library.clink.core.ReceiveDispatcher;
import net.qiujuer.library.clink.core.SendDispatcher;
import net.qiujuer.library.clink.core.SendPacket;

import java.io.IOException;

public class AbsBridgeSocketDispatcher implements ReceiveDispatcher, SendDispatcher {
    @Override
    public void start() {

    }

    @Override
    public void send(SendPacket packet) {

    }

    @Override
    public void pause() {

    }

    @Override
    public void resume() {

    }

    @Override
    public void sendHeartbeat() {

    }

    @Override
    public void cancel(short packetId, SendPacket packet) {

    }

    @Override
    public void stop() {

    }

    @Override
    public void close() throws IOException {

    }
}
