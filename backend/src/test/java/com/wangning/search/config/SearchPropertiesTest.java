package com.wangning.search.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SearchPropertiesTest {

    @Test
    void shouldProvideSafeDefaultsForDisabledSearch() {
        SearchProperties properties = new SearchProperties();

        assertThat(properties.isEnabled()).isFalse();
        assertThat(properties.getUri()).isEqualTo("http://localhost:9200");
        assertThat(properties.getIndexAlias()).isEqualTo("zhiguang_knowpost");
        assertThat(properties.getIndexName()).isEqualTo("zhiguang_knowpost_v1");
        assertThat(properties.isRebuildOnStartup()).isFalse();
        assertThat(properties.getRebuildBatchSize()).isEqualTo(500);
    }
}
