package cs555.system.routing;

import cs555.system.util.HexUtilities;
import cs555.system.util.PeerInformation;

public class RoutingTable {

  private final PeerInformation self;
  private final PeerInformation[][] table;

  public RoutingTable(PeerInformation self) {
    this.self = self;
    this.table = new PeerInformation[4][16];
    addSelfToTable(this.self);
  }

  public void addSelfToTable(PeerInformation self) {
    for (int i = 0; i < table.length; ++i) {
      table[i][HexUtilities.hexToDecimal(self.getIdentifier().charAt(i))] =
          self;
    }
  }

  public RoutingTable(PeerInformation self, PeerInformation[][] table) {
    this.self = self;
    this.table = table;
  }

  public PeerInformation getSelf() {
    return self;
  }

  public synchronized PeerInformation get(int row, int col) {
    return table[row][col];
  }

  public synchronized boolean add(PeerInformation peer) {
    String selfID = self.getIdentifier();
    String peerID = peer.getIdentifier();
    int row = HexUtilities.firstDifference(selfID, peerID);
    int col = HexUtilities.hexToDecimal(peerID.charAt(row));
    PeerInformation currentPeer = table[row][col];
    if (currentPeer == null) {
      table[row][col] = peer;
      return true;
    }
    return false;
  }

  /**
   * Removes one instance of a Peer (there should only be one) from the table.
   *
   * @param peer peer to remove
   * @return true if something was removed, false if not
   */
  public synchronized boolean remove(PeerInformation peer) {
    for (int row = 0; row < table.length; ++row) {
      for (int col = 0; col < table[row].length; ++col) {
        if (peer.equals(table[row][col])) {
          table[row][col] = null;
          return true;
        }
      }
    }
    return false;
  }

  public synchronized String toString() {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < 4; ++i) {
      for (int j = 0; j < 16; ++j) {
        String identifier =
            table[i][j] == null ? "NULL" : table[i][j].getIdentifier();
        sb.append(identifier).append(" ");
      }
      sb.deleteCharAt(sb.length() - 1);
      sb.append('\n');
    }
    sb.deleteCharAt(sb.length() - 1);
    return sb.toString();
  }
}
