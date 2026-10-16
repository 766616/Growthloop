"""Skill dependency graph (NetworkX). Edge prerequisite -> skill."""
from typing import Dict, Iterable, List, Set

import networkx as nx

from .text import norm


class SkillGraph:
    def __init__(self, catalog: List[dict]):
        self.g = nx.DiGraph()
        self.by_id: Dict[int, dict] = {}
        self.by_name: Dict[str, dict] = {}
        for s in catalog:
            self.g.add_node(s["name"], **s)
            if s.get("id") is not None:
                self.by_id[s["id"]] = s
            self.by_name[norm(s["name"])] = s
        for s in catalog:
            for pid in s.get("prerequisite_ids") or []:
                p = self.by_id.get(pid)
                if p and p["name"] != s["name"]:
                    self.g.add_edge(p["name"], s["name"])
            for pname in s.get("prerequisites") or []:
                p = self.by_name.get(norm(pname))
                if p and p["name"] != s["name"]:
                    self.g.add_edge(p["name"], s["name"])
        # Defensive: a bad admin edit must not break everything - drop cycle edges.
        while True:
            try:
                cycle = nx.find_cycle(self.g)
                self.g.remove_edge(*cycle[-1][:2])
            except nx.NetworkXNoCycle:
                break

    def canonical(self, name: str) -> str | None:
        s = self.by_name.get(norm(name))
        return s["name"] if s else None

    def prerequisites(self, skill: str) -> List[str]:
        return sorted(self.g.predecessors(skill)) if skill in self.g else []

    def ancestors(self, skill: str) -> Set[str]:
        return set(nx.ancestors(self.g, skill)) if skill in self.g else set()

    def implied_known(self, known: Iterable[str]) -> Set[str]:
        """If you know Spring Boot you necessarily know its prerequisites' basics."""
        out: Set[str] = set()
        for k in known:
            out |= self.ancestors(k)
        return out

    def learning_order(self, targets: Iterable[str], known: Set[str], priority: Dict[str, float]) -> List[str]:
        """Targets + their missing ancestors, topologically ordered, ties broken by priority."""
        needed: Set[str] = set()
        for t in targets:
            if t not in self.g:
                continue
            needed.add(t)
            needed |= {a for a in self.ancestors(t) if a not in known}
        needed -= known
        sub = self.g.subgraph(needed)
        key = lambda n: f"{9999 - int(priority.get(n, 0) * 10):05d}{n}"
        return list(nx.lexicographical_topological_sort(sub, key=key))

    def is_ready(self, skill: str, known: Set[str]) -> bool:
        return all(p in known for p in self.prerequisites(skill))

    def to_dict(self) -> dict:
        return {"nodes": list(self.g.nodes), "edges": [list(e) for e in self.g.edges]}
