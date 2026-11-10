package com.minies.vector;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Educational HNSW (Hierarchical Navigable Small World) for cosine on L2 vectors.
 *
 * <p>Defaults mirror common ES dense_vector knn settings: {@code m=16},
 * {@code efConstruction} / {@code efSearch} configurable.
 */
public final class HnswVectorIndex implements VectorIndex {

    private final int dims;
    private final int m;
    private final int mMax0;
    private final int efConstruction;
    private final int efSearch;
    private final double levelMult;
    private final Random random;

    private final List<Node> nodes = new ArrayList<>();
    private int entryPoint = -1;
    private int maxLevel = -1;

    public HnswVectorIndex(int dims) {
        this(dims, 16, 100, 50);
    }

    public HnswVectorIndex(int dims, int m, int efConstruction, int efSearch) {
        if (dims <= 0 || m <= 0 || efConstruction <= 0 || efSearch <= 0) {
            throw new IllegalArgumentException("invalid HNSW params");
        }
        this.dims = dims;
        this.m = m;
        this.mMax0 = m * 2;
        this.efConstruction = Math.max(efConstruction, m);
        this.efSearch = efSearch;
        this.levelMult = 1.0 / Math.log(m);
        this.random = ThreadLocalRandom.current();
    }

    @Override
    public synchronized void add(String docId, float[] vector) {
        if (vector.length != dims) {
            throw new IllegalArgumentException("expected dims=" + dims);
        }
        float[] copy = vector.clone();
        int level = randomLevel();
        Node node = new Node(docId, copy, level);
        int nodeId = nodes.size();
        nodes.add(node);

        if (entryPoint < 0) {
            entryPoint = nodeId;
            maxLevel = level;
            return;
        }

        int curr = entryPoint;
        for (int lc = maxLevel; lc > level; lc--) {
            curr = greedySearchLayer(copy, curr, lc);
        }

        for (int lc = Math.min(level, maxLevel); lc >= 0; lc--) {
            List<Neighbor> candidates = searchLayer(copy, curr, efConstruction, lc);
            int mMax = lc == 0 ? mMax0 : m;
            List<Neighbor> selected = selectNeighbors(candidates, m);
            for (Neighbor nb : selected) {
                connect(nodeId, nb.id, lc);
                connect(nb.id, nodeId, lc);
                shrink(nb.id, lc, mMax);
            }
            if (!candidates.isEmpty()) {
                curr = candidates.get(0).id;
            }
        }

        if (level > maxLevel) {
            entryPoint = nodeId;
            maxLevel = level;
        }
    }

    @Override
    public synchronized List<VectorHit> search(float[] query, int k) {
        if (query.length != dims) {
            throw new IllegalArgumentException("query dims mismatch");
        }
        if (k <= 0 || entryPoint < 0) {
            return List.of();
        }
        int curr = entryPoint;
        for (int lc = maxLevel; lc > 0; lc--) {
            curr = greedySearchLayer(query, curr, lc);
        }
        List<Neighbor> candidates = searchLayer(query, curr, Math.max(efSearch, k), 0);
        candidates.sort(Comparator.comparingDouble((Neighbor n) -> n.distance));
        int limit = Math.min(k, candidates.size());
        List<VectorHit> hits = new ArrayList<>(limit);
        for (int i = 0; i < limit; i++) {
            Neighbor n = candidates.get(i);
            float score = 1.0f - n.distance; // distance = 1 - cosine for unit vectors
            hits.add(new VectorHit(nodes.get(n.id).docId, score, i + 1));
        }
        return hits;
    }

    @Override
    public synchronized int size() {
        return nodes.size();
    }

    private int randomLevel() {
        double r = random.nextDouble();
        int level = (int) Math.floor(-Math.log(r) * levelMult);
        return Math.max(0, level);
    }

    private float distance(float[] a, float[] b) {
        return 1.0f - Vectors.cosine(a, b);
    }

    private int greedySearchLayer(float[] query, int enter, int layer) {
        int curr = enter;
        float currDist = distance(query, nodes.get(curr).vector);
        boolean changed = true;
        while (changed) {
            changed = false;
            for (int nb : nodes.get(curr).neighborsAt(layer)) {
                float d = distance(query, nodes.get(nb).vector);
                if (d < currDist) {
                    currDist = d;
                    curr = nb;
                    changed = true;
                }
            }
        }
        return curr;
    }

    private List<Neighbor> searchLayer(float[] query, int enter, int ef, int layer) {
        Set<Integer> visited = new HashSet<>();
        PriorityQueue<Neighbor> candidates = new PriorityQueue<>(Comparator.comparingDouble(n -> n.distance));
        PriorityQueue<Neighbor> w = new PriorityQueue<>((a, b) -> Float.compare(b.distance, a.distance));

        float enterDist = distance(query, nodes.get(enter).vector);
        Neighbor enterNb = new Neighbor(enter, enterDist);
        candidates.add(enterNb);
        w.add(enterNb);
        visited.add(enter);

        while (!candidates.isEmpty()) {
            Neighbor c = candidates.poll();
            Neighbor f = w.peek();
            if (f != null && c.distance > f.distance) {
                break;
            }
            for (int nb : nodes.get(c.id).neighborsAt(layer)) {
                if (!visited.add(nb)) {
                    continue;
                }
                float d = distance(query, nodes.get(nb).vector);
                Neighbor nn = new Neighbor(nb, d);
                if (w.size() < ef || d < w.peek().distance) {
                    candidates.add(nn);
                    w.add(nn);
                    if (w.size() > ef) {
                        w.poll();
                    }
                }
            }
        }
        return new ArrayList<>(w);
    }

    private List<Neighbor> selectNeighbors(List<Neighbor> candidates, int mLimit) {
        candidates.sort(Comparator.comparingDouble(n -> n.distance));
        int limit = Math.min(mLimit, candidates.size());
        return new ArrayList<>(candidates.subList(0, limit));
    }

    private void connect(int from, int to, int layer) {
        nodes.get(from).neighborsAt(layer).add(to);
    }

    private void shrink(int nodeId, int layer, int mMax) {
        List<Integer> nbs = nodes.get(nodeId).neighborsAt(layer);
        if (nbs.size() <= mMax) {
            return;
        }
        float[] vec = nodes.get(nodeId).vector;
        List<Neighbor> scored = new ArrayList<>(nbs.size());
        for (int nb : nbs) {
            scored.add(new Neighbor(nb, distance(vec, nodes.get(nb).vector)));
        }
        scored.sort(Comparator.comparingDouble(n -> n.distance));
        nbs.clear();
        for (int i = 0; i < mMax; i++) {
            nbs.add(scored.get(i).id);
        }
    }

    private static final class Node {
        final String docId;
        final float[] vector;
        final List<List<Integer>> links;

        Node(String docId, float[] vector, int level) {
            this.docId = docId;
            this.vector = vector;
            this.links = new ArrayList<>(level + 1);
            for (int i = 0; i <= level; i++) {
                links.add(new ArrayList<>());
            }
        }

        List<Integer> neighborsAt(int layer) {
            while (links.size() <= layer) {
                links.add(new ArrayList<>());
            }
            return links.get(layer);
        }
    }

    private static final class Neighbor {
        final int id;
        final float distance;

        Neighbor(int id, float distance) {
            this.id = id;
            this.distance = distance;
        }
    }
}
