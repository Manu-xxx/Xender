/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.swirlds.gui;

import com.swirlds.common.utility.CommonUtils;
import java.awt.Color;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.GraphicsEnvironment;
import java.awt.Window;
import java.awt.event.HierarchyEvent;
import java.awt.event.HierarchyListener;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;
import javax.swing.text.DefaultCaret;

/**
 * Utility functions for the GUI.
 */
public class GuiUtils {

    private GuiUtils() {}

    /**
     * Extracted from {@code Browser}
     *
     * @deprecated we need to refactor the ui. This method is horrible
     */
    @Deprecated
    public static void initUI()
            throws UnsupportedLookAndFeelException, ClassNotFoundException, InstantiationException,
                    IllegalAccessException {
        // discover the inset size and set the look and feel
        if (!GraphicsEnvironment.isHeadless()) {
            UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());
            final JFrame jframe = new JFrame();
            jframe.setPreferredSize(new Dimension(200, 200));
            jframe.pack();
            WindowManager.setInsets(jframe.getInsets());
            jframe.dispose();
        }
    }

    /**
     * Insert line breaks into the given string so that each line is at most len characters long (not including trailing
     * whitespace and the line break iteslf). Line breaks are only inserted after a whitespace character and before a
     * non-whitespace character, resulting in some lines possibly being shorter. If a line has no whitespace, then it
     * will insert between two non-whitespace characters to make it exactly len characters long.
     *
     * @param len the desired length
     * @param str the input string, where a newline is always just \n (never \n\r or \r or \r\n)
     */
    public static String wrap(final int len, final String str) {
        StringBuilder ans = new StringBuilder();
        String[] lines = str.split("\n"); // break into lines
        for (String line : lines) { // we'll add \n to end of every line, then strip off the last
            if (line.length() == 0) {
                ans.append('\n');
            }
            char[] c = line.toCharArray();
            int i = 0;
            while (i < c.length) { // repeatedly add a string starting at i, followed by a \n
                int j = i + len;
                if (j >= c.length) { // grab the rest of the characters
                    j = c.length;
                } else if (Character.isWhitespace(c[j])) { // grab more than len characters
                    while (j < c.length && Character.isWhitespace(c[j])) {
                        j++;
                    }
                } else { // grab len or fewer characters
                    while (j >= i && !Character.isWhitespace(c[j])) {
                        j--;
                    }
                    if (j < i) { // there is no whitespace before len
                        j = i + len;
                    } else { // the last whitespace before len is at j
                        j++;
                    }
                }
                ans.append(c, i, j - i); // append c[i...j-1]
                ans.append('\n');
                i = j; // continue, starting at j
            }
        }
        return ans.substring(0, ans.length() - 1); // remove the last '\n' that was added
    }

    /**
     * Instantiates and returns a JTextArea whose settings are suitable for use inside the browser window's scroll area
     * in a tab.
     */
    public static JTextArea newJTextArea(final String text) {
        JTextArea txt = new JTextArea(0, 0);
        ((DefaultCaret) txt.getCaret()).setUpdatePolicy(DefaultCaret.NEVER_UPDATE);
        txt.setBackground(Color.WHITE);
        txt.setForeground(Color.BLACK);
        txt.setDisabledTextColor(Color.BLACK);
        txt.setFont(GuiConstants.FONT);
        txt.setEditable(false);
        txt.setEnabled(false);
        txt.setText(text);
        txt.setVisible(true);
        return txt;
    }

    /**
     * This is equivalent to sending text to doing both Utilities.tellUserConsole() and writing to a popup window. It is
     * not used for debugging; it is used for production code for communicating to the user.
     *
     * @param title the title of the window to pop up
     * @param msg   the message for the user
     */
    public static void tellUserConsolePopup(final String title, final String msg) {
        CommonUtils.tellUserConsole("\n***** " + msg + " *****\n");
        if (!GraphicsEnvironment.isHeadless()) {
            final String[] ss = msg.split("\n");
            int w = 0;
            for (final String str : ss) {
                w = Math.max(w, str.length());
            }
            final JTextArea ta = new JTextArea(ss.length + 1, (int) (w * 0.65));
            ta.setText(msg);
            ta.setWrapStyleWord(true);
            ta.setLineWrap(true);
            ta.setCaretPosition(0);
            ta.setEditable(false);
            ta.addHierarchyListener(
                    new HierarchyListener() { // make ta resizable
                        @Override
                        public void hierarchyChanged(final HierarchyEvent e) {
                            final Window window = SwingUtilities.getWindowAncestor(ta);
                            if (window instanceof Dialog) {
                                final Dialog dialog = (Dialog) window;
                                if (!dialog.isResizable()) {
                                    dialog.setResizable(true);
                                }
                            }
                        }
                    });
            final JScrollPane sp = new JScrollPane(ta);
            JOptionPane.showMessageDialog(null, sp, title, JOptionPane.PLAIN_MESSAGE);
        }
    }
}
