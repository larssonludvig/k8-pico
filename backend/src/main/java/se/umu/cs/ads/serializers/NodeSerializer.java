package se.umu.cs.ads.serializers;

import java.net.InetSocketAddress;
import java.util.*;

import se.umu.cs.ads.types.*;
import se.umu.cs.ads.communication.*;

public final class NodeSerializer {
    public static Node fromRPC(RpcNode node) {
        return new Node(
            new InetSocketAddress(node.getIp(), node.getPort()),
            node.getClusterName(),
            new ArrayList<PicoContainer>(ContainerSerializer.fromRPC(node.getContainers()))
        );
    }

    public static List<Node> fromRPC(RpcNodes nodes) {
        List<Node> n = new ArrayList<>();
        for (int i = 0; i < nodes.getNodesCount(); i++) {
            n.add(fromRPC(nodes.getNodes(i)));
        }
        return n;
    }

    public static RpcNode toRPC(Node node) {
        String[] buff = node.getAddress().toString().split(":");
        String ip = buff[0];
        int port = Integer.parseInt(buff[1]);

        return RpcNode.newBuilder()
            .setIp(ip)
            .setPort(port)
            .setClusterName(node.getCluster())
            .setContainers(ContainerSerializer.toRPC(node.getContainers()))
            .build();
    }

    public static RpcNodes toRPC(List<Node> nodes) {
        RpcNodes.Builder builder = RpcNodes.newBuilder();
        for (Node n : nodes) {
            builder.addNodes(toRPC(n));
        }
        return builder.build();
    }
}