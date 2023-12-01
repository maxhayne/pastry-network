package cs555.system.transport;

import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;

/**
 * Class to provide functions for sending messages out of an encapsulated
 * socket.
 *
 * @author hayne
 */
public class TCPSender {
  protected DataOutputStream dout;

  /**
   * Default constructor.
   *
   * @param socket socket of the connection
   */
  public TCPSender(Socket socket) throws IOException {
    this.dout = new DataOutputStream(socket.getOutputStream());
  }

  /**
   * Synchronized method for sending messages out of the socket. First writes
   * the length of the message to the stream, then the byte array, then calls
   * flush() to ensure timely delivery.
   *
   * @param msg byte[] to send over socket
   * @throws IOException if writing to socket fails
   */
  public synchronized void send(byte[] msg) throws IOException {
    int len = msg.length;
    dout.writeInt(len);
    dout.write(msg);
    dout.flush();
  }
}
