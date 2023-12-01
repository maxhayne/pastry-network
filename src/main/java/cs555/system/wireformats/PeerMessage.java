package cs555.system.wireformats;

import cs555.system.util.MarshallHelper;
import cs555.system.util.PeerInformation;

import java.io.*;

/**
 * General Peer Message containing information about one peer, along with a
 * message type. Will be used to Register at the Discovery, among other things.
 *
 * @author hayne
 */
public class PeerMessage implements Event {

  private final byte type;
  private final PeerInformation peer;

  /**
   * Default constructor.
   *
   * @param peer to be registered
   */
  public PeerMessage(byte type, PeerInformation peer) {
    this.type = type;
    this.peer = peer;
  }

  /**
   * Constructor which unmarshalls bytes to fill fields.
   *
   * @param marshalledBytes byte[] to be unmarshalled
   * @throws IOException if streams cannot be read from
   */
  public PeerMessage(byte[] marshalledBytes) throws IOException {
    ByteArrayInputStream bin = new ByteArrayInputStream(marshalledBytes);
    DataInputStream din = new DataInputStream(bin);

    type = din.readByte();
    peer = MarshallHelper.unmarshallPeerInformation(din);

    bin.close();
    din.close();
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

    byte[] returnable = bout.toByteArray();
    bout.close();
    dout.close();
    return returnable;
  }
}