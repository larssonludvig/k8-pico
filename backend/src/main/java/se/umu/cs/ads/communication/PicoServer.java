package se.umu.cs.ads.communication;

import io.grpc.Grpc;
import io.grpc.InsecureServerCredentials;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.stub.StreamObserver;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.List;

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
			server = Grpc.newServerBuilderForPort(port, InsecureServerCredentials.create())
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
		public void join(RpcNode msg, StreamObserver<RpcNodes> responseObserver) {

			//TODO: broadcast
			//Broadcast send reply back to other members
			// return with list of cluster members
			RpcNodes reply = this.comm.joinReply(msg);
			responseObserver.onNext(reply);
			responseObserver.onCompleted();
		}
    }
}
