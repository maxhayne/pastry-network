package cs555.system.wireformats;

import cs555.system.util.MarshallHelper;
import cs555.system.util.PeerInformation;

import java.io.*;
import java.util.HashSet;
import java.util.Set;

/**
 * Used by a new peer directly after initialization to send its leaf set and
 * routing table to all peers it knows of.
 *
 * @author hayne
 */
public class PeerBroadcast implements Event {
  private final byte type;
  private final PeerInformation peer;
  private final Set<PeerInformation> contents;

  public PeerBroadcast(PeerInformation peer, Set<PeerInformation> contents) {
    this.type = Protocol.PEER_BROADCAST;
    this.peer = peer;
    this.contents = contents;
  }

  public PeerBroadcast(byte[] marshalledBytes) throws IOException {
    ByteArrayInputStream bin = new ByteArrayInputStream(marshalledBytes);
    DataInputStream din = new DataInputStream(bin);

    type = din.readByte();

    peer = MarshallHelper.unmarshallPeerInformation(din);

    int size = din.readInt();
    contents = new HashSet<>();
    for (int i = 0; i < size; ++i) {
      PeerInformation entry = MarshallHelper.unmarshallPeerInformation(din);
      contents.add(entry);
    }

    din.close();
    bin.close();
  }

  public Set<PeerInformation> getContents() {
    return contents;
  }

  public PeerInformation getPeer() {
    return peer;
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

    MarshallHelper.marshallPeerInformation(peer, dout);

    dout.writeInt(contents.size());
    for (PeerInformation entry : contents) {
      MarshallHelper.marshallPeerInformation(entry, dout);
    }

    byte[] marshalledBytes = bout.toByteArray();
    bout.close();
    dout.close();
    return marshalledBytes;
  }
}
