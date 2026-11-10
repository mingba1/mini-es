# 06 · NRT：refresh vs flush/commit vs translog

近实时（NRT）的关键是：**写入先持久意图，再异步变成可搜段**。mini-es 用 `InternalEngine` 串起 translog、writer 缓冲与 searcher 快照。

## 公式 / 模型

时间线上三类动作：

| 动作 | 可见性 | 持久性（教学语义） |
|------|--------|-------------------|
| `index(doc)` | 不可搜 | 写入 translog + buffer |
| `refresh()` | 可搜 | buffer → 新段；换 `IndexSearcher`；translog **保留** |
| `flush()` | 可搜 | `refresh()` 后 **清空** translog（模拟 commit 完成） |

不变量：

\[
\text{Searcher.segments} = \text{已 refresh 的不可变段快照}
\]

\[
\text{buffer} \cap \text{Searcher} = \emptyset
\]

崩溃恢复教学含义：translog 中未 flush 的 INDEX 操作可重放进 buffer。

## 伪代码

```
function index(doc):
  translog.add(INDEX, doc)
  writer.index(doc)          # 仅进 buffer

function refresh():
  writer.refresh()           # buffer → segment (+ maybeMerge)
  searcher ← new IndexSearcher(writer.segments(), bm25)

function flush():            # 模拟 commit
  refresh()
  translog.clear()

function search(q, n):
  return searcher.search(q, n)   # 看不到未 refresh 文档
```

## 复杂度

| 操作 | 成本 |
|------|------|
| `index` | \(O(\text{analyze} + \text{tf 累积})\)，摊销便宜 |
| `refresh` | 冻结段 \(O(\text{buffer})\) + 可选 merge；换 searcher \(O(1)\) 引用 |
| `search` | 与段数 × posting 相关；不含 buffer |

频繁 refresh 会制造许多小段 → 触发 merge；真实 ES 默认 `index.refresh_interval=1s`。

## mini-es 落点

- `InternalEngine.index / refresh / flush / searcher`
- `Translog`：append-only `INDEX`；`flush` 时 `clear`
- `IndexWriter` buffer vs `segments()`
- 测试：`RefreshVisibilityTest`

## 「真实 ES / Lucene」对照

| mini-es | Elasticsearch / Lucene |
|---------|-------------------------|
| `InternalEngine` | `org.elasticsearch.index.engine.InternalEngine` |
| `Translog` | `org.elasticsearch.index.translog.Translog` |
| `refresh()` | ES `_refresh` / 周期性 refresh；Lucene `DirectoryReader.openIfChanged`（NRT） |
| `flush()` ≈ commit | ES `_flush` / 周期性 flush；Lucene `IndexWriter.commit` |
| searcher 快照 | `Engine.Searcher` + `SearcherManager` |
| buffer 段构建 | `DocumentsWriter` → 新 segment |
| translog 仅 INDEX | 真实还有 DELETE / NO_OP；按 generation 切分、fsync |

要点对照：

1. **refresh**：搜索可见，不保证 durable commit 完成。
2. **flush/commit**：段落盘 + translog 裁剪到安全点。
3. **translog**：在下一次安全 commit 前保证可恢复。

ES 写入链路：`primary` 写内存 + translog → refresh 后副本也可搜；详见 [09-sharding-routing.md](09-sharding-routing.md)。
