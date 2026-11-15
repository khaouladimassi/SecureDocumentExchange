package com.securedocumentexchange.test;


import com.securedocumentexchange.dao.UserDAO;
import com.securedocumentexchange.model.User;



/*public class UserDAOTest {


    public static void main(String[] args) {


        UserDAO userDAO = new UserDAO();



        // Test ajout utilisateur

        User user = new User(
                "khaoula",
                "123456",
                "USER",
                "PENDING"
        );


        /*boolean result = userDAO.addUser(user);


        System.out.println(
                "Ajout utilisateur : " + result
        );*/




        // Test recherche

        /*User found =
                userDAO.getUserByUsername("khaoula");


        if(found != null){

            System.out.println(
                    "Utilisateur trouvé : "
                            + found.getUsername()
            );

        }
        else{

            System.out.println(
                    "Utilisateur introuvable"
            );

        }



        // Test récupération liste

        for(User u : userDAO.getAllUsers()){


            System.out.println(
                    u.getUsername()
                            + " "
                            + u.getRole()
            );

        }


    }

}*/