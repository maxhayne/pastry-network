package cs555.system.util;

import java.nio.file.Paths;

/**
 * Interface to store properties as loaded from the application.properties file.
 * The singleton PropertyLoader is created, loads the properties stored in the
 * properties file, and then its method getProperty( String ) are used
 */
public interface ApplicationProperties {
  String propertyFile = Paths
                            .get(System.getProperty("user.dir"), "config",
                                "application" + ".properties")
                            .toString();

  String discoveryHost =
      PropertyLoader.getInstance().getProperty("discoveryHost", "localhost");

  int discoveryPort = Integer.parseInt(
      PropertyLoader.getInstance().getProperty("discoveryPort", "32096"));

  String discoveryAddress = discoveryHost + ":" + discoveryPort; // nice-to-have

  String logLevel =
      PropertyLoader.getInstance().getProperty("logLevel", "info");
}
