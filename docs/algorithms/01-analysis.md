# 01 · 分析链（Tokenizer / TokenFilter）

文本进入倒排前必须切成 **token**。Lucene 把这条管线拆成 Tokenizer（切分）+ TokenFilter（变换）；mini-es 用单一 `Analyzer` 把两步合在一起，便于教学。

## 公式 / 模型

把字符串 \(S\) 映射为有序 token 序列：

\[
\text{analyze}(S) = [(t_0,p_0),(t_1,p_1),\ldots]
\]

其中 \(t_i\) 为小写词项，\(p_i\) 为递增位置（相邻 token 间距为 1；标点不占位置）。

mini-es `StandardAnalyzer` 规则：

1. 按 Unicode 码点扫描；`LetterOrDigit` 累积，其余字符触发 flush。
2. 累积字符一律 `toLowerCase`。
3. 丢弃空 token。

## 伪代码

```
function analyze(text):
  tokens ← []
  buf ← ""
  pos ← 0
  for each codePoint cp in text:
    if isLetterOrDigit(cp):
      buf.append(toLower(cp))
    else:
      if buf not empty:
        tokens.append(Token(buf, pos)); pos ← pos + 1
        buf ← ""
  flush remaining buf
  return tokens
```

## 复杂度

| 操作 | 时间 | 空间 |
|------|------|------|
| 分析长度 \(n\) 的字符串 | \(O(n)\) | \(O(n)\)（最坏 token 数） |

无词典、无回退；与真实 `StandardTokenizer` 的 UAX#29 规则相比是简化版。

## mini-es 落点

- `com.minies.core.analysis.Analyzer`
- `com.minies.core.analysis.StandardAnalyzer`
- `com.minies.core.analysis.Token`（`term`, `position`）
- 索引侧：`SegmentBuilder` 对每个 `Field` 调 `analyzer.analyze`
- 查询侧：`MultiMatchQuery` 用同一 Analyzer，保证索引/查询对称

默认字段 boost：`title=3`，`content=1`（分析本身不改 boost，只产出 token）。

## 「真实 ES / Lucene」对照

| mini-es | Lucene / ES |
|---------|-------------|
| `Analyzer.analyze` | `Analyzer.tokenStream` + `TokenStream` 链式消费 |
| `StandardAnalyzer`（字母数字切分） | `org.apache.lucene.analysis.standard.StandardAnalyzer`（StandardTokenizer + LowerCaseFilter + StopFilter） |
| 内嵌 lowercase | `LowerCaseFilter` |
| 无停用词 / 无 stemming | 可配 `StopFilter`、`PorterStemFilter`、ICU、IK、smartcn 等 |
| 单次 List 返回 | 惰性 `incrementToken()`，可复用 Attribute |
| ES `analyzer` / `search_analyzer` | mapping 字段级指定；查询缺省用同一链 |

ES 中 `standard` analyzer ≈ Lucene StandardTokenizer + lowercase +（可选）stop。生产中文常用 `ik_max_word` / `icu_analyzer`，本项目刻意保持 Unicode 字母数字切分，突出「对称分析」思想。
