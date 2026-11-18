from __future__ import annotations

import math
import re
from collections import Counter
from collections.abc import Iterable, Sequence

WORD = re.compile(r"[a-z0-9]+(?:[+#]+|(?:[._'][a-z0-9]+)*)")
STOP_WORDS = frozenset("""
a about above after again all also am an and any are as at be because been before being below between both but by
can could did do does doing done down during each either few for from further had has have having he her here hers
him his how i if in into is it its itself just me more most my no nor not now of off on once only or other our out
over own same she should so some such than that the their them then there these they this those through to too
under until up very was we were what when where which while who whom why will with would you your
explain describe define tell give show mean means meaning please example examples
""".split())
K1 = 1.2
B = 0.75
RRF_K = 60


def terms(text: str) -> list[str]:
    words = WORD.findall(text.lower())
    pairs = [f"{first} {second}" for first, second in zip(words, words[1:], strict=False)]
    return words + pairs


def query_terms(question: str) -> list[str]:
    capitals = {word.lower() for word in re.findall(r"\b[A-Z][A-Z0-9_]+\b", question)}

    def meaningful(word: str) -> bool:
        return word not in STOP_WORDS or word in capitals

    found = []
    for term in dict.fromkeys(terms(question)):
        if any(meaningful(part) for part in term.split(" ")):
            found.append(term)
    return found


def bm25(documents: Sequence[str], question: str) -> list[float]:
    wanted = query_terms(question)
    if not wanted or not documents:
        return [0.0] * len(documents)
    counted = [Counter(terms(document)) for document in documents]
    lengths = [sum(counts.values()) for counts in counted]
    average = (sum(lengths) / len(lengths)) or 1.0
    total = len(documents)
    scores = []
    for counts, length in zip(counted, lengths, strict=True):
        score = 0.0
        for term in wanted:
            frequency = counts.get(term, 0)
            if not frequency:
                continue
            having = sum(1 for other in counted if term in other)
            idf = math.log(1 + (total - having + 0.5) / (having + 0.5))
            score += idf * frequency * (K1 + 1) / (frequency + K1 * (1 - B + B * length / average))
        scores.append(score)
    return scores


def fuse(*rankings: Iterable[int]) -> list[int]:
    fused: dict[int, float] = {}
    for ranking in rankings:
        for rank, item in enumerate(ranking, start=1):
            fused[item] = fused.get(item, 0.0) + 1.0 / (RRF_K + rank)
    return sorted(fused, key=lambda item: (-fused[item], item))
