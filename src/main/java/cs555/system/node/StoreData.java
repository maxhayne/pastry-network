package cs555.system.node;

import cs555.system.transport.TCPConnection;
import cs555.system.transport.TCPConnectionCache;
import cs555.system.transport.TCPServerThread;
import cs555.system.util.ApplicationProperties;
import cs555.system.util.HexUtilities;
import cs555.system.util.Logger;
import cs555.system.util.PeerInformation;
import cs555.system.wireformats.*;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Random;
import java.util.Scanner;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.LinkedBlockingQueue;

public class StoreData implements Node {

  private static final Logger logger = Logger.getInstance();
  private final String host;
  private final int port;
  private final TCPConnectionCache connections;
  private final BlockingQueue<Path> filesAwaitingDiscovery;
  private final ConcurrentSkipListSet<Path> storedFiles;

  public StoreData(String host, int port) {
    this.host = host;
    this.port = port;
    this.connections = new TCPConnectionCache(this);
    this.filesAwaitingDiscovery = new LinkedBlockingQueue<>();
    this.storedFiles = new ConcurrentSkipListSet<>();
  }

  public static void main(String[] args) {
    try (ServerSocket serverSocket = new ServerSocket(0)) {

      StoreData node =
          new StoreData(InetAddress.getLocalHost().getHostAddress(),
              serverSocket.getLocalPort());

      new Thread(new TCPServerThread(node, serverSocket)).start();
      logger.info(
          "StoreData started at " + node.getHost() + ":" + node.getPort());

      node.interact();

    } catch (IOException e) {
      logger.error("StoreData didn't start up properly. " + e.getMessage());
      System.exit(1);
    }
  }

  @Override
  public void onEvent(Event event, TCPConnection connection) {
    switch (event.getType()) {
      case Protocol.NO_PEERS:
        logger.info(
            "No peers available to store " + filesAwaitingDiscovery.poll());
        break;

      case Protocol.SELECT_RESPONSE:
        initiateFileStorage(event, filesAwaitingDiscovery.poll());
        break;

      case Protocol.DENY_STORAGE:
        storageDenied(event);
        break;

      case Protocol.ACCEPT_STORAGE:
        sendFileToPeer(event, connection);
        break;

      case Protocol.WRITE_SUCCESS, Protocol.WRITE_FAIL:
        writeHandler(event);
        break;

      default:
        logger.debug("Event couldn't be processed. " + event.getType());
        break;
    }
  }

  private void storageDenied(Event event) {
    String[] split = ((GeneralMessage) event).getMessage().split("\\|");

    String filename = split[0];
    String trace = split[1].replaceAll(",", " -> ");

    logger.info("Peer denied storage request for " + filename + ". " +
                "Possible filename conflict.");
    logger.info("Traceroute of file lookup: " + trace);
  }

  private void writeHandler(Event event) {
    GeneralMessage message = ((GeneralMessage) event);

    String filename = message.getMessage();

    if (message.getType() == Protocol.WRITE_SUCCESS) {
      logger.info("Storage operation of " + filename + " succeeded. ");
    } else {
      logger.info("Storage operation of " + filename + " failed. ");
      storedFiles.removeIf(
          path -> path.getFileName().toString().equals(filename));
    }
  }

  private void sendFileToPeer(Event event, TCPConnection connection) {
    String message = ((GeneralMessage) event).getMessage();
    String[] split = message.split("\\|");

    String pathString = split[0];
    String trace = split[1].replaceAll(",", " -> ");
    logger.info("Traceroute of file lookup: " + trace);

    Path path = Paths.get(pathString);
    byte[] content;
    try {
      content = Files.readAllBytes(path);
    } catch (IOException e) {
      logger.info("Unable to read " + path + ". " + e.getMessage());
      return;
    }

    String filename = path.getFileName().toString();
    String key = generateKeyFromFilename(filename);

    RelayFile relayFile =
        new RelayFile(key, filename, content, host + ":" + port);
    try {
      connection.getSender().send(relayFile.getBytes());
      storedFiles.add(path);
      logger.info("Sent " + filename + " to peer.");
    } catch (IOException e) {
      logger.info("Failed to send " + path + " to peer for storage.");
    }
  }


