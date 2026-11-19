-- Candidatos experimentais. Não remover índices de PK ou UNIQUE.
CREATE INDEX idx_events_starts_at ON events USING btree (starts_at);
CREATE INDEX idx_events_venue_starts ON events USING btree (venue_id, starts_at);
CREATE INDEX idx_registrations_event_id ON registrations USING btree (event_id);
-- UNIQUE(user_id, event_id) já fornece um B-tree iniciado por user_id.
