package cs555.system.wireformats;

import cs555.system.routing.LeafSet;
import cs555.system.routing.RoutingTable;
import cs555.system.util.MarshallHelper;
import cs555.system.util.PeerInformation;

import java.io.*;
import java.util.ArrayList;
import java.util.Set;

/**
 * Message to be used by a new Peer in the network when attempting to build its
 * routing table and leaf set. Message will first be sent to entry Peer provided
 * by the Discovery node, with the destination set to itself.
 *
 * @author hayne
 */
public class JoinMessage implements Event {

  private final byte type;
  private final PeerInformation destination;
  private final ArrayList<PeerInformation> hops;
  private final LeafSet leafSet;
  private final RoutingTable routingTable;

  /**
   * Default constructor. Will be used by the new Peer in the network when
   * sending out a message to the entry peer it has received from the Discovery
   * node.
   *
   * @param destination peer
   */
  public JoinMessage(PeerInformation destination) {
    this.type = Protocol.SPECIAL_JOIN;
    this.destination = destination;
    this.hops = new ArrayList<>();
    this.leafSet = new LeafSet(destination);
    this.routingTable = new RoutingTable(destination);
  }

  /**
   * Constructor which unmarshalls bytes to fill fields.
   *
   * @param marshalledBytes byte[] to be unmarshalled
   * @throws IOException if streams cannot be read from
   */
  public JoinMessage(byte[] marshalledBytes) throws IOException {
    ByteArrayInputStream bin = new ByteArrayInputStream(marshalledBytes);
    DataInputStream din = new DataInputStream(bin);

    type = din.readByte();

    destination = MarshallHelper.unmarshallPeerInformation(din);

    short size = din.readShort();
    hops = new ArrayList<>(size);
    for (int i = 0; i < size; i++) {
      hops.add(MarshallHelper.unmarshallPeerInformation(din));
    }

    leafSet = MarshallHelper.unmarshallLeafSet(din);

    routingTable = MarshallHelper.unmarshallRoutingTable(din);

    bin.close();
    din.close();
  }

  public PeerInformation getDestination() {
    return destination;
  }

  public ArrayList<PeerInformation> getHops() {
    return hops;
  }

  public LeafSet getLeafSet() {
    return leafSet;
  }

  public RoutingTable getRoutingTable() {
    return routingTable;
  }

  public void addRelevantEntries(Set<PeerInformation> peerSet) {
    for (PeerInformation peer : peerSet) {
      routingTable.add(peer);
      PeerInformation replacedLeaf = leafSet.add(peer);
      if (replacedLeaf != null) {
        routingTable.add(replacedLeaf);
      }
    }
  }

  public void removePeer(PeerInformation peer) {
    routingTable.remove(peer);
    leafSet.remove(peer);
    // leaf set will be rebuilt upon initialization, don't rebuild here
  }

  public String getTrace() {
    StringBuilder sb = new StringBuilder();
    for (PeerInformation peer : hops) {
      sb.append(peer.getIdentifier()).append(" -> ");
    }
    sb.delete(sb.lastIndexOf(" -> "), sb.length());
    return sb.toString();
  }

  @Override
  public byte getType() {
    return type;
  }

  @Override
  public byte[] getBytes() throws IOException {
    ByteArrayOutputStream bout = new ByteArrayOutputStream();
    DataOutputStream dout = new DataOutputStream(bout);

    dout.write(type);

    MarshallHelper.marshallPeerInformation(destination, dout);

    if (!hops.isEmpty()) {
      dout.writeShort(hops.size());
      for (PeerInformation hop : hops) {
        MarshallHelper.marshallPeerInformation(hop, dout);
      }
    } else {
      dout.writeShort(0);
    }

    MarshallHelper.marshallLeafSet(leafSet, dout);

    MarshallHelper.marshallRoutingTable(routingTable, dout);

    byte[] marshalledBytes = bout.toByteArray();
    bout.close();
    dout.close();
    return marshalledBytes;
  }
}
