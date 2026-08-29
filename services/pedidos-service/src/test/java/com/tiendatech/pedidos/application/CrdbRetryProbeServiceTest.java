package com.tiendatech.pedidos.application;

import com.tiendatech.pedidos.domain.*;
import org.junit.jupiter.api.Test;
import java.util.concurrent.CountDownLatch;
import java.util.function.Supplier;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CrdbRetryProbeServiceTest {
    @Test void coordinaLaColisionYReportaReintentos() {
        var probe=mock(CrdbProbePort.class); var retry=mock(CrdbRetryPort.class); var metrics=mock(CrdbMetricsPort.class);
        when(metrics.retryCount()).thenReturn(4d,5d);
        doAnswer(inv->{((CountDownLatch)inv.getArgument(0)).countDown();((CountDownLatch)inv.getArgument(2)).countDown();return null;})
                .when(probe).ejecutarCompetidor(any(),any(),any());
        when(probe.ejecutarSonda(anyInt(),any(),any())).thenAnswer(inv->{((CountDownLatch)inv.getArgument(1)).countDown();return 42L;});
        when(retry.execute(any())).thenAnswer(inv->((Supplier<?>)inv.getArgument(0)).get());
        var result=new CrdbRetryProbeService(probe,retry,metrics).provocarColision();
        assertEquals("40001",result.get("sqlStateProvocado"));assertEquals(1,result.get("intentos"));assertEquals(42L,result.get("valorFinal"));assertEquals(5d,result.get("reintentosDespues"));
    }

    @Test void convierteFalloEnErrorDeDominio() {
        var probe=mock(CrdbProbePort.class);var retry=mock(CrdbRetryPort.class);var metrics=mock(CrdbMetricsPort.class);
        doThrow(new RuntimeException("boom")).when(probe).ejecutarCompetidor(any(),any(),any());
        assertThrows(IllegalStateException.class,()->new CrdbRetryProbeService(probe,retry,metrics).provocarColision());
    }
}
