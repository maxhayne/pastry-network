package cs555.system.wireformats;

import java.io.*;

/**
 * General Message with a type and a String message. Will be used for general
 * updates, like when the Discovery reports that a request to register has
 * failed.
 *
 * @author hayne
 */
public class GeneralMessage implements Event {

  private final byte type;
  private final String message;

  /**
   * Constructor with empty message.
   *
   * @param type of message
   */
  public GeneralMessage(byte type) {
    this.type = type;
    this.message = "";
  }

  /**
   * Default Constructor with type and non-null message.
   *
   * @param type of message
   * @param message to be sent
   */
  public GeneralMessage(byte type, String message) {
    this.type = type;

    if (message == null) {
      this.message = "";
    } else {
      this.message = message;
    }
  }

  public GeneralMessage(byte[] marshalledBytes) throws IOException {
    ByteArrayInputStream bin = new ByteArrayInputStream(marshalledBytes);
    DataInputStream din = new DataInputStream(bin);

    type = din.readByte();

    short len = din.readShort();
    if (len != 0) {
      byte[] messageBytes = new byte[len];
      din.readFully(messageBytes);
      message = new String(messageBytes);
    } else {
      message = "";
    }

    bin.close();
    din.close();
  }

  public String getMessage() {
    return message;
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

    if (!message.isEmpty()) {
      byte[] messageBytes = message.getBytes();
      dout.writeShort(messageBytes.length);
      dout.write(messageBytes);
    } else {
      dout.writeShort(0);
    }

    byte[] returnable = bout.toByteArray();
    bout.close();
    dout.close();
    return returnable;
  }
}
