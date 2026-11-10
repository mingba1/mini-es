# mini-es 算法笔记

本目录用中文梳理教育版 mini-es 的核心算法，并对照真实 Elasticsearch / Lucene 中的类与行为。

**约定参数（与代码默认一致）**

| 参数 | 值 | 说明 |
|------|-----|------|
| BM25 `k1` | `1.2` | 词频饱和 |
| BM25 `b` | `0.6` | 文档长度归一 |
| 字段 boost | `title=3`, `content=1` | 索引/查询默认同步 |
| HNSW `m` | `16` | 每层最大出边数 |
| RRF `k` | `60` | 排名常数 `rank_constant` |

**模块对应**

- `mini-es-core`：分析、倒排、BM25、布尔检索、段合并、NRT/translog
- `mini-es-vector`：HNSW / 暴力向量、RRF、`HybridEngine`
- `mini-es-cluster`：分片路由、主副本 failover、scatter-gather

## 目录

| 文档 | 主题 |
|------|------|
| [01-analysis.md](01-analysis.md) | 分析链 Tokenizer / TokenFilter |
| [02-inverted-index.md](02-inverted-index.md) | 倒排 postings |
| [03-bm25.md](03-bm25.md) | BM25 公式与 Lucene 风格 IDF |
| [04-boolean-scoring.md](04-boolean-scoring.md) | MUST / SHOULD 交集并集 |
| [05-segment-merge.md](05-segment-merge.md) | 不可变段 + TieredMerge 简化 |
| [06-nrt-refresh-translog.md](06-nrt-refresh-translog.md) | refresh / flush / translog |
| [07-hnsw.md](07-hnsw.md) | HNSW 构图与搜索 |
| [08-rrf-hybrid.md](08-rrf-hybrid.md) | RRF 混合检索与降级 |
| [09-sharding-routing.md](09-sharding-routing.md) | 路由哈希与主副本 |
| [10-scatter-gather.md](10-scatter-gather.md) | 协调节点归并 Top-N |

阅读顺序建议：`01 → 02 → 03 → 04`，再读段生命周期 `05 → 06`，向量与混合检索 `07 → 08`，集群侧 `09 → 10`。
