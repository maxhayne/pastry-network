package cs555.system.routing;

import cs555.system.transport.TCPConnectionCache;
import cs555.system.util.PeerInformation;
import cs555.system.wireformats.JoinMessage;
import cs555.system.wireformats.PeerBroadcast;

import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;

public class RoutingInformation {

  private final CountDownLatch initializationLatch;
  private PeerInformation self;
  private LeafSet leafSet;
  private RoutingTable routingTable;

  public RoutingInformation() {
    this.initializationLatch = new CountDownLatch(1);
  }

  public void initialize(PeerInformation self, JoinMessage joinMessage,
      TCPConnectionCache connections) {
    if (joinMessage == null) { // nothing more to do
      this.self = self;
      this.leafSet = new LeafSet(self);
      this.routingTable = new RoutingTable(self);
    } else {
      this.self = self;
      this.leafSet = joinMessage.getLeafSet();
      this.routingTable = joinMessage.getRoutingTable();
      rebuildLeafSet();
      // Send routing information to all known peers
      Set<PeerInformation> peerSet = getPeerSet(false);
      PeerBroadcast message = new PeerBroadcast(self, peerSet);
      for (Iterator<PeerInformation> i = peerSet.iterator(); i.hasNext(); ) {
        PeerInformation peer = i.next();
        String address = peer.getAddress();
        boolean sent = connections.send(address, message, false);
        if (!sent) {
          removePeer(peer);
          i.remove();
        }
      }
      joinMessage.getHops().add(self);
      System.out.println("Join Message Traceroute: " + joinMessage.getTrace());
    }
    initializationLatch.countDown();
    displayRoutingInformation();
  }

  /**
   * Generates a set of all peers in the routing table and leaf set.
   *
   * @param includeSelf true if set should include self, false if not
   * @return set of all peers
   */
  public synchronized Set<PeerInformation> getPeerSet(boolean includeSelf) {
    Set<PeerInformation> peerSet = new LinkedHashSet<>();
    peerSet.add(leafSet.getLeft());
    if (includeSelf) {
      peerSet.add(self);
    }
    peerSet.add(leafSet.getRight());
    PeerInformation peer;
    for (int row = 0; row < 4; ++row) {
      for (int col = 0; col < 16; ++col) {
        peer = routingTable.get(row, col);
        if (peer != null && !peer.equals(self)) {
          peerSet.add(peer);
        }
      }
    }
    peerSet.remove(null);
    return peerSet;
  }

  /**
   * Returns the peer that is closest in id-space to the key. If there are two
   * peers in the network that are equidistant from the key, this function will
   * return the one that is counter-clockwise (left) of the key in id-space.
   * This is guaranteed by the order used to check the peers.
   *
   * @param key key to compare against
   * @return peer whose id is closest numerically to the key
   */
  public synchronized PeerInformation lookup(String key) {
    waitForInitialization();
    return getClosestPeer(key);
  }

  /**
   * Finds the peer whose id is closest to the supplied key. Checking the leaf
   * set first, left to right, ensures deterministic routing in the case of a
   * tie between two peers. The counterclockwise will tie always win.
   *
   * @param key key to be compared against
   * @return peer closest to key
   */
  private synchronized PeerInformation getClosestPeer(String key) {
    PeerInformation closestPeer = null;
    int closestDistance = 65536 + 1; // could use (1 << key.length())+1
    PeerInformation comparePeer;
    int distance;

    // First check the leaf set
    if ((comparePeer = leafSet.getLeft()) != null) {
      closestPeer = comparePeer;
      closestDistance = closestPeer.distanceTo(key);
    }

    distance = self.distanceTo(key);
    if (distance < closestDistance) {
      closestPeer = self;
      closestDistance = distance;
    }

    if ((comparePeer = leafSet.getRight()) != null) {
      distance = comparePeer.distanceTo(key);
      if (distance < closestDistance) {
        closestPeer = comparePeer;
        closestDistance = distance;
      }
    }

    // Then check the routing table
    for (int row = 0; row < 4; ++row) {
      for (int col = 0; col < 16; ++col) {
        comparePeer = routingTable.get(row, col);
        if (comparePeer != null && !comparePeer.equals(self)) {
          distance = comparePeer.distanceTo(key);
          if (distance < closestDistance) {
            closestPeer = comparePeer;
            closestDistance = distance;
          }
        }
      }
    }

    return closestPeer;
  }

  public synchronized void attachRoutingToJoinMessage(JoinMessage joinMessage) {
    waitForInitialization();
    Set<PeerInformation> peerSet = getPeerSet(true);
    joinMessage.addRelevantEntries(peerSet);
  }

  private void waitForInitialization() {
    try {
      initializationLatch.await();
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    }
  }

  public synchronized boolean addPeer(PeerInformation peer) {
    boolean added = routingTable.add(peer);
    PeerInformation replacedLeaf = leafSet.add(peer);
    if (replacedLeaf != null) {
      routingTable.add(replacedLeaf);
      added = true;
    }
    return added;
  }

  public synchronized boolean removePeer(PeerInformation peer) {
    boolean removed = leafSet.remove(peer);
    if (routingTable.remove(peer)) {
      removed = true;
    }
    rebuildLeafSet();
    return removed;
  }

  private synchronized void rebuildLeafSet() {
    Set<PeerInformation> peerSet = getPeerSet(false);
    for (PeerInformation peer : peerSet) {
      leafSet.add(peer);
    }
  }

  public void displayRoutingInformation() {
    StringBuilder sb = new StringBuilder();
    if (initializationLatch.getCount() == 0) {
      synchronized(this) {
        sb.append("  ").append("Leaf Set: ").append(leafSet).append("\n");
        sb.append("  ").append("Routing Table: ").append("\n");
        for (String row : routingTable.toString().split("\n")) {
          sb.append("  ").append(row).append("\n");
        }
      }
    } else {
      sb.append("  ").append("Not yet initialized.").append("\n");
    }
    System.out.print(sb);
  }

  public LeafSet getLeafSet() {
    return leafSet;
  }
}
