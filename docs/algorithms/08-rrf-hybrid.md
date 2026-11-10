# 08 · RRF 混合检索

混合检索 = **BM25 关键词路** + **HNSW 向量路**，再用 **倒数排名融合（RRF）** 合成一路结果。默认：

- RRF 常数 \(k = 60\)（ES `rank_constant`）
- BM25：`k1=1.2`, `b=0.6`；title boost `3`
- HNSW：`m=16`

## 公式

对每个文档 \(d\)，在各路结果中的名次为 \(\mathrm{rank}_i(d)\)（从 1 起；未进入窗口则不计）：

\[
\mathrm{RRF}(d) = \sum_{i\in\{\mathrm{bm25},\mathrm{knn}\}}
  \frac{1}{k + \mathrm{rank}_i(d)}
\]

窗口：每路只取 Top-\(W\)（ES `window_size` / 应用层截断），再按 \(\mathrm{RRF}\) 降序取最终 Top-\(N\)。

### 为何不用分数加权求和？

\[
\alpha\cdot\mathrm{BM25} + (1-\alpha)\cdot\mathrm{cos}
\]

问题：

1. **量纲不同**：BM25 常在 \(0\sim 20+\)，余弦在 \([0,1]\)。
2. \(\alpha\) 依赖语料与查询，换数据易失效。
3. RRF 只依赖名次，工业界混合检索的稳定默认。

加权求和仅作对照实验，不作主路径。

## 伪代码

```
function rrf(bm25Hits, knnHits, k=60, window=W, size=N):
  score ← {}
  for leg in [bm25Hits, knnHits]:
    for rank, hit in enumerate(leg[:window], start=1):
      score[hit.id] += 1.0 / (k + rank)
  return topN(score, size)

function hybrid(query):
  try:
    qvec ← embed(query.text)
    knn ← hnsw.search(qvec, k=window, efSearch=...)
  catch EmbeddingError | VectorUnavailable:
    return bm25Only(query)          # 降级
  bm25 ← lexicalSearch(query)       # multi_match, title^3
  return rrf(bm25, knn, k=60)
```

## 降级策略

| 场景 | 行为 |
|------|------|
| Embedding 服务失败 / 超时 | 仅 BM25 |
| 无向量字段 / 索引未建 HNSW | 仅 BM25 |
| 向量路空结果 | RRF 退化为单路名次 |
| ES &lt; 8.8 无原生 RRF | 应用层按上式合并 |

## 复杂度

| 步骤 | 成本 |
|------|------|
| 两路并行召回 | \(\approx \max(T_{\mathrm{bm25}}, T_{\mathrm{knn}})\) |
| RRF 合并 | \(O(W\log W)\) |
| Embedding | 通常主导延迟 |

## mini-es 落点

- 关键词：`mini-es-core`（已实现 BM25 + MultiMatch）
- 向量：`mini-es-vector` HNSW
- 融合：集群/服务层 `RrfFusion`（`k=60`）
- 降级开关：向量失败 → 纯 BM25

## 「真实 ES / Lucene」对照

| mini-es / 方案 | Elasticsearch |
|----------------|---------------|
| 应用层 RRF | ES 7.x 或通用客户端合并 |
| 原生 RRF | 8.8+ `sub_searches` + `rank.rrf`；8.16+ `retriever` |
| `k=60` | `rank_constant: 60` |
| 双路 | `multi_match` + `knn` / `standard`+`knn` retriever |
| 分数加权对照 | `knn.boost` 或应用层线性融合（不推荐主路径） |

```json
"rank": { "rrf": { "window_size": 50, "rank_constant": 60 } }
```
