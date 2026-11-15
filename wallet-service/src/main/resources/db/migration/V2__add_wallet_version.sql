/*
 * Add the optimistic-locking version column required by BaseEntity.
 *
 * Existing wallets receive version 0 so the new NOT NULL column
 * can be introduced safely without invalidating existing rows.
 */
 ALTER TABLE wallet
 ADD COLUMN version BIGINT NOT NULL DEFAULT  0;