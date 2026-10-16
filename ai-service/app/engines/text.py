"""Shared text helpers: normalisation + TF-IDF cosine similarity."""
import re
from typing import List

import numpy as np
from sklearn.feature_extraction.text import TfidfVectorizer
from sklearn.metrics.pairwise import cosine_similarity

LEVELS = {"BEGINNER": 1, "INTERMEDIATE": 2, "ADVANCED": 3}
LEVEL_NAMES = {v: k for k, v in LEVELS.items()}


def norm(s: str) -> str:
    return re.sub(r"[^a-z0-9+#]+", " ", (s or "").lower()).strip()


def level_value(level: str | None) -> int:
    return LEVELS.get((level or "BEGINNER").upper(), 1)


def similarity_matrix(a: List[str], b: List[str], analyzer: str = "char_wb") -> np.ndarray:
    """Cosine similarity between every string in a and every string in b."""
    if not a or not b:
        return np.zeros((len(a), len(b)))
    corpus = [norm(x) or "_" for x in a + b]
    if analyzer == "char_wb":
        vec = TfidfVectorizer(analyzer="char_wb", ngram_range=(2, 4))
    else:
        vec = TfidfVectorizer(analyzer="word", stop_words="english", token_pattern=r"[a-z0-9+#]{2,}")
    try:
        m = vec.fit_transform(corpus)
    except ValueError:  # empty vocabulary
        return np.zeros((len(a), len(b)))
    return cosine_similarity(m[: len(a)], m[len(a):])


def blended_similarity(names_a, texts_a, names_b, texts_b) -> np.ndarray:
    """Character n-gram similarity on names blended with word similarity on descriptions."""
    s_char = similarity_matrix(names_a, names_b, "char_wb")
    s_word = similarity_matrix(texts_a, texts_b, "word")
    # Names like "MySQL" have no description, so never let the word part drag the name match down.
    return np.maximum(s_char, 0.6 * s_char + 0.4 * s_word)
