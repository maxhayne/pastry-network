package cs555.system.transport;

import cs555.system.node.Node;
import cs555.system.util.Logger;

import java.io.IOException;
import java.net.Socket;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Class to hold information about a socket connection between the current node
 * another node on the network. Has functionality to send and receive messages
 * over its socket. Contains an active TCPReceiverThread which automatically
 * receives and parses messages.
 *
 * @author hayne
 */
public class TCPConnection {

  private static final Logger logger = Logger.getInstance();
  private final Socket socket;
  private final TCPSender sender;
  private final TCPReceiverThread receiver;
  private final AtomicBoolean started;

  /**
   * Default constructor.
   *
   * @param node node TCPConnection is a part of
   * @param socket socket of the connection
   * @throws IOException if data input/output streams fail to open
   */
  public TCPConnection(Node node, Socket socket) throws IOException {
    this.socket = socket;
    this.sender = new TCPSender(socket);
    this.receiver = new TCPReceiverThread(node, socket, this);
    this.started = new AtomicBoolean(false);
  }

  /**
   * Starts the run() method of the TCPReceiverThread to start reading messages
   * (if that hasn't already happened).
   */
  public void start() {
    if (!started.getAndSet(true)) {
      (new Thread(receiver)).start();
    }
  }

  /**
   * Getter for socket.
   *
   * @return connection socket
   */
  public Socket getSocket() {
    return socket;
  }

  /**
   * Getter for TCPSender.
   *
   * @return connection's TCPSender
   */
  public TCPSender getSender() {
    return sender;
  }

  /**
   * Close this connection's socket. If the receiver thread has been started,
   * this will stop the thread as well.
   */
  public synchronized void close() {
    try {
      sender.dout.close(); // closes everything
    } catch (IOException ioe) {
      logger.error("Problem closing socket/streams. " + ioe.getMessage());
    }
  }
}
