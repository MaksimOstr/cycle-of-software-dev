package com.cycleofsoftwaredev;

import static org.assertj.core.api.Assertions.assertThat;

import com.cycleofsoftwaredev.catalog.application.CategoryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class CycleOfSoftwareDevApplicationTests {

    @Autowired
    private CategoryService categories;

    @Test
    void contextLoadsWithDemoData() {
        assertThat(categories.listCategories()).isNotEmpty();
    }
}
