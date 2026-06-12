package com.aicampus.ai.service.knowledge;

import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import javax.sql.DataSource;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.mapper.MapperFactoryBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;

@Configuration
@ConditionalOnExpression("'${ai.knowledge.persistence.enabled:false}' == 'true' && '${spring.datasource.url:}' != ''")
public class KnowledgeBasePersistenceAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean(DataSource.class)
    public DataSource knowledgeDataSource(Environment environment) {
        DataSourceBuilder<?> builder = DataSourceBuilder.create()
                .url(environment.getRequiredProperty("spring.datasource.url"))
                .username(environment.getProperty("spring.datasource.username", "root"))
                .password(environment.getProperty("spring.datasource.password", ""));
        String driverClassName = environment.getProperty("spring.datasource.driver-class-name");
        builder.driverClassName(StringUtils.hasText(driverClassName) ? driverClassName : "com.mysql.cj.jdbc.Driver");
        return builder.build();
    }

    @Bean
    @ConditionalOnMissingBean(SqlSessionFactory.class)
    public SqlSessionFactory knowledgeSqlSessionFactory(DataSource dataSource) throws Exception {
        MybatisSqlSessionFactoryBean factoryBean = new MybatisSqlSessionFactoryBean();
        factoryBean.setDataSource(dataSource);
        return factoryBean.getObject();
    }

    @Bean
    @ConditionalOnMissingBean(KnowledgeDocumentMapper.class)
    public MapperFactoryBean<KnowledgeDocumentMapper> knowledgeDocumentMapper(SqlSessionFactory sqlSessionFactory) {
        MapperFactoryBean<KnowledgeDocumentMapper> factoryBean =
                new MapperFactoryBean<>(KnowledgeDocumentMapper.class);
        factoryBean.setSqlSessionFactory(sqlSessionFactory);
        return factoryBean;
    }

    @Bean
    @ConditionalOnMissingBean(KnowledgeChunkMapper.class)
    public MapperFactoryBean<KnowledgeChunkMapper> knowledgeChunkMapper(SqlSessionFactory sqlSessionFactory) {
        MapperFactoryBean<KnowledgeChunkMapper> factoryBean =
                new MapperFactoryBean<>(KnowledgeChunkMapper.class);
        factoryBean.setSqlSessionFactory(sqlSessionFactory);
        return factoryBean;
    }

    @Bean
    @ConditionalOnMissingBean(KnowledgeIngestionJobMapper.class)
    public MapperFactoryBean<KnowledgeIngestionJobMapper> knowledgeIngestionJobMapper(SqlSessionFactory sqlSessionFactory) {
        MapperFactoryBean<KnowledgeIngestionJobMapper> factoryBean =
                new MapperFactoryBean<>(KnowledgeIngestionJobMapper.class);
        factoryBean.setSqlSessionFactory(sqlSessionFactory);
        return factoryBean;
    }
}
