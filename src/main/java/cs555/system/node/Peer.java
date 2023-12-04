package cs555.system.node;

import cs555.system.routing.RoutingInformation;
import cs555.system.transport.TCPConnection;
import cs555.system.transport.TCPConnectionCache;
import cs555.system.transport.TCPServerThread;
import cs555.system.util.ApplicationProperties;
import cs555.system.util.FileSynchronizer;
import cs555.system.util.Logger;
import cs555.system.util.PeerInformation;
import cs555.system.wireformats.*;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Scanner;
import java.util.Set;
import java.util.stream.Collectors;

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
  private final FileSynchronizer files;

  public Peer(String identifier, String host, int port) {
    this.self = new PeerInformation(identifier, host, port);
    this.routingInformation = new RoutingInformation();
    this.connections = new TCPConnectionCache(this);
    this.files = new FileSynchronizer();
  }

  public static void main(String[] args) {
    // Interpret command line argument as custom identifier
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

      case Protocol.SEEK:
        routeSeekMessage(event);
        break;

      case Protocol.RELAY_FILE:
        fileHandler(event);
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

  private void fileHandler(Event event) {
    RelayFile message = (RelayFile) event;
    message.incrementHops();
    PeerInformation next = relay(message.getKey(), message, message.getHops());
    if (self.equals(next)) { // We should store the file
      storeFile(message);
      next = routingInformation.lookup(message.getKey());
      if (!self.equals(next)) {
        migrateFiles();
      }
    }
  }

  private synchronized void migrateFiles() {
    logger.debug("migrateFiles() called.");
    Set<Path> fileSet = files.getFileSet();
    for (Path path : fileSet) {
      String filename = path.getFileName().toString();
      String key = StoreData.generateKeyFromFilename(filename);
      PeerInformation closestPeer = routingInformation.lookup(key);
      if (!self.equals(closestPeer)) {
        byte[] content = files.readFile(path);
        if (content != null) {
          RelayFile message = new RelayFile(key, filename, content, "");
          closestPeer = relay(key, message, message.getHops());
          if (!self.equals(closestPeer)) {
            files.deleteFile(path);
            logger.debug("File " + path + " was relocated to " +
                         closestPeer.getIdentifier() + " and deleted.");
          } else {
            logger.debug("File " + path + "was not relocated.");
          }
        }
      }
    }
  }

  // TODO sent notification back to StoreData about whether the operation was
  //  successful or not. This means that the RelayFile message must have the
  //  address of the StoreData node that originated it.
  private void storeFile(RelayFile message) {
    Path path = getFilePath(message.getFilename());
    boolean written = files.writeFile(path, message.getContent());

    if (written) {
      logger.info(path + " was written to disk.");
    } else {
      logger.info("Failed to write " + path + " to disk.");
    }

    // Send status message back to StoreData if necessary
    if (!message.getAddress().isEmpty()) {
      byte type = written ? Protocol.WRITE_SUCCESS : Protocol.WRITE_FAIL;
      GeneralMessage response = new GeneralMessage(type, message.getFilename());
      connections.send(message.getAddress(), response, false);
    }
  }

  // We could remove this large synchronized block, and just allow an
  // interleaving of joins and removes and adds, as all of those calls are
  // synchronized anyway. But, if we take it out, there
  // is one edge case where it will be destructive. Say we perform a lookup,
  // lookup returns node ffff. Then we do a context switch and receive a
  // message from ffff that it has deregistered. In the meantime, a new peer
  // joins the network with id ffff, routes its entire routing message, and
  // then broadcasts its arrival in the network to all peers it knows about
  // (including us). Then, the new ffff is put into the routing table and
  // leafset. Meanwhile, we have been context switched out the whole time, and
  // because we couldn't send the message to ffff, we decide to remove ffff.
  // Unbeknownst to us, the old ffff was replaced with a new ffff, and now
  // we're removing the wrong node. So, this could be an issue, though it is
  // extremely unlikely. When we're removing a peer, we could modify the
  // equals() function to check not only the id but also the ip and port.
  // This would solve the issue too, but we'd have to make sure that that
  // would work in other contexts. This would affect the behavior at the
  // discovery node, as we couldn't use the straight equals() function to
  // check if a node of that id has already been added.

  private PeerInformation relay(String key, Event event, int hop) {
    PeerInformation next;
    synchronized(routingInformation) {
      next = routingInformation.lookup(key);
      if (self.equals(next)) {
        return self;
      } else {
        while (!connections.send(next.getAddress(), event, false)) {
          removePeerFromRouting(next); // Could some time if relocating files
          next = routingInformation.lookup(key);
          // Next might now be self...
          if (self.equals(next)) {
            return self;
          }
        }
      }
    }
    logger.info("Message type " + event.getType() + " with key " + key +
                " relayed to " + next.getIdentifier() + ", hop " + hop);
    return next;
  }

  private void routeSeekMessage(Event event) {
    SeekMessage message = (SeekMessage) event;
    message.addHop(self);
    PeerInformation next =
        relay(message.getKey(), message, message.getHops().size());
    if (self.equals(next)) {
      contactStoreData(message);
    }
  }

  private void contactStoreData(SeekMessage message) {
    Path remotePath = Paths.get(message.getPath());
    String filename = remotePath.getFileName().toString();

    Path localPath = getFilePath(filename);
    byte type = files.contains(localPath) ? Protocol.DENY_STORAGE :
                    Protocol.ACCEPT_STORAGE;

    // Adding the hops to the message in GeneralMessage
    // A little lazy, but creating another message type for this is too much
    String joinedHops = message
                            .getHops()
                            .stream()
                            .map(PeerInformation::getIdentifier)
                            .collect(Collectors.joining(","));

    String pathAndHops = String.join("|", remotePath.toString(), joinedHops);

    GeneralMessage response = new GeneralMessage(type, pathAndHops);
    connections.send(message.getRequestAddress(), response, true);
  }

  private void removePeerFromRouting(PeerInformation peer) {
    boolean removed = routingInformation.removePeer(peer);
    if (removed) {
      routingInformation.displayRoutingInformation();
    }
  }

  private void integrateNewPeer(Event event) {
    PeerBroadcast broadcast = (PeerBroadcast) event;
    boolean added = routingInformation.addPeer(broadcast.getPeer());
    for (PeerInformation peer : broadcast.getContents()) {
      if (!self.equals(peer)) {
        if (routingInformation.addPeer(peer)) {
          added = true;
        }
      }
    }
    if (added) {
      System.out.println("ROUTING UPDATED:");
      routingInformation.displayRoutingInformation();
      migrateFiles();
    }
  }

  private void buildRoutingInformation(JoinMessage joinMessage) {
    routingInformation.initialize(self, joinMessage, connections);
  }

  private void attachRoutingInformation(JoinMessage message) {
    routingInformation.attachRoutingToJoinMessage(message);
    message.getHops().add(self);

    String key = message.getDestination().getIdentifier();
    int hop = message.getHops().size();
    PeerInformation next = relay(key, message, hop);
    if (self.equals(next)) { // Forward directly to destination
      String destAddress = message.getDestination().getAddress();
      boolean sent = connections.send(destAddress, message, false);
      // TODO create a function that will generate a log message for a
      //  message relay based on type and key and hop count and success
      if (sent) {
        logger.info("Message type " + message.getType() + " with key " + key +
                    " relayed to " + key + ", hop " + hop);
      } else {
        logger.info("Join message couldn't be relayed to destination.");
      }
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
    if (event.getType() == Protocol.NO_PEERS) {
      printRegistrationDetails(null);
      routingInformation.initialize(self, null, null);
    } else {
      PeerInformation selectPeer = ((PeerMessage) event).getPeer();
      JoinMessage joinMessage = new JoinMessage(self);
      joinMessage.getHops().add(self);
      if (!connections.send(selectPeer.getAddress(), joinMessage, false)) {
        PeerMessage select = new PeerMessage(Protocol.SELECT_REQUEST, self);
        connections.send(ApplicationProperties.discoveryAddress, select, true);
      } else {
        printRegistrationDetails(selectPeer);
      }
    }
  }

  private void printRegistrationDetails(PeerInformation randomPeer) {
    System.out.println("Registered as " + self);
    if (randomPeer != null) {
      System.out.println("Entry peer: " + randomPeer);
    } else {
      System.out.println("We are the first peer to join the network.");
    }
  }


  // Assumes that the Peer has already been initialized
  private Path getFilePath(String filename) {
    return Paths.get(File.separator, "tmp", "peer-" + self.getIdentifier(),
        filename);
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
          files.displayFiles();
          break;

        case "l", "leave":
          leave();
          break interactLoop;

        case "h", "help":
          displayHelp();
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
  private void displayHelp() {
    System.out.printf("%2s%-9s : %s%n", "", "r[outing]",
        "print routing table and leaf set for this node");
    System.out.printf("%2s%-9s : %s%n", "", "f[iles]",
        "print the list of files stored at this node");
    System.out.printf("%2s%-9s : %s%n", "", "l[eave]", "leave the network");
    System.out.printf("%2s%-9s : %s%n", "", "h[elp]",
        "print a list of valid commands");
  }

  /**
   * Gracefully leaves the P2P network. First sends deregistration request to
   * the Discovery, then attempts to relocate local files to appropriate
   * replacement peers.
   */
  private void leave() {
    logger.debug("Notifying the Discovery node of deregistration.");
    // Deregister with Discovery
    PeerMessage deregister = new PeerMessage(Protocol.DEREGISTER, self);
    connections.send(ApplicationProperties.discoveryAddress, deregister, false);

    logger.debug("Notifying peers of exit.");
    // Notify other peers as a courtesy
    PeerMessage leave = new PeerMessage(Protocol.LEAVE, self);
    Set<PeerInformation> peerSet = routingInformation.getPeerSet(false);
    for (PeerInformation peer : peerSet) {
      connections.send(peer.getAddress(), leave, false);
    }

    // Sleep to wait for any remaining messages to be processed
    try {
      logger.debug("Waiting 1 second for remaining messages to be processed.");
      Thread.sleep(1000);
    } catch (InterruptedException e) {
      logger.error("Interrupted. " + e.getMessage());
    }

    // Attempt to relocate all local files to the best peer
    // Could just send the file to any peer, and let the routing do the rest,
    // but that would be wasteful of network activity. Wrote a couple of
    // functions that somewhat violate DRY to find the closest non-self peers
    migrateFilesBeforeLeaving();

    // Sleep to wait for messages to send
    try {
      logger.debug("Waiting 1 second for files to be sent to other peers.");
      Thread.sleep(1000);
    } catch (InterruptedException e) {
      logger.error("Interrupted. " + e.getMessage());
    }

  }

  private void migrateFilesBeforeLeaving() {
    Set<PeerInformation> peerSet = routingInformation.getPeerSet(false);
    Set<Path> localFiles = files.getFileSet();
    for (Path path : localFiles) {
      byte[] content = files.readFile(path);
      if (content != null) {
        String filename = path.getFileName().toString();
        String key = StoreData.generateKeyFromFilename(filename);
        RelayFile message = new RelayFile(key, filename, content, "");
        PeerInformation closestPeer = getClosestPeer(key, peerSet);
        while (closestPeer != null &&
               !connections.send(closestPeer.getAddress(), message, false)) {
          peerSet.remove(closestPeer);
          closestPeer = getClosestPeer(key, peerSet);
        }
        if (closestPeer != null) {
          logger.info("File " + path + " sent to " + closestPeer);
        } else {
          logger.info("File " + path + " is permanently lost.");
        }
      }
    }
  }

  private PeerInformation getClosestPeer(String key,
      Set<PeerInformation> peerSet) {
    PeerInformation closestPeer = null;
    int closestDistance = 65536 + 1;
    for (PeerInformation peer : peerSet) {
      int distance = peer.distanceTo(key);
      if (distance < closestDistance) {
        closestDistance = distance;
        closestPeer = peer;
      }
    }
    return closestPeer;
  }
}