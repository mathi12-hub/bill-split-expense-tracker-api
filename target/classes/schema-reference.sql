-- ============================================================
-- Bill Split & Expense Tracker — Reference DDL
-- (Hibernate auto-generates this via ddl-auto=update, but this
--  file documents the schema explicitly and can be run manually.)
-- ============================================================

CREATE DATABASE IF NOT EXISTS billsplit_db;
USE billsplit_db;

-- ---------------------------------------------------------
-- users
-- ---------------------------------------------------------
CREATE TABLE IF NOT EXISTS users (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    name        VARCHAR(100)  NOT NULL,
    email       VARCHAR(150)  NOT NULL UNIQUE,
    created_at  DATETIME      NOT NULL
);

-- ---------------------------------------------------------
-- groups
-- ---------------------------------------------------------
CREATE TABLE IF NOT EXISTS groups (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    name        VARCHAR(150)  NOT NULL,
    created_at  DATETIME      NOT NULL
);

-- ---------------------------------------------------------
-- group_members (many-to-many: users <-> groups)
-- ---------------------------------------------------------
CREATE TABLE IF NOT EXISTS group_members (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    group_id    BIGINT   NOT NULL,
    user_id     BIGINT   NOT NULL,
    joined_at   DATETIME NOT NULL,
    CONSTRAINT fk_gm_group FOREIGN KEY (group_id) REFERENCES groups(id) ON DELETE CASCADE,
    CONSTRAINT fk_gm_user  FOREIGN KEY (user_id)  REFERENCES users(id)  ON DELETE CASCADE,
    CONSTRAINT uq_group_user UNIQUE (group_id, user_id)
);

-- ---------------------------------------------------------
-- expenses
-- ---------------------------------------------------------
CREATE TABLE IF NOT EXISTS expenses (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    group_id          BIGINT         NOT NULL,
    paid_by_user_id   BIGINT         NOT NULL,
    description       VARCHAR(255)   NOT NULL,
    amount            DECIMAL(12,2)  NOT NULL,
    created_at        DATETIME       NOT NULL,
    CONSTRAINT fk_expense_group FOREIGN KEY (group_id) REFERENCES groups(id) ON DELETE CASCADE,
    CONSTRAINT fk_expense_payer FOREIGN KEY (paid_by_user_id) REFERENCES users(id)
);

-- ---------------------------------------------------------
-- expense_splits (how each expense is divided among users)
-- ---------------------------------------------------------
CREATE TABLE IF NOT EXISTS expense_splits (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    expense_id     BIGINT        NOT NULL,
    user_id        BIGINT        NOT NULL,
    share_amount   DECIMAL(12,2) NOT NULL,
    CONSTRAINT fk_split_expense FOREIGN KEY (expense_id) REFERENCES expenses(id) ON DELETE CASCADE,
    CONSTRAINT fk_split_user    FOREIGN KEY (user_id)    REFERENCES users(id),
    CONSTRAINT uq_expense_user  UNIQUE (expense_id, user_id)
);

-- ---------------------------------------------------------
-- Helpful indexes for balance-calculation joins
-- ---------------------------------------------------------
CREATE INDEX idx_expenses_group        ON expenses(group_id);
CREATE INDEX idx_expenses_payer        ON expenses(paid_by_user_id);
CREATE INDEX idx_splits_user           ON expense_splits(user_id);
CREATE INDEX idx_splits_expense        ON expense_splits(expense_id);

-- ============================================================
-- The core balance query (also implemented in
-- ExpenseSplitRepository.calculateGroupBalances):
--
-- For each member of a group, compute:
--   total_paid  = SUM(expenses.amount)      where they are the payer
--   total_owed  = SUM(expense_splits.share) allocated to them
--   net_balance = total_paid - total_owed
--
-- net_balance > 0  => the group owes this person money
-- net_balance < 0  => this person owes the group money
-- ============================================================
-- SELECT
--     u.id, u.name,
--     COALESCE(paid.total_paid, 0) AS total_paid,
--     COALESCE(owed.total_owed, 0) AS total_owed,
--     COALESCE(paid.total_paid, 0) - COALESCE(owed.total_owed, 0) AS net_balance
-- FROM group_members gm
-- JOIN users u ON u.id = gm.user_id
-- LEFT JOIN (
--     SELECT paid_by_user_id AS user_id, SUM(amount) AS total_paid
--     FROM expenses WHERE group_id = ? GROUP BY paid_by_user_id
-- ) paid ON paid.user_id = u.id
-- LEFT JOIN (
--     SELECT es.user_id, SUM(es.share_amount) AS total_owed
--     FROM expense_splits es
--     JOIN expenses e ON e.id = es.expense_id
--     WHERE e.group_id = ? GROUP BY es.user_id
-- ) owed ON owed.user_id = u.id
-- WHERE gm.group_id = ?
-- ORDER BY net_balance DESC;
