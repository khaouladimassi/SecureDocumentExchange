package com.securedocumentexchange.security;


import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.Arrays;


public class AESManager {


    private static final String TRANSFORMATION =
            "AES/GCM/NoPadding";


    private static final int KEY_SIZE = 256;


    private static final int IV_SIZE = 12;


    private static final int TAG_SIZE = 128;



    // Génération AES-256

    public static SecretKey generateKey()
            throws Exception {


        KeyGenerator generator =
                KeyGenerator.getInstance("AES");


        generator.init(KEY_SIZE);


        return generator.generateKey();

    }





    // Chiffrement fichier

    public static void encrypt(
            File input,
            File output,
            SecretKey key
    ) throws Exception {



        byte[] iv =
                new byte[IV_SIZE];


        SecureRandom random =
                new SecureRandom();


        random.nextBytes(iv);



        Cipher cipher =
                Cipher.getInstance(
                        TRANSFORMATION
                );



        cipher.init(
                Cipher.ENCRYPT_MODE,
                key,
                new GCMParameterSpec(
                        TAG_SIZE,
                        iv
                )
        );



        byte[] data =
                Files.readAllBytes(
                        input.toPath()
                );



        byte[] encrypted =
                cipher.doFinal(data);



        // fichier .enc :
        // IV + CipherText + TAG


        byte[] result =
                new byte[
                        iv.length
                                +
                                encrypted.length
                        ];



        System.arraycopy(
                iv,
                0,
                result,
                0,
                iv.length
        );


        System.arraycopy(
                encrypted,
                0,
                result,
                iv.length,
                encrypted.length
        );



        Files.write(
                output.toPath(),
                result
        );


    }







    // Déchiffrement fichier


    public static void decrypt(
            File input,
            File output,
            SecretKey key
    ) throws Exception {



        byte[] file =
                Files.readAllBytes(
                        input.toPath()
                );



        byte[] iv =
                Arrays.copyOfRange(
                        file,
                        0,
                        IV_SIZE
                );



        byte[] encrypted =
                Arrays.copyOfRange(
                        file,
                        IV_SIZE,
                        file.length
                );



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



        byte[] original =
                cipher.doFinal(
                        encrypted
                );



        Files.write(
                output.toPath(),
                original
        );


    }



}