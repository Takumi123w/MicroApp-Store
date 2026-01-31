import javax.microedition.midlet.*;
import javax.microedition.lcdui.*;
import javax.microedition.io.*;
import java.io.*;
import java.util.Vector;
import java.util.Enumeration;
import javax.microedition.io.file.FileConnection;
import javax.microedition.io.file.FileSystemRegistry;
import javax.microedition.rms.*;

public class MicroAppStore extends MIDlet implements CommandListener, Runnable {
    private Display display;
    private List list;
    private Command downloadCmd, refreshCmd, backCmd, installCmd, settingsCmd, selectHereCmd, searchCmd, clearSearchCmd;
    
    private Vector allNames = new Vector();
    private Vector allUrls = new Vector();
    private Vector allDescs = new Vector();
    private Vector allIconUrls = new Vector();
    
    private Vector filteredUrls = new Vector();
    private Vector filteredDescs = new Vector();
    private Vector filteredIconUrls = new Vector();
    
    private List settingsMenu;
    
    private String selectedPath = "file:///c:/";
    private final String DEFAULT_REPO = "http://nnp.nnchan.ru/glype/browse.php?u=https://raw.githubusercontent.com/Takumi123w/MicroApp-Store/refs/heads/main/apps.txt";
    private String listUrl = ""; 

    public MicroAppStore() {
        loadSettings();
        display = Display.getDisplay(this);
        
        settingsCmd = new Command("Settings", Command.SCREEN, 5);
        downloadCmd = new Command("Download", Command.ITEM, 1);
        refreshCmd = new Command("Refresh", Command.SCREEN, 4);
        searchCmd = new Command("Search", Command.SCREEN, 1);
        clearSearchCmd = new Command("Clear Search", Command.SCREEN, 2);
        
        backCmd = new Command("Back", Command.BACK, 2);
        installCmd = new Command("Download", Command.OK, 1);
        selectHereCmd = new Command("Select Here", Command.OK, 1);
        
        initMainList("MicroApp Store");
    }

    private void initMainList(String title) {
        list = new List(title, List.IMPLICIT);
        list.addCommand(searchCmd);
        list.addCommand(downloadCmd);
        list.addCommand(refreshCmd);
        list.addCommand(settingsCmd);
        list.setCommandListener(this);
    }

    protected void startApp() {
        new Thread(this).start();
    }

    public void run() {
        allNames.removeAllElements();
        allUrls.removeAllElements();
        allDescs.removeAllElements();
        allIconUrls.removeAllElements();

        fetchRawData(DEFAULT_REPO);

        if (listUrl != null && listUrl.length() > 5 && !listUrl.equals(DEFAULT_REPO)) {
            fetchRawData(listUrl);
        }

        updateDisplay(null);
    }

    private void fetchRawData(String targetUrl) {
        HttpConnection hc = null;
        InputStream is = null;
        try {
            hc = (HttpConnection) Connector.open(targetUrl);
            hc.setRequestProperty("User-Agent", "Nokia6300/2.0 (05.50)");
            is = hc.openInputStream();
            
            StringBuffer sb = new StringBuffer();
            int ch;
            while ((ch = is.read()) != -1) {
                sb.append((char) ch);
            }
            parseList(sb.toString());
        } catch (Exception e) {
            System.out.println("Fetch Failed for " + targetUrl + ": " + e.getMessage());
        } finally {
            cleanup(hc, is, null, null);
        }
    }

