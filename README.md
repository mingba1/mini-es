# mini-es

纯 Java 教学版 Elasticsearch：倒排 + BM25、稠密向量 / HNSW、RRF 混合检索，以及进程内分片 / 副本 / 协调节点 scatter-gather。

不依赖 Lucene 或真实 ES。参数对齐本仓库旁的 [`hybrid-search-es`](../hybrid-search-es)：BM25 `k1=1.2` / `b=0.6`、title boost `3`、RRF `k=60`、HNSW `m=16`。

## 模块

| 模块 | 职责 |
| --- | --- |
| `mini-es-core` | 分词、倒排段、合并、BM25、Boolean 查询、NRT Engine |
| `mini-es-vector` | L2 向量、暴力 / HNSW、RRF、HybridEngine |
| `mini-es-cluster` | 路由、主副本、failover、协调节点 |
| `mini-es-server` | JDK `HttpServer` 演示 API |
| `docs/algorithms/` | 算法说明（公式 + 真实 ES/Lucene 对照） |

## 构建与测试

需要 JDK 17+。本仓库可用捆绑 Maven：

```bash
chmod +x mvnw
./mvnw test
```

只跑某一模块：

```bash
./mvnw -pl mini-es-core test
./mvnw -pl mini-es-vector,mini-es-cluster test
```

## 演示服务

```bash
./mvnw -pl mini-es-server -am package -DskipTests
java -jar mini-es-server/target/mini-es-server-0.1.0-SNAPSHOT.jar 9201 3
```

```bash
curl -X POST http://127.0.0.1:9201/docs \
  -H 'content-type: application/json' \
  -d '{"id":"1","title":"外汇支付报文异常处理","content":"SWIFT MT103 跨境汇款"}'

curl -X POST http://127.0.0.1:9201/refresh

curl -X POST http://127.0.0.1:9201/search \
  -H 'content-type: application/json' \
  -d '{"query":"跨境汇款报文出错怎么办","top_n":5,"fusion":"RRF"}'
```

`fusion` 可选：`RRF`（默认）、`BM25`、`KNN`、`WEIGHTED`。

## 与真实 ES 的对照

| mini-es | 真实世界 |
| --- | --- |
| `InternalEngine` + `refresh` | ES `Engine` / `InternalEngine`，`refresh_interval` |
| `InMemorySegment` + `TieredMergePolicy` | Lucene `SegmentReader` / `TieredMergePolicy` |
| `BM25Similarity` | Lucene/ES BM25（`similarity` 设置） |
| `HnswVectorIndex` | Lucene HNSW / ES `dense_vector` knn |
| `ReciprocalRankFusion` | ES `rank.rrf` / `retriever.rrf` |
| `DocRouter` | `_routing` + `number_of_shards` |
| `CoordinatingNode` | 协调节点 scatter-gather |

算法详解见 [docs/algorithms/README.md](docs/algorithms/README.md)。

## 边界

进程内模拟集群，无真实网络 RPC、无 Master 选举完整协议、无磁盘 Segment 格式、无完整 Query DSL。向量测试默认用 `MockEmbeddingService`（哈希投影），不强制本机 ONNX。
