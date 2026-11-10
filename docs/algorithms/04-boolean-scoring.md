# 04 · 布尔打分（MUST / SHOULD）

布尔查询组合多个子句。mini-es 实现两种 occur：

- **MUST**：交集 — 文档必须命中全部 MUST；分数相加
- **SHOULD**：并集或加分 — 无 MUST 时为并集；有 MUST 时只给已在交集内的文档加分

真实 Lucene 用 **有序 docId 游标**（`DocIdSetIterator`）做跳表交集/并集；mini-es 用哈希集合模拟同一语义。

## 公式 / 模型

设 MUST 子句命中集为 \(M_1,\ldots,M_m\)，SHOULD 为 \(S_1,\ldots,S_s\)。

有 MUST 时：

\[
D = \bigcap_{i=1}^{m} M_i,\quad
\mathrm{score}(d) = \sum_{i:\,d\in M_i}\mathrm{score}_i(d)
  + \sum_{j:\,d\in S_j}\mathrm{score}_j(d)\quad(d\in D)
\]

仅 SHOULD 时：

\[
D = \bigcup_{j} S_j,\quad
\mathrm{score}(d) = \sum_{j:\,d\in S_j}\mathrm{score}_j(d)
\]

`minimum_should_match`、MUST_NOT 未实现。

## 伪代码（游标思想）

```
# Lucene 风格：各子句 posting 游标按 docId 递增
function intersectMust(cursors):
  # 推进最落后的游标，或 advance 到当前候选
  while all cursors active:
    if all point to same docId d:
      yield d; advance all
    else:
      advance lagging cursors toward max(docId)

# mini-es 等价实现：
intersection ← null
mustScores ← {}
for each MUST clause:
  hits ← clause.accumulateScores()
  intersection ← hits.keys if first else intersection ∩ hits.keys
  mustScores.merge(hits, +)
result ← { d → mustScores[d] | d in intersection }
for each SHOULD clause:           # 仅加分，不扩大集合
  for (d, s) in clause.hits:
    if d in result: result[d] += s
```

## 复杂度

| 实现 | 交集 | 说明 |
|------|------|------|
| 哈希 `retainAll`（mini-es） | \(O(\sum |M_i|)\) | 教学清晰 |
| 有序游标 + skipTo（Lucene） | 近 \(O(\sum \mathit{df}_i / \mathrm{skip})\) | 稀有词驱动 |

纯 SHOULD 并集：\(O(\sum |S_j|)\)。

## mini-es 落点

- `BooleanQuery.Occur.MUST / SHOULD`
- `BooleanQuery.Builder.must / should`
- `MultiMatchQuery`：各 token×field 的 `TermQuery` 全部 SHOULD（title boost 3）

## 「真实 ES / Lucene」对照

| mini-es | Lucene / ES |
|---------|-------------|
| `BooleanQuery` | `org.apache.lucene.search.BooleanQuery` |
| `Occur.MUST` | `Occur.MUST` → ES `must` |
| `Occur.SHOULD` | `Occur.SHOULD` → ES `should` |
| 哈希交集 | `ConjunctionDISI` / `ConjunctionScorer` 游标交集 |
| SHOULD 加分 | `DisjunctionSumScorer`；有 MUST 时 SHOULD 为 optional |
| 无 `MUST_NOT` | `Occur.FILTER` / `MUST_NOT`；FILTER 不计分 |
| 无 `minShouldMatch` | ES `minimum_should_match` |

ES DSL：

```json
{ "bool": { "must": [...], "should": [...], "minimum_should_match": 1 } }
```

协调节点侧的「归并 Top-N」见 [10-scatter-gather.md](10-scatter-gather.md)；此处只讨论单 shard / 单进程内布尔语义。
