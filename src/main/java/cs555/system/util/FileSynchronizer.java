package cs555.system.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public class FileSynchronizer {

  private final static Logger logger = Logger.getInstance();
  private final ConcurrentHashMap<Path,Integer> files;

  public FileSynchronizer() {
    this.files = new ConcurrentHashMap<>();
  }

  /**
   * An attempt at synchronizing access to files of a particular name. If the
   * file has already been written, it will not be overwritten.
   *
   * @param path path to the file
   * @param content content to write to the file
   * @return true if the file was written, false otherwise
   */
  public boolean writeFile(Path path, byte[] content) {
    AtomicBoolean written = new AtomicBoolean(false);
    AtomicReference<byte[]> atomicContent = new AtomicReference<>(content);
    files.compute(path, (key, value) -> {
      if (value != null) {
        logger.error(path + " already exists, not overwriting. ");
        return value;
      } else {
        try {
          Files.createDirectories(path.getParent());
          Files.write(path, atomicContent.get());
          written.set(true);
          return 0;
        } catch (IOException e) {
          logger.error("Couldn't write " + path + ". " + e.getMessage());
          return null;
        }
      }
    });
    return written.get();
  }

  /**
   * An attempt at synchronizing access to files of a particular name.
   *
   * @param path path to the file
   * @return byte[] content of file, null if read failed or file isn't in the
   * files map
   */
  public byte[] readFile(Path path) {
    AtomicReference<byte[]> content = new AtomicReference<>();
    files.compute(path, (key, value) -> {
      if (value == null) {
        return null;
      } else {
        try {
          content.set(Files.readAllBytes(path));
          return value + 1;
        } catch (IOException e) {
          logger.error("Couldn't read " + path + ". " + e.getMessage());
          return null;
        }
      }
    });
    return content.get();
  }

  public void deleteFile(Path path) {
    files.compute(path, (key, value) -> {
      if (value != null) {
        try {
          Files.delete(path);
        } catch (IOException e) {
          logger.error("Couldn't delete " + path + ". " + e.getMessage());
        }
      }
      return null;
    });
  }

  public boolean contains(Path path) {
    return files.containsKey(path);
  }

  public Set<Path> getFileSet() {
    return files.keySet();
  }

  /**
   * Prints a list of files in the map, with the number of times each has been
   * read.
   */
  public void displayFiles() {
    StringBuilder sb = new StringBuilder();
    files.forEach((k, v) -> {
      sb.append("  ").append(v).append("  ").append(k).append("\n");
    });
    System.out.print(sb);
  }
}
