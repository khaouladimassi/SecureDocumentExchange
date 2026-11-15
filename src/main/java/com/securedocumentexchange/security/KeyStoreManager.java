package com.securedocumentexchange.security;


import javax.crypto.SecretKey;
import java.io.*;
import java.security.KeyStore;
import java.util.Properties;



public class KeyStoreManager {


    private static final Properties properties =
            new Properties();



    static {

        try {

            InputStream input =
                    KeyStoreManager.class
                            .getClassLoader()
                            .getResourceAsStream(
                                    "app.properties"
                            );


            properties.load(input);


        } catch(Exception e){

            e.printStackTrace();

        }

    }





    private static final String FILE =
            properties.getProperty(
                    "keystore.path"
            );



    private static final String PASSWORD =
            properties.getProperty(
                    "keystore.password"
            );



    private static final String TYPE =
            properties.getProperty(
                    "keystore.type"
            );



    public static void saveKey(
            String alias,
            SecretKey key
    ) throws Exception {



        KeyStore keyStore =
                KeyStore.getInstance(TYPE);



        File file =
                new File(FILE);



        if(file.exists()){


            keyStore.load(
                    new FileInputStream(file),
                    PASSWORD.toCharArray()
            );


        }
        else{


            keyStore.load(
                    null,
                    null
            );

        }



        KeyStore.SecretKeyEntry entry =
                new KeyStore.SecretKeyEntry(
                        key
                );



        KeyStore.PasswordProtection protection =
                new KeyStore.PasswordProtection(
                        PASSWORD.toCharArray()
                );



        keyStore.setEntry(
                alias,
                entry,
                protection
        );



        keyStore.store(
                new FileOutputStream(file),
                PASSWORD.toCharArray()
        );


    }





    public static SecretKey getKey(
            String alias
    ) throws Exception {



        KeyStore keyStore =
                KeyStore.getInstance(TYPE);



        keyStore.load(
                new FileInputStream(FILE),
                PASSWORD.toCharArray()
        );



        KeyStore.SecretKeyEntry entry =
                (KeyStore.SecretKeyEntry)
                        keyStore.getEntry(
                                alias,
                                new KeyStore.PasswordProtection(
                                        PASSWORD.toCharArray()
                                )
                        );



        return entry.getSecretKey();


    }


}