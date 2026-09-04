/*
 * Copyright 2026 ekwongchum
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.ekwong.journalarchiver.config;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean(name = "journalProcessingExecutor")
    public Executor journalProcessingExecutor(AppProperties properties) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(properties.getProcessing().getCorePoolSize());
        executor.setMaxPoolSize(properties.getProcessing().getMaxPoolSize());
        executor.setQueueCapacity(properties.getProcessing().getQueueCapacity());
        executor.setThreadNamePrefix("journal-processing-");
        // when the queue is full, run the archive inline in the SMTP handler
        // thread instead of dropping the message with a broken connection
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }

    @Bean(name = "resendExecutor")
    public Executor resendExecutor(AppProperties properties) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(properties.getResend().getParallelism());
        executor.setMaxPoolSize(properties.getResend().getParallelism());
        executor.setQueueCapacity(properties.getResend().getQueueCapacity());
        executor.setThreadNamePrefix("resend-");
        // when the queue is full, run the resend inline in the calling thread
        // instead of failing the HTTP request
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}
