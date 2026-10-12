package com.chheang.mengheak.subscriptionmanager.common;

import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

@Schema(name = "PageResponse", description = "A page of results")
public record PageResponse<T>(

        @Schema(description = "Items on the current page")
        List<T> content,

        @Schema(description = "Zero-based page index", example = "0")
        int page,

        @Schema(description = "Requested page size", example = "20")
        int size,

        @Schema(description = "Total number of matching items", example = "137")
        long totalElements,

        @Schema(description = "Total number of pages", example = "7")
        int totalPages,

        @Schema(description = "Whether this is the last page", example = "false")
        boolean last
) {

    public static <E, D> PageResponse<D> from(Page<E> page, Function<E, D> mapper) {
        return new PageResponse<>(
                page.getContent().stream().map(mapper).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isLast()
        );
    }
}
