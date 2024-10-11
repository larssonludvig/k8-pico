package se.umu.cs.ads.communication;

import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.stub.StreamObserver;

import java.io.IOException;
import java.net.InetSocketAddress;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import se.umu.cs.ads.types.*;

public class PicoServer {
    private final static Logger logger = LogManager.getLogger(PicoServer.class);
    private PicoCommunication comm;
	private Server server;
	private final InetSocketAddress address;
    public PicoServer(PicoCommunication comm) {
        this.comm = comm;
		this.address = this.comm.getAddress();
    }

	
    public void start() {
		int port = address.getPort();
        try {
			server = ServerBuilder.forPort(port)
				.addService(new RpcService(comm))
				.build()
				.start();
		} catch (IllegalStateException e) {
			logger.error("Unable to start gRPC server: Server is already started or has been shut down: {}", port, e.getMessage());
		} catch (IOException e) {
			logger.error("Unable to bind port {} for gRPC server: {}", port, e.getMessage());
		}	
    }

    private class RpcService extends RpcServiceGrpc.RpcServiceImplBase {
		private final PicoCommunication comm; 
		public RpcService(PicoCommunication comm) {
			this.comm = comm;
		}
		
		
        @Override
        public void send(RpcMessage msg, StreamObserver<RpcMessage> responseObserver) {
            JMessage message = JMessage.fromJson(msg.getPayload());
			JMessage reply = this.comm.receive(message);
			RpcMessage rpcReply = JMessage.toRPC(reply);
			responseObserver.onNext(rpcReply);
			responseObserver.onCompleted();
        }
		
		@Override
		public void join(RpcMessage msg, StreamObserver<RpcMessage> responseObserver) {
			JMessage message = JMessage.fromJson(msg.getPayload());
			JMessage reply = this.comm.join(message);
			RpcMessage rpcReply = JMessage.toRPC(reply);
			responseObserver.onNext(rpcReply);
			responseObserver.onCompleted();
		}
    }
}
