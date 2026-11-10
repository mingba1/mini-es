# 09 · 分片路由与主副本

集群把一个索引拆成多个 **主分片（primary）**，每个主分片可有零个或多个 **副本（replica）**。文档落在哪个主分片由路由函数决定。

## 公式

默认路由键 \(r\)（显式 `routing`，否则文档 `_id`）：

\[
\mathrm{shard}(r) = \mathrm{hash}(r)\bmod \texttt{numPrimaries}
\]

ES 实际实现近似：

\[
\mathrm{shard} = \lfloor \mathrm{MurmurHash3}(\mathrm{routing}) \bmod \mathrm{numPrimaryShards} \rfloor
\]

（历史版本用 `hash % numShards`，并与 `routing_partition` 等变体组合；教学用上式即可。）

副本不参与「选片」：写入必须到对应 **primary**，再复制到 replicas；读取可在 primary 或任一活跃 replica（偏好可配）。

## 伪代码

```
function primaryShard(id, routing, numPrimaries):
  key ← routing if routing != null else id
  return murmurHash3(key) % numPrimaries

function indexDoc(doc, indexMeta):
  shardId ← primaryShard(doc.id, doc.routing, indexMeta.numPrimaries)
  primary ← shardRoutingTable.primary(shardId)
  send INDEX to primary.node
  primary → replicate to replica nodes (sync/async per refresh/translog 策略)

function searchPrefer(shardId, preference):
  candidates ← primary + replicas for shardId
  return pick(candidates, preference)  # _primary / _replica / _local / …
```

## 复杂度

| 操作 | 成本 |
|------|------|
| 路由计算 | \(O(|key|)\) 哈希，可忽略 |
| 单文档写 | 1 次 primary + \(R\) 次 replica 复制 |
| 扩主分片数 | **不能**随意改 `numPrimaries`（需 reindex）；副本数可改 |

主分片数决定并行度与单分片体积上限；过多小分片浪费堆与文件句柄。

## mini-es 落点

- 模块：`mini-es-cluster`
- `Routing.hash(routing) % numPrimaries`
- `ShardRouting`：primary / replica 成员表
- 单机教学可跑「逻辑多 shard、同进程多 `InternalEngine`」

## 「真实 ES / Lucene」对照

| 概念 | Elasticsearch |
|------|---------------|
| 路由 | `MetadataCreateIndexService` / `OperationRouting.generateShardId` |
| 主分片 | `index.number_of_shards`（创建后基本固定） |
| 副本 | `index.number_of_replicas`（可动态调整） |
| 分片引擎 | 每 shard 一个 `InternalEngine`（Lucene `IndexWriter`） |
| 副本机制 | primary 写 translog/lucene，转发到 replica |
| 自定义 routing | 文档字段 `_routing`；父子/join 常用 |

注意：

1. **同一 routing 键 → 同一主分片**，保证相关文档共置。
2. 搜索默认 **扇出到所有主分片的拷贝**（见 scatter-gather），不是只查一个分片（除非指定 `routing`）。
3. 向量 HNSW 图 **按分片本地构建**；跨分片 ANN 是「各片 Top-K 再协调合并」，不是全局一张图。
