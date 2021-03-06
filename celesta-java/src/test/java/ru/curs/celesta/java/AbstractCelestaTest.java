package ru.curs.celesta.java;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import ru.curs.celesta.CelestaException;
import ru.curs.celesta.SessionContext;

import java.io.File;
import java.util.Optional;
import java.util.Properties;

public class AbstractCelestaTest {
    Optional<Celesta> celesta;

    @BeforeEach
    void setUp() {
        Properties params = new Properties();
        params.setProperty(
                "score.path",
                "src" + File.separator + "test" + File.separator + "resources" + File.separator + "emptyScore"
        );
        params.setProperty("h2.in-memory", "true");
        params.setProperty("celestaScan", "ru.curs.celesta.java.annotated, ru.curs.celesta.java.callcontext");
        this.celesta = Optional.of(Celesta.createInstance(params));

        this.celesta.get().login(SessionContext.SYSTEM_SESSION_ID, SessionContext.SYSTEM_USER_ID);
    }

    @AfterEach
    void tearDown() {
        this.celesta.ifPresent(this::closeCelesta);
    }

    private void closeCelesta(Celesta celesta) {
        try {
            celesta.callContext().getConn().createStatement().execute("SHUTDOWN");
            celesta.close();
        } catch (Exception e) {
            throw new CelestaException(e);
        }
    }
}
