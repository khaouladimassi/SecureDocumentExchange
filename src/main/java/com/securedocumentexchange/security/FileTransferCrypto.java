package com.securedocumentexchange.security;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import java.io.*;
import java.security.SecureRandom;

/**
 * ================================================================
 * FILE TRANSFER CRYPTO
 * ================================================================
 *
 * Chiffrement / déchiffrement des fichiers avec AES-256-GCM.
 *
 * Format :
 *
 * +----------------------+
 * | IV : 12 octets       |
 * +----------------------+
 * | Données chiffrées    |
 * +----------------------+
 * | Tag GCM : 16 octets  |
 * +----------------------+
 *
 * AES-GCM assure :
 * - Confidentialité
 * - Intégrité
 * - Authentification
 */
public final class FileTransferCrypto {

    private FileTransferCrypto() {
    }

    // ============================================================
    // CONSTANTES
    // ============================================================

    private static final String ALGORITHM = "AES";

    private static final String TRANSFORMATION =
            "AES/GCM/NoPadding";

    private static final int AES_KEY_SIZE = 256;

    /**
     * Taille recommandée de l'IV AES-GCM.
     */
    private static final int IV_SIZE = 12;

    /**
     * Tag GCM de 128 bits = 16 octets.
     */
    private static final int TAG_SIZE = 128;

    private static final int BUFFER_SIZE = 8192;

    private static final SecureRandom SECURE_RANDOM =
            new SecureRandom();


    // ============================================================
    // GENERER CLE AES-256
    // ============================================================

    /**
     * Génère une clé AES-256.
     *
     * Chaque fichier sensible doit idéalement
     * utiliser une nouvelle clé.
     */
    public static SecretKey generateKey()
            throws Exception {

        KeyGenerator keyGenerator =
                KeyGenerator.getInstance(ALGORITHM);

        keyGenerator.init(
                AES_KEY_SIZE,
                SECURE_RANDOM
        );

        return keyGenerator.generateKey();
    }


    // ============================================================
    // COMPATIBILITE
    // ============================================================

    /**
     * Alias pour les anciennes parties du projet
     * qui utilisent generateAESKey().
     *
     * Cela permet d'éviter :
     *
     * Cannot resolve method 'generateAESKey'
     */
    public static SecretKey generateAESKey()
            throws Exception {

        return generateKey();
    }


    // ============================================================
    // CHIFFRER -> FICHIER TEMPORAIRE
    // ============================================================

    /**
     * Chiffre un fichier et crée automatiquement
     * un fichier temporaire .enc.
     */
    public static File encryptFile(
            File inputFile,
            SecretKey key
    ) throws Exception {

        validateInputFile(inputFile);
        validateKey(key);

        File encryptedFile =
                File.createTempFile(
                        "secure_",
                        ".enc"
                );

        try {

            encryptFile(
                    inputFile,
                    encryptedFile,
                    key
            );

            return encryptedFile;

        } catch (Exception e) {

            deleteQuietly(encryptedFile);

            throw e;
        }
    }


    // ============================================================
    // CHIFFRER -> FICHIER DE SORTIE
    // ============================================================

