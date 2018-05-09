/*
 This file is part of the BlueJ program. 
 Copyright (C) 1999-2009,2011,2013,2014,2016  Michael Kolling and John Rosenberg
 
 This program is free software; you can redistribute it and/or 
 modify it under the terms of the GNU General Public License 
 as published by the Free Software Foundation; either version 2 
 of the License, or (at your option) any later version. 
 
 This program is distributed in the hope that it will be useful, 
 but WITHOUT ANY WARRANTY; without even the implied warranty of 
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the 
 GNU General Public License for more details. 
 
 You should have received a copy of the GNU General Public License 
 along with this program; if not, write to the Free Software 
 Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA. 
 
 This file is subject to the Classpath exception as provided in the  
 LICENSE.txt file that accompanied this code.
 */
package bluej.editor.moe;

import bluej.Config;
import bluej.prefmgr.PrefMgr;
import bluej.utility.DialogManager;
import bluej.utility.javafx.FXPlatformSupplier;

import java.awt.*;              // MenuBar, MenuItem, Menu, Button, etc.

import javafx.application.Platform;
import javax.swing.*;           // all the GUI components
import javax.swing.border.EmptyBorder;

/**
 * An information panel, displayed at the bottom of a MoeEditor window. The panel can
 * display error messages / notices to the user.
 *
 * @author Michael Kolling
 */
public final class Info extends JPanel
{
    private static Font infoFont = new Font("SansSerif", Font.BOLD, PrefMgr.getEditorFontSize() - 1);
    private final FXPlatformSupplier<javafx.stage.Window> fxParent;

    // -------- INSTANCE VARIABLES --------

    private JLabel line1;
    private JLabel line2;
    String originalMsg;
    boolean isClear;

    // ------------- METHODS --------------

    /**
     * Construct a new Info instance.
     */
    public Info(FXPlatformSupplier<javafx.stage.Window> fxParent)
    {
        super();
        this.fxParent = fxParent;
        setLayout(new BorderLayout());
        setBorder(BorderFactory.createLineBorder(Color.black));

        JPanel body = new JPanel(new GridLayout(0, 1)); // one col, many rows
        body.setBackground(MoeEditor.infoColor);
        body.setBorder(new EmptyBorder(0,6,0,4));
        line1 = new JLabel() {
            @Override
            public void setBounds(int x, int y, int width, int height)
            {
                super.setBounds(x,y,width,height);
                if (originalMsg != null) {
                    rebreakLine();
                }
            }
        };
        line2 = new JLabel();
        body.add(line1);
        body.add(line2);
        if (!Config.isRaspberryPi()) body.setOpaque(false);
        add(body, BorderLayout.CENTER);

        isClear = true;
        refresh();
    }

    /**
     * Reset the font size for all Info instances - each instance must be
     * individually refresh()ed after calling this.
     */
    public static void resetFont()
    {
        int fsize = Math.max(PrefMgr.getEditorFontSize() - 1, 1);
        infoFont = new Font("SansSerif", Font.BOLD, fsize);
    }

    /**
     * display a one- or two-line message (using '\n' to separate multiple lines).
     */
    public void message(String msg)
    {
        originalMsg = msg;
        rebreakLine();
        isClear = false;
    }
    
    /**
     * Like message(String), but the message may be displayed in a pop-up dialog if the user
     * has enabled this preference (e.g. for blind users with screen readers)
     */
    public void messageImportant(String msg)
    {
        message(msg);
    }
    
    /**
     * Find a suitable place to split the original message over the two lines,
     * and do so.
     */
    private void rebreakLine()
    {
        int newline = originalMsg.indexOf('\n');

        String firstLine, secondLine;

        if (newline == -1) {
            int ipos = breakLine(originalMsg);
            if(originalMsg.length() <= ipos) {
                firstLine = originalMsg;
                secondLine = "";
            }
            else {
                // look for a suitable place to break to a new line
                int lspace = originalMsg.lastIndexOf(' ', ipos);
                int tspace = originalMsg.lastIndexOf('\t', ipos);
                int space = Math.max(lspace, tspace);
                if (space > ipos / 2) {
                    // Roughly: if breaking at the space means breaking before the halfway point,
                    // don't do it.
                    ipos = space;
                }

                firstLine = originalMsg.substring(0, ipos);
                secondLine = originalMsg.substring(ipos);
            }
        }
        else {
            firstLine = originalMsg.substring(0, newline);
            secondLine = originalMsg.substring(newline+1);
        }

        secondLine = secondLine.replace("\n", ";  ");
        line1.setText(firstLine);
        line2.setText(secondLine);
    }

    /**
     * Calculate at which character to break a line across the line1 label
     */
    private int breakLine(String msg)
    {
        if (msg.length() <= 2) {
            return msg.length(); // don't even bother
        }

        FontMetrics metrics = line1.getFontMetrics(line1.getFont());
        Insets insets = line1.getInsets();
        int hInsets = 0;
        if (insets != null) {
            hInsets = insets.left + insets.right;
        }
        int lineWidth = line1.getWidth() - hInsets;

        char [] charBuf = new char[msg.length()];
        msg.getChars(0, msg.length(), charBuf, 0);
        int curWidth = metrics.charsWidth(charBuf, 0, charBuf.length);
        if (curWidth < lineWidth) {
            return charBuf.length;
        }

        int lowerBound = 1;
        int upperBound = charBuf.length;

        while (lowerBound != upperBound) {
            int mid = (lowerBound + upperBound + 1) / 2; // favour towards the upper bound
            curWidth = metrics.charsWidth(charBuf, 0, mid);
            if (curWidth < lineWidth) {
                lowerBound = mid;
            }
            else {
                upperBound = Math.min(mid, upperBound - 1);
            }
        }

        return lowerBound;
    }
    
    /**
     * display a two line message
     */
    public void message(String msg1, String msg2)
    {
        message(msg1 + '\n' + msg2);
    }

    /**
     * clear the display
     */
    public void clear()
    {
        if (!isClear) {
            message (" ", " ");
            isClear = true;
        }
    }

    /**
     * Refresh display parameters (font size).
     */
    public void refresh()
    {
        line1.setFont(infoFont);
        line2.setFont(infoFont);
    }
}
