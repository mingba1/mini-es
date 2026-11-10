# 05 · 不可变段与 TieredMerge（简化）

Lucene / ES 的核心不变式：**段一旦提交便只读**。写入先进缓冲，refresh/flush 冻成新段；段过多时后台合并，降低文件句柄与检索扇出。

## 公式 / 模型

设当前段集合为 \(S=\{s_1,\ldots,s_k\}\)，政策阈值 \(M=\texttt{maxSegmentCount}\)（mini-es 默认 \(M=8\)）。

\[
\text{若 } k > M,\quad
\text{选取按 } |s| \text{ 升序的最小约 } \lceil k/2\rceil \text{ 个段合并为 } s'
\]

合并语义：

\[
\text{postings}'(f,t) = \big\|_{s\in\text{toMerge}} \text{remap}(\text{postings}_s(f,t))
\]

`docOrd` 按段拼接偏移重映射；`fieldBoost` 取 \(\max\)；`sumFieldLengths` 相加。

## 伪代码

```
function refresh(writer):
  if buffer empty: return
  segments.append(buffer.build())   # 不可变段
  buffer ← new SegmentBuilder(...)
  maybeMerge(segments)

function findMerges(segments, maxSegmentCount=8):
  if |segments| <= maxSegmentCount: return []
  sorted ← sort by maxDoc ascending
  half ← max(2, |sorted| / 2)
  return sorted[0 .. half)

function merge(toMerge):
  outDocs / outPostings ← concat with docOrd offsets
  return new InMemorySegment(...)
```

## 复杂度

| 操作 | 时间 / 空间 |
|------|-------------|
| 选 merge 集合 | \(O(k\log k)\) 排序 |
| 合并总文档数 \(N_m\)、总 posting 项 \(P\) | \(O(N_m + P)\) |
| 检索扇出 | 随段数 \(k\) 近似线性；合并降低 \(k\) |

真实 `TieredMergePolicy` 还考虑段大小字节预算、删文档比例、并发 merge 上限；mini-es 仅用「段个数 + 合并较小一半」。

## mini-es 落点

- `InMemorySegment`：build 后不可变
- `IndexWriter.refresh` → `maybeMerge`
- `TieredMergePolicy.DEFAULT_MAX_SEGMENT_COUNT = 8`
- `SegmentMerger.merge`
- `IndexWriter.forceMerge`：测试用全量合并

## 「真实 ES / Lucene」对照

| mini-es | Lucene / ES |
|---------|-------------|
| `InMemorySegment` | Lucene `SegmentCommitInfo` + 只读文件 |
| `TieredMergePolicy`（数段阈值） | `org.apache.lucene.index.TieredMergePolicy` |
| 合并最小一半 | 真实策略按 size 分层（floor / maxMergeAtOnce） |
| `SegmentMerger` | `IndexWriter` 内部 merge + `MergeScheduler` |
| 无删文档位图 | `LiveDocs`；合并时物理删除 |
| ES `forcemerge` API | 触发 `forceMerge` |

ES 设置相关：`index.merge.policy.*`、`index.merge.scheduler.max_thread_count`。段不可变使得 NRT 可用「换 searcher 快照」发布可见性（见下一篇）。
