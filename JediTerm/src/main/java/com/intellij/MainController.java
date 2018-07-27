package com.intellij;

import com.google.common.collect.Maps;
import com.intellij.util.EncodingEnvironmentUtil;
import com.jediterm.pty.PtyMain;
import com.jediterm.terminal.emulator.ColorPalette;
import com.jediterm.terminal.ui.JediTermWidget;
import com.jediterm.terminal.ui.UIUtil;
import com.jediterm.terminal.ui.settings.DefaultSettingsProvider;
import com.pty4j.PtyProcess;
import javafx.application.Platform;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ObservableValue;
import javafx.embed.swing.SwingNode;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.AnchorPane;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

public class MainController implements Initializable {
    private static final Logger log = LoggerFactory.getLogger(MainController.class);

    private Stage mainStage;

    @FXML
    private WebView mainWebView;
    @FXML
    private WebView previewWebView;
    @FXML
    private AnchorPane termAnchorPane;

    private SimpleObjectProperty<File> mainFile;
    private SimpleObjectProperty<File> previewFile;

    @Override
    public void initialize(URL location, ResourceBundle resources) {

        final WebEngine mainEngine = mainWebView.getEngine();
        final String mainUrl = Objects.requireNonNull(getClass().getClassLoader().getResource("pdfjs/web/viewer.html")).toExternalForm();
        mainEngine.setUserStyleSheetLocation(Objects.requireNonNull(getClass().getClassLoader().getResource("pdfjs/web.css")).toExternalForm());
        mainEngine.setJavaScriptEnabled(true);
        mainEngine.load(mainUrl);

        mainFile = new SimpleObjectProperty<>();
        mainFile.addListener((observable, oldValue, newValue) -> fileChanged(observable, oldValue, newValue, mainWebView));

        final WebEngine previewEngine = previewWebView.getEngine();
        final String previewUrl = Objects.requireNonNull(getClass().getClassLoader().getResource("pdfjs/web/viewer.html")).toExternalForm();
        previewEngine.setUserStyleSheetLocation(Objects.requireNonNull(getClass().getClassLoader().getResource("pdfjs/web.css")).toExternalForm());
        previewEngine.setJavaScriptEnabled(true);
        previewEngine.load(previewUrl);

        previewFile = new SimpleObjectProperty<>();
        previewFile.addListener((observable, oldValue, newValue) -> fileChanged(observable, oldValue, newValue, previewWebView));

        final DefaultSettingsProvider settingsProvider = new MySettingsProvider();
        final JediTermWidget jediTermWidget = new JediTermWidget(settingsProvider);

        try {
            Charset charset = Charset.forName("UTF-8");
            Map<String, String> envs = Maps.newHashMap(System.getenv());
            EncodingEnvironmentUtil.setLocaleEnvironmentIfMac(envs, charset);

            String[] command;
            if (UIUtil.isWindows) {
                command = new String[]{"cmd.exe"};
            } else {
                String shellPath = System.getenv("SHELL");
                if (shellPath == null) {
                    log.error("Cannot get $SHELL environment variable. Trying '/bin/bash'.");
                    shellPath = "/bin/bash";
                }
                command = new String[]{shellPath, "--login"};
                envs.put("TERM", "xterm");
            }

            PtyProcess process = PtyProcess.exec(command, envs, System.getenv("HOME"));
            final PtyMain.LoggingPtyProcessTtyConnector loggingPtyProcessTtyConnector = new PtyMain.LoggingPtyProcessTtyConnector(process, charset);
            jediTermWidget.setTtyConnector(loggingPtyProcessTtyConnector);

            jediTermWidget.start();

            SwingNode swingNode = new SwingNode();
            swingNode.setContent(jediTermWidget);
            termAnchorPane.getChildren().add(swingNode);
            AnchorPane.setTopAnchor(swingNode, 0d);
            AnchorPane.setBottomAnchor(swingNode, 0d);
            AnchorPane.setLeftAnchor(swingNode, 0d);
            AnchorPane.setRightAnchor(swingNode, 0d);

            Platform.runLater(swingNode::requestFocus);

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                loggingPtyProcessTtyConnector.close();
                jediTermWidget.stop();
                jediTermWidget.close();
            }));

        } catch (IOException e) {
            e.printStackTrace();
        } catch (IllegalStateException e) {
            throw e;
        }
    }

    @FXML
    void keyPressed(KeyEvent event) {
        switch (event.getCode()) {
            case O:
                if (event.isControlDown() && event.isShiftDown()) {
                    File selectedFile = getFileWithChooser();
                    updateFile(selectedFile, previewFile);
                } else if (event.isControlDown()) {
                    File selectedFile = getFileWithChooser();
                    updateFile(selectedFile, mainFile);
                }
                break;
            default:
                break;
        }
    }

    private void updateFile(File selectedFile, SimpleObjectProperty<File> previewFile) {
        if (selectedFile != null) {
            log.debug("Opening {}", selectedFile);
            previewFile.set(selectedFile);
        }
    }

    private File getFileWithChooser() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Open Resource File");
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("PDF Files", "*.pdf"),
                new FileChooser.ExtensionFilter("All Files", "*.*"));
        return fileChooser.showOpenDialog(mainStage);
    }

    void setMainStage(Stage mainStage) {
        this.mainStage = mainStage;
    }

    private void fileChanged(ObservableValue<? extends File> observableFile, File oldFile, File newFile, WebView webView) {
        try {
            final Path path = Paths.get(newFile.toURI());
            final byte[] data = Files.readAllBytes(path);
            System.out.println("data = " + Arrays.toString(data));
            String base64 = Base64.getEncoder().encodeToString(data);
            webView.getEngine().executeScript("openFileFromBase64('" + base64 + "')");
        } catch (Exception ex) {
            log.error("Could not load file: {}", newFile);
            ex.printStackTrace();
        }
    }

    public class JavaBridge {
        public void log(String text) {
            System.out.println(text);
        }
    }

    private class MySettingsProvider extends DefaultSettingsProvider {
        @Override
        public boolean audibleBell() {
            return false;
        }

        @Override
        public ColorPalette getTerminalColorPalette() {
            return new ColorPalette() {
                @Override
                public Color[] getIndexColors() {
                    return new Color[]{
                            new Color(0xfdf6e3),
                            new Color(0xdc322f),
                            new Color(0x859900),
                            new Color(0xb58900),
                            new Color(0x268bd2),
                            new Color(0xd33682),
                            new Color(0x2aa198),
                            new Color(0xeee8d5),

                            new Color(0x073642),
                            new Color(0xcb4b16),
                            new Color(0x586e75),
                            new Color(0x657b83),
                            new Color(0x839496),
                            new Color(0x6c71c4),
                            new Color(0x93a1a1),
                            new Color(0x002b36),
                    };
                }
            };
        }
    }
}