package cs555.system.wireformats;

import cs555.system.util.MarshallHelper;
import cs555.system.util.PeerInformation;

import java.io.*;
import java.util.ArrayList;

public class SeekMessage implements Event {

  private final byte type;
  private final String key;
  private final String path;
  private final String requestAddress;
  private final ArrayList<PeerInformation> hops;

  public SeekMessage(String key, String path, String requestAddress) {
    this.type = Protocol.SEEK;
    this.key = key;
    this.path = path;
    this.requestAddress = requestAddress;
    this.hops = new ArrayList<>();
  }

  public SeekMessage(byte[] marshalledBytes) throws IOException {
    ByteArrayInputStream bin = new ByteArrayInputStream(marshalledBytes);
    DataInputStream din = new DataInputStream(bin);

    type = din.readByte();

    int len = din.readInt();
    byte[] keyBytes = new byte[len];
    din.readFully(keyBytes);
    key = new String(keyBytes);

    len = din.readInt();
    byte[] pathBytes = new byte[len];
    din.readFully(pathBytes);
    path = new String(pathBytes);

    len = din.readInt();
    byte[] addressBytes = new byte[len];
    din.readFully(addressBytes);
    requestAddress = new String(addressBytes);

    len = din.readInt();
    hops = new ArrayList<>(len);
    for (int i = 0; i < len; ++i) {
      PeerInformation peer = MarshallHelper.unmarshallPeerInformation(din);
      hops.add(peer);
    }

    din.close();
    bin.close();
  }

  public String getKey() {
    return key;
  }

  public String getPath() {
    return path;
  }

  public String getRequestAddress() {
    return requestAddress;
  }

  public ArrayList<PeerInformation> getHops() {
    return hops;
  }

  public void addHop(PeerInformation peer) {
    hops.add(peer);
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

    byte[] pathBytes = path.getBytes();
    dout.writeInt(pathBytes.length);
    dout.write(pathBytes);

    byte[] addressBytes = requestAddress.getBytes();
    dout.writeInt(addressBytes.length);
    dout.write(addressBytes);

    dout.writeInt(hops.size());
    for (PeerInformation peer : hops) {
      MarshallHelper.marshallPeerInformation(peer, dout);
    }

    byte[] marshalledBytes = bout.toByteArray();
    dout.close();
    bout.close();
    return marshalledBytes;
  }
}
