package com.securedocumentexchange.test;


import com.securedocumentexchange.security.*;

import javax.crypto.SecretKey;
import java.io.File;



public class EncryptionTest {


    public static void main(String[] args)
    {


        try{


            File original =
                    new File(
                            "C:/Users/elbootic.com/Desktop/StageEte2026/Brochure1.pdf"
                    );



            File encrypted =
                    new File(
                            "C:/Users/elbootic.com/Desktop/StageEte2026/Brochure1.enc"
                    );



            File decrypted =
                    new File(
                            "C:/Users/elbootic.com/Desktop/StageEte2026/Brochure1_decrypted.pdf"
                    );



            String alias =
                    "doc_001";





            // 1 génération clé

            SecretKey key =
                    AESManager.generateKey();




            // 2 stockage KeyStore

            KeyStoreManager.saveKey(
                    alias,
                    key
            );



            System.out.println(
                    "Clé sauvegardée"
            );





            // 3 chiffrement

            AESManager.encrypt(
                    original,
                    encrypted,
                    key
            );


            System.out.println(
                    "Fichier chiffré"
            );





            // 4 récupération clé

            SecretKey recovered =
                    KeyStoreManager.getKey(
                            alias
                    );





            // 5 déchiffrement


            AESManager.decrypt(
                    encrypted,
                    decrypted,
                    recovered
            );



            System.out.println(
                    "Fichier déchiffré"
            );



        }
        catch(Exception e){

            e.printStackTrace();

        }


    }

}