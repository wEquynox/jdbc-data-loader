package br.com.database;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class AppTest {
    @Test
    void defaultsMatchAssignment() {
        LoadConfig c = LoadConfig.fromArgs(new String[0]);
        assertEquals(5_000_000, c.users);
        assertEquals(100_000, c.events);
        assertEquals(10_000_000, c.registrations);
        assertEquals(1_000_000, c.comments);
        assertEquals(1_000_000, c.ratings);
        assertEquals(42, c.seed);
        assertFalse(c.reset);
        assertFalse(c.noAudit);
    }

    @Test
    void acceptsSmallLoadAndSeed() {
        LoadConfig c = LoadConfig.fromArgs(new String[]{"--users=100", "--venues=2",
            "--events=10", "--registrations=1_000", "--comments=0", "--ratings=0",
            "--batch=7", "--seed=17", "--past-ratio=0"});
        assertEquals(1000, c.registrations);
        assertEquals(17, c.seed);
        assertEquals(7, c.batchSize);
    }

    @ParameterizedTest
    @ValueSource(strings = {"--users", "--users=", "--users=0", "--batch=0",
        "--comments=-1", "--past-ratio=NaN", "--past-ratio=Infinity",
        "--past-ratio=1.1", "--past-ratio=0", "--registrations=0",
        "--ratings=10000001", "--reset=false", "--no-audit=false", "--unknown=1"})
    void rejectsInvalidArguments(String arg) {
        assertThrows(IllegalArgumentException.class,
            () -> LoadConfig.fromArgs(new String[]{arg}));
    }

    @Test
    void rejectsMorePairsThanPossible() {
        assertThrows(IllegalArgumentException.class, () -> LoadConfig.fromArgs(new String[]{
            "--users=1", "--events=1", "--registrations=2", "--ratings=0"}));
    }
}
