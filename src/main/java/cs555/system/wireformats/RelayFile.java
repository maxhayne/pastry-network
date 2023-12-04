package cs555.system.wireformats;

import java.io.*;

public class RelayFile implements Event {

  private final byte type;
  private final String key;
  private final String filename;
  private final byte[] content;
  private final String address;  // of StoreData program
  private int hops;

  public RelayFile(String key, String filename, byte[] content,
      String address) {
    this.type = Protocol.RELAY_FILE;
    this.key = key;
    this.filename = filename;
    this.content = content;
    this.address = address;
    this.hops = 0;
  }

  public RelayFile(byte[] marshalledBytes) throws IOException {
    ByteArrayInputStream bin = new ByteArrayInputStream(marshalledBytes);
    DataInputStream din = new DataInputStream(bin);

    type = din.readByte();

    int len = din.readInt();
    byte[] keyBytes = new byte[len];
    din.readFully(keyBytes);
    key = new String(keyBytes);

    len = din.readInt();
    byte[] filenameBytes = new byte[len];
    din.readFully(filenameBytes);
    filename = new String(filenameBytes);

    len = din.readInt();
    content = new byte[len];
    din.readFully(content);

    len = din.readInt();
    byte[] addressBytes = new byte[len];
    din.readFully(addressBytes);
    address = new String(addressBytes);

    hops = din.readInt();

    din.close();
    bin.close();
  }

  public String getKey() {
    return key;
  }

  public String getFilename() {
    return filename;
  }

  public byte[] getContent() {
    return content;
  }

  public String getAddress() {
    return address;
  }

  public int getHops() {
    return hops;
  }

  public void incrementHops() {
    ++hops;
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

    byte[] keyBytes = key.getBytes();
    dout.writeInt(keyBytes.length);
    dout.write(keyBytes);

    byte[] filenameBytes = filename.getBytes();
    dout.writeInt(filenameBytes.length);
    dout.write(filenameBytes);

    dout.writeInt(content.length);
    dout.write(content);

    byte[] addressBytes = address.getBytes();
    dout.writeInt(addressBytes.length);
    dout.write(addressBytes);

    dout.writeInt(hops);

    byte[] marshalledBytes = bout.toByteArray();
    dout.close();
    bout.close();
    return marshalledBytes;
  }
}
