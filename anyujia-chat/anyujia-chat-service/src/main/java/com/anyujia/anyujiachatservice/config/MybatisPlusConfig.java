package com.anyujia.anyujiachatservice.config;

import com.anyujia.anyujiachatservice.sharding.ChatMessageShardContext;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.DynamicTableNameInnerInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MybatisPlusConfig {
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        DynamicTableNameInnerInterceptor dynamicTables = new DynamicTableNameInnerInterceptor();
        dynamicTables.setTableNameHandler((sql, tableName) -> {
            if (!"chat_message".equalsIgnoreCase(tableName)) {
                return tableName;
            }
            String selected = ChatMessageShardContext.currentTable();
            return selected == null ? tableName : selected;
        });
        interceptor.addInnerInterceptor(dynamicTables);
        return interceptor;
    }
}
