package cs555.system.node;

import cs555.system.transport.TCPConnection;
import cs555.system.transport.TCPConnectionCache;
import cs555.system.transport.TCPServerThread;
import cs555.system.util.ApplicationProperties;
import cs555.system.util.Logger;
import cs555.system.util.PeerInformation;
import cs555.system.wireformats.Event;
import cs555.system.wireformats.GeneralMessage;
import cs555.system.wireformats.PeerMessage;
import cs555.system.wireformats.Protocol;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Scanner;

/**
 * Class to encapsulate behavior for the Discovery node. The Discovery node is
 * responsible for registering and deregistering peers to/from the network, and
 * for providing a random Peer to new registrants whom they can contact to
 * join the network.
 *
 * @author hayne
 */
public class Discovery implements Node {

  private static final Logger logger = Logger.getInstance();
  private final ArrayList<PeerInformation> registeredPeers;
  private final String host;
  private final int port;

  /**
   * Default constructor. Every Discovery node (only one per P2P network) must
   * have a host and a port at which they can be contacted.
   *
   * @param host at which they can be contacted
   * @param port on which their server socket is listening
   */
  public Discovery(String host, int port) {
    this.host = host;
    this.port = port;
    this.registeredPeers = new ArrayList<>();
  }

  /**
   * Discovery's main method acts as a launchpad for the Discovery node itself.
   * First, a ServerSocket is created, as the node must be reachable by other
   * nodes.
   *
   * @param args command line arguments for the Discovery node
   */
  public static void main(String[] args) {
    try (ServerSocket serverSocket = new ServerSocket(
        ApplicationProperties.discoveryPort)) {
      Discovery discovery =
          new Discovery(InetAddress.getLocalHost().getHostName(),
              serverSocket.getLocalPort());

      (new Thread(new TCPServerThread(discovery, serverSocket))).start();
      logger.info("Discovery started at " + discovery.getHost() + ":" +
                  discovery.getPort());

      discovery.interact();
    } catch (IOException e) {
      logger.error("Discovery failed to start. " + e.getMessage());
      System.exit(1);
    }
  }

  /**
   * Loop for user input at the Discovery node.
   */
  private void interact() {
    System.out.println(
        "Input a command or use 'help' to print a list of commands.");
    Scanner scanner = new Scanner(System.in);
    while (true) {
      String input = scanner.nextLine().toLowerCase();
      String command = input.split("\\s+")[0];
      switch (command) {
        case "p", "peers":
          displayPeers();
          break;

        case "h", "help":
          displayHelp();
          break;

        default:
          logger.error("Invalid command. Use 'help' for help.");
          break;
      }
    }
  }

  /**
   * Print all information about all Peers registered in the network.
   */
  private synchronized void displayPeers() {
    registeredPeers.forEach(peer -> {
      System.out.printf("%2s%s%n", "", peer.toString());
    });
  }

  /**
   * Print a list of valid commands for the user.
   */
  private void displayHelp() {
    System.out.printf("%2s%-10s : %s%n", "", "p[eers]",
        "print a list of peers constituting the network");
    System.out.printf("%2s%-10s : %s%n", "", "h[elp]",
        "print a list of valid commands");
  }

  @Override
  public String getHost() {
    return host;
  }

  @Override
  public int getPort() {
    return port;
  }

  @Override
  public void onEvent(Event event, TCPConnection connection) {
    switch (event.getType()) {
      case Protocol.REGISTER:
        register(event, connection);
        break;

      case Protocol.DEREGISTER:
        deregister(event);
        break;

      case Protocol.SELECT_REQUEST:
        select(connection);
        break;

      default:
        logger.error("Event couldn't be processed.");
        break;
    }
  }

  /**
   * Attempt to register the peer which has requested to register.
   *
   * @param event message received by Discovery
   * @param connection TCPConnection from which the request was received
   */
  private synchronized void register(Event event, TCPConnection connection) {
    PeerInformation newPeer = ((PeerMessage) event).getPeer();
    if (registeredPeers.contains(newPeer)) { // ID already exists
      GeneralMessage reply = new GeneralMessage(Protocol.ID_COLLISION);
      try {
        connection.getSender().send(reply.getBytes());
      } catch (IOException e) {
        logger.error("Failed to send ID_COLLISION reply. " + e.getMessage());
      }
      logger.debug(
          "Collision when " + newPeer.getIdentifier() + " tried to register.");
    } else { // Reply concerning successful registration
      select(connection); // Send PeerInformation about entry Peer
      registeredPeers.add(newPeer); // Add new Peer to registeredPeers
      logger.info("Peer joined: " + newPeer);
    }
  }

  /**
   * Send a PeerInformation object of a random LIVE peer in the overlay back to
   * the requester. If no peers have registered yet, a GeneralMessage is sent of
   * type Protocol.NO_PEERS.
   *
   * @param connection to be replied to
   */
  private synchronized void select(TCPConnection connection) {
    PeerInformation randomPeer;
    if ((randomPeer = getLivePeer()) == null) {
      GeneralMessage reply = new GeneralMessage(Protocol.NO_PEERS);
      try {
        connection.getSender().send(reply.getBytes());
      } catch (IOException e) {
        logger.error("Failed to send NO_PEERS reply. " + e.getMessage());
      }
    } else {
      PeerMessage reply = new PeerMessage(Protocol.SELECT_RESPONSE, randomPeer);
      try {
        connection.getSender().send(reply.getBytes());
      } catch (IOException e) {
        logger.error("Failed to send SELECT reply. " + e.getMessage());
      }
    }
  }

  /**
   * Returns one random, live peer from the registeredPeers array.
   *
   * @return PeerInformation of random, live peer, or null if none could be
   * found
   */
  private synchronized PeerInformation getLivePeer() {
    Collections.shuffle(registeredPeers); // won't scale, optimize later
    for (PeerInformation registeredPeer : registeredPeers) {
      if (isLive(registeredPeer)) {
        return registeredPeer;
      }
    }
    return null;
  }

  /**
   * Tests if a peer is live or not by attempting to establish a connection with
   * it.
   *
   * @param peer to test for liveness
   * @return true if the peer is live, false if not
   */
  private synchronized boolean isLive(PeerInformation peer) {
    TCPConnection connection =
        TCPConnectionCache.establishConnection(this, peer.getAddress());
    if (connection != null) {
      connection.close();
      return true;
    } else {
      return false;
    }
  }

  /**
   * Deregister the peer associated with the PeerInformation object included in
   * the message.
   *
   * @param event containing deregistration request
   */
  private synchronized void deregister(Event event) {
    PeerInformation peerToRemove = ((PeerMessage) event).getPeer();
    if (registeredPeers.remove(peerToRemove)) {
      logger.info("Peer left: " + peerToRemove);
    } else {
      logger.info("Peer tried to leave: " + peerToRemove);
    }
  }
}
