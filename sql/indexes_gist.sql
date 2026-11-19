CREATE INDEX idx_events_period_gist ON events USING gist (period);