    private void parseList(String data) {
        int start = 0;
        int end;
        while ((end = data.indexOf('\n', start)) != -1) {
            String line = data.substring(start, end).trim();
            if (line.length() > 0 && line.indexOf('|') != -1) {
                int p1 = line.indexOf('|');
                int p2 = line.indexOf('|', p1 + 1);
                int p3 = line.indexOf('|', p2 + 1);

                String name = line.substring(0, p1).trim();
                String jarUrl = (p2 == -1) ? line.substring(p1 + 1).trim() : line.substring(p1 + 1, p2).trim();
                
                String iconUrl = null;
                if (p2 != -1) {
                    iconUrl = (p3 == -1) ? line.substring(p2 + 1).trim() : line.substring(p2 + 1, p3).trim();
                }
                String desc = (p3 != -1) ? line.substring(p3 + 1).trim() : "No description provided.";

                if (!allNames.contains(name)) {
                    allNames.addElement(name);
                    allUrls.addElement(jarUrl);
                    allDescs.addElement(desc);
                    allIconUrls.addElement(iconUrl != null ? iconUrl : "");
                }
            }
            start = end + 1;
        }
    }

    private void saveSettings() {
        try {
            RecordStore rs = RecordStore.openRecordStore("AppSettings", true);
            String dataStr = selectedPath + "|" + listUrl;
            byte[] data = dataStr.getBytes();
            if (rs.getNumRecords() > 0) {
                rs.setRecord(1, data, 0, data.length);
            } else {
                rs.addRecord(data, 0, data.length);
            }
            rs.closeRecordStore();
        } catch (Exception e) {}
    }

    private void loadSettings() {
        try {
            RecordStore rs = RecordStore.openRecordStore("AppSettings", true);
            if (rs.getNumRecords() > 0) {
                String raw = new String(rs.getRecord(1));
                int sep = raw.indexOf('|');
                if (sep != -1) {
                    selectedPath = raw.substring(0, sep);
                    listUrl = raw.substring(sep + 1);
                } else {
                    selectedPath = raw;
                }
            }
            rs.closeRecordStore();
        } catch (Exception e) {}
    }
    
    private void updateDisplay(String query) {
        initMainList(query == null ? "MicroApp Store" : "Search: " + query);
        if (query != null) list.addCommand(clearSearchCmd);
        
        filteredUrls.removeAllElements();
        filteredDescs.removeAllElements();
        filteredIconUrls.removeAllElements();

        for (int i = 0; i < allNames.size(); i++) {
            String name = (String) allNames.elementAt(i);
            if (query == null || name.toLowerCase().indexOf(query.toLowerCase()) != -1) {
                list.append(name, null); 
                filteredUrls.addElement(allUrls.elementAt(i));
                filteredDescs.addElement(allDescs.elementAt(i));
                filteredIconUrls.addElement(allIconUrls.elementAt(i));
            }
        }
        display.setCurrent(list);
    }

    private void showSearchBox() {
        final TextBox searchBox = new TextBox("Search App", "", 20, TextField.ANY);
        searchBox.addCommand(new Command("OK", Command.OK, 1));
        searchBox.addCommand(backCmd);
        searchBox.setCommandListener(new CommandListener() {
            public void commandAction(Command c, Displayable d) {
                if (c.getCommandType() == Command.OK) {
                    updateDisplay(searchBox.getString());
                } else {
                    display.setCurrent(list);
                }
            }
        });
        display.setCurrent(searchBox);
    }

    private void showRepoEditor() {
        final TextBox repoBox = new TextBox("Custom Repo URL", listUrl, 256, TextField.URL);
        final Command saveCmd = new Command("Save", Command.OK, 1);
        final Command clearCmd = new Command("Clear Custom", Command.SCREEN, 2);
        
        repoBox.addCommand(saveCmd);
        repoBox.addCommand(clearCmd);
        repoBox.addCommand(backCmd);
        
        repoBox.setCommandListener(new CommandListener() {
            public void commandAction(Command c, Displayable d) {
                if (c == saveCmd) {
                    listUrl = repoBox.getString();
                    saveSettings();
                    Alert a = new Alert("Repo Saved", "Combining Stock with: " + (listUrl.length() > 0 ? listUrl : "None"), null, AlertType.INFO);
                    a.setTimeout(2000);
                    display.setCurrent(a, list);
                    new Thread(MicroAppStore.this).start();
                } else if (c == clearCmd) {
                    repoBox.setString("");
                } else if (c == backCmd) {
                    display.setCurrent(settingsMenu);
                }
            }
        });
        display.setCurrent(repoBox);
    }

