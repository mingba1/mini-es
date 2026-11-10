# 10 · Scatter-Gather 与协调节点 Top-N

分布式搜索由 **协调节点（coordinating node）** 扇出（scatter）到各分片，再归并（gather）全局 Top-N。这与单机多段归并同一思想，只是网络边界换成了 shard。

## 公式 / 模型

索引有 \(P\) 个主分片（每片选一个活跃拷贝查询）。请求 `size=N`、`from=0` 时：

1. 每片返回本地 Top-\(N\)（实际常取 `N`，有 `from` 时取 `from+N`）。
2. 协调节点合并 \(P\cdot N\) 个候选，按分数（或 RRF 分）取全局 Top-\(N\)。

\[
\mathrm{GlobalTopN}
  = \mathrm{TopN}\Bigl(\bigcup_{s=1}^{P} \mathrm{LocalTopN}(s)\Bigr)
\]

该过程 **不能** 保证全局精确序以外的命中都被看到：未进入任一片 LocalTopN 的文档会被裁掉——这是分布式 Top-N 的标准近似/正确性边界（在「每片返回深度足够」时，对纯排序 Top-N 是正确的）。

`track_total_hits` 时另聚合各片 `hits.total`。

## 伪代码

```
function search(index, query, N):
  shards ← routingTable.activeCopies(index)   # 每 primary 选 1 拷贝
  futures ← []
  for shard in shards:                        # scatter
    futures.add(async shard.search(query, from+N))
  partials ← waitAll(futures)                 # gather
  merged ← []
  for p in partials:
    merged.extend(p.hits)
    total += p.totalHits
  sort merged by score desc, tie-break id
  return merged[:N], total

# 混合检索：可每片先本地 BM25+knn+RRF，再协调合并；
# 或协调节点分别合并两路——教学推荐「每片本地 RRF，再按 RRF 分归并」。
```

## 复杂度

| 步骤 | 成本 |
|------|------|
| Scatter | \(O(P)\) 网络请求（可并行） |
| 每片本地搜 | 同单机 BM25 / HNSW |
| Gather 归并 | \(O(P\cdot N\log(P\cdot N))\) |
| 瓶颈 | 最慢分片（尾延迟）；\(P\) 过大时协调合并与线程池压力上升 |

带 `from=F` 的深分页会使每片取 \(F+N\)，代价线性涨；生产多用 `search_after`。

## mini-es 落点

- 模块：`mini-es-cluster` + `mini-es-server`
- `CoordinatingNode.search`：扇出到多个 `InternalEngine` / shard
- 归并 `ScoreDoc`：分数降序，`docId` 平局打破（与 `IndexSearcher` 一致）
- 单分片时退化为本地 `IndexSearcher.search`

## 「真实 ES / Lucene」对照

| mini-es / 概念 | Elasticsearch |
|----------------|---------------|
| 协调节点 | 处理 REST 的节点；`TransportSearchAction` |
| Scatter | `SearchPhase`：Query phase 发到各 shard |
| Gather Top-N | `QueryPhase` 结果归并；Fetch phase 再取 `_source` |
| 每片 Top-N | Lucene `IndexSearcher.search(query, N)` |
| 偏好 | `preference=_local|_primary|...` |
| 减少扇出 | 请求带 `routing` 只打相关分片 |
| DFS 模式 | `dfs_query_then_fetch` 预聚合 DF（更准 IDF，更贵） |

与本项目其它章节的关系：

- 单片内：分析 → 倒排 → BM25 / HNSW → 布尔或 RRF
- 跨片：本篇 scatter-gather
- 写入路径选片： [09-sharding-routing.md](09-sharding-routing.md)
