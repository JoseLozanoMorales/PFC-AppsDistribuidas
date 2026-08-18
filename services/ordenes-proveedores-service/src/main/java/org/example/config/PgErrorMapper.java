package org.example.config;

import org.postgresql.util.PSQLException;
import org.springframework.http.HttpStatus;

final class PgErrorMapper {

    private PgErrorMapper() {
    }

    static HttpStatus statusFor(Throwable ex) {
        PSQLException pg = findPsqlException(ex);
        if (pg == null || pg.getServerErrorMessage() == null) {
            return HttpStatus.INTERNAL_SERVER_ERROR;
        }
        String sqlState = pg.getServerErrorMessage().getSQLState();
        if (sqlState == null) {
            return HttpStatus.INTERNAL_SERVER_ERROR;
        }
        return switch (sqlState) {
            case "P0002" -> HttpStatus.NOT_FOUND;
            case "25000" -> HttpStatus.CONFLICT;
            case "23505" -> HttpStatus.CONFLICT;
            case "23503" -> HttpStatus.CONFLICT;
            case "40001" -> HttpStatus.CONFLICT;
            case "P0001" -> HttpStatus.BAD_REQUEST;
            default -> HttpStatus.BAD_REQUEST;
        };
    }

    static String messageFor(Throwable ex) {
        PSQLException pg = findPsqlException(ex);
        if (pg != null && pg.getServerErrorMessage() != null) {
            return pg.getServerErrorMessage().getMessage();
        }
        return ex.getMessage();
    }

    private static PSQLException findPsqlException(Throwable ex) {
        Throwable current = ex;
        while (current != null) {
            if (current instanceof PSQLException psql) {
                return psql;
            }
            current = current.getCause();
        }
        return null;
    }
}
