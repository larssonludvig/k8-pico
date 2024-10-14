package se.umu.cs.ads.communication;

import io.grpc.Grpc;
import io.grpc.InsecureServerCredentials;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.stub.StreamObserver;

import java.io.IOException;
import se.umu.cs.ads.types.PicoAddress;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.config.plugins.convert.TypeConverters.InetAddressConverter;
import org.bouncycastle.util.io.StreamOverflowException;

import se.umu.cs.ads.serializers.NodeSerializer;
import se.umu.cs.ads.types.*;
import se.umu.cs.ads.communication.*;
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
		public void createContainer(RpcContainer container, StreamObserver<RpcEmpty> responseObserver) {
			
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
    }
}
