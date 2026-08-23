package org.example.domain;

import java.util.concurrent.CountDownLatch;

public interface CrdbProbePort {
    void ejecutarCompetidor(CountDownLatch competidorListo, CountDownLatch sondaLeida,
                            CountDownLatch competidorConfirmado);

    long ejecutarSonda(int intento, CountDownLatch sondaLeida,
                       CountDownLatch competidorConfirmado);
}
