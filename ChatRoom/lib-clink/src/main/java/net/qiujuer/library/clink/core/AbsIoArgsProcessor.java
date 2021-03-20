package net.qiujuer.library.clink.core;


public class AbsIoArgsProcessor implements IoArgs.IoArgsEventProcessor{

    @Override
    public IoArgs provideIoArgs() {
        return null;
    }

    @Override
    public void onConsumeFailed(IoArgs args, Exception e) {

    }

    @Override
    public void onConsumeCompleted(IoArgs args) {

    }

    @Override
    public void notifyReceiveStop() {

    }

    @Override
    public void notifyReceiveStart() {

    }


    @Override
    public void notifyStartSend() {

    }

    @Override
    public void notifyDataFullSent() {

    }
}
