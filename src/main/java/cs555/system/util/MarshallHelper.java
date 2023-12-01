package cs555.system.util;

import cs555.system.routing.LeafSet;
import cs555.system.routing.RoutingTable;

import java.io.*;

/**
 * Class which contains functions that help to marshall and unmarshall slightly
 * more complicated sequences.
 *
 * @author hayne
 */
public class MarshallHelper {

  /**
   * Serialize PeerInformation object into DataOutputStream.
   *
   * @param peer to be serialized
   * @param dout to write serialized object to
   * @throws IOException if the output stream cannot be written to
   */
  public static void marshallPeerInformation(PeerInformation peer,
      DataOutputStream dout) throws IOException {
    byte[] identifierBytes =
        HexUtilities.convertHexToBytes(peer.getIdentifier());
    dout.write(identifierBytes);

    byte[] hostBytes = peer.getHost().getBytes();
    dout.writeShort(hostBytes.length);
    dout.write(hostBytes);

    dout.writeInt(peer.getPort());
  }

  /**
   * Serialize LeafSet object into DataOutputStream.
   *
   * @param leafSet to be serialized
   * @param dout to write serialized object to
   * @throws IOException if the output stream cannot be written to
   */
  public static void marshallLeafSet(LeafSet leafSet, DataOutputStream dout)
      throws IOException {

    if (leafSet.getLeft() == null) {
      dout.writeByte(0);
    } else {
      dout.writeByte(1);
      marshallPeerInformation(leafSet.getLeft(), dout);
    }

    if (leafSet.getSelf() == null) {
      dout.writeByte(0);
    } else {
      dout.writeByte(1);
      marshallPeerInformation(leafSet.getSelf(), dout);
    }

    if (leafSet.getRight() == null) {
      dout.writeByte(0);
    } else {
      dout.writeByte(1);
      marshallPeerInformation(leafSet.getRight(), dout);
    }

  }

  /**
   * Serialize RoutingTable into DataOutputStream.
   *
   * @param routingTable to be serialized
   * @param dout to write serialized object to
   * @throws IOException if the output stream cannot be written to
   */
  public static void marshallRoutingTable(RoutingTable routingTable,
      DataOutputStream dout) throws IOException {
    marshallPeerInformation(routingTable.getSelf(), dout);
    for (int row = 0; row < 4; ++row) {
      for (int col = 0; col < 16; ++col) {
        if (routingTable.get(row, col) == null) {
          dout.writeByte(0);
        } else {
          dout.writeByte(1);
          marshallPeerInformation(routingTable.get(row, col), dout);
        }
      }
    }
  }

  /**
   * Deserialize RoutingTable into object from DataInputStream.
   *
   * @param din to read serialized object from
   * @return unmarshalled RoutingTable object
   * @throws IOException if the input stream cannot be read from
   */
  public static RoutingTable unmarshallRoutingTable(DataInputStream din)
      throws IOException {
    PeerInformation self = unmarshallPeerInformation(din);
    PeerInformation[][] table = new PeerInformation[4][16];
    for (int row = 0; row < 4; ++row) {
      for (int col = 0; col < 16; ++col) {
        if (din.readByte() == 1) {
          table[row][col] = unmarshallPeerInformation(din);
        }
      }
    }
    return new RoutingTable(self, table);
  }

  /**
   * Deserialize LeafSet into object from DataInputStream.
   *
   * @param din to read serialized object from
   * @return unmarshalled LeafSet object
   * @throws IOException if the input stream cannot be read from
   */
  public static LeafSet unmarshallLeafSet(DataInputStream din)
      throws IOException {
    PeerInformation left = null, self = null, right = null;

    if (din.readByte() == 1) {
      left = unmarshallPeerInformation(din);
    }
    if (din.readByte() == 1) {
      self = unmarshallPeerInformation(din);
    }
    if (din.readByte() == 1) {
      right = unmarshallPeerInformation(din);
    }

    LeafSet leafSet = new LeafSet(self);
    leafSet.setLeft(left);
    leafSet.setRight(right);

    return leafSet;
  }

  /**
   * Deserialize PeerInformation into object from DataInputStream.
   *
   * @param din to read serialized object from
   * @return unmarshalled PeerInformation object
   * @throws IOException if the input stream cannot be read from
   */
  public static PeerInformation unmarshallPeerInformation(DataInputStream din)
      throws IOException {
    byte[] id = new byte[2];
    din.readFully(id);
    String identifier = HexUtilities.convertBytesToHex(id);

    short len = din.readShort();
    byte[] hostBytes = new byte[len];
    din.readFully(hostBytes);
    String host = new String(hostBytes);

    int port = din.readInt();

    return new PeerInformation(identifier, host, port);
  }
}
