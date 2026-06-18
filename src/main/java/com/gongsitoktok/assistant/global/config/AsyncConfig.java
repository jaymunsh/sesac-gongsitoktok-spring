/*
 * 상대 경로: src/main/java/com/gongsitoktok/assistant/global/config/AsyncConfig.java
 */
package com.gongsitoktok.assistant.global.config;

import com.gongsitoktok.assistant.GongsiTokTokAssistantApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * 비동기 실행기 설정 — 챗봇 영속화 전용 가상 스레드 풀.
 *
 * <h3>설계 결정</h3>
 * <ul>
 *     <li><b>가상 스레드 채택</b>: 영속화는 본질적으로 IO 대기 작업 (JDBC INSERT). 플랫폼 풀로 운용하면 동시성 폭주 시
 *         worker 스레드 고갈이 발생하지만, 가상 스레드는 carrier 점유 없이 무수히 생성·소멸할 수 있다.</li>
 *     <li><b>{@link SimpleAsyncTaskExecutor} + {@code setVirtualThreads(true)}</b>: Spring 6.1+ 제공.
 *         TaskDecorator 를 지원하므로 {@link MdcTaskDecorator} 결선이 가능하다.</li>
 *     <li><b>스레드 이름 prefix {@code chat-persist-}</b>: 스레드 덤프·프로파일러에서 영속화 작업을 즉시 식별.</li>
 *     <li><b>Bean 이름 {@code chatPersistenceExecutor}</b>: {@code @Async("chatPersistenceExecutor")} 로 명시 지정.</li>
 * </ul>
 *
 * <h3>Self-Invocation 함정</h3>
 * <p>{@code @Async} + {@code @Transactional} 은 Spring AOP 프록시 기반이라 같은 클래스 self-invocation 시
 * 프록시가 우회되어 둘 다 동작하지 않는다. 따라서 {@code ChatPersistenceService} 는 반드시 별도 빈으로 분리되어 있어야 하며,
 * 본 설정 클래스는 그 풀을 제공하는 역할만 담당한다.</p>
 *
 * @see GongsiTokTokAssistantApplication
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    /**
     * 챗봇 영속화 전용 가상 스레드 실행기.
     *
     * <p>{@code SimpleAsyncTaskExecutor + virtualThreads=true} 는 매 작업마다 새 가상 스레드를 spawn 한다.
     * 가상 스레드는 OS 자원이 아니라 JVM heap 의 객체이므로 동시 작업 수가 늘어도 carrier(=플랫폼) 스레드 풀에
     * 부담을 주지 않는다.</p>
     */
    @Bean("chatPersistenceExecutor")
    public AsyncTaskExecutor chatPersistenceExecutor() {
        SimpleAsyncTaskExecutor executor = new SimpleAsyncTaskExecutor("chat-persist-");
        executor.setVirtualThreads(true);
        executor.setTaskDecorator(new MdcTaskDecorator());
        // TODO(monitoring): MeterRegistry 주입 후 executor.active / executor.completed 게이지 노출
        return executor;
    }
}
