package cs555.system.node;

import cs555.system.transport.TCPConnection;
import cs555.system.transport.TCPConnectionCache;
import cs555.system.transport.TCPServerThread;
import cs555.system.util.ApplicationProperties;
import cs555.system.util.HexUtilities;
import cs555.system.util.Logger;
import cs555.system.util.PeerInformation;
import cs555.system.wireformats.*;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Random;
import java.util.Scanner;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.LinkedBlockingQueue;

public class StoreData implements Node {

  private static final Logger logger = Logger.getInstance();
  public final static String STORE = "STORE";
  public final static String RETRIEVE = "RETRIEVE";
  public final static String DELETE = "DELETE";
  private final String host;
  private final int port;
  private final TCPConnectionCache connections;
  private final ConcurrentSkipListSet<Path> storedFiles;
  private final BlockingQueue<Operation> ops;
  private Path workingDirectory;

  public record Operation(String type, Path path) {}

  public StoreData(String host, int port) {
    this.host = host;
    this.port = port;
    this.connections = new TCPConnectionCache(this);
    this.storedFiles = new ConcurrentSkipListSet<>();
    this.workingDirectory = Paths.get(System.getProperty("user.dir"), "data");
    this.ops = new LinkedBlockingQueue<Operation>();

  }

  public static void main(String[] args) {
    try (ServerSocket serverSocket = new ServerSocket(0)) {
      StoreData node =
          new StoreData(InetAddress.getLocalHost().getHostAddress(),
              serverSocket.getLocalPort());

      (new Thread(new TCPServerThread(node, serverSocket))).start();
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
      case Protocol.SELECT_RESPONSE:
        PeerInformation random = ((PeerMessage) event).getPeer();
        initiateFileOperation(random);
        break;

      case Protocol.NO_PEERS:
        initiateFileOperation(null);
        break;

      case Protocol.ACCEPT_STORAGE:
        sendFileToPeer(event, connection);
        break;

      case Protocol.DENY_STORAGE:
        storageDenied(event);
        break;

      case Protocol.WRITE_SUCCESS, Protocol.WRITE_FAIL:
        writeHandler(event);
        break;

      case Protocol.SERVE_FILE:
        receiveFile(event);
        break;

      default:
        logger.debug("Event couldn't be processed. " + event.getType());
        break;
    }
  }

  private void initiateFileOperation(PeerInformation peer) {
    Operation op = ops.poll();
    if (op != null) {
      if (peer == null) {
        logger.info("Cannot " + op.type() + " " + op.path() + ". No peers.");
        if (op.type().equals(DELETE)) {
          storedFiles.clear();
        }
      } else {
        initiateOperation(peer, op);
      }
    } else {
      logger.debug("There are no operations to initiate.");
    }
  }

