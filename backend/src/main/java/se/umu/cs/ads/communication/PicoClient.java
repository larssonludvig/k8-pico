package se.umu.cs.ads.communication;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.MoreExecutors;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;

import java.util.concurrent.ExecutionException;

import se.umu.cs.ads.types.*;

public class PicoClient {
    // private final static 
    private ManagedChannel channel;
    private RpcServiceGrpc.RpcServiceFutureStub futureStub;

    private String ip;
    private int port;

    public PicoClient(String ip, int port) {
        this.ip = ip;
        this.port = port;

        this.channel = ManagedChannelBuilder.forAddress(ip, port)
            .usePlaintext()
            .build();

        this.futureStub = RpcServiceGrpc.newFutureStub(channel);
    }

    public String send(JMessage jmsg) throws RuntimeException {
        RpcMessage msg = RpcMessage.newBuilder().setPayload(jmsg.toString()).build();
        ListenableFuture<RpcMessage> future = this.futureStub.send(msg);

        Futures.addCallback(future, new FutureCallback<RpcMessage>() {
            @Override
            public void onSuccess(RpcMessage result) {}

            @Override
            public void onFailure(Throwable t) {
                throw new RuntimeException(t);
            }
        }, MoreExecutors.directExecutor());

        while (!future.isDone()) {
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }

        try {
            return future.get().getPayload();
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        }
    }
}
