package com.fcv.citas.infrastructure.persistence.catalog;

import java.util.Set;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import com.fcv.citas.domain.catalog.SiteCatalog;

/** Adaptador de lectura de {@link SiteCatalog} sobre la tabla fija {@code sites}. */
@Component
class JdbcSiteCatalog implements SiteCatalog {

    private final NamedParameterJdbcTemplate jdbc;

    JdbcSiteCatalog(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean allActive(Set<Integer> siteIds) {
        if (siteIds.isEmpty()) {
            return false;
        }
        Integer found = jdbc.queryForObject("SELECT COUNT(*) FROM sites WHERE active AND id IN (:ids)",
                new MapSqlParameterSource("ids", siteIds), Integer.class);
        return found != null && found == siteIds.size();
    }
}
