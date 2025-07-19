package io.github.pixelclover.uview.gui;

import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLightLaf;
import com.formdev.flatlaf.extras.FlatSVGIcon;
import io.github.pixelclover.uview.App;
import io.github.pixelclover.uview.core.PackageManager;
import io.github.pixelclover.uview.core.SettingsManager;
import io.github.pixelclover.uview.model.UnityAsset;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.*;
import java.util.List;
import javax.swing.*;
import javax.swing.Timer;
import javax.swing.event.MenuEvent;
import javax.swing.event.MenuListener;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.plaf.FontUIResource;

/**
 * The main window of the UView application. It manages the UI, including menus, tabs, and status
 * bar.
 */
public class MainWindow extends JFrame {

  private final SettingsManager settingsManager = new SettingsManager();
  private final JTabbedPane tabbedPane;

  private final Map<String, JFrame> openAssetViewers = new HashMap<>();
  private final Map<String, JDialog> openMetaEditors = new HashMap<>();

  private JMenuItem saveMenuItem;
  private JMenuItem saveAsMenuItem;
  private JMenuItem closeMenuItem;
  private JMenuItem extractAllMenuItem;
  private JMenu openRecentMenu;
  private JLabel statusLabel;
  private JLabel fileCountLabel;
  private JLabel packageSizeLabel;
  private JLabel memoryUsageLabel;

  /** Constructs the main application window. */
  public MainWindow() {
    super("UView");
    setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
    setSize(800, 600);
    setLocationRelativeTo(null);

    loadAndApplySettings();

    addWindowListener(
        new WindowAdapter() {
          @Override
          public void windowClosing(WindowEvent e) {
            handleExit();
          }
        });

    setJMenuBar(createMenuBar());

    tabbedPane = new JTabbedPane();
    tabbedPane.addChangeListener(e -> updateState());

    setTransferHandler(new FileDropHandler());

    add(tabbedPane, BorderLayout.CENTER);
    add(createStatusBar(), BorderLayout.SOUTH);
    updateState();
  }

  private static String formatSize(long bytes) {
    if (bytes < 1024) return bytes + " B";
    int exp = (int) (Math.log(bytes) / Math.log(1024));
    char pre = "KMGTPE".charAt(exp - 1);
    return String.format("%.1f %sB", bytes / Math.pow(1024, exp), pre);
  }

  private void loadAndApplySettings() {
    applyTheme(settingsManager.isDarkTheme());
    updateUiFont(settingsManager.getFontFamily(), settingsManager.getFontSize(), false);
  }

