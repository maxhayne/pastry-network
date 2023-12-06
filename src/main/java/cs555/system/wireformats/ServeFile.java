package cs555.system.wireformats;

import java.io.*;

public class ServeFile implements Event {
  private final byte type;
  private final String filename;
  private final byte[] content;

  public ServeFile(String filename, byte[] content) {
    this.type = Protocol.SERVE_FILE;
    this.filename = filename;
    this.content = content;
  }

  public ServeFile(byte[] marshalledBytes) throws IOException {
    ByteArrayInputStream bin = new ByteArrayInputStream(marshalledBytes);
    DataInputStream din = new DataInputStream(bin);

    type = din.readByte();

    int len = din.readInt();
    byte[] filenameBytes = new byte[len];
    din.readFully(filenameBytes);
    filename = new String(filenameBytes);

    len = din.readInt();
    if ( len == 0 ) {
      content = null;
    } else {
      content = new byte[len];
      din.readFully(content);
    }

    din.close();
    bin.close();
  }

  public String getFilename() {
    return filename;
  }

  public byte[] getContent() {
    return content;
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

    byte[] filenameBytes = filename.getBytes();
    dout.writeInt(filenameBytes.length);
    dout.write(filenameBytes);

    if ( content == null ) {
      dout.writeInt(0);
    } else {
      dout.writeInt(content.length);
      dout.write(content);
    }

    byte[] marshalledBytes = bout.toByteArray();
    dout.close();
    bout.close();
    return marshalledBytes;
  }
}
