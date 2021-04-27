/* $Id: 3eb381c04207e926bc1c518ddc7d2a87e3a19985 $
 *
 * Unpublished work.
 * Copyright © 2018 Michael G. Binz
 */
package org.smack.util;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Base64;
import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;

/**
 * Security utilities.
 *
 * @author Michael Binz
 */
public class SecurityUtil {

    private final static String ALGORITHM =
            "SHA1withDSA";
    private final static String PROVIDER =
            "SUN";

    private SecurityUtil() {
        throw new AssertionError();
    }

    /**
     * Check if the passed data has a valid signature.
     *
     * @param pub  The signature's public key.
     * @param sign The signature.
     * @param data The signed data.
     *
     * @return True if the verification succeeded.
     */
    public static boolean performVerify(
            final PublicKey pub,
            final byte[] sign,
            final byte[] data) {
        try {
            final Signature signature =
                    Signature.getInstance(ALGORITHM, PROVIDER);

            signature.initVerify(pub);
            signature.update(data);

            return signature.verify(sign);
        } catch (final Exception e) {
            return false;
        }
    }

    /**
     * Signs the passed data using the private key.
     *
     * @param priv The key to use.
     * @param data The data to sign.
     *
     * @return The binary signature.
     *
     * @throws Exception In case of an error.
     */
    public static byte[] performSign(final PrivateKey priv, final byte[] data) throws Exception {
        final Signature signature =
                Signature.getInstance(ALGORITHM, PROVIDER);

        signature.initSign(
                priv);
        signature.update(
                data);

        return signature.sign();
    }

    /**
     * Read the first certificate from the passed stream and close the stream.
     *
     * @param fis The certificate stream. This is closed after reading.
     *
     * @return The certificate, never null. If no certificate is in the file,
     * an exception is thrown.
     *
     * @throws Exception In case of an error.
     * @see #writeCert(X509Certificate, File)
     */
    public static X509Certificate readCert(final InputStream fis)
            throws Exception {
        try (final BufferedInputStream bis = new BufferedInputStream(fis)) {
            final CertificateFactory cf =
                    CertificateFactory.getInstance("X.509");

            while (bis.available() > 0) {
                final Certificate cert = cf.generateCertificate(bis);

                // We return the first certificate we find.
                // TODO: we could read more, but this is not needed now.
                return (X509Certificate) cert;
            }

            // File did not contain a certificate.
            throw new IllegalArgumentException();
        }
    }

    /**
     * Read the first certificate from the passed file.
     *
     * @param f The certificate file.
     *
     * @return The certificate.
     *
     * @throws Exception In case of an error.
     * @see #writeCert(X509Certificate, File)
     */
    public static X509Certificate readCert(final File f) throws Exception {
        return readCert(new FileInputStream(f));
    }

    /**
     * Write a certificate into the passed file.
     *
     * @param cert The certificate to write.
     * @param f    The target file. If the file exists the content is
     *             overwritten.
     *
     * @throws Exception In case of an error.
     * @see #readCert(File)
     */
    public static void writeCert(final X509Certificate cert, final File f)
            throws Exception {
        try (final FileWriter sw = new FileWriter(f)) {
            sw.write("-----BEGIN CERTIFICATE-----\n");
            sw.write(
                    Base64.getMimeEncoder().encodeToString(
                            cert.getEncoded()));
            sw.write("\n-----END CERTIFICATE-----\n");

            sw.flush();
        }
    }

    public static byte[] encrypt(final byte[] data, final PrivateKey key)
            throws NoSuchAlgorithmException, NoSuchPaddingException,
            UnsupportedEncodingException, IllegalBlockSizeException,
            BadPaddingException, InvalidKeyException {
        final Cipher cipher =
                Cipher.getInstance("RSA");
        cipher.init(
                Cipher.ENCRYPT_MODE, key);

        return cipher.doFinal(data);
    }

    public static byte[] decrypt(final byte[] encryptedData, final PublicKey key)
            throws
            InvalidKeyException,
            UnsupportedEncodingException,
            IllegalBlockSizeException,
            BadPaddingException,
            NoSuchAlgorithmException,
            NoSuchPaddingException {
        final Cipher cipher =
                Cipher.getInstance("RSA");
        cipher.init(
                Cipher.DECRYPT_MODE, key);

        return cipher.doFinal(
                encryptedData);
    }
}
