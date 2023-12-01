package cs555.system.wireformats;

import java.io.IOException;

/**
 * All messages implement this interface. All events can then be handled by a
 * generic function (onEvent) which can, without knowing the exact message type,
 * call the correct method to deal with the event.
 *
 * @author hayne
 */
public interface Event {
  /**
   * All message types are numbered, this returns the type.
   *
   * @return Type of message this is.
   */
  byte getType();

  /**
   * Converts all data in object which represents the message into a byte stream
   * ready to be sent out over the network.
   *
   * @return An array of bytes of the object
   * @throws IOException if data output streams fail to be written to
   */
  byte[] getBytes() throws IOException;
}
