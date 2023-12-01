package cs555.system.transport;

import cs555.system.node.Node;
import cs555.system.util.Logger;
import cs555.system.wireformats.Event;
import cs555.system.wireformats.EventFactory;

import java.io.DataInputStream;
import java.io.IOException;
import java.net.Socket;

/**
 * Class which reads and interprets messages sent to a socket. Implements
 * Runnable, so a thread can be created to automatically receive messages using
 * it's run() method.
 *
 * @author hayne
 */
public class TCPReceiverThread implements Runnable {

  private static final Logger logger = Logger.getInstance();
  private final Node node;
  private final Socket socket;
  private final TCPConnection connection;
  protected DataInputStream din;

  /**
   * Default constructor.
   *
   * @param node node TCPReceiverThread is being run on
   * @param socket socket of the connection
   * @param connection TCPConnection the TCPReceiverThread is a part of
   * @throws IOException if the data input stream fails to open
   */
  public TCPReceiverThread(Node node, Socket socket, TCPConnection connection)
      throws IOException {
    this.node = node;
    this.socket = socket;
    this.connection = connection;
    this.din = new DataInputStream(socket.getInputStream());
  }

  /**
   * While the socket is open, this method attempts to read messages from it
   * sent from other nodes. Upon receiving a message, the message is converted
   * into an event (the message type) by the EventFactory. The event is then
   * passed to the node's onEvent() method along with a reference to the
   * TCPConnection with which this TCPReceiverThread is associated. onEvent()
   * then has control over what actions must be taken to deal with the message
   * -- to reply, relay, read from a file, etc.
   */
  @Override
  public void run() {
    while (!socket.isClosed()) {
      try {
        int len = din.readInt();
        byte[] marshalledBytes = new byte[len];
        din.readFully(marshalledBytes);

        EventFactory eventFactory = EventFactory.getInstance();
        Event event = eventFactory.createEvent(marshalledBytes);
        node.onEvent(event, connection);
      } catch (IOException ioe) {
        logger.debug("Socket connection has closed. " + ioe);
        break;
      }
    }
  }
}