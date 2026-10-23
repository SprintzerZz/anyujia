package com.anyujia.anyujiachatservice.sharding;

import io.seata.rm.datasource.DataSourceProxy;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Wraps the auto-configured {@code dataSource} bean in a Seata {@link DataSourceProxy} <em>in
 * place</em>, keeping its bean name and qualifier so MyBatis/JdbcTemplate keep resolving it.
 *
 * <p>Declared as a top-level {@code @Component} (rather than a {@code static @Bean} inside a
 * conditional {@code @Configuration}) so Spring's {@code registerBeanPostProcessors} reliably picks
 * it up by type before any {@code DataSource} is autowired into consumers.
 */
@Component
@ConditionalOnProperty(prefix = "chat.message-sharding", name = "physical-enabled", havingValue = "true")
public class SeataMainDataSourceProxyProcessor implements BeanPostProcessor {

    private static final Logger log = LoggerFactory.getLogger(SeataMainDataSourceProxyProcessor.class);

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) {
        if (!"dataSource".equals(beanName)) {
            return bean;
        }
        if (!(bean instanceof DataSource)) {
            log.warn("seata proxy: bean 'dataSource' is not a DataSource, got {}; skipping Seata wrap",
                    bean == null ? "null" : bean.getClass().getName());
            return bean;
        }
        if (bean instanceof DataSourceProxy) {
            log.info("seata proxy: bean 'dataSource' already a DataSourceProxy, leaving as-is");
            return bean;
        }
        DataSourceProxy proxied = new DataSourceProxy((DataSource) bean);
        log.info("seata proxy: wrapped main dataSource {} in DataSourceProxy -> {}", bean.getClass().getName(),
                proxied.getClass().getName());
        return proxied;
    }
}