  private void applyTheme(boolean useDarkTheme) {
    try {
      if (useDarkTheme) {
        UIManager.setLookAndFeel(new FlatDarkLaf());
      } else {
        UIManager.setLookAndFeel(new FlatLightLaf());
      }
      SwingUtilities.updateComponentTreeUI(this);
    } catch (Exception ex) {
      JOptionPane.showMessageDialog(
          this, "Failed to set theme: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
    }
  }

  private void toggleTheme() {
    boolean newIsDark = !settingsManager.isDarkTheme();
    settingsManager.setDarkTheme(newIsDark);
    applyTheme(newIsDark);
  }

  private void updateUiFont(String family, int size, boolean save) {
    if (size < 10 || size > 24) {
      return; // Clamp size
    }
    FontUIResource newFont = new FontUIResource(family, Font.PLAIN, size);
    Enumeration<Object> keys = UIManager.getDefaults().keys();
    while (keys.hasMoreElements()) {
      Object key = keys.nextElement();
      Object value = UIManager.get(key);
      if (value instanceof FontUIResource) {
        UIManager.put(key, newFont);
      }
    }
    if (save) {
      settingsManager.setFontFamily(family);
      settingsManager.setFontSize(size);
    }
    SwingUtilities.updateComponentTreeUI(this);
  }

  private void changeGlobalFontSize(int amount) {
    int currentSize = settingsManager.getFontSize();
    updateUiFont(settingsManager.getFontFamily(), currentSize + amount, true);
  }

  private JMenuBar createMenuBar() {
    JMenuBar menuBar = new JMenuBar();

    JMenu fileMenu = new JMenu("File");
    menuBar.add(fileMenu);

    JMenuItem newMenuItem = new JMenuItem("New Package");
    newMenuItem.addActionListener(e -> newPackage());
    fileMenu.add(newMenuItem);

    JMenuItem openMenuItem = new JMenuItem("Open...");
    openMenuItem.addActionListener(e -> openFile());
    fileMenu.add(openMenuItem);

    openRecentMenu = new JMenu("Open Recent");
    fileMenu.add(openRecentMenu);

    closeMenuItem = new JMenuItem("Close");
    closeMenuItem.addActionListener(e -> closePackage());
    fileMenu.add(closeMenuItem);

    fileMenu.addMenuListener(
        new MenuListener() {
          public void menuSelected(MenuEvent e) {
            populateRecentFilesMenu();
          }

          public void menuDeselected(MenuEvent e) {}

          public void menuCanceled(MenuEvent e) {}
        });

    fileMenu.add(new JSeparator());

    saveMenuItem = new JMenuItem("Save");
    saveMenuItem.addActionListener(e -> saveFile());
    fileMenu.add(saveMenuItem);

    saveAsMenuItem = new JMenuItem("Save As...");
    saveAsMenuItem.addActionListener(e -> saveFileAs());
    fileMenu.add(saveAsMenuItem);

    fileMenu.add(new JSeparator());

    extractAllMenuItem = new JMenuItem("Extract All...");
    extractAllMenuItem.addActionListener(e -> extractAll());
    fileMenu.add(extractAllMenuItem);

    fileMenu.add(new JSeparator());

    JMenuItem exitMenuItem = new JMenuItem("Exit");
    exitMenuItem.addActionListener(e -> handleExit());
    fileMenu.add(exitMenuItem);

    JMenu settingsMenu = new JMenu("Settings");
    menuBar.add(settingsMenu);

    JMenuItem toggleThemeItem = new JMenuItem("Toggle Light/Dark Theme");
    toggleThemeItem.addActionListener(e -> toggleTheme());
    settingsMenu.add(toggleThemeItem);

    settingsMenu.addSeparator();

    JMenu fontMenu = new JMenu("Font");
    settingsMenu.add(fontMenu);

    ButtonGroup fontGroup = new ButtonGroup();
    String[] fontFamilies = {"JetBrains Mono", Font.SANS_SERIF, Font.SERIF, Font.MONOSPACED};
    String currentFontFamily = settingsManager.getFontFamily();

    for (String fontFamily : fontFamilies) {
      JRadioButtonMenuItem fontItem = new JRadioButtonMenuItem(fontFamily);
      fontItem.setSelected(fontFamily.equals(currentFontFamily));
      fontItem.addActionListener(
          e -> {
            updateUiFont(fontFamily, settingsManager.getFontSize(), true);
            JOptionPane.showMessageDialog(
                this,
                "Font changed to "
                    + fontFamily
                    + ".\nSome components may require a restart to update fully.",
                "Font Changed",
                JOptionPane.INFORMATION_MESSAGE);
          });
      fontGroup.add(fontItem);
      fontMenu.add(fontItem);
    }

    settingsMenu.addSeparator();

    JMenuItem increaseFontItem = new JMenuItem("Increase Font Size");
    increaseFontItem.addActionListener(e -> changeGlobalFontSize(1));
    settingsMenu.add(increaseFontItem);

    JMenuItem decreaseFontItem = new JMenuItem("Decrease Font Size");
    decreaseFontItem.addActionListener(e -> changeGlobalFontSize(-1));
    settingsMenu.add(decreaseFontItem);

    settingsMenu.addSeparator();

    JMenuItem resetSettingsItem = new JMenuItem("Reset UI Settings");
    resetSettingsItem.addActionListener(
        e -> {
          settingsManager.resetUiSettings();
          JOptionPane.showMessageDialog(
              this,
              "UI settings have been reset.\nPlease restart the application for all changes to take effect.",
              "Restart Required",
              JOptionPane.INFORMATION_MESSAGE);
        });
    settingsMenu.add(resetSettingsItem);

    JMenu helpMenu = new JMenu("Help");
    menuBar.add(helpMenu);
    JMenuItem aboutMenuItem = new JMenuItem("About UView");
    aboutMenuItem.addActionListener(e -> showAboutDialog());
    helpMenu.add(aboutMenuItem);

    return menuBar;
  }

  /**
   * Shows a viewer for the specified asset. This method manages open viewers to prevent duplicates.
   * If a viewer for the asset is already open, it brings it to the front.
   *
   * @param asset The asset to view.
   * @param packageManager The package manager instance.
   * @param onSaveCallback A callback to run when the asset is saved in the viewer.
   */
  public void showAssetViewer(
      UnityAsset asset, PackageManager packageManager, Runnable onSaveCallback) {
    String assetPath = asset.assetPath();
    if (openAssetViewers.containsKey(assetPath)) {
      JFrame frame = openAssetViewers.get(assetPath);
      frame.toFront();
      frame.requestFocus();
    } else {
      AssetViewerFrame viewer = new AssetViewerFrame(this, asset, packageManager, onSaveCallback);
      viewer.addWindowListener(
          new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
              openAssetViewers.remove(assetPath);
            }
          });
      openAssetViewers.put(assetPath, viewer);
      viewer.setVisible(true);
    }
  }

  /**
   * Shows a meta file editor for the specified asset. This method manages open editors to prevent
   * duplicates. If an editor for the asset's meta file is already open, it brings it to the front.
   *
   * @param asset The asset whose meta file is to be edited.
   * @param packageManager The package manager instance.
   * @param onSaveCallback A callback to run when the meta file is saved.
   */
  public void showMetaEditor(
      UnityAsset asset, PackageManager packageManager, Runnable onSaveCallback) {
    String assetPath = asset.assetPath();
    if (openMetaEditors.containsKey(assetPath)) {
      JDialog dialog = openMetaEditors.get(assetPath);
      dialog.toFront();
      dialog.requestFocus();
    } else {
      MetaEditorFrame editor = new MetaEditorFrame(this, asset, packageManager, onSaveCallback);
      editor.addWindowListener(
          new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
              openMetaEditors.remove(assetPath);
            }
          });
      openMetaEditors.put(assetPath, editor);
      editor.setVisible(true);
    }
  }

  private JPanel createStatusBar() {
    JPanel statusBar = new JPanel();
    statusBar.setLayout(new BoxLayout(statusBar, BoxLayout.X_AXIS));
    statusBar.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));

    statusLabel = new JLabel("Ready");
    fileCountLabel = new JLabel("");
    packageSizeLabel = new JLabel("");
    memoryUsageLabel = new JLabel("");

    statusBar.add(statusLabel);
    statusBar.add(Box.createHorizontalGlue());
    statusBar.add(fileCountLabel);
    statusBar.add(Box.createRigidArea(new Dimension(15, 0)));
    statusBar.add(packageSizeLabel);
    statusBar.add(Box.createRigidArea(new Dimension(15, 0)));
    statusBar.add(memoryUsageLabel);

    new Timer(3000, e -> updateMemoryUsage()).start();
    updateMemoryUsage();

    return statusBar;
  }

  private void updateMemoryUsage() {
    Runtime runtime = Runtime.getRuntime();
    long usedMemory = runtime.totalMemory() - runtime.freeMemory();
    memoryUsageLabel.setText(String.format("Mem: %s", formatSize(usedMemory)));
  }

  private void showAboutDialog() {
    String version = getAppVersion();
    String title = "About UView";
    String message =
        "<html><body style='width: 300px;'>"
            + "<h2>UView</h2>"
            + "<p><b>Version:</b> "
            + version
            + "</p>"
            + "<p>A cross-platform tool for viewing and modifying Unity package files.</p>"
            + "<p><b>GitHub:</b> <a href='https://github.com/pixel-clover/uview'>https://github.com/pixel-clover/uview</a></p>"
            + "</body></html>";

    java.net.URL iconUrl = App.class.getResource("/logo.svg");
    Icon aboutIcon = (iconUrl != null) ? new FlatSVGIcon(iconUrl).derive(64, 64) : null;

    JLabel messageLabel = new JLabel(message);
    messageLabel.addMouseListener(
        new MouseAdapter() {
          public void mouseClicked(MouseEvent e) {
            try {
              Desktop.getDesktop().browse(new java.net.URI("https://github.com/habedi/uview"));
            } catch (Exception ex) {
              // Ignore
            }
          }
        });

    JOptionPane.showMessageDialog(
        this, messageLabel, title, JOptionPane.INFORMATION_MESSAGE, aboutIcon);
  }

  private String getAppVersion() {
    try (InputStream is =
        App.class.getResourceAsStream(
            "/META-INF/maven/io.github.pixelclover/uview/pom.properties")) {
      if (is == null) return "N/A";
      Properties props = new Properties();
      props.load(is);
      return props.getProperty("version", "N/A");
    } catch (IOException e) {
      return "N/A";
    }
  }

  private void populateRecentFilesMenu() {
    openRecentMenu.removeAll();
    List<File> recentFiles = settingsManager.getRecentFiles();
    if (recentFiles.isEmpty()) {
      openRecentMenu.setEnabled(false);
      return;
    }

    openRecentMenu.setEnabled(true);
    for (File file : recentFiles) {
      JMenuItem menuItem = new JMenuItem(file.getAbsolutePath());
      menuItem.addActionListener(e -> openPackage(file));
      openRecentMenu.add(menuItem);
    }
  }

  private JFileChooser createFileChooser(String title) {
    JFileChooser chooser = new JFileChooser();
    chooser.setDialogTitle(title);
    chooser.setCurrentDirectory(settingsManager.getLastDirectory());
    chooser.setPreferredSize(new Dimension(1024, 768));
    return chooser;
  }

  private void newPackage() {
    openPackage(null);
  }

  private void openFile() {
    JFileChooser chooser = createFileChooser("Open Unity Package");
    chooser.setFileFilter(new FileNameExtensionFilter("Unity Package", "unitypackage"));
    if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
      File selectedFile = chooser.getSelectedFile();
      settingsManager.setLastDirectory(selectedFile.getParentFile());
      openPackage(selectedFile);
    }
  }

  private void openPackage(File file) {
    if (file != null) {
      for (int i = 0; i < tabbedPane.getTabCount(); i++) {
        Component comp = tabbedPane.getComponentAt(i);
        if (comp instanceof PackageViewPanel panel) {
          File openFile = panel.getPackageFile();
          if (openFile != null && openFile.getAbsolutePath().equals(file.getAbsolutePath())) {
            tabbedPane.setSelectedIndex(i);
            toFront();
            return;
          }
        }
      }
    }

    setWorking(true, "Loading...");
    PackageViewPanel newPanel = new PackageViewPanel(this, file, settingsManager);
    newPanel.loadPackage(
        () -> {
          int newIndex = tabbedPane.getTabCount();
          tabbedPane.addTab(null, newPanel);
          tabbedPane.setTabComponentAt(newIndex, new ButtonTabComponent(tabbedPane));
          tabbedPane.setSelectedIndex(newIndex);
          if (file != null) {
            settingsManager.addRecentFile(file);
          }
          updateState();
          setWorking(false, null);
        });
  }

  private PackageViewPanel getCurrentPanel() {
    if (tabbedPane.getSelectedIndex() == -1) {
      return null;
    }
    return (PackageViewPanel) tabbedPane.getSelectedComponent();
  }

  private void saveFile() {
    PackageViewPanel currentPanel = getCurrentPanel();
    if (currentPanel == null) return;
    if (currentPanel.getPackageFile() == null) {
      saveFileAs();
    } else {
      savePackageInBackground(currentPanel, currentPanel.getPackageFile());
    }
  }

  private void saveFileAs() {
    PackageViewPanel currentPanel = getCurrentPanel();
    if (currentPanel == null) return;

    JFileChooser chooser = createFileChooser("Save Unity Package As...");
    chooser.setFileFilter(new FileNameExtensionFilter("Unity Package", "unitypackage"));
    if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
      File selectedFile = chooser.getSelectedFile();
      if (!selectedFile.getName().endsWith(".unitypackage")) {
        selectedFile =
            new File(selectedFile.getParentFile(), selectedFile.getName() + ".unitypackage");
      }
      settingsManager.setLastDirectory(selectedFile.getParentFile());
      savePackageInBackground(currentPanel, selectedFile);
    }
  }

  private boolean confirmAndSaveChanges() {
    PackageViewPanel currentPanel = getCurrentPanel();
    if (currentPanel == null || !currentPanel.getPackageManager().isModified()) {
      return true;
    }

    int result =
        JOptionPane.showConfirmDialog(
            this,
            "The package '"
                + currentPanel.getTabTitle().replace("*", "")
                + "' has unsaved changes. Do you want to save them?",
            "Unsaved Changes",
            JOptionPane.YES_NO_CANCEL_OPTION,
            JOptionPane.WARNING_MESSAGE);

    if (result == JOptionPane.CANCEL_OPTION) {
      return false;
    }
    if (result == JOptionPane.YES_OPTION) {
      saveFile();
    }
    return true;
  }

  /** Closes the currently selected package tab, prompting to save changes if necessary. */
  void closePackage() {
    if (confirmAndSaveChanges()) {
      int selectedIndex = tabbedPane.getSelectedIndex();
      if (selectedIndex != -1) {
        tabbedPane.remove(selectedIndex);
      }
    }
  }

  private void handleExit() {
    for (int i = 0; i < tabbedPane.getTabCount(); i++) {
      tabbedPane.setSelectedIndex(i);
      if (!confirmAndSaveChanges()) {
        return;
      }
    }
    dispose();
    System.exit(0);
  }

  private void savePackageInBackground(PackageViewPanel panel, File file) {
    setWorking(true, "Saving " + file.getName() + "...");
    SwingWorker<Void, Void> worker =
        new SwingWorker<>() {
          @Override
          protected Void doInBackground() throws Exception {
            panel.getPackageManager().savePackage(file);
            return null;
          }

          @Override
          protected void done() {
            panel.setPackageFile(file);
            updateState();
            setWorking(false, null);
          }
        };
    worker.execute();
  }

  private void extractAll() {
    PackageViewPanel currentPanel = getCurrentPanel();
    if (currentPanel == null) return;

    JFileChooser chooser = createFileChooser("Select Directory to Extract All Assets");
    chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);

    if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
      Path outputDir = chooser.getSelectedFile().toPath();
      settingsManager.setLastDirectory(outputDir.toFile());
      extractAllInBackground(currentPanel, outputDir);
    }
  }

  private void extractAllInBackground(PackageViewPanel panel, Path outputDir) {
    setWorking(true, "Extracting all assets...");
    SwingWorker<Void, Void> worker =
        new SwingWorker<>() {
          @Override
          protected Void doInBackground() throws Exception {
            panel
                .getPackageManager()
                .extractAssets(panel.getPackageManager().getAssets(), outputDir, "Assets/");
            return null;
          }

          @Override
          protected void done() {
            setWorking(false, "Extraction complete.");
            try {
              get();
              Desktop.getDesktop().open(outputDir.toFile());
            } catch (Exception ex) {
              JOptionPane.showMessageDialog(
                  MainWindow.this,
                  "Extraction failed: " + ex.getCause().getMessage(),
                  "Error",
                  JOptionPane.ERROR_MESSAGE);
            }
          }
        };
    worker.execute();
  }

  /**
   * Updates the state of the main window's components (title, menu items, status bar) based on the
   * currently selected tab.
   */
  void updateState() {
    PackageViewPanel currentPanel = getCurrentPanel();
    boolean hasPanel = currentPanel != null;

    saveMenuItem.setEnabled(hasPanel && currentPanel.getPackageManager().isModified());
    saveAsMenuItem.setEnabled(hasPanel);
    closeMenuItem.setEnabled(hasPanel);
    extractAllMenuItem.setEnabled(hasPanel);

    if (hasPanel) {
      int selectedIndex = tabbedPane.getSelectedIndex();
      tabbedPane.setTitleAt(selectedIndex, currentPanel.getTabTitle());

      File file = currentPanel.getPackageFile();
      int fileCount = currentPanel.getPackageManager().getAssets().size();
      fileCountLabel.setText(String.format("Files: %,d", fileCount));

      if (file != null) {
        setTitle("UView - " + file.getName());
        statusLabel.setText("Loaded '" + file.getName() + "'");
        packageSizeLabel.setText(String.format("Size: %s", formatSize(file.length())));
      } else {
        setTitle("UView - New Package");
        statusLabel.setText("New Package");
        packageSizeLabel.setText("");
      }
    } else {
      setTitle("UView");
      statusLabel.setText("Ready");
      fileCountLabel.setText("");
      packageSizeLabel.setText("");
    }
  }

  private void setWorking(boolean working, String status) {
    setCursor(working ? Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR) : Cursor.getDefaultCursor());
    if (status != null) {
      statusLabel.setText(status);
    }
  }

  private class FileDropHandler extends TransferHandler {
    @Override
    public boolean canImport(TransferSupport support) {
      if (!support.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
        return false;
      }
      try {
        Transferable t = support.getTransferable();
        List<File> files = (List<File>) t.getTransferData(DataFlavor.javaFileListFlavor);
        for (File file : files) {
          if (file.isFile() && file.getName().toLowerCase().endsWith(".unitypackage")) {
            return true;
          }
        }
      } catch (Exception e) {
        return false;
      }
      return false;
    }

    @Override
    public boolean importData(TransferSupport support) {
      if (!canImport(support)) {
        return false;
      }
      try {
        Transferable t = support.getTransferable();
        List<File> files = (List<File>) t.getTransferData(DataFlavor.javaFileListFlavor);
        for (File file : files) {
          if (file.isFile() && file.getName().toLowerCase().endsWith(".unitypackage")) {
            openPackage(file);
          }
        }
        return true;
      } catch (Exception e) {
        return false;
      }
    }
  }
}
