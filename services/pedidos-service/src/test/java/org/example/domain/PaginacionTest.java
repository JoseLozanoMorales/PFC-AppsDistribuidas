package org.example.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Paginacion.de(...) es donde vive el clamp de size y los defaults -- lo
 * invocan los controladores antes de llegar a los servicios de aplicacion.
 */
class PaginacionTest {

    @Test
    void de_conPageYSizeAusentes_usaDefaults0y20() {
        Paginacion paginacion = Paginacion.de(null, null);

        assertThat(paginacion.page()).isZero();
        assertThat(paginacion.size()).isEqualTo(20);
    }

    @Test
    void de_conSizeMayorA100_seRecortaA100() {
        Paginacion paginacion = Paginacion.de(0, 500);

        assertThat(paginacion.size()).isEqualTo(100);
    }

    @Test
    void de_conSizeExactamente100_noSeRecorta() {
        Paginacion paginacion = Paginacion.de(0, 100);

        assertThat(paginacion.size()).isEqualTo(100);
    }

    @Test
    void de_conSizeMenorA1_usaElDefault20() {
        assertThat(Paginacion.de(0, 0).size()).isEqualTo(20);
        assertThat(Paginacion.de(0, -5).size()).isEqualTo(20);
    }

    @Test
    void de_conPageNegativo_seNormalizaA0() {
        Paginacion paginacion = Paginacion.de(-3, 20);

        assertThat(paginacion.page()).isZero();
    }

    @Test
    void de_conPageYSizeValidos_losRespetaTalCual() {
        Paginacion paginacion = Paginacion.de(2, 50);

        assertThat(paginacion.page()).isEqualTo(2);
        assertThat(paginacion.size()).isEqualTo(50);
    }

    @Test
    void offset_esPageMultiplicadoPorSize() {
        assertThat(Paginacion.de(3, 20).offset()).isEqualTo(60);
    }
}
