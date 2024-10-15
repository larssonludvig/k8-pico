package se.umu.cs.ads.communication;

import io.grpc.Grpc;
import io.grpc.InsecureServerCredentials;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;

import java.io.IOException;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import io.grpc.Grpc;
import io.grpc.InsecureServerCredentials;
import io.grpc.Server;
import io.grpc.stub.StreamObserver;
import se.umu.cs.ads.serializers.NodeSerializer;
import se.umu.cs.ads.types.Node;
import se.umu.cs.ads.types.PicoAddress;
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

    private class RpcService extends RpcServiceGrpc.RpcServiceImplBase {
		private final PicoCommunication comm; 
		public RpcService(PicoCommunication comm) {
			this.comm = comm;
		}
		
		
		@Override
		public void fetchNodePerformance(RpcEmpty empty, StreamObserver<RpcPerformance> responseObserver) {
			logger.info("Fetching performance...");
			RpcPerformance resp = this.comm.fetchPerformance();
			responseObserver.onNext(resp);
			responseObserver.onCompleted();
		}

		@Override
		public void createContainer(RpcContainer container, StreamObserver<RpcContainer> responseObserver) {
			RpcContainer res = this.comm.createLocalContainer(container);
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

				this.comm.joinRequest(addr, NodeSerializer.fromRPC(aspirant));
			}
			
			responseObserver.onNext(reply);
			responseObserver.onCompleted();
		}

		@Override
		public void leave(RpcMetadata msg, StreamObserver<RpcEmpty> responseObserver) {
			PicoAddress adr = new PicoAddress(msg.getIp(), msg.getPort());
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
		public void heartBeat(RpcEmpty empty, StreamObserver<RpcNode> responseObserver) {
			Node node = this.comm.fetchNode();
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
			} catch (StatusRuntimeException e) {
				responseObserver.onError(e);
			} catch (Exception e) {
				responseObserver.onError(new StatusRuntimeException(Status.INTERNAL.withCause(e)));
			}
		}

		@Override
		public void elvaluateContainer(RpcContainer container, StreamObserver<RpcContainerEvaluation> ro) {
			RpcContainerEvaluation resp = this.comm.evaluateContainer(container);
			ro.onNext(resp);
			ro.onCompleted();
		}
    }
}
