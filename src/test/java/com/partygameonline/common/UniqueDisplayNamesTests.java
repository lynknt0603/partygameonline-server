package com.partygameonline.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class UniqueDisplayNamesTests {

    @Test
    void uniquifyAllNumbersDuplicatesFromOne() {
        Map<String, String> names = new LinkedHashMap<>();
        names.put("a", "Linh");
        names.put("b", "Linh");
        names.put("c", "Linh");
        names.put("d", "Minh");

        Map<String, String> unique = UniqueDisplayNames.uniquifyAll(List.of("a", "b", "c", "d"), names);

        assertThat(unique.values()).containsExactly("Linh 1", "Linh 2", "Linh 3", "Minh");
    }
}
