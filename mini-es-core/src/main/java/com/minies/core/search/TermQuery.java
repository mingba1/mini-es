package com.minies.core.search;

import com.minies.core.index.InMemorySegment;
import com.minies.core.index.Posting;
import com.minies.core.similarity.BM25Similarity;

import java.util.List;
import java.util.Map;

/**
 * Match a single term in a single field; score = BM25 * fieldBoost * queryBoost.
 */
public final class TermQuery implements Query {

    private final String field;
    private final String term;
    private final float boost;

    public TermQuery(String field, String term) {
        this(field, term, 1.0f);
    }

    public TermQuery(String field, String term, float boost) {
        this.field = field;
        this.term = term;
        this.boost = boost;
    }

    public String field() {
        return field;
    }

    public String term() {
        return term;
    }

    public float boost() {
        return boost;
    }

    @Override
    public void accumulateScores(IndexSearcher searcher, Map<String, Float> scores) {
        BM25Similarity similarity = searcher.similarity();
        for (InMemorySegment segment : searcher.segments()) {
            List<Posting> postings = segment.postings(field, term);
            if (postings.isEmpty()) {
                continue;
            }

            int docFreq = postings.size();
            int docCount = segment.numDocs();
            double idf = similarity.idf(docFreq, docCount);
            double avgdl = segment.avgFieldLength(field);
            float fieldBoost = segment.fieldBoost(field);

            for (Posting posting : postings) {
                int docLen = segment.fieldLength(posting.docOrd(), field);
                double bm25 = similarity.score(posting.freq(), docLen, avgdl, idf);
                float contribution = (float) (bm25 * fieldBoost * boost);
                String docId = segment.docId(posting.docOrd());
                scores.merge(docId, contribution, Float::sum);
            }
        }
    }

    @Override
    public String toString() {
        return "TermQuery{field='" + field + "', term='" + term + "', boost=" + boost + "}";
    }
}
