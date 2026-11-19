-- Usado exclusivamente por benchmark.sql em banco descartável.
VACUUM (ANALYZE) users;
VACUUM (ANALYZE) venues;
VACUUM (ANALYZE) events;
VACUUM (ANALYZE) registrations;
VACUUM (ANALYZE) ratings;
VACUUM (ANALYZE) audit_log;
BEGIN;
DROP INDEX IF EXISTS idx_events_starts_at, idx_events_venue_starts,
    idx_registrations_event_id, idx_events_period_gist,
    idx_registrations_event_hash, idx_registrations_user_id;