    private void showDescription(int index) {
        final String name = list.getString(index);
        final String desc = (String) filteredDescs.elementAt(index);
        final String url = (String) filteredUrls.elementAt(index);
        final String iconUrl = (String) filteredIconUrls.elementAt(index);

        final Form details = new Form("App Details");
        details.append(new StringItem("App: ", name));
        details.append(new StringItem("Description: ", desc));
        
        details.addCommand(backCmd);
        details.addCommand(installCmd);
        details.setCommandListener(new CommandListener() {
            public void commandAction(Command c, Displayable d) {
                if (c == backCmd) display.setCurrent(list);
                else if (c == installCmd) {
                    new Thread(new Runnable() {
                        public void run() { saveJarToPhone(url, name); }
                    }).start();
                }
            }
        });

        display.setCurrent(details);

        if (iconUrl != null && iconUrl.length() > 10) {
            new Thread(new Runnable() {
                public void run() {
                    Image icon = fetchIcon(iconUrl);
                    if (icon != null) {
                        details.insert(0, new ImageItem(null, icon, ImageItem.LAYOUT_CENTER, "Icon"));
                    }
                }
            }).start();
        }
    }

    private void showSettingsMenu() {
        settingsMenu = new List("Settings", List.IMPLICIT);
        settingsMenu.append("Set Download Path", null);
        settingsMenu.append("Set Custom Repo URL", null);
        settingsMenu.addCommand(backCmd);
        settingsMenu.setCommandListener(this);
        display.setCurrent(settingsMenu);
    }

    public void commandAction(Command c, Displayable d) {
        if (d == list) {
            if (c == List.SELECT_COMMAND || c == downloadCmd) {
                int index = list.getSelectedIndex();
                if (index >= 0) showDescription(index);
            } else if (c == settingsCmd) {
                showSettingsMenu();
            } else if (c == searchCmd) {
                showSearchBox();
            } else if (c == refreshCmd) {
                new Thread(this).start();
            } else if (c == clearSearchCmd) {
                updateDisplay(null);
            }
        } 
        else if (d == settingsMenu) {
            if (c == backCmd) {
                display.setCurrent(list);
            } else if (c == List.SELECT_COMMAND) {
                int index = settingsMenu.getSelectedIndex();
                if (index == 0) {
                    chooseSavePath(null);
                } else if (index == 1) {
                    showRepoEditor();
                }
            }
        }
        else if (d instanceof List && !d.equals(list) && !d.equals(settingsMenu)) {
            List l = (List) d;
            if (c == backCmd) {
                showSettingsMenu();
            } else if (c == selectHereCmd) {
                selectedPath = "file:///" + l.getTitle();
                saveSettings();
                Alert a = new Alert("Path Saved", "New path: " + selectedPath, null, AlertType.CONFIRMATION);
                a.setTimeout(2000);
                display.setCurrent(a, list);
            } else if (c == List.SELECT_COMMAND) {
                String selected = l.getString(l.getSelectedIndex());
                chooseSavePath(l.getTitle().equals("Select Root") ? selected : l.getTitle() + selected);
            }
        }
    }

