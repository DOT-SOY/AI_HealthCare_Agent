package com.backend.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateProperties;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateSettings;
import org.springframework.boot.autoconfigure.orm.jpa.JpaProperties;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Configuration
// ★ 중요: MariaDB용 Repository들이 있는 패키지 경로를 지정합니다.
@EnableJpaRepositories(
        basePackages = "com.backend.repository",
        entityManagerFactoryRef = "entityManagerFactory",
        transactionManagerRef = "transactionManager"
)
public class DataSourceConfig {

    private final JpaProperties jpaProperties;
    private final HibernateProperties hibernateProperties;
    private final List<HibernatePropertiesCustomizer> hibernatePropertiesCustomizers;

    public DataSourceConfig(JpaProperties jpaProperties,
                            HibernateProperties hibernateProperties,
                            ObjectProvider<List<HibernatePropertiesCustomizer>> hibernatePropertiesCustomizers) {
        this.jpaProperties = jpaProperties;
        this.hibernateProperties = hibernateProperties;
        this.hibernatePropertiesCustomizers = hibernatePropertiesCustomizers.getIfAvailable(ArrayList::new);
    }

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

        Map<String, Object> properties = this.hibernateProperties.determineHibernateProperties(
                this.jpaProperties.getProperties(), new HibernateSettings());
        this.hibernatePropertiesCustomizers.forEach(customizer -> customizer.customize(properties));

        return builder
                .dataSource(dataSource)
                .packages("com.backend.domain") // Member 엔티티가 있는 패키지 경로
                .persistenceUnit("main")
                .properties(properties)
                .build();
    }

    @Bean
    @Primary
    @SuppressWarnings("null")
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