    /**
     * Chiffre inputFile vers outputFile.
     *
     * Format :
     *
     * IV + ciphertext + GCM tag
     */
    public static File encryptFile(
            File inputFile,
            File outputFile,
            SecretKey key
    ) throws Exception {

        validateInputFile(inputFile);
        validateKey(key);

        if (outputFile == null) {

            throw new IllegalArgumentException(
                    "Fichier de sortie absent."
            );
        }

        if (inputFile.getCanonicalPath()
                .equals(outputFile.getCanonicalPath())) {

            throw new IOException(
                    "Le fichier source et le fichier de sortie "
                            + "doivent être différents."
            );
        }

        // --------------------------------------------------------
        // CREATION DOSSIER
        // --------------------------------------------------------

        File parent =
                outputFile.getParentFile();

        if (parent != null
                && !parent.exists()
                && !parent.mkdirs()) {

            throw new IOException(
                    "Impossible de créer le dossier : "
                            + parent.getAbsolutePath()
            );
        }

        // --------------------------------------------------------
        // GENERATION IV
        // --------------------------------------------------------

        byte[] iv =
                new byte[IV_SIZE];

        SECURE_RANDOM.nextBytes(iv);

        // --------------------------------------------------------
        // INITIALISATION AES-GCM
        // --------------------------------------------------------

        Cipher cipher =
                Cipher.getInstance(
                        TRANSFORMATION
                );

        GCMParameterSpec gcmSpec =
                new GCMParameterSpec(
                        TAG_SIZE,
                        iv
                );

        cipher.init(
                Cipher.ENCRYPT_MODE,
                key,
                gcmSpec
        );

        boolean success = false;

        // --------------------------------------------------------
        // CHIFFREMENT
        // --------------------------------------------------------

        try (
                InputStream input =
                        new BufferedInputStream(
                                new FileInputStream(
                                        inputFile
                                )
                        );

                OutputStream output =
                        new BufferedOutputStream(
                                new FileOutputStream(
                                        outputFile
                                )
                        )
        ) {

            // ----------------------------------------------------
            // IV
            // ----------------------------------------------------

            output.write(iv);

            // ----------------------------------------------------
            // DONNEES
            // ----------------------------------------------------

            byte[] buffer =
                    new byte[BUFFER_SIZE];

            int read;

            while (
                    (read = input.read(buffer)) != -1
            ) {

                if (read == 0) {
                    continue;
                }

                byte[] encrypted =
                        cipher.update(
                                buffer,
                                0,
                                read
                        );

                if (encrypted != null
                        && encrypted.length > 0) {

                    output.write(encrypted);
                }
            }

            // ----------------------------------------------------
            // FINAL + TAG GCM
            // ----------------------------------------------------

            byte[] finalBytes =
                    cipher.doFinal();

            if (finalBytes != null
                    && finalBytes.length > 0) {

                output.write(finalBytes);
            }

            output.flush();

            success = true;

            return outputFile;

        } finally {

            if (!success) {

                deleteQuietly(outputFile);
            }
        }
    }


    // ============================================================
    // DECHIFFRER
    // ============================================================

