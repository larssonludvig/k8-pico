package se.umu.cs.ads.communication;

import io.grpc.*;
import io.grpc.stub.StreamObserver;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.commons.collections.functors.ExceptionClosure;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.rpc.Code;
import org.springframework.ui.context.ThemeSource;

import io.grpc.Grpc;
import io.grpc.InsecureServerCredentials;
import io.grpc.Server;
import io.grpc.stub.StreamObserver;
import se.umu.cs.ads.serializers.NodeSerializer;
import se.umu.cs.ads.types.*;
import se.umu.cs.ads.arguments.CommandLineArguments;
import se.umu.cs.ads.exception.*;

public class PicoServer {
    private final static Logger logger = LogManager.getLogger(PicoServer.class);
    private PicoCommunication comm;
	private Server server;
	private final PicoAddress address;
    private final ExecutorService pool = CommandLineArguments.pool;
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
			logger.info("Received create container request for container {}", container.getName());
			RpcContainer res = null;
			
			try {
				pool.submit(() -> {
					this.comm.broadcastElectionEnd(container);
				});

				res = this.comm.createLocalContainer(container);
			} catch (PicoException e) {
				logger.error(e.getMessage());
				responseObserver.onError(e.toStatusException());
			} catch (Exception e) {
				logger.error("Error creating local container: {}", e.getMessage());
			}
			responseObserver.onNext(res);
			responseObserver.onCompleted();
		}


		@Override
		public synchronized void join(RpcJoinRequest msg, StreamObserver<RpcNodes> responseObserver) {
			RpcNode aspirant = msg.getAspirant();
			RpcMetadata metadata = msg.getSender();


			PicoAddress aspirantAddress = new PicoAddress(aspirant.getIp(), aspirant.getPort());
			PicoAddress senderAddress = new PicoAddress(metadata.getIp(), metadata.getPort());
		
			logger.info("Received JOIN_REQUEST from {} for {}", senderAddress, aspirantAddress);

			//if sender != aspirant we just add it to our system
			//otherwise we forward it to other members
			if (!senderAddress.equals(aspirantAddress)) {
				logger.info("Join request has been forwarded, adding aspirant {} to cluster", aspirantAddress);
				this.comm.addNewMember(aspirant);
				//create empty response
				RpcNodes empty = RpcNodes.newBuilder().build();
				responseObserver.onNext(empty);
				responseObserver.onCompleted();
				return;
			}
			
			RpcNodes reply = this.comm.joinReply(aspirant);
			List<PicoAddress> clusterMembers = this.comm.getClusterAddresses();
		
			String b = "Broadcasting JOIN_REQUEST to () {}:\n";
			//now send request to members in cluster
			//except ourselves and the aspirant
			for (PicoAddress addr : clusterMembers) {

				if (addr.equals(this.comm.getAddress()))
					continue;

				if (addr.equals(aspirantAddress))
					continue;

				try {
					b += "\t" + addr + "\n";
					this.comm.joinRequest(addr, NodeSerializer.fromRPC(aspirant));
				} catch(PicoException e) {
					responseObserver.onError(e.toStatusException());
				} catch(Exception e) {
					logger.error("Failed to broadcast join request: {}", e.getMessage());
				}
			}
			
			logger.info(b, clusterMembers.size());
			responseObserver.onNext(reply);
			responseObserver.onCompleted();
		}

		@Override
		public synchronized void leave(RpcMetadata msg, StreamObserver<RpcEmpty> responseObserver) {
			PicoAddress adr = new PicoAddress(msg.getIp(), msg.getPort());
			//TODO: Error handling
			this.comm.removeNodeRemote(null);

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
		public void heartbeat(RpcEmpty empty, StreamObserver<RpcNode> responseObserver) {
			Node node = this.comm.fetchNode();
			responseObserver.onNext(NodeSerializer.toRPC(node));
			responseObserver.onCompleted();
		}

		@Override
		public void isSuspect(RpcMetadata msg, StreamObserver<RpcBool> responseObserver) {
			PicoAddress adr = new PicoAddress(msg.getIp(), msg.getPort());
			boolean res = this.comm.isSuspect(adr);

			responseObserver.onNext(RpcBool.newBuilder().setValue(res).build());
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
				logger.info("Received evaluation request for container {}",
					container.getName());
				RpcContainerEvaluation resp = this.comm.evaluateContainer(container);
				ro.onNext(resp);
				ro.onCompleted();
			} catch (PicoException e) {
				ro.onError(e.toStatusException());
			}
		}

		@Override
		public void containerElectionEnd(RpcContainerElectionEnd msg, StreamObserver<RpcEmpty> responseObserver) {
			RpcMetadata sender = msg.getSender();
			RpcContainer container = msg.getContainer();

			logger.info("Received ELECTION_END for container {} from {}:{}", 
				container.getName(), sender.getIp(), sender.getIp());
			this.comm.receiveElectionEnd(container, sender);
			responseObserver.onNext(RpcEmpty.newBuilder().build());
			responseObserver.onCompleted();
		}

		@Override
		public void containerCommand(RpcContainerCommand action, StreamObserver<RpcMessage> responseObserver) {
			String res = "";
			try {
				res = this.comm.handleContainerCommand(action);
			} catch (PicoException e) {
				responseObserver.onError(e.toStatusException());
			}

			responseObserver.onNext(RpcMessage.newBuilder().setPayload(res).build());
			responseObserver.onCompleted();
		}
    }
}
