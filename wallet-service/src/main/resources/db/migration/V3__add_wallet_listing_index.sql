/*
 * The wallet listing endpoint filters wallets by account_id
 * and sorts them by created_at.
 *
 * This composite index supports that access pattern so PostgreSQL
 * can efficiently locate an account's wallets in creation-time order.
 */
 CREATE INDEX idx_wallet_account_created_at
 ON wallet (account_id, created_at DESC );