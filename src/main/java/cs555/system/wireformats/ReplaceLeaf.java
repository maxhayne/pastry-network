package cs555.system.wireformats;

import cs555.system.util.MarshallHelper;
import cs555.system.util.PeerInformation;

import java.io.*;

public class ReplaceLeaf implements Event {

  private final byte type;
  private final PeerInformation leavingPeer;
  private PeerInformation replacement;

  public ReplaceLeaf(PeerInformation leavingPeer, PeerInformation replacement) {
    this.type = Protocol.REPLACE_LEAF;
    this.leavingPeer = leavingPeer;
    this.replacement = replacement;
  }

  public ReplaceLeaf(byte[] marshalledBytes) throws IOException {
    ByteArrayInputStream bin = new ByteArrayInputStream(marshalledBytes);
    DataInputStream din = new DataInputStream(bin);

    type = din.readByte();
    leavingPeer = MarshallHelper.unmarshallPeerInformation(din);
    replacement = MarshallHelper.unmarshallPeerInformation(din);

    bin.close();
    din.close();
  }

  public PeerInformation getLeavingPeer() {
    return leavingPeer;
  }

  public PeerInformation getReplacement() {
    return replacement;
  }

  public void setReplacement(PeerInformation replacement) {
    this.replacement = replacement;
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
    MarshallHelper.marshallPeerInformation(leavingPeer, dout);
    MarshallHelper.marshallPeerInformation(replacement, dout);

    byte[] returnable = bout.toByteArray();
    bout.close();
    dout.close();
    return returnable;
  }
}