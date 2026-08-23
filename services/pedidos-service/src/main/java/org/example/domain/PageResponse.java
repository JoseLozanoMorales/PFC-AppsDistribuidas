package org.example.domain;

import java.util.List;

public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {

    public static <T> PageResponse<T> of(List<T> content, Paginacion paginacion, long totalElements) {
        int totalPages = paginacion.size() == 0
                ? 0
                : (int) Math.ceil(totalElements / (double) paginacion.size());
        return new PageResponse<>(content, paginacion.page(), paginacion.size(), totalElements, totalPages);
    }
}
