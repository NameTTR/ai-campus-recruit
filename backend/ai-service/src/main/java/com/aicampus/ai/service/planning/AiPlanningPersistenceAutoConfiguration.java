package com.aicampus.ai.service.planning;

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
@ConditionalOnExpression("'${ai.planning.persistence.enabled:false}' == 'true' && '${spring.datasource.url:}' != ''")
public class AiPlanningPersistenceAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean(DataSource.class)
    public DataSource aiPlanningDataSource(Environment environment) {
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
    public SqlSessionFactory aiPlanningSqlSessionFactory(DataSource dataSource) throws Exception {
        MybatisSqlSessionFactoryBean factoryBean = new MybatisSqlSessionFactoryBean();
        factoryBean.setDataSource(dataSource);
        return factoryBean.getObject();
    }

    @Bean
    @ConditionalOnMissingBean(AiPlanningRecordMapper.class)
    public MapperFactoryBean<AiPlanningRecordMapper> aiPlanningRecordMapper(SqlSessionFactory sqlSessionFactory) {
        MapperFactoryBean<AiPlanningRecordMapper> factoryBean =
                new MapperFactoryBean<>(AiPlanningRecordMapper.class);
        factoryBean.setSqlSessionFactory(sqlSessionFactory);
        return factoryBean;
    }
}
