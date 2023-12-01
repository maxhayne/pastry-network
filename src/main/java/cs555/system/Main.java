package cs555.system;

import cs555.system.util.ApplicationProperties;
import cs555.system.util.HexUtilities;
import cs555.system.util.Logger;

import java.io.*;
import java.util.Properties;

public class Main {

  private static final Logger logger = Logger.getInstance();

  public static void main(String[] args) {

    logger.debug("DEBUG working.");
    logger.info("INFO working.");
    logger.error("ERROR working.");

    String hex = "09ad";
    byte[] con = HexUtilities.convertHexToBytes(hex);
    System.out.println(con.length);
    String hexBack = HexUtilities.convertBytesToHex(con);
    System.out.println(hexBack);

    // Playing around with properties
    Properties defaultProps = new Properties();
    try {
      FileInputStream in = new FileInputStream(
          System.getProperty("user.dir") + "/config/application.properties");
      defaultProps.load(in);
      in.close();
    } catch (Exception e) {
      logger.error(e.getMessage());
    }
    System.out.println(System.getProperty("user.dir"));
    System.out.println(defaultProps.getProperty("discoveryHost"));
    System.out.println(defaultProps.getProperty("discoveryPort"));
    System.out.println(ApplicationProperties.discoveryHost);
    System.out.println(ApplicationProperties.discoveryPort);
    System.out.println(ApplicationProperties.logLevel);

    System.out.println((short) Integer.MAX_VALUE);

    try {
      ByteArrayOutputStream bout = new ByteArrayOutputStream();
      DataOutputStream dout = new DataOutputStream(bout);

      dout.writeShort(Integer.MAX_VALUE);

      byte[] returnable = bout.toByteArray();
      bout.close();
      dout.close();

      ByteArrayInputStream bin = new ByteArrayInputStream(returnable);
      DataInputStream din = new DataInputStream(bin);

      System.out.println(din.readShort());
      System.out.println(Short.MAX_VALUE);

      bin.close();
      din.close();
    } catch (Exception e) {
      System.out.println(e.getMessage());
    }
  }
}