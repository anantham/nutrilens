package com.nutritheous.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Generic paginated response wrapper.
 * Wraps any list of data with pagination metadata.
 *
 * @param <T> The type of data being paginated
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PageResponse<T> {

    /**
     * The list of items for the current page
     */
    private List<T> content;

    /**
     * Current page number (0-indexed)
     */
    private int page;

    /**
     * Number of items per page
     */
    private int size;

    /**
     * Total number of items across all pages
     */
    private long totalElements;

    /**
     * Total number of pages
     */
    private int totalPages;

    /**
     * Whether this is the first page
     */
    private boolean first;

    /**
     * Whether this is the last page
     */
    private boolean last;

    /**
     * Whether there are more items after this page
     */
    private boolean hasNext;

    /**
     * Whether there are items before this page
     */
    private boolean hasPrevious;

    /**
     * Creates a PageResponse from Spring Data's Page object.
     *
     * @param springPage Spring Data Page object
     * @param <T> Type of data
     * @return PageResponse with populated metadata
     */
    public static <T> PageResponse<T> from(org.springframework.data.domain.Page<T> springPage) {
        return PageResponse.<T>builder()
                .content(springPage.getContent())
                .page(springPage.getNumber())
                .size(springPage.getSize())
                .totalElements(springPage.getTotalElements())
                .totalPages(springPage.getTotalPages())
                .first(springPage.isFirst())
                .last(springPage.isLast())
                .hasNext(springPage.hasNext())
                .hasPrevious(springPage.hasPrevious())
                .build();
    }
}
