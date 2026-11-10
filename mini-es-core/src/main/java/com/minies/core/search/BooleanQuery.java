package com.minies.core.search;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Boolean combination of clauses.
 *
 * <ul>
 *   <li>{@link Occur#MUST}: intersection — a doc must match every MUST clause; scores are summed</li>
 *   <li>{@link Occur#SHOULD}: union — scores are summed; if MUST clauses exist, SHOULD only
 *       contributes bonus scores to docs already in the MUST intersection</li>
 * </ul>
 */
public final class BooleanQuery implements Query {

    public enum Occur {
        MUST,
        SHOULD
    }

    public static final class Clause {
        private final Query query;
        private final Occur occur;

        public Clause(Query query, Occur occur) {
            this.query = query;
            this.occur = occur;
        }

        public Query query() {
            return query;
        }

        public Occur occur() {
            return occur;
        }
    }

    public static final class Builder {
        private final List<Clause> clauses = new ArrayList<>();

        public Builder must(Query query) {
            clauses.add(new Clause(query, Occur.MUST));
            return this;
        }

        public Builder should(Query query) {
            clauses.add(new Clause(query, Occur.SHOULD));
            return this;
        }

        public BooleanQuery build() {
            if (clauses.isEmpty()) {
                throw new IllegalStateException("BooleanQuery needs at least one clause");
            }
            return new BooleanQuery(List.copyOf(clauses));
        }
    }

    private final List<Clause> clauses;

    public BooleanQuery(List<Clause> clauses) {
        this.clauses = List.copyOf(clauses);
    }

    public List<Clause> clauses() {
        return clauses;
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public void accumulateScores(IndexSearcher searcher, Map<String, Float> scores) {
        List<Clause> must = new ArrayList<>();
        List<Clause> should = new ArrayList<>();
        for (Clause c : clauses) {
            if (c.occur() == Occur.MUST) {
                must.add(c);
            } else {
                should.add(c);
            }
        }

        Map<String, Float> result = new HashMap<>();

        if (!must.isEmpty()) {
            // Intersection of MUST clause hits, summing their scores.
            Set<String> intersection = null;
            Map<String, Float> mustScores = new HashMap<>();

            for (Clause clause : must) {
                Map<String, Float> clauseScores = new HashMap<>();
                clause.query().accumulateScores(searcher, clauseScores);
                if (intersection == null) {
                    intersection = new HashSet<>(clauseScores.keySet());
                } else {
                    intersection.retainAll(clauseScores.keySet());
                }
                for (Map.Entry<String, Float> e : clauseScores.entrySet()) {
                    mustScores.merge(e.getKey(), e.getValue(), Float::sum);
                }
            }

            if (intersection == null || intersection.isEmpty()) {
                return;
            }

            for (String docId : intersection) {
                result.put(docId, mustScores.getOrDefault(docId, 0f));
            }

            // SHOULD clauses add bonus scores only for docs already matching all MUST.
            for (Clause clause : should) {
                Map<String, Float> clauseScores = new HashMap<>();
                clause.query().accumulateScores(searcher, clauseScores);
                for (Map.Entry<String, Float> e : clauseScores.entrySet()) {
                    if (result.containsKey(e.getKey())) {
                        result.merge(e.getKey(), e.getValue(), Float::sum);
                    }
                }
            }
        } else {
            // Pure SHOULD: union with score sum.
            for (Clause clause : should) {
                clause.query().accumulateScores(searcher, result);
            }
        }

        for (Map.Entry<String, Float> e : result.entrySet()) {
            scores.merge(e.getKey(), e.getValue(), Float::sum);
        }
    }

    @Override
    public String toString() {
        return "BooleanQuery{clauses=" + clauses.size() + "}";
    }
}
