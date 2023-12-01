package cs555.system.node;

import cs555.system.routing.RoutingInformation;
import cs555.system.transport.TCPConnection;
import cs555.system.transport.TCPConnectionCache;
import cs555.system.transport.TCPServerThread;
import cs555.system.util.ApplicationProperties;
import cs555.system.util.Logger;
import cs555.system.util.PeerInformation;
import cs555.system.wireformats.*;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.util.Scanner;
import java.util.Set;

/**
 * Class to encapsulate behavior for the Peer node.
 *
 * @author hayne
 */
public class Peer implements Node {

  private static final Logger logger = Logger.getInstance();
  private final PeerInformation self;
  private final RoutingInformation routingInformation;
  private final TCPConnectionCache connections;

  public Peer(String identifier, String host, int port) {
    this.self = new PeerInformation(identifier, host, port);
    this.routingInformation = new RoutingInformation();
    this.connections = new TCPConnectionCache(this);
  }

  public static void main(String[] args) {
    String identifier =
        args.length > 0 ? args[0] : PeerInformation.generateIdentifier();
    try (ServerSocket serverSocket = new ServerSocket(0)) {
      Peer peer =
          new Peer(identifier, InetAddress.getLocalHost().getHostAddress(),
              serverSocket.getLocalPort());

      // Join the network
      if (peer.sendRegistrationToDiscovery()) {
        TCPServerThread serverThread = new TCPServerThread(peer, serverSocket);
        new Thread(serverThread).start();
        peer.interact();
      }
    } catch (IOException e) {
      logger.info("Peer startup failed. " + e.getMessage());
      System.exit(1);
    }
  }

  private boolean sendRegistrationToDiscovery() {
    PeerMessage registration = new PeerMessage(Protocol.REGISTER, self);
    return connections.send(ApplicationProperties.discoveryAddress,
        registration, true);
  }

  @Override
  public String getHost() {
    return self.getHost();
  }

  @Override
  public int getPort() {
    return self.getPort();
  }

  @Override
  public void onEvent(Event event, TCPConnection connection) {
    switch (event.getType()) {

      case Protocol.SELECT_RESPONSE, Protocol.NO_PEERS:
        registrationHandler(event);
        break;

      case Protocol.ID_COLLISION:
        collisionHandler();
        break;

      case Protocol.SPECIAL_JOIN:
        JoinMessage joinMessage = (JoinMessage) event;
        if (joinMessage.getDestination().equals(self)) {
          buildRoutingInformation(joinMessage);
        } else {
          attachRoutingInformation(joinMessage);
        }
        break;

      case Protocol.PEER_BROADCAST:
        integrateNewPeer(event);
        break;

      case Protocol.LEAVE:
        removePeerFromRouting(((PeerMessage) event).getPeer());
        break;

      default:
        logger.debug("Event couldn't be processed. " + event.getType());
    }
  }

  private synchronized void removePeerFromRouting(PeerInformation peer) {
    boolean removed = routingInformation.removePeer(peer);
    if (removed) {
      routingInformation.displayRoutingInformation();
    }
  }

  private synchronized void integrateNewPeer(Event event) {
    PeerBroadcast broadcast = (PeerBroadcast) event;
    boolean added = routingInformation.addPeer(broadcast.getPeer());
    for (PeerInformation peer : broadcast.getContents()) {
      if (!peer.equals(self)) {
        if (routingInformation.addPeer(peer)) {
          added = true;
        }
      }
    }
    if (added) {
      routingInformation.displayRoutingInformation();
    }
  }

  private void buildRoutingInformation(JoinMessage joinMessage) {
    routingInformation.initialize(self, joinMessage, connections);
  }

