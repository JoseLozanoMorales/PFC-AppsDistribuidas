package org.example.domain;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public record Paginacion(int page, int size) {

    private static final Logger log = LoggerFactory.getLogger(Paginacion.class);

    private static final int SIZE_DEFAULT = 20;
    private static final int SIZE_MAXIMO = 100;

    // size > 100 no se rechaza (para no romper una pantalla si el cliente pide
    // de más): se recorta al tope, pero se loguea en WARN para que el recorte
    // sea tolerante sin ser opaco. La metadata de PageResponse siempre lleva el
    // size efectivamente aplicado (este record ya recortado), no el solicitado.
    public static Paginacion de(Integer page, Integer size) {
        int paginaNormalizada = (page == null || page < 0) ? 0 : page;
        if (size != null && size > SIZE_MAXIMO) {
            log.warn("size solicitado ({}) excede el maximo permitido ({}); se recorta a {}",
                    size, SIZE_MAXIMO, SIZE_MAXIMO);
        }
        int tamanioNormalizado = (size == null || size < 1) ? SIZE_DEFAULT : Math.min(size, SIZE_MAXIMO);
        return new Paginacion(paginaNormalizada, tamanioNormalizado);
    }

    public int offset() {
        return page * size;
    }
}
