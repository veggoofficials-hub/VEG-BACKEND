package com.veggofresh.platform;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.hibernate.annotations.Where;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.core.type.filter.AssignableTypeFilter;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * BUILD-TIME GUARD: hand-written (native) SQL must not forget the soft-delete filter.
 *
 * Why: entities such as Shop, DeliveryPartnerProfile or User are "soft-deleted" -- the row stays but gets a
 * deleted_at date -- and Hibernate hides those rows automatically through @Where. That automatic filter does
 * NOT apply to native SQL. A native query that reads such a table without {@code deleted_at IS NULL} would
 * silently return deleted accounts (a deleted shop showing up for customers, for example).
 *
 * The test scans every {@code @Query(nativeQuery = true)} on every repository in com.veggofresh. For each
 * reference to a soft-delete table ({@code FROM x} / {@code JOIN x}) the query must mention
 * {@code <alias>.deleted_at} (or plain {@code deleted_at} when the table has no alias).
 *
 * If a query MUST see deleted rows on purpose:
 *   - name the method ...IncludingDeleted (those are skipped automatically), or
 *   - add "RepositoryName.methodName" to {@link #ALLOWED} below, with a comment saying why.
 */
class NativeQuerySoftDeleteGuardTest {

    /** Intentional exceptions: "RepositoryName.methodName" -> why. */
    private static final Set<String> ALLOWED = Set.of(
            // Admin "who used this coupon" list: shows every redemption, including those of deleted/purged
            // users and soft-deleted orders; the real phone comes from deleted_account_log.
            "CouponRedemptionRepository.findRedemptionRows"
    );

    private static final Set<String> RESERVED_WORDS = Set.of(
            "on", "where", "left", "right", "inner", "outer", "join", "order", "group", "limit", "offset", "set",
            "using", "cross", "full", "union", "and", "or", "having", "select", "from", "natural", "lateral");

    @Test
    void nativeQueriesOnSoftDeleteTablesMustFilterDeletedAt() throws Exception {
        Set<String> softDeleteTables = findSoftDeleteTables();
        assertThat(softDeleteTables)
                .as("soft-delete (@Where) tables found by scanning the entities -- if empty the scan is broken")
                .isNotEmpty();

        List<String> violations = new ArrayList<>();
        int repositories = 0;
        int nativeQueries = 0;

        for (Class<?> repo : findRepositories()) {
            repositories++;
            for (Method method : repo.getDeclaredMethods()) {
                Query query = method.getAnnotation(Query.class);
                if (query == null || !query.nativeQuery()) {
                    continue;
                }
                nativeQueries++;
                String id = repo.getSimpleName() + "." + method.getName();
                if (ALLOWED.contains(id) || method.getName().contains("IncludingDeleted")) {
                    continue;
                }
                for (String sql : new String[]{query.value(), query.countQuery()}) {
                    if (sql == null || sql.isBlank()) {
                        continue;
                    }
                    for (String table : softDeleteTables) {
                        if (readsWithoutDeletedFilter(sql, table)) {
                            violations.add(id + " reads soft-delete table '" + table
                                    + "' without a deleted_at filter");
                        }
                    }
                }
            }
        }

        assertThat(repositories).as("repositories scanned -- if 0 the scan is broken").isGreaterThan(0);
        assertThat(nativeQueries).as("native queries scanned -- if 0 the scan is broken").isGreaterThan(0);
        assertThat(violations)
                .as("Native SQL must filter deleted_at on soft-delete tables (Hibernate's @Where does not apply to "
                        + "native SQL). Add 'AND <alias>.deleted_at IS NULL', rename the method ...IncludingDeleted "
                        + "if it must see deleted rows, or add it to ALLOWED with a reason.")
                .isEmpty();
    }

    /** The checker itself: proves it flags a missing filter and accepts a present one. */
    @Test
    void checkerFlagsMissingFilterAndAcceptsPresentOne() {
        assertThat(readsWithoutDeletedFilter(
                "SELECT * FROM vendor_shops s WHERE s.city = :city", "vendor_shops")).isTrue();
        assertThat(readsWithoutDeletedFilter(
                "SELECT * FROM vendor_shops s WHERE s.city = :city AND s.deleted_at IS NULL", "vendor_shops")).isFalse();
        // no alias: a bare deleted_at is enough
        assertThat(readsWithoutDeletedFilter(
                "select count(*) from orders where user_id = :id and deleted_at is null", "orders")).isFalse();
        // filtering a DIFFERENT table's deleted_at does not cover this one
        assertThat(readsWithoutDeletedFilter(
                "select * from vendor_shops s join users u on u.id = s.owner_user_id where u.deleted_at is null",
                "vendor_shops")).isTrue();
        // a table that is not referenced at all is fine
        assertThat(readsWithoutDeletedFilter("select * from deleted_account_log", "vendor_shops")).isFalse();
        // a longer table name must not be mistaken for the shorter one
        assertThat(readsWithoutDeletedFilter("select * from orders_archive", "orders")).isFalse();
    }

    // ------------------------------------------------------------------------------------------------

    /** True when {@code sql} reads {@code table} (FROM/JOIN) and does not filter that table's deleted_at. */
    static boolean readsWithoutDeletedFilter(String sql, String table) {
        String low = sql.toLowerCase().replaceAll("\\s+", " ");
        Matcher m = Pattern.compile("\\b(?:from|join)\\s+" + Pattern.quote(table) + "(?!\\w)(?:\\s+(?:as\\s+)?(\\w+))?")
                .matcher(low);
        while (m.find()) {
            String alias = m.group(1);
            if (alias != null && RESERVED_WORDS.contains(alias)) {
                alias = null;
            }
            Pattern filter = alias != null
                    ? Pattern.compile("\\b" + Pattern.quote(alias) + "\\.deleted_at\\b")
                    : Pattern.compile("\\bdeleted_at\\b");
            if (!filter.matcher(low).find()) {
                return true;
            }
        }
        return false;
    }

    private static Set<String> findSoftDeleteTables() throws ClassNotFoundException {
        ClassPathScanningCandidateComponentProvider provider = new ClassPathScanningCandidateComponentProvider(false);
        provider.addIncludeFilter(new AnnotationTypeFilter(Entity.class));
        Set<String> tables = new TreeSet<>();
        for (BeanDefinition bd : provider.findCandidateComponents("com.veggofresh")) {
            Class<?> entity = Class.forName(bd.getBeanClassName());
            Table table = entity.getAnnotation(Table.class);
            if (table != null && !table.name().isBlank() && entity.isAnnotationPresent(Where.class)) {
                tables.add(table.name().toLowerCase());
            }
        }
        return tables;
    }

    private static List<Class<?>> findRepositories() throws ClassNotFoundException {
        // The default scanner skips interfaces, so accept them explicitly.
        ClassPathScanningCandidateComponentProvider provider = new ClassPathScanningCandidateComponentProvider(false) {
            @Override
            protected boolean isCandidateComponent(AnnotatedBeanDefinition beanDefinition) {
                return beanDefinition.getMetadata().isInterface();
            }
        };
        provider.addIncludeFilter(new AssignableTypeFilter(Repository.class));
        TreeMap<String, Class<?>> byName = new TreeMap<>();
        for (BeanDefinition bd : provider.findCandidateComponents("com.veggofresh")) {
            Class<?> repo = Class.forName(bd.getBeanClassName());
            byName.put(repo.getName(), repo);
        }
        return new ArrayList<>(byName.values());
    }
}
