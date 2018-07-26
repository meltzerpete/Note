package com.jediterm.app;

import com.google.common.collect.Maps;
import com.intellij.util.EncodingEnvironmentUtil;
import com.jediterm.terminal.SubstringFinder;
import com.jediterm.terminal.TerminalMode;
import com.jediterm.terminal.model.JediTerminal;
import com.jediterm.terminal.model.StyleState;
import com.jediterm.terminal.model.TerminalTextBuffer;
import com.jediterm.terminal.model.hyperlinks.TextProcessing;
import com.jediterm.terminal.ui.*;
import com.jediterm.terminal.ui.settings.DefaultSettingsProvider;
import com.jediterm.terminal.ui.settings.SettingsProvider;
import com.pty4j.PtyProcess;

import javax.swing.*;
import javax.swing.plaf.basic.BasicScrollBarUI;
import java.awt.*;
import java.nio.charset.Charset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

public class Term implements TerminalActionProvider {

    private TerminalActionProvider provider;
    private AtomicBoolean mySessionRunning = new AtomicBoolean();

    /**
     * Create the GUI and show it.  For thread safety,
     * this method should be invoked from the
     * event-dispatching thread.
     */
    public JediTerminalPanel getTerm() {
        //Create and set up the window.
        JFrame frame = new JFrame("HelloWorldSwing");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        final DefaultSettingsProvider settingsProvider = new DefaultSettingsProvider();
        StyleState styleState = new StyleState();
        styleState.setDefaultStyle(settingsProvider.getDefaultStyle());

        final JediTerminalPanel myTerminalPanel = new JediTerminalPanel(
                settingsProvider,
                styleState,
                new TerminalTextBuffer(640, 480, styleState)
        );

        int columns = 20;
        int lines = 40;

        TextProcessing myTextProcessing = new TextProcessing(settingsProvider.getHyperlinkColor(), settingsProvider.getHyperlinkHighlightingMode());
        TerminalTextBuffer terminalTextBuffer = new TerminalTextBuffer(columns, lines, styleState, settingsProvider.getBufferMaxLinesCount(), myTextProcessing);

        JediTerminal myTerminal = new JediTerminal(myTerminalPanel, terminalTextBuffer, styleState);

        myTerminal.setModeEnabled(TerminalMode.AltSendsEscape, settingsProvider.altSendsEscape());

        myTerminalPanel.addTerminalMouseListener(myTerminal);
        myTerminalPanel.setNextProvider(this);
        myTerminalPanel.setCoordAccessor(myTerminal);

        PreConnectHandler myPreConnectHandler = new PreConnectHandler(myTerminal);
        myTerminalPanel.setKeyListener(myPreConnectHandler);
        JScrollBar scrollBar = new JScrollBar();
        scrollBar.setUI(new FindResultScrollBarUI());

        JLayeredPane myInnerPanel = new JLayeredPane();
        myInnerPanel.setFocusable(false);

        frame.getContentPane().setFocusable(false);

        myInnerPanel.setLayout(new TerminalLayout());
        myInnerPanel.add(myTerminalPanel, TerminalLayout.TERMINAL);
        myInnerPanel.add(scrollBar, TerminalLayout.SCROLL);

        frame.getContentPane().add(myInnerPanel, BorderLayout.CENTER);

        scrollBar.setModel(myTerminalPanel.getBoundedRangeModel());
        mySessionRunning.set(false);

        myTerminalPanel.init();

        myTerminalPanel.setVisible(true);

        frame.getContentPane().add(myTerminalPanel);
        //Display the window.
        frame.setSize(640, 480);
        frame.setVisible(true);

        return myTerminalPanel;
    }

    public static JediTermWidget showContent() {
//        JFrame frame = new JFrame("HelloWorldSwing");
//        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        final JediTermWidget jediTermWidget = new JediTermWidget(640, 480, new DefaultSettingsProvider());

//        frame.getContentPane().add(jediTermWidget);

        try {

            Charset charset = Charset.forName("UTF-8");

            Map<String, String> envs = Maps.newHashMap(System.getenv());

            EncodingEnvironmentUtil.setLocaleEnvironmentIfMac(envs, charset);

            String[] command;

            if (UIUtil.isWindows) {
                command = new String[]{"cmd.exe"};
            } else {
                System.out.println("bash");
                command = new String[]{"/bin/bash", "--login"};
                envs.put("TERM", "xterm");
            }

            PtyProcess process = PtyProcess.exec(command, envs, null);

            final JediTerm.LoggingPtyProcessTtyConnector loggingPtyProcessTtyConnector = new JediTerm.LoggingPtyProcessTtyConnector(process, charset);
            jediTermWidget.setTtyConnector(loggingPtyProcessTtyConnector);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }

        jediTermWidget.start();

        return jediTermWidget;
    }

