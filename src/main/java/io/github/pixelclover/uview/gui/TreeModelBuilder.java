package io.github.pixelclover.uview.gui;

import io.github.pixelclover.uview.gui.tree.TreeEntry;
import io.github.pixelclover.uview.model.UnityAsset;
import java.io.File;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import javax.swing.tree.DefaultMutableTreeNode;

/**
 * A utility class to build a {@link DefaultMutableTreeNode} structure from a collection of {@link
 * UnityAsset}s.
 */
public final class TreeModelBuilder {

  private TreeModelBuilder() {}

  /**
   * Builds a tree model structure from a flat collection of assets. It constructs the necessary
   * parent directory nodes to create a proper file system hierarchy.
   *
   * @param assets The collection of assets to include in the tree.
   * @return The root node of the constructed tree.
   */
  public static DefaultMutableTreeNode build(Collection<UnityAsset> assets) {
    // A temporary root to build the full package hierarchy under.
    DefaultMutableTreeNode masterRoot = new DefaultMutableTreeNode("master-root");
    if (assets == null || assets.isEmpty()) {
      // Return a root that can indicate emptiness, the view panel can handle this.
      return masterRoot;
    }

    Map<String, DefaultMutableTreeNode> nodeMap = new HashMap<>();
    nodeMap.put("", masterRoot);

    for (UnityAsset asset : assets) {
      String path = asset.assetPath().replace('\\', '/');
      String normalizedPath = path.endsWith("/") ? path.substring(0, path.length() - 1) : path;

      // Ensure the parent hierarchy for this asset exists.
      String parentPathStr = new File(normalizedPath).getParent();
      DefaultMutableTreeNode parentNode = getOrCreatePath(nodeMap, masterRoot, parentPathStr);

      // Now that the parent is guaranteed to exist, create the node for the current asset.
      if (!nodeMap.containsKey(normalizedPath)) {
        // FIX: Always use AssetEntry for an actual asset from the package, whether it's
        // a file or a folder asset. This preserves the underlying UnityAsset object.
        TreeEntry entry = new TreeEntry.AssetEntry(asset);
        DefaultMutableTreeNode assetNode = new DefaultMutableTreeNode(entry);
        parentNode.add(assetNode);
        nodeMap.put(normalizedPath, assetNode);
      }
    }

    // Return the master root itself. The JTree is set with setRootVisible(false),
    // so this root won't be seen, but its children (the actual top-level package folders) will.
    return masterRoot;
  }

  private static DefaultMutableTreeNode getOrCreatePath(
      Map<String, DefaultMutableTreeNode> nodeMap, DefaultMutableTreeNode root, String path) {
    if (path == null) {
      return root;
    }
    // Normalize path for lookup
    String normalizedPath = path.replace('\\', '/');
    if (nodeMap.containsKey(normalizedPath)) {
      return nodeMap.get(normalizedPath);
    }

    // If the path doesn't exist, create it by first creating its parent.
    String parentPathStr = new File(normalizedPath).getParent();
    DefaultMutableTreeNode parentNode = getOrCreatePath(nodeMap, root, parentPathStr);

    // Now, create the node for the current path.
    DefaultMutableTreeNode newNode =
        new DefaultMutableTreeNode(new TreeEntry.DirectoryEntry(normalizedPath + "/"));
    parentNode.add(newNode);
    nodeMap.put(normalizedPath, newNode);

    return newNode;
  }
}