    private Image fetchIcon(String url) {
        HttpConnection hc = null;
        InputStream is = null;
        try {
            hc = (HttpConnection) Connector.open(url);
            is = hc.openInputStream();
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buffer = new byte[512];
            int length;
            while ((length = is.read(buffer)) != -1) {
                baos.write(buffer, 0, length);
            }
            byte[] imageData = baos.toByteArray();
            return Image.createImage(imageData, 0, imageData.length);
        } catch (Exception e) {
            return null;
        } finally {
            cleanup(hc, is, null, null);
        }
    }

private void saveJarToPhone(String jarUrl, String fileName) {
        HttpConnection hc = null; 
        FileConnection fc = null;
        InputStream is = null; 
        OutputStream os = null;

        String urlLower = jarUrl.toLowerCase();
        if (urlLower.indexOf(".jar") == -1 && urlLower.indexOf(".jad") == -1) {
            showError("Link Error: Not a Java file.");
            return;
        }

        String cleanName = fileName.replace(' ', '_').replace('/', '_').replace('.', '_');
        
        String localPath = selectedPath + cleanName + "_jar"; 

        Form progressForm = new Form("Downloading");
        StringItem statusLabel = new StringItem(null, "Connecting...");
        progressForm.append(statusLabel);
        display.setCurrent(progressForm);

        System.gc();

        try {
            hc = (HttpConnection) Connector.open(jarUrl);
            hc.setRequestProperty("User-Agent", "Nokia6300/2.0 (05.50)");
            hc.setRequestMethod(HttpConnection.GET);
            
            int responseCode = hc.getResponseCode();
            if (responseCode != HttpConnection.HTTP_OK) {
                throw new IOException("Server: " + responseCode);
            }

            is = hc.openInputStream();
            long len = hc.getLength();
            int totalRead = 0;

            fc = (FileConnection) Connector.open(localPath, Connector.READ_WRITE);
            
            if (fc.exists()) { 
                fc.delete();
            }
            fc.create();

            os = fc.openOutputStream();
            byte[] buffer = new byte[4096];
            int bytesRead;
            
            while ((bytesRead = is.read(buffer)) != -1) {
                os.write(buffer, 0, bytesRead);
                totalRead += bytesRead;
                
                if (len > 0) {
                    int percent = (int) ((totalRead * 100) / len);
                    statusLabel.setText(generateBar(percent) + " " + percent + "%");
                } else {
                    statusLabel.setText("Downloaded: " + (totalRead / 1024) + " KB");
                }
            }

            Alert a = new Alert("Success", "Saved as " + cleanName + "_jar\nRename it to .jar to install.", null, AlertType.CONFIRMATION);
            a.setTimeout(Alert.FOREVER);
            display.setCurrent(a, list);
            
        } catch (Exception e) {
            showError("Error: " + e.toString());
        } finally {
            cleanup(hc, is, fc, os);
            System.gc();
        }
    }

    private void chooseSavePath(String path) {
        List pathList = new List(path == null ? "Select Root" : path, List.IMPLICIT);
        pathList.addCommand(backCmd);
        pathList.addCommand(selectHereCmd);
        pathList.setCommandListener(this);
        try {
            Enumeration items;
            if (path == null) {
                items = FileSystemRegistry.listRoots();
            } else {
                FileConnection fc = (FileConnection) Connector.open("file:///" + path, Connector.READ);
                items = fc.list();
            }
            while (items.hasMoreElements()) {
                String item = (String) items.nextElement();
                if (path == null || item.endsWith("/")) {
                    pathList.append(item, null);
                }
            }
            display.setCurrent(pathList);
        } catch (Exception e) {
            showError("FS Error: " + e.getMessage());
        }
    }

    private String generateBar(int percent) {
        int bars = percent / 10;
        StringBuffer sb = new StringBuffer("[");
        for (int i = 0; i < 10; i++) {
            if (i < bars) sb.append("#");
            else sb.append("-");
        }
        sb.append("]");
        return sb.toString();
    }

    private void cleanup(HttpConnection hc, InputStream is, FileConnection fc, OutputStream os) {
        try {
            if (is != null) is.close();
            if (os != null) os.close();
            if (hc != null) hc.close();
            if (fc != null) fc.close();
        } catch (Exception e) {}
    }

    private void showError(String msg) {
        Alert a = new Alert("Error", msg, null, AlertType.ERROR);
        a.setTimeout(3000);
        display.setCurrent(a, list);
    }

    protected void pauseApp() {}
    protected void destroyApp(boolean u) {}
}