    public static void main(String... args) {
//        final Term term = new Term();
//        term.getTerm();
        showContent();
        System.out.println("hello");
    }

    @Override
    public List<TerminalAction> getActions() {
        return null;
    }

    @Override
    public TerminalActionProvider getNextProvider() {
        return this.provider;
    }

    @Override
    public void setNextProvider(TerminalActionProvider provider) {
        this.provider = provider;
    }

    private class FindResultScrollBarUI extends BasicScrollBarUI {

        protected void paintTrack(
                Graphics g,
                JComponent c,
                Rectangle trackBounds,
                TerminalPanel terminalPanel,
                SettingsProvider settingsProvider
        ) {
            super.paintTrack(g, c, trackBounds);

            SubstringFinder.FindResult result = terminalPanel.getFindResult();
            if (result != null) {
                int modelHeight = scrollbar.getModel().getMaximum() - scrollbar.getModel().getMinimum();
                int anchorHeight = Math.max(2, trackBounds.height / modelHeight);

                Color color = settingsProvider.getTerminalColorPalette()
                        .getColor(settingsProvider.getFoundPatternColor().getBackground());
                g.setColor(color);
                for (SubstringFinder.FindResult.FindItem r : result.getItems()) {
                    int where = trackBounds.height * r.getStart().y / modelHeight;
                    g.fillRect(trackBounds.x, trackBounds.y + where, trackBounds.width, anchorHeight);
                }
            }
        }
    }

    private static class TerminalLayout implements LayoutManager {
        public static final String TERMINAL = "TERMINAL";
        public static final String SCROLL = "SCROLL";
        public static final String FIND = "FIND";

        private Component terminal;
        private Component scroll;
        private Component find;

        @Override
        public void addLayoutComponent(String name, Component comp) {
            if (TERMINAL.equals(name)) {
                terminal = comp;
            } else if (FIND.equals(name)) {
                find = comp;
            } else if (SCROLL.equals(name)) {
                scroll = comp;
            } else throw new IllegalArgumentException("unknown component name " + name);
        }

        @Override
        public void removeLayoutComponent(Component comp) {
            if (comp == terminal) {
                terminal = null;
            }
            if (comp == scroll) {
                scroll = null;
            }
            if (comp == find) {
                find = comp;
            }
        }

        @Override
        public Dimension preferredLayoutSize(Container target) {
            synchronized (target.getTreeLock()) {
                Dimension dim = new Dimension(0, 0);

                if (terminal != null) {
                    Dimension d = terminal.getPreferredSize();
                    dim.width = Math.max(d.width, dim.width);
                    dim.height = Math.max(d.height, dim.height);
                }

                if (scroll != null) {
                    Dimension d = scroll.getPreferredSize();
                    dim.width += d.width;
                    dim.height = Math.max(d.height, dim.height);
                }

                if (find != null) {
                    Dimension d = find.getPreferredSize();
                    dim.width = Math.max(d.width, dim.width);
                    dim.height = Math.max(d.height, dim.height);
                }

                Insets insets = target.getInsets();
                dim.width += insets.left + insets.right;
                dim.height += insets.top + insets.bottom;

                return dim;
            }
        }

        @Override
        public Dimension minimumLayoutSize(Container target) {
            synchronized (target.getTreeLock()) {
                Dimension dim = new Dimension(0, 0);

                if (terminal != null) {
                    Dimension d = terminal.getMinimumSize();
                    dim.width = Math.max(d.width, dim.width);
                    dim.height = Math.max(d.height, dim.height);
                }

                if (scroll != null) {
                    Dimension d = scroll.getPreferredSize();
                    dim.width += d.width;
                    dim.height = Math.max(d.height, dim.height);
                }

                if (find != null) {
                    Dimension d = find.getMinimumSize();
                    dim.width = Math.max(d.width, dim.width);
                    dim.height = Math.max(d.height, dim.height);
                }

                Insets insets = target.getInsets();
                dim.width += insets.left + insets.right;
                dim.height += insets.top + insets.bottom;

                return dim;
            }
        }

        @Override
        public void layoutContainer(Container target) {
            synchronized (target.getTreeLock()) {
                Insets insets = target.getInsets();
                int top = insets.top;
                int bottom = target.getHeight() - insets.bottom;
                int left = insets.left;
                int right = target.getWidth() - insets.right;

                Dimension scrollDim = new Dimension(0, 0);
                if (scroll != null) {
                    scrollDim = scroll.getPreferredSize();
                    scroll.setBounds(right - scrollDim.width, top, scrollDim.width, bottom - top);
                }

                if (terminal != null) {
                    terminal.setBounds(left, top, right - left - scrollDim.width, bottom - top);
                }

                if (find != null) {
                    Dimension d = find.getPreferredSize();
                    find.setBounds(right - d.width - scrollDim.width, top, d.width, d.height);
                }
            }

        }
    }
}
