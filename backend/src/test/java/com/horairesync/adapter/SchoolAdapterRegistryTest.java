package com.horairesync.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.horairesync.error.ErrorCode;
import com.horairesync.error.HoraireSyncException;
import com.horairesync.model.School;
import java.util.List;
import org.junit.jupiter.api.Test;

class SchoolAdapterRegistryTest {

    private static SchoolAdapter adapterFor(String id) {
        SchoolAdapter adapter = mock(SchoolAdapter.class);
        when(adapter.school()).thenReturn(new School(id, id.toUpperCase(), true));
        return adapter;
    }

    @Test
    void indexesAdaptersBySchoolId() {
        SchoolAdapter uqam = adapterFor("uqam");
        SchoolAdapter other = adapterFor("other");

        SchoolAdapterRegistry registry = new SchoolAdapterRegistry(List.of(uqam, other));

        assertThat(registry.require("uqam")).isSameAs(uqam);
        assertThat(registry.find("other")).containsSame(other);
        assertThat(registry.all()).hasSize(2);
    }

    @Test
    void unknownSchoolIsNotSupported() {
        SchoolAdapterRegistry registry = new SchoolAdapterRegistry(List.of(adapterFor("uqam")));

        assertThat(registry.find("mcgill")).isEmpty();
        assertThat(registry.find(null)).isEmpty();
        assertThatThrownBy(() -> registry.require("mcgill"))
                .isInstanceOf(HoraireSyncException.class)
                .extracting(e -> ((HoraireSyncException) e).code())
                .isEqualTo(ErrorCode.SCHOOL_NOT_SUPPORTED);
    }

    @Test
    void duplicateSchoolIdFailsAtStartup() {
        List<SchoolAdapter> adapters = List.of(adapterFor("uqam"), adapterFor("uqam"));

        assertThatThrownBy(() -> new SchoolAdapterRegistry(adapters))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("uqam");
    }
}
