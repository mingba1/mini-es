# 02 · 倒排索引（Postings）

倒排把「词 → 文档列表」作为检索主结构。mini-es 按 **字段** 分层：`field → term → List<Posting>`。

## 公式 / 模型

对字段 \(f\)、词项 \(t\)：

\[
\text{postings}(f,t) = [(d_1,\mathit{tf}_1),\ldots,(d_{n},\mathit{tf}_{n})]
\]

- \(d_i\)：段内文档序（`docOrd`，\(0..maxDoc-1\)）
- \(\mathit{tf}_i\)：该文档该字段中 \(t\) 的出现次数
- \(\mathit{df}(t) = |\text{postings}(f,t)|\)
- 字段长度 \(L(d,f)\)：该字段 token 数（BM25 用）

段级还维护 \(\sum_d L(d,f)\)，以便 \(\mathit{avgdl}(f) = \sum L / N\)。

## 伪代码

```
function addDocument(doc):
  docOrd ← docs.size()
  for each field in doc:
    tokens ← analyzer.analyze(field.text)
    fieldLength[field] ← |tokens|
    tf ← count frequencies of tokens
    for (term, freq) in tf:
      postings[field][term].append(Posting(docOrd, freq))
  docs.append(StoredDoc(...))

function buildSegment():
  freeze postings / docs / fieldBoosts / sumFieldLengths
  return immutable InMemorySegment
```

## 复杂度

| 操作 | 时间 | 说明 |
|------|------|------|
| 索引 1 篇、\(T\) 个 token | \(O(T)\) | 哈希表累积 TF |
| 查 `postings(f,t)` | 期望 \(O(1)\) + 列表长度 | 内存 Map |
| 遍历 posting 打分 | \(O(\mathit{df})\) | 见 BM25 |

真实 Lucene 用跳表 / FST / 压缩编码；此处为内存 `ArrayList`，语义等价。

## mini-es 落点

- `Posting(docOrd, freq)`
- `SegmentBuilder`：可变缓冲
- `InMemorySegment`：不可变段；`postings(field, term)`、`avgFieldLength`、`fieldBoost`
- `SegmentMerger`：合并多段 postings，重映射 `docOrd`

字段 boost 存在段上（`title` 默认 3），打分时 `BM25 × fieldBoost × queryBoost`。

## 「真实 ES / Lucene」对照

| mini-es | Lucene / ES |
|---------|-------------|
| `InMemorySegment` | `SegmentReader` / `LeafReader`（不可变段） |
| `Map<field, Map<term, List>>` | `Fields` → `Terms` → `PostingsEnum` |
| `Posting.docOrd` | `PostingsEnum.docID()`（段内 ID） |
| `Posting.freq` | `PostingsEnum.freq()` |
| 无 position / offset 存储 | 可选 `DOCS_AND_FREQS_AND_POSITIONS` |
| `StoredDoc.fieldTexts` | stored fields / `_source`（ES） |
| 哈希倒排 | `BlockTreeTerms`（FST）+ 压缩 postings（FOR/PFOR） |

ES 每个 shard 内是一组 Lucene 段；查询先在 leaf 上扫 postings，再由 `IndexSearcher` 跨段归并。mini-es 的 `IndexSearcher` 对多个 `InMemorySegment` 做同样的事。
