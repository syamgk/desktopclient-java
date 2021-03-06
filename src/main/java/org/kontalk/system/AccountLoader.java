/*
 * Kontalk Java client
 * Copyright (C) 2014 Kontalk Devteam <devteam@kontalk.org>

 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.

 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.

 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.kontalk.system;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.security.NoSuchProviderException;
import java.security.cert.CertificateException;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.apache.commons.io.IOUtils;
import org.bouncycastle.bcpg.ArmoredInputStream;
import org.bouncycastle.bcpg.ArmoredOutputStream;
import org.bouncycastle.openpgp.PGPException;
import org.jivesoftware.smack.util.StringUtils;
import org.kontalk.misc.KonException;
import org.kontalk.Kontalk;
import org.kontalk.crypto.PGPUtils;
import org.kontalk.crypto.PersonalKey;

public final class AccountLoader {
    private static final Logger LOGGER = Logger.getLogger(AccountLoader.class.getName());

    private static final String PRIVATE_KEY_FILENAME = "kontalk-private.asc";
    private static final String BRIDGE_CERT_FILENAME = "kontalk-login.crt";

    private static AccountLoader INSTANCE = null;

    private final Config mConf;

    private PersonalKey mKey = null;

    private AccountLoader(Config config) {
        mConf = config;
    }

    public Optional<PersonalKey> getPersonalKey() {
        return Optional.ofNullable(mKey);
    }

    PersonalKey load(char[] password) throws KonException {
        // read key files
        byte[] privateKeyData = readArmoredFile(PRIVATE_KEY_FILENAME);
        byte[] bridgeCertData = readFile(BRIDGE_CERT_FILENAME);

        // load key
        try {
            mKey = PersonalKey.load(privateKeyData,
                    password,
                    bridgeCertData);
        } catch (PGPException | IOException | CertificateException | NoSuchProviderException ex) {
            LOGGER.log(Level.WARNING, "can't load personal key", ex);
            throw new KonException(KonException.Error.LOAD_KEY, ex);
        }
        return mKey;
    }

    public void importAccount(String zipFilePath, char[] password) throws KonException {
        byte[] privateKeyData;
        byte[] bridgeCertData;

        // read key files
        try (ZipFile zipFile = new ZipFile(zipFilePath)) {
            privateKeyData = AccountLoader.readBytesFromZip(zipFile, PRIVATE_KEY_FILENAME);
            bridgeCertData = AccountLoader.readBytesFromZip(zipFile, BRIDGE_CERT_FILENAME);
        } catch (IOException ex) {
            LOGGER.log(Level.WARNING, "can't open zip archive: ", ex);
            throw new KonException(KonException.Error.IMPORT_ARCHIVE, ex);
        }

        // try to load key
        PersonalKey key;
        byte[] encodedPrivateKey;
        try {
            encodedPrivateKey = disarm(privateKeyData);
            key = PersonalKey.load(encodedPrivateKey,
                    password,
                    bridgeCertData);
        } catch (PGPException | IOException | CertificateException |
                NoSuchProviderException ex) {
            LOGGER.log(Level.WARNING, "can't import personal key", ex);
            throw new KonException(KonException.Error.IMPORT_KEY, ex);
        }

        // key seems valid. Copy to config dir
        writeBytesToFile(bridgeCertData, BRIDGE_CERT_FILENAME, false);
        this.writePrivateKey(encodedPrivateKey, password, new char[0]);

        // success! use the new key
        mKey = key;
    }

    public void setPassword(char[] oldPassword, char[] newPassword) throws KonException {
        byte[] privateKeyData = readArmoredFile(PRIVATE_KEY_FILENAME);
        this.writePrivateKey(privateKeyData, oldPassword, newPassword);
    }

    private void writePrivateKey(byte[] privateKeyData,
            char[] oldPassword,
            char[] newPassword)
            throws KonException {
        // old password
        if (oldPassword.length < 1)
            oldPassword = mConf.getString(Config.ACC_PASS).toCharArray();

        // new password
        boolean unset = newPassword.length == 0;
        if (unset)
            newPassword = StringUtils.randomString(40).toCharArray();

        // write new
        try {
            privateKeyData = PGPUtils.copySecretKeyRingWithNewPassword(privateKeyData,
                    oldPassword, newPassword).getEncoded();
        } catch (IOException | PGPException ex) {
            LOGGER.log(Level.WARNING, "can't change password", ex);
            throw new KonException(KonException.Error.CHANGE_PASS, ex);
        }
        writeBytesToFile(privateKeyData, PRIVATE_KEY_FILENAME, true);

        // new saved password
        String savedPass = unset ? new String(newPassword) : "";
        mConf.setProperty(Config.ACC_PASS, savedPass);
    }

    boolean isPresent() {
        return fileExists(PRIVATE_KEY_FILENAME) &&
                fileExists(BRIDGE_CERT_FILENAME);
    }

    public boolean isPasswordProtected() {
        // use configuration option to determine this
        return mConf.getString(Config.ACC_PASS).isEmpty();
    }

    private static boolean fileExists(String filename) {
        return new File(Kontalk.getConfigDir(), filename).isFile();
    }

    private static byte[] readBytesFromZip(ZipFile zipFile, String filename) throws KonException {
        ZipEntry zipEntry = zipFile.getEntry(filename);
        byte[] bytes = null;
        try {
            bytes = IOUtils.toByteArray(zipFile.getInputStream(zipEntry));
        } catch (IOException ex) {
            LOGGER.warning("can't read key file from archive: "+ex.getLocalizedMessage());
            throw new KonException(KonException.Error.IMPORT_READ_FILE, ex);
        }
        return bytes;
    }

    private static byte[] readArmoredFile(String filename) throws KonException {
        try {
            return disarm(readFile(filename));
        } catch (IOException ex) {
             LOGGER.warning("can't read armored key file: "+ex.getLocalizedMessage());
            throw new KonException(KonException.Error.READ_FILE, ex);
        }
    }

    private static byte[] disarm(byte[] key) throws IOException {
        return IOUtils.toByteArray(new ArmoredInputStream(new ByteArrayInputStream(key)));
    }

    private static byte[] readFile(String filename) throws KonException {
        String configDir = Kontalk.getConfigDir();
        byte[] bytes = null;
        try {
            bytes = Files.readAllBytes(new File(configDir, filename).toPath());
        } catch (IOException ex) {
            LOGGER.warning("can't read key file: "+ex.getLocalizedMessage());
            throw new KonException(KonException.Error.READ_FILE, ex);
        }
        return bytes;
    }

    private static void writeBytesToFile(byte[] bytes, String filename, boolean armored) throws KonException {
        String configDir = Kontalk.getConfigDir();
        try {
            OutputStream outStream = new FileOutputStream(new File(configDir, filename));
            if (armored)
                outStream = new ArmoredOutputStream(outStream);
            outStream.write(bytes);
            outStream.close();
        } catch (IOException ex) {
            LOGGER.log(Level.WARNING, "can't write key file", ex);
            throw new KonException(KonException.Error.WRITE_FILE, ex);
        }
    }

    public synchronized static AccountLoader getInstance() {
        if (INSTANCE == null)
            INSTANCE = new AccountLoader(Config.getInstance());
        return INSTANCE;
    }
}
