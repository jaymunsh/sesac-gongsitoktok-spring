/*
 * 상대 경로: src/main/java/com/gongsitoktok/assistant/GongsiTokTokkAssistantApplication.java
 */
package com.gongsitoktok.assistant;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * 공시톡톡(gongsitoktok) 어시스턴트 백엔드 진입점.
 *
 * <h3>핵심 동작 모델</h3>
 * <ul>
 *     <li>Java 21 가상 스레드(Virtual Threads) 활성 — {@code spring.threads.virtual.enabled=true}.
 *         서블릿 요청, 가상 스레드 풀 기반 {@code @Async} 작업, WebClient {@code .block()} 모두
 *         carrier thread 점유 없이 동작.</li>
 *     <li>{@link EnableAsync} 로 비동기 메서드 인식 — 영속화({@code ChatPersistenceService}) 가
 *         별도 빈에서 fire-and-forget 으로 처리.</li>
 *     <li>{@code Hooks.enableAutomaticContextPropagation()} 은 {@code ReactorContextConfig} 에서
 *         {@code @PostConstruct} 로 활성 — WebClient 리액티브 체인의 MDC 전파 자동화.</li>
 * </ul>
 *
 * <p>본 클래스는 골격만 담당. 모든 비즈니스 빈은 컴포넌트 스캔으로 자동 등록.</p>
 */
@SpringBootApplication
@EnableAsync
public class GongsiTokTokAssistantApplication {

    /**
     * 애플리케이션 부팅 진입점.
     *
     * @param args JVM args (Gradle bootRun 의 {@code -Djdk.tracePinnedThreads=short} 포함)
     */
    public static void main(String[] args) {
        SpringApplication.run(GongsiTokTokAssistantApplication.class, args);
    }
}
