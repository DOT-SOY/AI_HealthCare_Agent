package com.backend.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateProperties;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateSettings;
import org.springframework.boot.autoconfigure.orm.jpa.JpaProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.orm.jpa.EntityManagerFactoryBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;
import jakarta.persistence.EntityManagerFactory;

import java.util.HashMap;
import java.util.Map;

@Configuration
@SuppressWarnings("null") // IDE null-type-safety 경고 억제 (Spring DI로 주입 값은 런타임에 보장됨)
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
            EntityManagerFactoryBuilder builder,
            @Qualifier("mainDataSource") DataSource dataSource,
            JpaProperties jpaProperties,
            HibernateProperties hibernateProperties
    ) {

        Map<String, Object> properties = new HashMap<>();

        // Spring Boot 설정(application.properties)의 spring.jpa.* / hibernate.*(ddl-auto 포함)를
        // 실제 Hibernate vendor properties로 변환하여 EntityManagerFactory에 주입합니다.
        properties.putAll(
                hibernateProperties.determineHibernateProperties(
                        jpaProperties.getProperties(),
                        new HibernateSettings()
                )
        );

        // 자바의 카멜케이스(createdAt)를 DB의 스네이크케이스(created_at)로 매핑해줍니다.
        properties.put("hibernate.physical_naming_strategy",
                "org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy");

        return builder
                .dataSource(dataSource)
                .packages("com.backend.domain") // Member 엔티티가 있는 패키지 경로
                .persistenceUnit("main")
                .properties(properties)
                .build();
    }

    @Bean
    @Primary
    public PlatformTransactionManager transactionManager(
            @Qualifier("entityManagerFactory") EntityManagerFactory entityManagerFactory) {
        return new JpaTransactionManager(entityManagerFactory);
    }

    // PostgreSQL 설정 (Secondary)
    // 현재는 PostgreSQL 관련 Bean은 비활성화합니다.
    // 얼굴 인식(임베딩) 기능을 사용하지 않으므로, PostgreSQL 관련 Bean은 비활성화합니다.
    // 필요해지면 아래 Bean 들을 복원하고, pg.datasource.* 설정 및 PostgreSQL 드라이버 의존성을 추가하세요.
//    @Bean
//    @ConfigurationProperties("pg.datasource")
//    public DataSourceProperties postgresDataSourceProperties() {
//        return new DataSourceProperties();
//    }
//
//    @Bean(name = "postgresDataSource")
//    public DataSource postgresDataSource() {
//        return postgresDataSourceProperties()
//                .initializeDataSourceBuilder()
//                .type(HikariDataSource.class)
//                .build();
//    }
//
//    // PostgreSQL 전용 JdbcTemplate (FaceService에서 사용)
//    @Bean(name = "postgresJdbcTemplate")
//    public JdbcTemplate postgresJdbcTemplate(@Qualifier("postgresDataSource") DataSource dataSource) {
//        return new JdbcTemplate(dataSource);
//    }
}