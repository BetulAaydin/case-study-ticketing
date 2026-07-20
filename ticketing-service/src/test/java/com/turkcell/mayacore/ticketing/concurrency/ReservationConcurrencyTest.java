package com.turkcell.mayacore.ticketing.concurrency;

import com.turkcell.mayacore.ticketing.domain.Event;
import com.turkcell.mayacore.ticketing.repository.EventRepository;
import com.turkcell.mayacore.ticketing.security.SessionRoleResolver;
import com.turkcell.mayacore.ticketing.service.ReservationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ReservationConcurrencyTest {

    @Autowired
    private ReservationService reservationService;

    @Autowired
    private EventRepository eventRepository;

    @MockitoBean
    private StringRedisTemplate stringRedisTemplate;

    @MockitoBean
    private SessionRoleResolver sessionRoleResolver;

    @Test
    void concurrentReservations_shouldNotOversell() throws InterruptedException {
        Event event = new Event();
        event.setOwnerId(1L);
        event.setTitle("Sold Out Race");
        event.setVenue("Arena");
        event.setStartsAt(LocalDateTime.now().plusDays(2));
        event.setEndsAt(LocalDateTime.now().plusDays(2).plusHours(3));
        event.setCapacity(5);
        event.setReservedSeats(0);
        event.setPublished(true);
        event = eventRepository.saveAndFlush(event);
        Long eventId = event.getId();

        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            long userId = 100L + i;
            executor.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                    reservationService.create(userId, eventId, 1, "127.0.0.1", "concurrency-test");
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failCount.incrementAndGet();
                }
            });
        }

        ready.await(5, TimeUnit.SECONDS);
        start.countDown();
        executor.shutdown();
        assertThat(executor.awaitTermination(30, TimeUnit.SECONDS)).isTrue();

        assertThat(successCount.get()).isLessThanOrEqualTo(5);
        assertThat(successCount.get() + failCount.get()).isEqualTo(threadCount);

        Event updated = eventRepository.findById(eventId).orElseThrow();
        assertThat(updated.getReservedSeats()).isLessThanOrEqualTo(updated.getCapacity());
        assertThat(updated.getReservedSeats()).isEqualTo(successCount.get());
    }
}
