# 07 · HNSW（构图与搜索）

HNSW（Hierarchical Navigable Small World）是稠密向量近似最近邻的主流图索引。mini-es 实现于 `mini-es-vector` 的 `HnswVectorIndex`；参数对齐常见 ES 默认：

- \(M = 16\)：每层每个节点最大出边数（底层常为 \(2M\)）
- `efConstruction`：构图时动态候选集大小（越大越准、越慢）
- `efSearch`：查询时候选集大小（ES `num_candidates` 与之相关）

## 公式 / 模型

多层图 \(G_0,G_1,\ldots,G_L\)，层 \(0\) 含全部向量；上层是稀疏「高速公路」。

插入时随机层号（几何分布）：

\[
\ell \sim \lfloor -\ln U \cdot \mathrm{ml} \rfloor,\quad U\sim\mathrm{Unif}(0,1)
\]

距离常用余弦或内积（归一化向量时等价）。查询目标：找 \(\arg\min_{v} d(q,v)\) 的近似 Top-K。

邻居选择：在候选集中保留最多 \(M\) 条边（可用启发式多样性选择，不仅取最近 \(M\)）。

## 伪代码

```
# 构图插入
function insert(q, efConstruction, M):
  ℓ ← randomLevel()
  ep ← enterPoint
  for lc from L down to ℓ+1:
    ep ← greedySearch(q, ep, ef=1, layer=lc)   # 单贪婪
  for lc from min(L, ℓ) down to 0:
    W ← searchLayer(q, ep, efConstruction, lc)
    neighbors ← selectNeighbors(W, M)          # 底层可用 2M
    link q ↔ neighbors on layer lc
    ep ← W.nearest

# 搜索
function knn(q, k, efSearch):
  ep ← enterPoint
  for lc from L down to 1:
    ep ← greedySearch(q, ep, ef=1, lc)
  W ← searchLayer(q, ep, max(efSearch, k), layer=0)
  return k nearest in W
```

`searchLayer`：优先队列扩展未访问邻居，维持大小 `ef` 的候选。

## 复杂度

| 阶段 | 典型成本（经验） |
|------|------------------|
| 插入 | \(O(\mathrm{efConstruction}\cdot M\cdot \log N)\) 距离计算量级 |
| 查询 | \(O(\mathrm{efSearch}\cdot M\cdot \log N)\) |
| 内存 | 向量 \(N\cdot d\cdot 4\) 字节 + 图边约 \(O(N\cdot M)\) |

增大 `efSearch` / `efConstruction` 提高召回，延迟与内存上升。\(M=16\) 是精度与图膨胀的常用折中。

## mini-es 落点

- 模块：`mini-es-vector`
- 预期 API：`HnswGraph.index(vector)` / `search(query, k, efSearch)`
- 与关键词路并行召回后交给 RRF（见 [08-rrf-hybrid.md](08-rrf-hybrid.md)）

## 「真实 ES / Lucene」对照

| 概念 | Lucene / ES |
|------|-------------|
| HNSW 图 | Lucene `HnswGraph` / `HnswGraphBuilder` |
| 向量字段 | ES `dense_vector`（`index: true`, `similarity: cosine|dot_product|l2_norm`） |
| `m` | mapping `index_options.m`（默认常为 16） |
| `efConstruction` | `index_options.ef_construction` |
| 查询 `k` / `num_candidates` | ES `knn` 查询；`num_candidates` ≈ 搜索宽度（类 `efSearch`） |
| 底层文件 | `.vec` / `.vex` / `.vem` 等（版本相关） |

ES 8.x 示例：

```json
"embedding": {
  "type": "dense_vector",
  "dims": 768,
  "index": true,
  "similarity": "cosine",
  "index_options": { "type": "hnsw", "m": 16, "ef_construction": 100 }
}
```