    /**
     * Déchiffre un fichier AES-256-GCM.
     *
     * Format attendu :
     *
     * IV + ciphertext + GCM tag
     *
     * Si le fichier a été modifié,
     * cipher.doFinal() déclenche une exception
     * d'authentification.
     */
    public static File decryptFile(
            File encryptedFile,
            SecretKey key,
            File outputFile
    ) throws Exception {

        // --------------------------------------------------------
        // VALIDATION
        // --------------------------------------------------------

        if (encryptedFile == null
                || !encryptedFile.isFile()) {

            throw new FileNotFoundException(
                    "Fichier chiffré introuvable."
            );
        }

        if (!encryptedFile.canRead()) {

            throw new IOException(
                    "Impossible de lire le fichier chiffré."
            );
        }

        validateKey(key);

        if (outputFile == null) {

            throw new IllegalArgumentException(
                    "Fichier de sortie absent."
            );
        }

        if (encryptedFile.getCanonicalPath()
                .equals(outputFile.getCanonicalPath())) {

            throw new IOException(
                    "Le fichier chiffré et le fichier "
                            + "de sortie doivent être différents."
            );
        }

        // --------------------------------------------------------
        // VERIFICATION TAILLE MINIMALE
        // --------------------------------------------------------

        /*
         * Minimum :
         *
         * IV  = 12 octets
         * TAG = 16 octets
         *
         * Donc au minimum 28 octets.
         */

        if (encryptedFile.length()
                < IV_SIZE + 16L) {

            throw new IOException(
                    "Fichier chiffré trop petit ou invalide."
            );
        }

        // --------------------------------------------------------
        // CREATION DOSSIER
        // --------------------------------------------------------

        File parent =
                outputFile.getParentFile();

        if (parent != null
                && !parent.exists()
                && !parent.mkdirs()) {

            throw new IOException(
                    "Impossible de créer le dossier de sortie."
            );
        }

        boolean success = false;

        // --------------------------------------------------------
        // DECHIFFREMENT
        // --------------------------------------------------------

        try (
                InputStream input =
                        new BufferedInputStream(
                                new FileInputStream(
                                        encryptedFile
                                )
                        );

                OutputStream output =
                        new BufferedOutputStream(
                                new FileOutputStream(
                                        outputFile
                                )
                        )
        ) {

            // ----------------------------------------------------
            // LECTURE IV
            // ----------------------------------------------------

            byte[] iv =
                    new byte[IV_SIZE];

            readFully(
                    input,
                    iv
            );

            // ----------------------------------------------------
            // INITIALISATION AES-GCM
            // ----------------------------------------------------

            Cipher cipher =
                    Cipher.getInstance(
                            TRANSFORMATION
                    );

            cipher.init(
                    Cipher.DECRYPT_MODE,
                    key,
                    new GCMParameterSpec(
                            TAG_SIZE,
                            iv
                    )
            );

            // ----------------------------------------------------
            // DONNEES CHIFFREES
            // ----------------------------------------------------

            byte[] buffer =
                    new byte[BUFFER_SIZE];

            int read;

            while (
                    (read = input.read(buffer)) != -1
            ) {

                if (read == 0) {
                    continue;
                }

                byte[] decrypted =
                        cipher.update(
                                buffer,
                                0,
                                read
                        );

                if (decrypted != null
                        && decrypted.length > 0) {

                    output.write(decrypted);
                }
            }

            // ----------------------------------------------------
            // FINAL
            //
            // Vérification du TAG GCM.
            // ----------------------------------------------------

            byte[] finalBytes =
                    cipher.doFinal();

            if (finalBytes != null
                    && finalBytes.length > 0) {

                output.write(finalBytes);
            }

            output.flush();

            success = true;

            return outputFile;

        } finally {

            /*
             * Si le déchiffrement échoue,
             * notamment si le TAG GCM est invalide,
             * le fichier de sortie est supprimé.
             */
            if (!success) {

                deleteQuietly(outputFile);
            }
        }
    }


    // ============================================================
    // READ FULLY
    // ============================================================

    private static void readFully(
            InputStream input,
            byte[] buffer
    ) throws IOException {

        int offset = 0;

        while (offset < buffer.length) {

            int read =
                    input.read(
                            buffer,
                            offset,
                            buffer.length - offset
                    );

            if (read == -1) {

                throw new EOFException(
                        "Fichier chiffré incomplet : IV absent."
                );
            }

            if (read == 0) {
                continue;
            }

            offset += read;
        }
    }


    // ============================================================
    // VALIDATION FICHIER
    // ============================================================

    private static void validateInputFile(
            File file
    ) throws IOException {

        if (file == null) {

            throw new IllegalArgumentException(
                    "Fichier absent."
            );
        }

        if (!file.isFile()) {

            throw new FileNotFoundException(
                    "Fichier source introuvable : "
                            + file.getAbsolutePath()
            );
        }

        if (!file.canRead()) {

            throw new IOException(
                    "Impossible de lire le fichier : "
                            + file.getAbsolutePath()
            );
        }
    }


    // ============================================================
    // VALIDATION CLE
    // ============================================================

    private static void validateKey(
            SecretKey key
    ) {

        if (key == null) {

            throw new IllegalArgumentException(
                    "Clé AES absente."
            );
        }

        if (!ALGORITHM.equalsIgnoreCase(
                key.getAlgorithm()
        )) {

            throw new IllegalArgumentException(
                    "La clé doit être une clé AES."
            );
        }

        byte[] encoded =
                key.getEncoded();

        if (encoded == null
                || encoded.length != 32) {

            throw new IllegalArgumentException(
                    "La clé doit être AES-256 (32 octets)."
            );
        }
    }


    // ============================================================
    // DELETE QUIETLY
    // ============================================================

    private static void deleteQuietly(
            File file
    ) {

        if (file == null) {
            return;
        }

        try {

            if (file.exists()) {

                if (!file.delete()) {

                    file.deleteOnExit();
                }
            }

        } catch (Exception ignored) {
        }
    }
}