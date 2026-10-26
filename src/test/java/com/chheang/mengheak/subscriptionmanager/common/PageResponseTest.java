package com.chheang.mengheak.subscriptionmanager.common;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class PageResponseTest {
    @Test void paginationAndMappingMatchTheReferenceContract() {
        var first = PageResponse.from(new PageImpl<>(List.of(10, 20), PageRequest.of(0, 2), 3), Object::toString);
        assertThat(first.content()).containsExactly("10", "20");
        assertThat(first.first()).isTrue();
        assertThat(first.last()).isFalse();
        assertThat(first.totalElements()).isEqualTo(3);
        assertThat(first.totalPages()).isEqualTo(2);
        var last = PageResponse.from(new PageImpl<>(List.of(30), PageRequest.of(1, 2), 3), Object::toString);
        assertThat(last.first()).isFalse();
        assertThat(last.last()).isTrue();
        assertThat(last.page()).isEqualTo(1);
    }
}
