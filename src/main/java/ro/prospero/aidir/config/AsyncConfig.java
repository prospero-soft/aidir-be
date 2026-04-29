package ro.prospero.aidir.config;


import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

@Configuration
@EnableAsync
public class AsyncConfig {
    public static final String METRICS_EXECUTOR = "metricsEventExecutor";
    public static final String LOYALTY_EXECUTOR = "loyaltyPointEventExecutor";
    public static final String SEARCH_EXECUTOR = "searchEventExecutor";


    @Bean(METRICS_EXECUTOR)
    public Executor metricsExecutor() {
        ThreadFactory virtualThreadFactory = Thread.ofVirtual().name("metrics-event-executor").factory();
        return Executors.newThreadPerTaskExecutor(virtualThreadFactory);
    }

    @Bean(LOYALTY_EXECUTOR)
    public Executor loyaltyPointExecutor() {
        ThreadFactory virtualThreadFactory = Thread.ofVirtual().name("loyalty-point-event-executor").factory();
        return Executors.newThreadPerTaskExecutor(virtualThreadFactory);
    }

    @Bean(SEARCH_EXECUTOR)
    public Executor searchExecutor() {
        //todo: might not make much sense for this to be a virtual thread factory,
        // since we won't be IO-bound on index update operations, at least while the index lives entirely in-memory
        ThreadFactory virtualThreadFactory = Thread.ofVirtual().name("search-index-change-event-executor").factory();
        return Executors.newThreadPerTaskExecutor(virtualThreadFactory);
    }
}
