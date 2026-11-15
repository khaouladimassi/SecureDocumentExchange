package com.securedocumentexchange.dao;

import com.securedocumentexchange.database.DatabaseConnection;
import com.securedocumentexchange.model.Log;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public class LogDAO {

    // =========================================================
    // INSERT LOG
    // =========================================================

    public void insert(
            Log log
    ) throws SQLException {

        if (log == null) {

            throw new IllegalArgumentException(
                    "Log null."
            );
        }

        if (log.getUserId() <= 0) {

            throw new SQLException(
                    "userId invalide : "
                            + log.getUserId()
            );
        }

        String sql =
                """
                INSERT INTO logs
                (
                    user_id,
                    action,
                    description,
                    document_id,
                    ip_address,
                    date_action,
                    level
                )
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """;

        try (
                Connection connection =
                        DatabaseConnection.getConnection();

                PreparedStatement statement =
                        connection.prepareStatement(sql)
        ) {

            statement.setInt(
                    1,
                    log.getUserId()
            );

            statement.setString(
                    2,
                    log.getAction()
            );

            statement.setString(
                    3,
                    log.getDescription()
            );

            if (log.getDocumentId() > 0) {

                statement.setInt(
                        4,
                        log.getDocumentId()
                );

            } else {

                statement.setNull(
                        4,
                        java.sql.Types.INTEGER
                );
            }

            statement.setString(
                    5,
                    log.getIpAddress()
            );

            if (log.getDateAction() != null) {

                statement.setTimestamp(
                        6,
                        java.sql.Timestamp.valueOf(
                                log.getDateAction()
                        )
                );

            } else {

                statement.setTimestamp(
                        6,
                        java.sql.Timestamp.valueOf(
                                java.time.LocalDateTime.now()
                        )
                );
            }

            statement.setString(
                    7,
                    log.getLevel()
            );

            statement.executeUpdate();
        }
    }
}