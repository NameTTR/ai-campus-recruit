package com.aicampus.ai.service.core;

import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.zaxxer.hikari.HikariDataSource;
import javax.sql.DataSource;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
@ConditionalOnProperty(prefix = "ai.core.persistence", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(DataSourceProperties.class)
@MapperScan(
        basePackageClasses = LearningPlanMapper.class,
        annotationClass = Mapper.class,
        sqlSessionTemplateRef = "aiCoreSqlSessionTemplate")
public class CoreDataSourceConfiguration {
    @Bean
    @Primary
    @ConditionalOnMissingBean(DataSource.class)
    @ConfigurationProperties("spring.datasource.hikari")
    public DataSource aiCoreDataSource(DataSourceProperties properties) {
        return properties.initializeDataSourceBuilder().type(HikariDataSource.class).build();
    }

    @Bean
    @Primary
    @ConditionalOnMissingBean(name = "aiCoreSqlSessionFactory")
    public SqlSessionFactory aiCoreSqlSessionFactory(DataSource aiCoreDataSource) throws Exception {
        MybatisSqlSessionFactoryBean factory = new MybatisSqlSessionFactoryBean();
        factory.setDataSource(aiCoreDataSource);
        return factory.getObject();
    }

    @Bean
    @ConditionalOnMissingBean(name = "aiCoreSqlSessionTemplate")
    public SqlSessionTemplate aiCoreSqlSessionTemplate(SqlSessionFactory aiCoreSqlSessionFactory) {
        return new SqlSessionTemplate(aiCoreSqlSessionFactory);
    }

    @Bean
    @Primary
    @ConditionalOnMissingBean(name = "aiCoreTransactionManager")
    public PlatformTransactionManager aiCoreTransactionManager(DataSource aiCoreDataSource) {
        return new DataSourceTransactionManager(aiCoreDataSource);
    }
}
