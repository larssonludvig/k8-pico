package se.umu.cs.ads.communication;

import io.grpc.*;
import io.grpc.stub.StreamObserver;

import java.io.IOException;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.rpc.Code;

import se.umu.cs.ads.serializers.NodeSerializer;
import se.umu.cs.ads.types.*;
import se.umu.cs.ads.exception.*;

public class PicoServer {
    private final static Logger logger = LogManager.getLogger(PicoServer.class);
    private PicoCommunication comm;
	private Server server;
	private final PicoAddress address;
    
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

	public void shutdown() {
		try {
			this.server.awaitTermination();
		} catch (InterruptedException e) {
			logger.warn("Received interrupt while waiting for shutdown");
		}
	}
    private class RpcService extends RpcServiceGrpc.RpcServiceImplBase {
		private final PicoCommunication comm; 
		
		public RpcService(PicoCommunication comm) {
			this.comm = comm;
		}
		
		
		@Override
		public void fetchNodePerformance(RpcEmpty empty, StreamObserver<RpcPerformance> responseObserver) {
			logger.info("Fetching performance...");
			try {
				RpcPerformance resp = this.comm.fetchPerformance();
				responseObserver.onNext(resp);
				responseObserver.onCompleted();
			} catch (PicoException e) {
				responseObserver.onError(e.toStatusException());
			}
		}

		@Override
		public void createContainer(RpcContainer container, StreamObserver<RpcContainer> responseObserver) {
			RpcContainer res = null;
			try {
				res = this.comm.createLocalContainer(container);
			} catch (PicoException e) {
				logger.error(e.getMessage());
				responseObserver.onError(e.toStatusException());
			}
			responseObserver.onNext(res);
			responseObserver.onCompleted();
		}

		@Override
		public void join(RpcJoinRequest msg, StreamObserver<RpcNodes> responseObserver) {
			RpcNode aspirant = msg.getAspirant();
			RpcNodes reply = this.comm.joinReply(aspirant);
			List<PicoAddress> clusterMembers = this.comm.getClusterAddresses();
			RpcMetadata metadata = msg.getSender();


			PicoAddress aspirantAddress = new PicoAddress(aspirant.getIp(), aspirant.getPort());
			PicoAddress senderAddress = new PicoAddress(metadata.getIp(), metadata.getPort());
		

			//if sender != aspirant we just add it to our system
			//otherwise we forward it to other members
			if (!senderAddress.equals(aspirantAddress)) {
				this.comm.addNewMember(aspirant);
				//create empty response
				RpcNodes empty = RpcNodes.newBuilder().build();
				responseObserver.onNext(empty);
				responseObserver.onCompleted();
				return;
			}
		
			//now send request to members in cluster
			//except ourselves and the aspirant
			for (PicoAddress addr : clusterMembers) {
				if (addr.equals(this.comm.getAddress()))
					continue;

				if (addr.equals(aspirant))
					continue;

				try {
					this.comm.joinRequest(addr, NodeSerializer.fromRPC(aspirant));
				} catch(PicoException e) {
					responseObserver.onError(e.toStatusException());
				}
			}
			
			responseObserver.onNext(reply);
			responseObserver.onCompleted();
		}

		@Override
		public void leave(RpcMetadata msg, StreamObserver<RpcEmpty> responseObserver) {
			PicoAddress adr = new PicoAddress(msg.getIp(), msg.getPort());
			//TODO: Error handling
			this.comm.removeNodeRemote();

			responseObserver.onNext(RpcEmpty.newBuilder().build());
			responseObserver.onCompleted();
		}

		@Override
		public void removeNode(RpcMetadata msg, StreamObserver<RpcEmpty> responseObserver) {
			PicoAddress adr = new PicoAddress(msg.getIp(), msg.getPort());
			this.comm.removeNode(adr);

			responseObserver.onNext(RpcEmpty.newBuilder().build());
			responseObserver.onCompleted();
		}

		@Override
		public void fetchNode(RpcMetadata msg, StreamObserver<RpcNode> responseObserver) {
			PicoAddress adr = new PicoAddress(msg.getIp(), msg.getPort());
			Node node = this.comm.fetchNode(adr);

			responseObserver.onNext(NodeSerializer.toRPC(node));
			responseObserver.onCompleted();
		}

		@Override
		public void containerElectionStart(RpcContainer container, StreamObserver<RpcEmpty> responseObserver) {
			logger.info("Received CONTAINER_ELECTION_START for container {}", container.getName());
			try {
				this.comm.containerElectionStart(container);
				responseObserver.onNext(RpcEmpty.newBuilder().build());
				responseObserver.onCompleted();
			} catch (PicoException e) {
				responseObserver.onError(e.toStatusException());
			}
		}

		@Override
		public void elvaluateContainer(RpcContainer container, StreamObserver<RpcContainerEvaluation> ro) {
			try {
				RpcContainerEvaluation resp = this.comm.evaluateContainer(container);
				ro.onNext(resp);
				ro.onCompleted();
			} catch (PicoException e) {
				ro.onError(e.toStatusException());
			}
		}
    }
}
