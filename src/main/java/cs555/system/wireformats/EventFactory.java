package cs555.system.wireformats;

import cs555.system.util.Logger;

import java.io.IOException;

/**
 * Class which allows TCPReceiverThread to be simple. EventFactory creates new
 * events based on the first byte of every message sent to a node. It is
 * implemented as a singleton to reduce memory usage for a node which has many
 * TCPReceiverThreads running simultaneously.
 *
 * @author hayne
 */
public class EventFactory {

  private static final Logger logger = Logger.getInstance();
  private static final EventFactory eventFactory = new EventFactory();

  /**
   * Private Constructor.
   */
  private EventFactory() {
  }

  /**
   * Gets instance of singleton EventFactory.
   *
   * @return eventFactory singleton
   */
  public static EventFactory getInstance() {
    return eventFactory;
  }

  /**
   * Method to create message objects (events) from arrays of bytes. The first
   * byte contains the message identifier, and so is used in a switch statement
   * for creating the specific message to be returned.
   *
   * @param marshalledBytes byte[] message
   * @return event (message object) corresponding to marshalledBytes
   * @throws IOException if unmarshalling event fails
   */
  public Event createEvent(byte[] marshalledBytes) throws IOException {
    switch (marshalledBytes[0]) {
      case Protocol.REGISTER:
      case Protocol.DEREGISTER:
      case Protocol.SELECT_RESPONSE:
      case Protocol.LEAVE:
        return new PeerMessage(marshalledBytes);

      case Protocol.ID_COLLISION:
      case Protocol.NO_PEERS:
      case Protocol.SELECT_REQUEST:
      case Protocol.ACCEPT_STORAGE:
      case Protocol.DENY_STORAGE:
      case Protocol.WRITE_FAIL:
      case Protocol.WRITE_SUCCESS:
        return new GeneralMessage(marshalledBytes);

      case Protocol.SPECIAL_JOIN:
        return new JoinMessage(marshalledBytes);

      case Protocol.PEER_BROADCAST:
        return new PeerBroadcast(marshalledBytes);

      case Protocol.SEEK:
        return new SeekMessage(marshalledBytes);

      case Protocol.RELAY_FILE:
        return new RelayFile(marshalledBytes);

      default:
        logger.error("Event could not be created. " + marshalledBytes[0]);
        return null;
    }

  }
}