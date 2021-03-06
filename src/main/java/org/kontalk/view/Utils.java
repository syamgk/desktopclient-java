/*
 *  Kontalk Java client
 *  Copyright (C) 2014 Kontalk Devteam <devteam@kontalk.org>
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.kontalk.view;

import com.alee.extended.filechooser.WebFileChooserField;
import com.alee.extended.filefilter.ImageFilesFilter;
import com.alee.laf.menu.WebMenuItem;
import com.alee.laf.menu.WebPopupMenu;
import com.alee.laf.text.WebTextArea;
import com.alee.laf.text.WebTextField;
import java.awt.Image;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.security.cert.CertificateException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;
import javax.net.ssl.SSLHandshakeException;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import org.apache.commons.lang.StringUtils;
import org.jivesoftware.smack.SmackException;
import org.jivesoftware.smack.sasl.SASLErrorException;
import org.jxmpp.util.XmppStringUtils;
import org.kontalk.Kontalk;
import org.kontalk.misc.KonException;
import org.kontalk.model.User;
import org.kontalk.util.Tr;
import org.ocpsoft.prettytime.PrettyTime;

/**
 * Various utilities used in view.
 * @author Alexander Bikadorov {@literal <bikaejkb@mail.tu-berlin.de>}
 */
final class Utils {
    private static final Logger LOGGER = Logger.getLogger(Utils.class.getName());

    static final SimpleDateFormat SHORT_DATE_FORMAT = new SimpleDateFormat("EEE, HH:mm");
    static final SimpleDateFormat MID_DATE_FORMAT = new SimpleDateFormat("EEE, d MMM, HH:mm");
    static final SimpleDateFormat LONG_DATE_FORMAT = new SimpleDateFormat("EEE, d MMM yyyy, HH:mm:ss");
    static final PrettyTime PRETTY_TIME = new PrettyTime();

    private Utils() {}

    static WebFileChooserField createImageChooser(boolean enabled, String path) {
        WebFileChooserField chooser = new WebFileChooserField();
        chooser.setEnabled(enabled);
        chooser.getChooseButton().setEnabled(enabled);
        if (!path.isEmpty())
            chooser.setSelectedFile(new File(path));
        chooser.setMultiSelectionEnabled(false);
        chooser.setShowRemoveButton(true);
        chooser.getWebFileChooser().setFileFilter(new ImageFilesFilter());
        File file = new File(path);
        if (file.exists()) {
            chooser.setSelectedFile(file);
        }
        if (file.getParentFile() != null && file.getParentFile().exists())
            chooser.getWebFileChooser().setCurrentDirectory(file.getParentFile());
        return chooser;
    }

