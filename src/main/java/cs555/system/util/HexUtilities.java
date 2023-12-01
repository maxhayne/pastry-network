package cs555.system.util;

/**
 * Class to hold functions provided by Dr. Pallickara for converting between
 * Hexadecimal and String representations for node ID's.
 *
 * @author hayne
 */
public class HexUtilities {

  /**
   * This method converts a set of bytes into a hexadecimal representation.
   *
   * @param buf byte[] representation of hexadecimal
   * @return String representation of hexadecimal
   */
  public static String convertBytesToHex(byte[] buf) {
    StringBuilder strBuf = new StringBuilder();
    for (byte b : buf) {
      int byteValue = (int) b&0xff;
      if (byteValue <= 15) {
        strBuf.append("0");
      }
      strBuf.append(Integer.toString(byteValue, 16));
    }
    return strBuf.toString();
  }

  /**
   * Converts a specified hexadecimal String into a set of bytes.
   *
   * @param hexString String representing hexadecimal
   * @return byte[] representation of hexadecimal
   */
  public static byte[] convertHexToBytes(String hexString) {
    int size = hexString.length();
    byte[] buf = new byte[size/2];
    int j = 0;
    for (int i = 0; i < size; ++i) {
      String a = hexString.substring(i, i + 2);
      int valA = Integer.parseInt(a, 16);
      ++i;
      buf[j] = (byte) valA;
      ++j;
    }
    return buf;
  }

  // Assumes identifier is less than or equal to 4 bytes
  public static int convertHexToInt(String hexString) {
    byte[] idBytes = convertHexToBytes(hexString);
    assert idBytes.length <= 8;
    int value = 0;
    for (byte b : idBytes) {
      value = (value << 8) + (b&0xFF);
    }
    return value;
  }

  /**
   * Converts a single hexadecimal character to an integer.
   *
   * @param hex the hexadecimal to be converted
   * @return int representation of hexadecimal
   */
  public static int hexToDecimal(char hex) {
    return Integer.parseInt(String.valueOf(hex), 16);
  }

  /**
   * Returns the first index where two strings don't match. If the two strings
   * are identical, the index of the last character is returned.
   *
   * @param id1 first string
   * @param id2 second string
   * @return index of first difference
   */
  public static int firstDifference(String id1, String id2) {
    assert id1.length() == id2.length();
    int match = 0;
    for (int i = 0; i < id1.length(); ++i) {
      if (id1.charAt(i) == id2.charAt(i)) {
        ++match;
        continue;
      }
      break;
    }
    return match == id1.length() ? id1.length() - 1 : match;
  }
}
