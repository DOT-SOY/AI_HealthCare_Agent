package com.backend.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.orm.jpa.EntityManagerFactoryBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;
import jakarta.persistence.EntityManagerFactory;

import java.util.HashMap;
import java.util.Map;

@Configuration
// ★ 중요: MariaDB용 Repository들이 있는 패키지 경로를 지정합니다.
@EnableJpaRepositories(
        basePackages = "com.backend.repository",
        entityManagerFactoryRef = "entityManagerFactory",
        transactionManagerRef = "transactionManager"
)
public class DataSourceConfig {

    // MariaDB 설정 (Primary)
    @Bean
    @Primary
    @ConfigurationProperties("spring.datasource")
    public DataSourceProperties mainDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean
    @Primary
    public DataSource mainDataSource() {
        return mainDataSourceProperties()
                .initializeDataSourceBuilder()
                .type(HikariDataSource.class)
                .build();
    }

    // MariaDB용 EntityManager 설정 (JPA가 MemberRepository를 생성할 수 있게 함)
    @Bean
    @Primary
    public LocalContainerEntityManagerFactoryBean entityManagerFactory(
            EntityManagerFactoryBuilder builder, @Qualifier("mainDataSource") DataSource dataSource) {

        Map<String, Object> properties = new HashMap<>();

        // 자바의 카멜케이스(createdAt)를 DB의 스네이크케이스(created_at)로 매핑해줍니다.
        properties.put("hibernate.physical_naming_strategy",
                "org.springframework.boot.model.naming.CamelCaseToUnderscoresNamingStrategy");

        // 암시적 명명 전략 추가
        properties.put("hibernate.implicit_naming_strategy",
                "org.springframework.boot.model.naming.ImplicitNamingStrategyComponentPathImpl");

        return builder
                .dataSource(dataSource)
                .packages("com.backend.domain") // Member 엔티티가 있는 패키지 경로
                .persistenceUnit("main")
                .build();
    }

    @Bean
    @Primary
    public PlatformTransactionManager transactionManager(
            @Qualifier("entityManagerFactory") EntityManagerFactory entityManagerFactory) {
        return new JpaTransactionManager(entityManagerFactory);
    }
}