package io.github.pixelclover.uview.model;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Represents the contents of a .unitypackage file. This class is a container for all the {@link
 * UnityAsset} objects within the package, providing methods to access, add, and remove them.
 */
public class UnityPackage {
  private static final Logger LOGGER = LogManager.getLogger(UnityPackage.class);
  private final Map<String, UnityAsset> assetsByGuid = new HashMap<>();
  private final Map<String, String> pathToGuid = new HashMap<>();

  /**
   * Cleans the raw pathname string from a package's "pathname" file. Some versions of Unity or
   * external tools encode pathnames with trailing control characters (like newlines) or a specific
   * "00" suffix. This method sanitizes the path to make it usable.
   *
   * @param pathnameBytes The raw byte array for the pathname.
   * @return A clean, usable path string.
   */
  private static String getString(byte[] pathnameBytes) {
    String pathname = new String(pathnameBytes, StandardCharsets.UTF_8);

    // This robustly handles several observed formatting quirks:
    // 1. Remove all control characters, which gets rid of trailing newlines (\n) or nulls (\0).
    //    This turns "File.cs\n00" into "File.cs00".
    pathname = pathname.replaceAll("\\p{Cntrl}", "");

    // 2. After cleaning control chars, check for and remove a specific "00" suffix, which is a
    //    known bug in some package creation tools.
    if (pathname.endsWith("00")) {
      pathname = pathname.substring(0, pathname.length() - 2);
    }
    return pathname;
  }

  /**
   * Populates the package with assets from raw data. The raw data is expected to be a map of GUIDs
   * to a map of file types (e.g., "pathname", "asset") to their byte content.
   *
   * @param rawData The raw asset data, typically extracted from a .unitypackage file.
   */
  public void loadFromRawData(Map<String, Map<String, byte[]>> rawData) {
    clear();
    for (Map.Entry<String, Map<String, byte[]>> entry : rawData.entrySet()) {
      String guid = entry.getKey();
      Map<String, byte[]> files = entry.getValue();
      if (!files.containsKey("pathname")) {
        continue;
      }

      byte[] pathnameBytes = files.get("pathname");
      if (pathnameBytes == null) {
        continue;
      }

      String pathname = getString(pathnameBytes);

      UnityAsset asset =
          new UnityAsset(
              guid,
              pathname,
              files.get("asset"),
              files.get("asset.meta"),
              files.get("preview.png"));
      addAsset(asset);
    }
  }

  /** Clears all assets from the package, resetting it to an empty state. */
  public void clear() {
    assetsByGuid.clear();
    pathToGuid.clear();
  }

  /**
   * Gets an unmodifiable view of the assets in this package, mapped by their GUID.
   *
   * @return An unmodifiable map of GUIDs to {@link UnityAsset}s.
   */
  public Map<String, UnityAsset> getAssets() {
    return Collections.unmodifiableMap(assetsByGuid);
  }

  /**
   * Retrieves an asset by its full path.
   *
   * @param assetPath The path of the asset to retrieve (e.g., "Assets/Scripts/Player.cs").
   * @return The {@link UnityAsset} if found, otherwise {@code null}.
   */
  public UnityAsset getAssetByPath(String assetPath) {
    String guid = pathToGuid.get(assetPath);
    if (guid != null) {
      return assetsByGuid.get(guid);
    }
    return null;
  }

  /**
   * Adds an asset to the package. If an asset with the same GUID already exists, it will be
   * replaced, effectively updating the asset.
   *
   * @param asset The {@link UnityAsset} to add or update.
   */
  public void addAsset(UnityAsset asset) {
    assetsByGuid.put(asset.guid(), asset);
    pathToGuid.put(asset.assetPath(), asset.guid());
  }

  /**
   * Removes an asset from the package by its full path.
   *
   * @param assetPath The path of the asset to remove.
   */
  public void removeAssetByPath(String assetPath) {
    String guid = pathToGuid.remove(assetPath);
    if (guid != null) {
      assetsByGuid.remove(guid);
    }
  }
}
