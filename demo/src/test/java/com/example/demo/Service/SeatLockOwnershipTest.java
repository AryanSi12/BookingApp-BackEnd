package com.example.demo.Service;

import com.example.demo.Enums.SeatType;
import com.example.demo.Enums.Status;
import com.example.demo.Models.Booking;
import com.example.demo.Models.Event;
import com.example.demo.Models.Seat;
import com.example.demo.Models.SeatStatus;
import com.example.demo.Models.User;
import com.example.demo.Models.Venues;
import com.example.demo.Repository.BookingRepository;
import com.example.demo.Repository.SeatStatusRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.stripe.exception.ApiException;
import com.stripe.model.PaymentIntent;
import com.stripe.param.PaymentIntentCreateParams;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SeatLockOwnershipTest {

    private static final int PORT = 6397;

    private static RedisTemplate<String, Object> template;
    private static RedisService redisService;

    private final ObjectId aliceId = new ObjectId();
    private final ObjectId bobId = new ObjectId();

    @BeforeAll
    static void startRedis() throws Exception {
        if (PORT == 6379) {
            throw new IllegalStateException("refusing to run lock proofs against the shared Redis on 6379");
        }
        if (!ping()) {
            Path dir = Path.of("/tmp/seat-lock-redis");
            Files.createDirectories(dir);
            Process process = new ProcessBuilder(
                    "redis-server",
                    "--port", Integer.toString(PORT),
                    "--bind", "127.0.0.1",
                    "--daemonize", "yes",
                    "--protected-mode", "yes",
                    "--save", "",
                    "--appendonly", "no",
                    "--dir", dir.toString(),
                    "--logfile", dir.resolve("redis.log").toString())
                    .redirectErrorStream(true)
                    .start();
            int code = process.waitFor();
            if (code != 0 || !waitForPing()) {
                String log = Files.exists(dir.resolve("redis.log"))
                        ? Files.readString(dir.resolve("redis.log"))
                        : "";
                throw new IllegalStateException("redis-server on " + PORT + " exited " + code + " " + log);
            }
        }
        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration("127.0.0.1", PORT);
        LettuceConnectionFactory factory = new LettuceConnectionFactory(config);
        factory.afterPropertiesSet();
        RedisTemplate<String, Object> redis = new RedisTemplate<>();
        redis.setConnectionFactory(factory);
        redis.setKeySerializer(new StringRedisSerializer());
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        redis.setValueSerializer(new GenericJackson2JsonRedisSerializer(mapper));
        redis.afterPropertiesSet();
        template = redis;
        redisService = new RedisService(template);
    }

    @AfterEach
    void clearSecurity() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void releaseDeletesOnlyTheMatchingOwner() {
        ObjectId eventId = new ObjectId();
        String key = lockKey(eventId, "A1");
        RedisService.SeatHold alice = redisService.lockSeat(eventId, "a1", aliceId, 5);
        assertNotNull(alice);
        assertEquals("a1", alice.seatNumber());

        byte[] stored = rawGet(key);
        byte[] encoded = valueBytes(aliceId.toHexString());
        assertNotNull(stored);
        assertArrayEquals(encoded, stored);
        assertFalse(Arrays.equals(aliceId.toHexString().getBytes(StandardCharsets.UTF_8), stored));

        template.opsForValue().set(key, bobId.toHexString(), Duration.ofMinutes(5));
        alice.release();
        assertEquals(bobId.toHexString(), template.opsForValue().get(key));

        RedisService.SeatHold bob = redisService.lockSeat(eventId, "A1", bobId, 5);
        assertNotNull(bob);
        bob.release();
        assertNull(template.opsForValue().get(key));
        bob.release();
        assertNull(template.opsForValue().get(key));
    }

    @Test
    void sameUserRenewsWithoutClearingTheKey() {
        ObjectId eventId = new ObjectId();
        String key = lockKey(eventId, "A1");
        RedisService.SeatHold first = redisService.lockSeat(eventId, "A1", aliceId, 5);
        assertNotNull(first);
        template.expire(key, Duration.ofSeconds(10));
        Long shortened = template.getExpire(key);

        RedisService.SeatHold second = redisService.lockSeat(eventId, "a1", aliceId, 5);
        assertNotNull(second);
        Long renewed = template.getExpire(key);
        assertNotNull(shortened);
        assertNotNull(renewed);
        assertTrue(renewed > shortened);
        assertTrue(renewed > 60);
        assertEquals(aliceId.toHexString(), template.opsForValue().get(key));
        assertNull(template.opsForValue().get("seat-lock:" + eventId + ":a1"));
    }

    @Test
    void otherUserCannotTakeOrDeleteTheLock() {
        ObjectId eventId = new ObjectId();
        String key = lockKey(eventId, "b2");
        RedisService.SeatHold alice = redisService.lockSeat(eventId, "b2", aliceId, 5);
        assertNotNull(alice);
        assertNull(redisService.lockSeat(eventId, "B2", bobId, 5));
        assertEquals(aliceId.toHexString(), template.opsForValue().get(key));
        alice.release();
        assertNull(template.opsForValue().get(key));
    }

    @Test
    void acquireAndReleaseEachUseOneEval() {
        ObjectId eventId = new ObjectId();
        long before = evalCalls();
        RedisService.SeatHold hold = redisService.lockSeat(eventId, "C3", aliceId, 5);
        long afterAcquire = evalCalls();
        assertNotNull(hold);
        hold.release();
        long afterRelease = evalCalls();
        assertEquals(1, afterAcquire - before);
        assertEquals(1, afterRelease - afterAcquire);
    }

    @Test
    void lostRaceLeavesTheWinnerAndReleasesOnlyTheSeatThisAttemptLocked() {
        ObjectId eventId = new ObjectId();
        Event event = event(eventId);
        User alice = user(aliceId, "ada");
        BookingService booking = bookingService(event, alice);
        SeatStatusRepository seats = seatStatusRepository(booking);
        BookingRepository bookings = bookingRepository(booking);

        assertNotNull(redisService.lockSeat(event.getEventId(), "B1", bobId, 5));
        Booking request = new Booking();
        request.setBookedSeats(List.of("A1", "b1"));

        ResponseEntity<?> response = booking.bookSeat(request, eventId, List.of(
                available("A1"),
                available("B1")));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("Failed to book the tickets", response.getBody());
        assertNull(template.opsForValue().get(lockKey(eventId, "A1")));
        assertEquals(bobId.toHexString(), template.opsForValue().get(lockKey(eventId, "B1")));
        verify(seats, never()).saveAll(anyList());
        verify(bookings, never()).save(any());
    }

    @Test
    void sameUserAlreadyHoldingTheSeatCanBookWithoutClearingIt() {
        ObjectId eventId = new ObjectId();
        Event event = event(eventId);
        User alice = user(aliceId, "ada");
        BookingService booking = bookingService(event, alice);
        assertNotNull(redisService.lockSeat(event.getEventId(), "A1", aliceId, 5));

        Booking request = new Booking();
        request.setBookedSeats(List.of("a1", "A1"));
        List<SeatStatus> statuses = List.of(available("A1"), available("B1"));

        template.opsForValue().set("seatMap:" + eventId, "stale-map");
        template.opsForValue().set("allSeats" + eventId, "stale-seats");

        ResponseEntity<?> response = withIntent(() -> booking.bookSeat(request, eventId, statuses));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        assertEquals(List.of("A1"), body.get("seatsLocked"));
        assertEquals(10000L, body.get("amount"));
        assertEquals("cs_test", body.get("clientSecret"));
        assertEquals(aliceId.toHexString(), template.opsForValue().get(lockKey(eventId, "A1")));
        assertNull(template.opsForValue().get("seatMap:" + eventId));
        assertNull(template.opsForValue().get("allSeats" + eventId));
        assertEquals(Status.BOOKED, statuses.get(0).getStatus());
        assertEquals(Status.AVAILABLE, statuses.get(1).getStatus());
        assertNull(template.opsForValue().get("seat-lock:" + eventId + ":a1"));
    }

    @Test
    void stripeFailureReleasesOwnedLocksAndLeavesASeatThisAttemptDidNotTake() {
        ObjectId eventId = new ObjectId();
        Event event = event(eventId);
        User alice = user(aliceId, "ada");
        BookingService booking = bookingService(event, alice);
        SeatStatusRepository seats = seatStatusRepository(booking);
        assertNotNull(redisService.lockSeat(event.getEventId(), "C9", bobId, 5));

        Booking request = new Booking();
        request.setBookedSeats(List.of("A1", "B1"));
        List<SeatStatus> statuses = List.of(available("A1"), available("B1"));

        ResponseEntity<?> response = withStripeFailure(() -> booking.bookSeat(request, eventId, statuses));

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertTrue(String.valueOf(response.getBody()).startsWith("Payment creation failed"));
        assertNull(template.opsForValue().get(lockKey(eventId, "A1")));
        assertNull(template.opsForValue().get(lockKey(eventId, "B1")));
        assertEquals(bobId.toHexString(), template.opsForValue().get(lockKey(eventId, "C9")));
        assertEquals(Status.AVAILABLE, statuses.get(0).getStatus());
        verify(seats, never()).saveAll(anyList());
    }

    @Test
    void missingVenueSeatDoesNotLock() {
        ObjectId eventId = new ObjectId();
        Event event = event(eventId);
        event.getVenueId().setSeats(List.of(seat("A1", SeatType.VIP)));
        User alice = user(aliceId, "ada");
        BookingService booking = bookingService(event, alice);
        Booking request = new Booking();
        request.setBookedSeats(List.of("B1"));

        ResponseEntity<?> response = booking.bookSeat(request, eventId, List.of(available("B1")));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNull(template.opsForValue().get(lockKey(eventId, "B1")));
    }

    private ResponseEntity<?> withIntent(BookCall call) {
        try (var stripe = mockStatic(PaymentIntent.class)) {
            PaymentIntent intent = mock(PaymentIntent.class);
            when(intent.getClientSecret()).thenReturn("cs_test");
            stripe.when(() -> PaymentIntent.create(any(PaymentIntentCreateParams.class))).thenReturn(intent);
            return call.book();
        }
    }

    private ResponseEntity<?> withStripeFailure(BookCall call) {
        try (var stripe = mockStatic(PaymentIntent.class)) {
            stripe.when(() -> PaymentIntent.create(any(PaymentIntentCreateParams.class)))
                    .thenThrow(new ApiException("card_declined", "req_test", "card_declined", 402, null));
            return call.book();
        }
    }

    private BookingService bookingService(Event event, User user) {
        BookingService service = new BookingService();
        BookingRepository bookingRepository = mock(BookingRepository.class);
        SeatStatusRepository seatStatusRepository = mock(SeatStatusRepository.class);
        EventService eventService = mock(EventService.class);
        UserService userService = mock(UserService.class);
        ReflectionTestUtils.setField(service, "bookingRepository", bookingRepository);
        ReflectionTestUtils.setField(service, "eventService", eventService);
        ReflectionTestUtils.setField(service, "userService", userService);
        ReflectionTestUtils.setField(service, "redisService", redisService);
        ReflectionTestUtils.setField(service, "seatStatusRepository", seatStatusRepository);
        ReflectionTestUtils.setField(service, "messagingTemplate", mock(SimpMessagingTemplate.class));
        ReflectionTestUtils.setField(service, "stripeApiKey", "sk_test_lock");
        when(eventService.getEventById(event.getEventId())).thenReturn(event);
        when(userService.getCurrUser(user.getUsername())).thenReturn(user);
        when(bookingRepository.save(any(Booking.class))).thenAnswer(invocation -> invocation.getArgument(0));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user.getUsername(), "n/a", List.of()));
        return service;
    }

    private SeatStatusRepository seatStatusRepository(BookingService service) {
        return (SeatStatusRepository) ReflectionTestUtils.getField(service, "seatStatusRepository");
    }

    private BookingRepository bookingRepository(BookingService service) {
        return (BookingRepository) ReflectionTestUtils.getField(service, "bookingRepository");
    }

    private Event event(ObjectId eventId) {
        Venues venue = new Venues();
        venue.setSeats(List.of(seat("A1", SeatType.VIP), seat("B1", SeatType.REGULAR)));
        Event event = new Event();
        event.setEventId(eventId);
        event.setVenueId(venue);
        return event;
    }

    private User user(ObjectId userId, String username) {
        User user = new User();
        user.setUserId(userId);
        user.setUsername(username);
        return user;
    }

    private Seat seat(String number, SeatType type) {
        Seat seat = new Seat();
        seat.setSeatNumber(number);
        seat.setSeatType(type);
        return seat;
    }

    private SeatStatus available(String number) {
        SeatStatus status = new SeatStatus();
        status.setSeatNumber(number);
        status.setStatus(Status.AVAILABLE);
        return status;
    }

    private String lockKey(ObjectId eventId, String seatNumber) {
        return "seat-lock:" + eventId + ":" + seatNumber.toUpperCase(java.util.Locale.ROOT);
    }

    private byte[] valueBytes(String value) {
        @SuppressWarnings("unchecked")
        RedisSerializer<Object> serializer = (RedisSerializer<Object>) template.getValueSerializer();
        return serializer.serialize(value);
    }

    private byte[] rawGet(String key) {
        byte[] keyBytes = new StringRedisSerializer().serialize(key);
        return template.execute((RedisCallback<byte[]>) connection -> connection.stringCommands().get(keyBytes));
    }

    private long evalCalls() {
        Properties info = template.execute((RedisCallback<Properties>) connection -> connection.serverCommands().info("commandstats"));
        if (info == null) {
            return 0L;
        }
        String line = info.getProperty("cmdstat_eval");
        if (line == null) {
            return 0L;
        }
        for (String part : line.split(",")) {
            if (part.startsWith("calls=")) {
                return Long.parseLong(part.substring("calls=".length()));
            }
        }
        return 0L;
    }

    private static boolean ping() throws Exception {
        Process process = new ProcessBuilder("redis-cli", "-p", Integer.toString(PORT), "ping")
                .redirectErrorStream(true)
                .start();
        String out = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        process.waitFor();
        return out.contains("PONG");
    }

    private static boolean waitForPing() throws Exception {
        for (int i = 0; i < 20; i++) {
            if (ping()) {
                return true;
            }
            Thread.sleep(100);
        }
        return false;
    }

    @FunctionalInterface
    private interface BookCall {
        ResponseEntity<?> book();
    }
}
