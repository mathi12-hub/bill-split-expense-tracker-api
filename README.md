# Bill Split & Expense Tracker API

A Spring Boot REST API for managing group expenses, tracking participants, and calculating
real-time balances between users — like a mini Splitwise backend.

## Tech Stack
- Java 17
- Spring Boot 3.3 (Web, Data JPA, Validation)
- MySQL 8
- Maven
- Lombok

## Features
- User & group management (many-to-many membership)
- Add expenses with **equal splits** or **exact custom splits**
- Real-time balance calculation via a single **SQL aggregation query** (LEFT JOIN + GROUP BY)
- Automatic **settlement plan** (minimum number of payments to clear all debts) using a
  greedy cash-flow-minimization algorithm
- Centralized validation & error handling (clean JSON error responses)

## Database Schema (ERD)

```
users                group_members              groups
+----+-------+       +----+----------+----+      +----+-------+
| id | name  |<---+  | id | group_id | id  |----->| id | name  |
|    | email |    +--| user_id       |     |      |    |       |
+----+-------+       +---------------+     |      +----+-------+
                                            |
expenses                                   |      expense_splits
+----+----------+------------+--------+    |      +----+------------+---------+--------------+
| id | group_id | paid_by_id | amount |<---+      | id | expense_id | user_id | share_amount |
+----+----------+------------+--------+           +----+------------+---------+--------------+
```

- `users` 1—* `group_members` *—1 `groups` (many-to-many via join table)
- `groups` 1—* `expenses`
- `users` 1—* `expenses` (as payer)
- `expenses` 1—* `expense_splits` *—1 `users` (how the expense is divided)

See `src/main/resources/schema-reference.sql` for full DDL. Hibernate auto-creates
this schema on startup (`ddl-auto=update`).

## Setup

1. **Create a MySQL database** (or let the app auto-create it):
   ```sql
   CREATE DATABASE billsplit_db;
   ```

2. **Configure credentials** in `src/main/resources/application.properties`:
   ```properties
   spring.datasource.username=root
   spring.datasource.password=your_mysql_password
   ```

3. **Run the app**:
   ```bash
   mvn spring-boot:run
   ```
   API available at `http://localhost:8080`

## API Reference

### Users
| Method | Endpoint          | Description       |
|--------|-------------------|--------------------|
| POST   | `/api/users`      | Create a user      |
| GET    | `/api/users`      | List all users     |
| GET    | `/api/users/{id}` | Get a user         |
| DELETE | `/api/users/{id}` | Delete a user      |

### Groups
| Method | Endpoint                    | Description              |
|--------|------------------------------|--------------------------|
| POST   | `/api/groups`                | Create a group + members |
| GET    | `/api/groups`                | List all groups          |
| GET    | `/api/groups/{id}`           | Get a group + members    |
| POST   | `/api/groups/{id}/members`   | Add a member to a group  |

### Expenses
| Method | Endpoint                          | Description                 |
|--------|------------------------------------|------------------------------|
| POST   | `/api/groups/{groupId}/expenses`  | Add an expense (with splits) |
| GET    | `/api/groups/{groupId}/expenses`  | List a group's expenses      |
| GET    | `/api/expenses/{id}`              | Get one expense              |
| DELETE | `/api/expenses/{id}`              | Delete an expense            |

### Balances
| Method | Endpoint                             | Description                          |
|--------|----------------------------------------|---------------------------------------|
| GET    | `/api/groups/{groupId}/balances`     | Net balance per user (paid − owed)   |
| GET    | `/api/groups/{groupId}/settlements`  | Minimal who-pays-whom settlement plan |

## Sample Walkthrough

**1. Create users**
```bash
curl -X POST localhost:8080/api/users -H "Content-Type: application/json" \
  -d '{"name":"Alice","email":"alice@example.com"}'

curl -X POST localhost:8080/api/users -H "Content-Type: application/json" \
  -d '{"name":"Bob","email":"bob@example.com"}'

curl -X POST localhost:8080/api/users -H "Content-Type: application/json" \
  -d '{"name":"Charlie","email":"charlie@example.com"}'
```

**2. Create a group** (assume Alice=1, Bob=2, Charlie=3)
```bash
curl -X POST localhost:8080/api/groups -H "Content-Type: application/json" \
  -d '{"name":"Goa Trip","memberUserIds":[1,2,3]}'
```

**3. Add an expense, split equally** (Alice pays ₹900 for dinner, split 3 ways)
```bash
curl -X POST localhost:8080/api/groups/1/expenses -H "Content-Type: application/json" \
  -d '{"paidByUserId":1,"description":"Dinner","amount":900.00,"splitEqually":true}'
```

**4. Add an expense with exact custom splits**
```bash
curl -X POST localhost:8080/api/groups/1/expenses -H "Content-Type: application/json" \
  -d '{
        "paidByUserId":2,
        "description":"Cab fare",
        "amount":500.00,
        "splits":[
          {"userId":1,"shareAmount":200.00},
          {"userId":2,"shareAmount":150.00},
          {"userId":3,"shareAmount":150.00}
        ]
      }'
```

**5. Check balances**
```bash
curl localhost:8080/api/groups/1/balances
```
```json
[
  { "userId": 1, "userName": "Alice",   "totalPaid": 900.00, "totalOwed": 500.00, "netBalance": 400.00 },
  { "userId": 2, "userName": "Bob",     "totalPaid": 500.00, "totalOwed": 450.00, "netBalance": 50.00 },
  { "userId": 3, "userName": "Charlie", "totalPaid": 0.00,   "totalOwed": 450.00, "netBalance": -450.00 }
]
```

**6. Get the settlement plan** (minimum transactions to settle up)
```bash
curl localhost:8080/api/groups/1/settlements
```
```json
[
  { "fromUserId": 3, "fromUserName": "Charlie", "toUserId": 1, "toUserName": "Alice", "amount": 400.00 },
  { "fromUserId": 3, "fromUserName": "Charlie", "toUserId": 2, "toUserName": "Bob",   "amount": 50.00 }
]
```

## Design Notes
- **Money as `BigDecimal`/`DECIMAL(12,2)`** everywhere — never floats — to avoid rounding errors.
- **Equal-split remainder handling**: when an amount doesn't divide evenly, leftover cents
  are assigned to the payer so splits always sum exactly to the total.
- **Balance query** runs as one native SQL query with two `LEFT JOIN` subqueries
  (paid vs. owed), aggregated with `GROUP BY`, instead of pulling all rows into Java and
  summing in memory — keeps it fast as expense volume grows.
- **Settlement algorithm** is a greedy max-creditor/max-debtor matching (priority queues),
  which minimizes the number of payments needed, same idea used by real-world split apps.