  private void initiateFileStorage(Event event, Path path) {
    if (path == null) {
      logger.info("There is no file waiting to be stored on the network.");
      return;
    }

    PeerInformation randomPeer = ((PeerMessage) event).getPeer();

    String filePath = path.toString();
    String filename = path.getFileName().toString();

    // Generate a key from the filename of the file being stored
    String key = generateKeyFromFilename(filename);

    SeekMessage seekMessage = new SeekMessage(key, filePath, host + ":" + port);
    boolean sent =
        connections.send(randomPeer.getAddress(), seekMessage, false);

    if (!sent) {
      filesAwaitingDiscovery.add(path);
      GeneralMessage select = new GeneralMessage(Protocol.SELECT_REQUEST);
      connections.send(ApplicationProperties.discoveryAddress, select, true);
    } else {
      logger.info(randomPeer.getIdentifier() + " is our random peer for " +
                  "for storing " + filePath + ", whose filename identifier is" +
                  " " + key + ".");
    }
  }

  public static String generateKeyFromFilename(String filename) {
    Random random = new Random(filename.hashCode());
    byte[] keyBytes = new byte[2];
    random.nextBytes(keyBytes);
    return HexUtilities.convertBytesToHex(keyBytes);
  }

  private void interact() {
    System.out.println(
        "Enter a command or use 'help' to print a list of commands.");
    Scanner scanner = new Scanner(System.in);
    interactLoop:
    while (true) {
      String command = scanner.nextLine();
      String[] splitCommand = command.split("\\s+");
      switch (splitCommand[0].toLowerCase()) {

        case "s", "store":
          store(splitCommand);
          break;

        case "f", "files":
          displayFiles();
          break;

        case "h", "help":
          showHelp();
          break;

        case "e", "exit":
          break interactLoop;

        default:
          logger.error("Invalid command. Use 'help' for help.");
          break;
      }
    }
    System.exit(0);
  }

  private void displayFiles() {
    storedFiles.forEach(path -> {
      System.out.printf("%2s%s%n", "", path.toString());
    });
  }

  private void store(String[] command) {
    if (command.length == 1) {
      logger.error("You must provide a filename. Use 'help' for usage.");
      return;
    }

    String filename = command[1];

    Path filePath;
    try {
      filePath = Paths.get(System.getProperty("user.dir"), filename);
      filePath = filePath.normalize();
    } catch (InvalidPathException e) {
      logger.info("Path cannot be discerned from filename. " + e.getMessage());
      return;
    }

    filesAwaitingDiscovery.add(filePath);

    GeneralMessage select = new GeneralMessage(Protocol.SELECT_REQUEST);
    boolean sent =
        connections.send(ApplicationProperties.discoveryAddress, select, true);
    if (!sent) {
      filesAwaitingDiscovery.remove(filePath);
    }
  }

  /**
   * Print a list of valid commands for the user.
   */
  private void showHelp() {
    System.out.printf("%2s%-21s : %s%n", "", "s[tore] path/filename",
        "store a file on the network");
    System.out.printf("%2s%-21s : %s%n", "", "r[etrieve] # [#...]",
        "retrieve a file stored previously on the network");
    System.out.printf("%2s%-21s : %s%n", "", "d[elete] # [#...]",
        "delete a file stored previously on the network");
    System.out.printf("%2s%-21s : %s%n", "", "f[iles]",
        "list the files this node has stored");
    System.out.printf("%2s%-21s : %s%n", "", "e[xit]", "shut down this node");
    System.out.printf("%2s%-21s : %s%n", "", "h[elp]",
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
}
