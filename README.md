# Optimistic Concurrency POC

## Prerequisites

- direnv (`brew install direnv`)
- jq (`brew install jq`)
- clone repo
    ```
    git clone git@github.com:dmadouros/optimistic-poc.git
    ```
- `cd optimistic-poc`
- `direnv allow` when prompted

## Getting Started

**Note:** This will _not_ run on Macs w/ M1s 😞.

1. Start EventstoreDB
    ```bash
    docker-compose up -d
    ```
2. Start application
    ```bash
    ./gradlew run
    ```
## Concurrency

Given a User:

```text
User = {
  ID = User123
  Email = bob@example.com
}
```

When two admins are making changes to the same user at the same time, like so:

Admin #1: Reads User123 from Users
Admin #2: Reads User123 from Users
Admin #1: Sets Email = bob.jones@example.com
Admin #2: Sets Email = bob.smith@example.com

Then who's changes win?

---

w/ no locking, last in wins. Admin #1 overwrites Admin #2's changes.
Therefore final result ==
```text
User = {
  ID = User123
  Email = bob.jones@example.com
}
```

---

w/ pessimistic locking, Admin #2 is prevented from reading User123.
Admin #1: Reads User123 from Users
Admin #2: Reads User123 from Users **Error**

---

w/ optimistic locking, Admin #1 is prevented from overwriting Admin #2's changes.
Admin #1: Reads User123 from Users
Admin #2: Reads User123 from Users
Admin #1: Sets Email = bob.jones@example.com
Admin #2: Sets Email = bob.smith@example.com **Error**

Optimistic locking is implemented by including the "last position" when reading the from the message store.

---

Here it is in action.

1. Add a user
    ```bash
    curl --location --request POST 'http://localhost:8080/addUser' \
    --header 'Content-Type: application/json' \
    --data-raw '{
        "id": "5df45406-d5bf-4aaa-abf9-f48efb20d88b",
        "emailAddress": "bob@example.com"
    }'
    ```

2. Admin #1: Reads User123 from Users (current position is 0)
    ```bash
    curl --location --request GET 'http://localhost:8080/users/5df45406-d5bf-4aaa-abf9-f48efb20d88b'
    ```

3. Admin #2: Reads User123 from Users (current position is 0)
    ```bash
    curl --location --request GET 'http://localhost:8080/users/5df45406-d5bf-4aaa-abf9-f48efb20d88b'
    ```

4. Admin #1: Sets Email = bob.jones@example.com (current position is 0; submitted position is 0)
    ```bash
    curl --location --request POST 'http://localhost:8080/changeUserEmailAddressName' \
    --header 'Content-Type: application/json' \
    --data-raw '{
        "id": "5df45406-d5bf-4aaa-abf9-f48efb20d88b",
        "emailAddress": "bob.jones@example.com",
        "position": 0
    }'
    ```

5. Admin #2: Sets Email = bob.smith@example.com (current position is 1; submitted position is 0)
    ```bash
    curl --location --request POST 'http://localhost:8080/changeUserEmailAddressName' \
    --header 'Content-Type: application/json' \
    --data-raw '{
        "id": "5df45406-d5bf-4aaa-abf9-f48efb20d88b",
        "emailAddress": "bob.smith@example.com",
        "position": 0
    }'
    ```
    ****Error****

## To Reset

1. Stop the application (`Ctrl-C`)
2. Shutdown EventstoreDB
    ```bash
    docker-compose down --volumes
    ```