  private void initiateOperation(PeerInformation peer, Operation op) {
    String path = op.path().toString();
    String filename = op.path().getFileName().toString();

    // Generate a key from the filename of the file being stored
    String key = generateKeyFromFilename(filename);

    SeekMessage message =
        new SeekMessage(op.type(), key, path, host + ":" + port);
    boolean sent = connections.send(peer.getAddress(), message, false);

    if (!sent) {
      ops.add(op);
      GeneralMessage select = new GeneralMessage(Protocol.SELECT_REQUEST);
      connections.send(ApplicationProperties.discoveryAddress, select, true);
    } else {
      logger.info(
          peer.getIdentifier() + " is our random peer to " + op.type() + " " +
          path + ", whose generated identifier is " + key + ".");
      if (op.type().equals(DELETE)) {
        storedFiles.removeIf(p -> p.getFileName().toString().equals(filename));
      }
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

  private void receiveFile(Event event) {
    ServeFile message = ((ServeFile) event);
    byte[] content = message.getContent();
    if (content != null) {
      writeReceivedFile(message.getFilename(), content);
    } else {
      logger.info(
          "The received content for " + message.getFilename() + " is null.");
    }
  }

  private void writeReceivedFile(String filename, byte[] content) {
    try {
      Path writeDirectory = Paths.get(System.getProperty("user.dir"), "reads");
      Files.createDirectories(writeDirectory);
      Path path = writeDirectory.resolve(filename);
      Files.write(path, content);
      logger.info("Wrote " + filename + " to the 'reads' directory.");
    } catch (IOException e) {
      logger.info("Unable to write " + filename + " to disk.");
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

        case "r", "retrieve":
          retrieve(splitCommand);
          break;

        case "d", "delete":
          delete(splitCommand);
          break;

        case "f", "files":
          displayFiles();
          break;

        case "wd":
          printWorkingDirectory(splitCommand);
          break;

        case "h", "help":
          displayHelp();
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

    Path path = parsePath(command[1]);
    Operation storeOp = new Operation(STORE, path);
    ops.add(storeOp);

    GeneralMessage select = new GeneralMessage(Protocol.SELECT_REQUEST);
    boolean sentMessage =
        connections.send(ApplicationProperties.discoveryAddress, select, true);
    if (!sentMessage) {
      ops.remove(storeOp);
    }
  }

  private void retrieve(String[] command) {
    if (command.length == 1) {
      logger.error("You must provide a filename. Use 'help' for usage.");
      return;
    }

    Path path = Paths.get(command[1]);
    Operation retrieveOp = new Operation(RETRIEVE, path);
    ops.add(retrieveOp);

    GeneralMessage select = new GeneralMessage(Protocol.SELECT_REQUEST);
    boolean sentMessage =
        connections.send(ApplicationProperties.discoveryAddress, select, true);
    if (!sentMessage) {
      ops.remove(retrieveOp);
    }
  }

  private void delete(String[] command) {
    if (command.length == 1) {
      logger.error("You must provide a filename. Use 'help' for usage.");
      return;
    }

    Path path = Paths.get(command[1]);
    Operation deleteOp = new Operation(DELETE, path);
    ops.add(deleteOp);

    GeneralMessage select = new GeneralMessage(Protocol.SELECT_REQUEST);
    boolean sentMessage =
        connections.send(ApplicationProperties.discoveryAddress, select, true);
    if (!sentMessage) {
      ops.remove(deleteOp);
    }
  }

  /**
   * Either prints the current value of 'workingDirectory', or tries to set it
   * based on input from the user.
   *
   * @param command String[] of command from user
   */
  private void printWorkingDirectory(String[] command) {
    if (command.length > 1) {
      setWorkingDirectory(command[1]);
    }
    System.out.printf("%2s%s%n", "", workingDirectory.toString());
  }

  /**
   * Takes a string representing a possible path and converts it to a path
   * object, replacing a leading tilde with the home directory, if possible.
   *
   * @param pathString String of possible path
   * @return Path object based on the pathString
   */
  private Path parsePath(String pathString) {
    Path parsedPath;
    if (pathString.startsWith("~")) {
      pathString = pathString.replaceFirst("~", "");
      pathString = removeLeadingFileSeparators(pathString);
      parsedPath = Paths.get(System.getProperty("user.home"));
    } else {
      parsedPath = workingDirectory;
    }
    if (!pathString.isEmpty()) {
      parsedPath = parsedPath.resolve(pathString);
    }
    return parsedPath.normalize(); // clean up path
  }

  /**
   * Sets the working directory based on input from the user.
   *
   * @param workdir new desired working directory
   */
  private void setWorkingDirectory(String workdir) {
    workingDirectory = parsePath(workdir);
  }

  /**
   * Removes leading file separators from a String. Helps to make sure calls
   * like 'pwd ~/' and 'pwd ~' and 'pwd ~///' all evaluate to the home
   * directory.
   *
   * @param directory String to be modified
   * @return string leading file separators removed
   */
  private String removeLeadingFileSeparators(String directory) {
    while (!directory.isEmpty() && directory.startsWith(File.separator)) {
      directory = directory.substring(1);
    }
    return directory;
  }

  /**
   * Print a list of valid commands for the user.
   */
  private void displayHelp() {
    System.out.printf("%2s%-21s : %s%n", "", "s[tore] path/filename",
        "store a file on the network");
    System.out.printf("%2s%-21s : %s%n", "", "r[etrieve] filename",
        "retrieve a file stored previously on the network");
    System.out.printf("%2s%-21s : %s%n", "", "d[elete] filename",
        "delete a file stored previously on the network");
    System.out.printf("%2s%-21s : %s%n", "", "f[iles]",
        "list the files this node has stored");
    System.out.printf("%2s%-21s : %s%n", "", "wd [new_workdir]",
        "print the current working directory or change it");
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
