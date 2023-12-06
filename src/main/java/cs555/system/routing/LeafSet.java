package cs555.system.routing;

import cs555.system.util.PeerInformation;

public class LeafSet {
  private PeerInformation left = null; // closest counterclockwise peer
  private final PeerInformation self;
  private PeerInformation right = null; // closest clockwise peer

  public LeafSet(PeerInformation self) {
    this.self = self;
  }

  /**
   * Adds a peer to the leaf set if it is closer to self than the current left
   * or right nodes. If the peer is added to the leaf set, the node it replaced
   * is returned.
   *
   * @param peer to add to leaf set
   * @return peer it replaced, or null
   */
  public synchronized PeerInformation add(PeerInformation peer) {
    PeerInformation replaced = null;
    if (self.equals(peer)) {
      return null;
    } else if (left == null || right == null) { // edge case
      left = peer;
      right = peer;
      return null;
    } else if (left.equals(right)) { // edge case
      if (self.distanceToRight(right) > self.distanceToRight(peer)) {
        right = peer;
      } else {
        left = peer;
      }
      return null;
    } else if (self.distanceToRight(right) > self.distanceToRight(peer)) {
      replaced = right;
      right = peer;
    } else if (self.distanceToLeft(left) > self.distanceToLeft(peer)) {
      replaced = left;
      left = peer;
    }
    return replaced;
  }

  public synchronized boolean remove(PeerInformation peer) {
    boolean removed = false;
    if (peer.equals(left)) {
      left = null;
      removed = true;
    }
    if (peer.equals(right)) {
      right = null;
      removed = true;
    }
    return removed;
  }

  public PeerInformation getSelf() {
    return self;
  }

  public synchronized PeerInformation getLeft() {
    return left;
  }

  public synchronized PeerInformation getRight() {
    return right;
  }

  public synchronized void setLeft(PeerInformation peer) {
    left = peer;
  }

  public synchronized void setRight(PeerInformation peer) {
    right = peer;
  }

  public synchronized String toString() {
    String printLeft = left == null ? "NULL" : left.getIdentifier();
    String printRight = right == null ? "NULL" : right.getIdentifier();
    return printLeft + " <- " + self.getIdentifier() + " -> " + printRight;
  }
}