  private synchronized void attachRoutingInformation(JoinMessage joinMessage) {
    routingInformation.attachRoutingToJoinMessage(joinMessage);
    joinMessage.getHops().add(self);
    String key = joinMessage.getDestination().getIdentifier();
    PeerInformation next = routingInformation.lookup(key);
    next = next.equals(self) ? joinMessage.getDestination() : next;
    boolean relayed = true;
    while (!connections.send(next.getAddress(), joinMessage, false)) {
      removePeerFromRouting(next);
      if (next.equals(joinMessage.getDestination())) {
        logger.info("JOIN for " + key + " couldn't be relayed.");
        relayed = false;
        break;
      }
      joinMessage.removePeer(next);
      next = routingInformation.lookup(key);
      next = next.equals(self) ? joinMessage.getDestination() : next;
    }
    if (relayed) {
      logger.info(
          "JOIN for " + key + " relayed to " + next.getIdentifier() + ", hop " +
          (joinMessage.getHops().size() - 1));
    }
  }

  private void collisionHandler() {
    self.setIdentifier(PeerInformation.generateIdentifier());
    sendRegistrationToDiscovery();
  }

  /**
   * Interprets the response from the Discovery node concerning a registration
   * request. If the response's attached PeerInformation is equal to this Peer's
   * PeerInformation, no other Peer is contacted. Otherwise, a SPECIAL_JOIN
   * message is sent to the random peer.
   *
   * @param event message being processed
   */
  private void registrationHandler(Event event) {
    StringBuilder sb = new StringBuilder();
    if (event.getType() == Protocol.NO_PEERS) {
      sb.append("Registered as ").append(self).append("\n");
      sb.append("We are first Peer to join the network.").append("\n");
      System.out.print(sb);
      routingInformation.initialize(self, null, null);
    } else {
      PeerInformation selectPeer = ((PeerMessage) event).getPeer();
      JoinMessage joinMessage = new JoinMessage(self);
      joinMessage.getHops().add(self);
      if (!connections.send(selectPeer.getAddress(), joinMessage, false)) {
        PeerMessage select = new PeerMessage(Protocol.SELECT_REQUEST, self);
        connections.send(ApplicationProperties.discoveryAddress, select, true);
      } else {
        sb.append("Registered as ").append(self).append("\n");
        sb.append("Entry Peer: ").append(selectPeer).append("\n");
        System.out.print(sb);
      }
    }
  }

  /**
   * Loops for user interaction at the Peer.
   */
  private void interact() {
    System.out.println(
        "Enter a command or use 'help' to print a list of commands.");
    Scanner scanner = new Scanner(System.in);
    interactLoop:
    while (true) {
      String command = scanner.nextLine();
      String[] splitCommand = command.split("\\s+");
      switch (splitCommand[0].toLowerCase()) {
        case "r", "routing":
          routingInformation.displayRoutingInformation();
          break;

        case "f", "files":
          // Print files command here
          break;

        case "l", "leave":
          leave();
          break interactLoop;

        case "h", "help":
          showHelp();
          break;

        default:
          logger.error("Invalid command. Use 'help' for help.");
          break;
      }
    }
    connections.closeConnections();
    System.exit(0);
  }

  /**
   * Print a list of valid commands for the user.
   */
  private void showHelp() {
    System.out.printf("%3s%-10s : %s%n", "", "r[outing]",
        "print routing table and leaf set for this node");
    System.out.printf("%3s%-10s : %s%n", "", "f[iles]",
        "print the list of files stored at this node");
    System.out.printf("%3s%-10s : %s%n", "", "l[eave]", "leave the network");
    System.out.printf("%3s%-10s : %s%n", "", "h[elp]",
        "print a list of valid commands");
  }

  /**
   * Gracefully leaves the P2P network. First sends deregistration request to
   * the Discovery, then attempts to relocate local files to appropriate
   * replacement peers.
   */
  private synchronized void leave() {
    // Deregister with Discovery
    PeerMessage deregister = new PeerMessage(Protocol.DEREGISTER, self);
    connections.send(ApplicationProperties.discoveryAddress, deregister, false);

    // Notify other peers as a courtesy
    PeerMessage leave = new PeerMessage(Protocol.LEAVE, self);
    Set<PeerInformation> peerSet = routingInformation.getPeerSet(false);
    for (PeerInformation peer : peerSet) {
      connections.send(peer.getAddress(), leave, false);
    }

    // TODO relocate files to other peers
  }
}