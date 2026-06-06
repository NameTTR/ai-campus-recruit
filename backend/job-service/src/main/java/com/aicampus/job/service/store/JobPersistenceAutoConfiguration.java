package com.aicampus.job.service.store;

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
@ConditionalOnExpression("'${job.persistence.enabled:false}' == 'true' && '${spring.datasource.url:}' != ''")
public class JobPersistenceAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean(DataSource.class)
    public DataSource jobDataSource(Environment environment) {
        DataSourceBuilder<?> builder = DataSourceBuilder.create()
                .url(environment.getRequiredProperty("spring.datasource.url"))
                .username(environment.getProperty("spring.datasource.username", "root"))
                .password(environment.getProperty("spring.datasource.password", ""));
        String driverClassName = environment.getProperty("spring.datasource.driver-class-name");
        if (StringUtils.hasText(driverClassName)) {
            builder.driverClassName(driverClassName);
        } else {
            builder.driverClassName("com.mysql.cj.jdbc.Driver");
        }
        return builder.build();
    }

    @Bean
    @ConditionalOnMissingBean(SqlSessionFactory.class)
    public SqlSessionFactory jobSqlSessionFactory(DataSource dataSource) throws Exception {
        MybatisSqlSessionFactoryBean factoryBean = new MybatisSqlSessionFactoryBean();
        factoryBean.setDataSource(dataSource);
        return factoryBean.getObject();
    }

    @Bean
    @ConditionalOnMissingBean(JobRecordMapper.class)
    public MapperFactoryBean<JobRecordMapper> jobRecordMapper(SqlSessionFactory sqlSessionFactory) {
        MapperFactoryBean<JobRecordMapper> factoryBean = new MapperFactoryBean<>(JobRecordMapper.class);
        factoryBean.setSqlSessionFactory(sqlSessionFactory);
        return factoryBean;
    }
}
