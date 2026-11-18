from __future__ import annotations

from dataclasses import dataclass

COURSE_KEY = "e0e0e0e0e0e0e0e0e0e0e0e0e0e0e0e0"
FINGERPRINT = "e" * 64

TOPICS: dict[int, tuple[str, str]] = {
    1: ("Relational model", "A relation is a table of rows and columns. Each row is a tuple and each column an "
        "attribute. A primary key uniquely identifies every row, and a foreign key refers to a key in another table."),
    2: ("Normalization", "# First normal form\nEvery value is atomic: no repeating groups or lists in a cell.\n"
        "# Second normal form\nNo non-key column depends on only part of a composite key.\n"
        "# Third normal form\n3NF removes transitive dependencies, where a non-key column depends on another "
        "non-key column. BCNF is a stricter version of 3NF."),
    3: ("SQL aggregation", "Aggregate functions such as COUNT, SUM, and AVG summarise rows. GROUP BY puts rows with "
        "the same values into groups, and HAVING filters those groups after aggregation, unlike WHERE."),
    4: ("Joins", "An INNER JOIN returns rows with matches in both tables. A LEFT OUTER JOIN keeps every row of the left "
        "table and fills missing matches with NULL. A self join joins a table to itself."),
    5: ("Indexes", "A B-tree index keeps keys sorted so lookups, range scans, and ORDER BY are fast. Indexes speed up "
        "reads but slow down inserts and updates, because each index must be maintained."),
    6: ("Transactions", "A transaction is a unit of work with the ACID properties: atomicity, consistency, isolation, "
        "and durability. COMMIT makes changes permanent and ROLLBACK undoes them."),
    7: ("Concurrency control", "Two-phase locking takes all locks before releasing any. Isolation levels such as READ "
        "COMMITTED and REPEATABLE READ trade consistency for concurrency. A deadlock is two transactions waiting for "
        "each other's locks."),
}


@dataclass(frozen=True)
class Question:
    text: str
    expected: frozenset[int]


QUESTIONS: tuple[Question, ...] = (
    Question("What is a primary key?", frozenset({1})),
    Question("How does a foreign key work?", frozenset({1})),
    Question("What does 3NF remove?", frozenset({2})),
    Question("Is BCNF stricter than third normal form?", frozenset({2})),
    Question("What does first normal form require of values?", frozenset({2})),
    Question("When do I use HAVING instead of WHERE?", frozenset({3})),
    Question("What does GROUP BY do?", frozenset({3})),
    Question("Which rows does a LEFT OUTER JOIN keep?", frozenset({4})),
    Question("What is a self join?", frozenset({4})),
    Question("Why do indexes slow down inserts?", frozenset({5})),
    Question("How does a B-tree help range scans?", frozenset({5})),
    Question("What are the ACID properties?", frozenset({6})),
    Question("What does ROLLBACK do?", frozenset({6})),
    Question("What is two-phase locking?", frozenset({7})),
    Question("What causes a deadlock?", frozenset({7})),
    Question("Who won the football world cup in 1998?", frozenset()),
    Question("What is the capital city of Australia?", frozenset()),
)


def sources() -> list[dict]:
    return [{"sourceType": "TOPIC", "sourceId": topic_id, "topicId": topic_id, "title": title, "text": text}
            for topic_id, (title, text) in TOPICS.items()]
