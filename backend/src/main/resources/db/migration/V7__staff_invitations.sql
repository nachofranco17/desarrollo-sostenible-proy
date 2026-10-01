ALTER TABLE membresia ADD COLUMN invitacion_hash VARCHAR(64);
ALTER TABLE membresia ADD COLUMN invitacion_expira TIMESTAMP WITH TIME ZONE;
CREATE UNIQUE INDEX ix_membresia_invitacion ON membresia(invitacion_hash);
