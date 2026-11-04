package com.horairesync.adapter;

import com.horairesync.error.ErrorCode;
import com.horairesync.error.HoraireSyncException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Indexes every {@link SchoolAdapter} bean by school id.
 *
 * <p>Spring injects all implementations of the interface, so adding a school means adding one {@code @Component}
 * class, with no change here or in the services.
 */
@Component
public class SchoolAdapterRegistry {

    private final Map<String, SchoolAdapter> adaptersById;

    public SchoolAdapterRegistry(List<SchoolAdapter> adapters) {
        this.adaptersById = adapters.stream()
                .collect(Collectors.toUnmodifiableMap(a -> a.school().id(), Function.identity(), (a, b) -> {
                    throw new IllegalStateException("Two adapters declare school id " + a.school().id());
                }));
    }

    public Optional<SchoolAdapter> find(String schoolId) {
        return Optional.ofNullable(schoolId).map(adaptersById::get);
    }

    /** Returns the adapter or throws {@code SCHOOL_NOT_SUPPORTED}. */
    public SchoolAdapter require(String schoolId) {
        return find(schoolId).orElseThrow(() -> new HoraireSyncException(ErrorCode.SCHOOL_NOT_SUPPORTED,
                "L'école « " + schoolId + " » n'est pas prise en charge. Utilisez la saisie manuelle."));
    }

    public Collection<SchoolAdapter> all() {
        return adaptersById.values();
    }
}
