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

package org.kontalk.model;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Date;
import java.util.HashMap;
import java.util.Observable;
import java.util.Optional;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.jxmpp.util.XmppStringUtils;
import org.kontalk.system.Database;
import org.kontalk.util.XMPPUtils;

/**
 * Global list of all contacts.
 *
 * @author Alexander Bikadorov {@literal <bikaejkb@mail.tu-berlin.de>}
 */
public final class UserList extends Observable {
    private static final Logger LOGGER = Logger.getLogger(UserList.class.getName());

    private static final UserList INSTANCE = new UserList();

    /** JID to user. */
    private final HashMap<String, User> mJIDMap = new HashMap<>();
    /** Database ID to user. */
    private final HashMap<Integer, User> mIDMap = new HashMap<>();

    private UserList() {}

    public void load() {
        Database db = Database.getInstance();
        try (ResultSet resultSet = db.execSelectAll(User.TABLE)) {
            while (resultSet.next()) {
                int id = resultSet.getInt("_id");
                String jid = resultSet.getString(User.COL_JID);
                String name = resultSet.getString(User.COL_NAME);
                String status = resultSet.getString(User.COL_STAT);
                long l = resultSet.getLong(User.COL_LAST_SEEN);
                Optional<Date> lastSeen = l == 0 ?
                        Optional.<Date>empty() :
                        Optional.<Date>of(new Date(l));
                boolean encr = resultSet.getBoolean(User.COL_ENCR);
                String key = Database.getString(resultSet, User.COL_PUB_KEY);
                String fp = Database.getString(resultSet, User.COL_KEY_FP);
                User newUser = new User(id, jid, name, status, lastSeen, encr, key, fp);
                synchronized (this) {
                    mJIDMap.put(jid, newUser);
                    mIDMap.put(id, newUser);
                }
            }
        } catch (SQLException ex) {
            LOGGER.log(Level.WARNING, "can't load users from db", ex);
        }
        this.changed(null);
    }

    public synchronized SortedSet<User> getAll() {
        return new TreeSet<>(mJIDMap.values());
    }

    /**
     * Create and add a new user.
     * @param jid JID of new user
     * @param name nickname of new user, use an empty string if not known
     * @return the newly created user, if one was created
     */
    public synchronized Optional<User> createUser(String jid, String name) {
        if (!this.isValid(jid))
            return Optional.empty();

        User newUser = new User(jid, name);
        if (newUser.getID() < 1)
            return Optional.empty();

        mJIDMap.put(newUser.getJID(), newUser);
        mIDMap.put(newUser.getID(), newUser);

        this.changed(newUser);
        return Optional.of(newUser);
    }

    public synchronized void changeJID(User user, String jid) {
        if (!this.isValid(jid))
            return;

        mJIDMap.put(jid, user);
        mJIDMap.remove(user.getJID());
        user.setJID(jid);

        this.changed(user);
    }

    private boolean isValid(String jid) {
        jid = XmppStringUtils.parseBareJid(jid);
        if (!XMPPUtils.isValid(jid)) {
            LOGGER.warning("invalid jid: "+jid);
            return false;
        }

        if (mJIDMap.containsKey(jid)) {
            LOGGER.warning("jid already exists: "+jid);
            return false;
        }

        return true;
    }

    public synchronized void save() {
        for (User user: mJIDMap.values()) {
            user.save();
        }
    }

    synchronized Optional<User> get(int id) {
        Optional<User> optUser = Optional.ofNullable(mIDMap.get(id));
        if (!optUser.isPresent())
            LOGGER.warning("can't find user with ID: "+id);
        return optUser;
    }

    public synchronized void remove(User user) {
        boolean removed = mJIDMap.remove(user.getJID(), user);
        if (!removed) {
            LOGGER.warning("can't find user to remove: "+user);
        }
        mIDMap.remove(user.getID());
    }

    /**
     * Get the user for a JID (if the JID is in the list).
     * Resource is removed for lookup.
     * @param jid
     * @return
     */
    public synchronized Optional<User> get(String jid) {
        jid = XmppStringUtils.parseBareJid(jid);
        return Optional.ofNullable(mJIDMap.get(jid));
    }

    /**
     * Return whether a user with a specified JID exists.
     * Resource is removed for lookup.
     * @param jid
     * @return
     */
    public synchronized boolean contains(String jid) {
        jid = XmppStringUtils.parseBareJid(jid);
        return mJIDMap.containsKey(jid);
    }

    private synchronized void changed(Object arg) {
        this.setChanged();
        this.notifyObservers(arg);
    }

    public static UserList getInstance() {
        return INSTANCE;
    }
}