    static WebTextField createTextField(final String text) {
        final WebTextField field = new WebTextField(text, false);
        field.setEditable(false);
        field.setBackground(null);
        field.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                check(e);
            }
            @Override
            public void mouseReleased(MouseEvent e) {
                check(e);
            }
            private void check(MouseEvent e) {
                if (e.isPopupTrigger()) {
                    WebPopupMenu popupMenu = new WebPopupMenu();
                    popupMenu.add(createCopyMenuItem(field.getText(), ""));
                    popupMenu.show(field, e.getX(), e.getY());
                }
            }
        });
        return field;
    }

    static WebMenuItem createCopyMenuItem(final String copyText, String toolTipText) {
        WebMenuItem item = new WebMenuItem(Tr.tr("Copy"));
        if (!toolTipText.isEmpty())
            item.setToolTipText(toolTipText);
        item.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent event) {
                Clipboard clip = Toolkit.getDefaultToolkit().getSystemClipboard();
                clip.setContents(new StringSelection(copyText), null);
            }
        });
        return item;
    }

    static WebTextArea createFingerprintArea() {
        WebTextArea area = new WebTextArea();
        area.setEditable(false);
        area.setOpaque(false);
        area.setFontSizeAndStyle(13, true, false);
        return area;
    }

    static String getErrorText(KonException ex) {
        String eol = " " + System.getProperty("line.separator");
        String errorText = Tr.tr("Unknown error!?");
        switch (ex.getError()) {
            case IMPORT_ARCHIVE:
                errorText = Tr.tr("Can't open key archive.");
                break;
            case IMPORT_READ_FILE:
                errorText = Tr.tr("Can't load keyfile(s) from archive.");
                break;
            case IMPORT_KEY:
                errorText = Tr.tr("Can't create personal key from key files.") + " ";
                if (ex.getExceptionClass().equals(IOException.class)) {
                    errorText += eol + Tr.tr("Is the public key file valid?");
                }
                if (ex.getExceptionClass().equals(CertificateException.class)) {
                    errorText += eol + Tr.tr("Are all key files valid?");
                }
                break;
            case CHANGE_PASS:
                errorText = Tr.tr("Can't change password. Internal error(!?)");
                break;
            case WRITE_FILE:
                errorText = Tr.tr("Can't write key files to configuration directory.");
                break;
            case READ_FILE:
            case LOAD_KEY:
                switch (ex.getError()) {
                    case READ_FILE:
                        errorText = Tr.tr("Can't read key files from configuration directory.");
                        break;
                    case LOAD_KEY:
                        errorText = Tr.tr("Can't load key files from configuration directory.");
                        break;
                }
                errorText += " " + Tr.tr("Please reimport your key.");
                break;
            case LOAD_KEY_DECRYPT:
                errorText = Tr.tr("Can't decrypt key. Is the passphrase correct?");
                break;
            case CLIENT_CONNECTION:
                errorText = Tr.tr("Can't create connection");
                break;
            case CLIENT_CONNECT:
                errorText = Tr.tr("Can't connect to server.");
                if (ex.getExceptionClass().equals(SmackException.ConnectionException.class)) {
                    errorText += eol + Tr.tr("Is the server address correct?");
                }
                if (ex.getExceptionClass().equals(SSLHandshakeException.class)) {
                    errorText += eol + Tr.tr("The server rejects the key.");
                }
                if (ex.getExceptionClass().equals(SmackException.NoResponseException.class)) {
                    errorText += eol + Tr.tr("The server does not respond.");
                }
                break;
            case CLIENT_LOGIN:
                errorText = Tr.tr("Can't login to server.");
                if (ex.getExceptionClass().equals(SASLErrorException.class)) {
                    errorText += eol + Tr.tr("The server rejects the account. Is the specified server correct and the account valid?");
                }
                break;
            case CLIENT_ERROR:
                errorText = Tr.tr("Connection to server closed on error.");
                // TODO more details
                break;
        }
        return errorText;
    }

    static String shortenUserName(String jid, int maxLength) {
        String local = XmppStringUtils.parseLocalpart(jid);
        local = StringUtils.abbreviate(local, maxLength);
        String domain = XmppStringUtils.parseDomain(jid);
        return XmppStringUtils.completeJidFrom(local, domain);
    }

    static String shortenJID(String jid, int maxLength) {
        if (jid.length() > maxLength) {
            String local = XmppStringUtils.parseLocalpart(jid);
            local = StringUtils.abbreviate(local, (int) (maxLength * 0.4));
            String domain = XmppStringUtils.parseDomain(jid);
            domain = StringUtils.abbreviate(domain, (int) (maxLength * 0.6));
            jid = XmppStringUtils.completeJidFrom(local, domain);
        }
        return jid;
    }

    static Icon getIcon(String fileName) {
        return new ImageIcon(getImage(fileName));
    }

    static Image getImage(String fileName) {
        URL imageUrl = ClassLoader.getSystemResource(Kontalk.RES_PATH + fileName);
        if (imageUrl == null) {
            LOGGER.warning("can't find icon image resource");
            return new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB);
        }
        return Toolkit.getDefaultToolkit().createImage(imageUrl);
    }

    static String formatFingerprint(String fp) {
        int m = fp.length() / 2;
        return group(fp.substring(0, m)) + "\n" + group(fp.substring(m));
    }

    private static String group(String s) {
        return StringUtils.join(s.split("(?<=\\G.{" + 4 + "})"), " ");
    }

    static String userNameList(Set<User> users) {
        List<String> nameList = new ArrayList<>(users.size());
        for (User user : users) {
            nameList.add(user.getName().isEmpty() ?
                    Tr.tr("<unknown>") :
                    user.getName());
        }
        return StringUtils.join(nameList, ", ");
    }

    static String name(User user) {
        return !user.getName().isEmpty() ? user.getName() : Tr.tr("<unknown>");
    }

    static String mainStatus(User u) {
        User.Subscription subStatus = u.getSubScription();
        return u.isMe() ? Tr.tr("Me myself") :
                    u.isBlocked() ? Tr.tr("Blocked") :
                    u.getOnline() == User.Online.YES ? Tr.tr("Online") :
                    subStatus == User.Subscription.UNSUBSCRIBED ? Tr.tr("Not authorized") :
                    subStatus == User.Subscription.PENDING ? Tr.tr("Waiting for authorization") :
                    lastSeen(u, true);
    }

    static String lastSeen(User user, boolean pretty) {
        String lastSeen = !user.getLastSeen().isPresent() ? Tr.tr("never") :
                pretty ? Utils.PRETTY_TIME.format(user.getLastSeen().get()) :
                Utils.MID_DATE_FORMAT.format(user.getLastSeen().get());
        return Tr.tr("Last seen")+": " + lastSeen;
    }
}
