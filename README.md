# ThreadLocal Context Leak Debug Lab

単一ワーカーのスレッドプールでテナント処理を行った後、テナント未指定のシステムジョブの監査ログが前テナントへ誤帰属する不具合を再現するJava 21のデバッグラボです。

既定ブランチは修正済みの状態です。`./scripts/test.sh`で3件の回帰テストが成功します。失敗を確認する場合は、バグ再現コミット`db87195`をチェックアウトしてください。

## 扱う契約

| 操作順 | 期待する監査帰属 | バグ状態の監査帰属 |
| --- | --- | --- |
| `tenant-a`のエクスポート | `tenant-a` | `tenant-a` |
| 続くテナント未指定の照合ジョブ | `SYSTEM` | `tenant-a` |

`./scripts/reproduce.sh`は、同じワーカーで2件のタスクが実行され、2件目の開始時点に`tenant-a`が残っている観測ログを出力します。`./scripts/test.sh`は、戻り値と監査台帳の最終状態を別々に検証します。

## 前提条件

Java 21以上が必要です。外部ライブラリやビルドツールは使用しません。

```bash
java -version
./scripts/reproduce.sh
./scripts/test.sh

# バグ状態の意図した失敗を確認する
git switch --detach db87195
./scripts/test.sh

git switch main
```

## 文書

| 文書 | 内容 |
| --- | --- |
| [題材企画](docs/topic-brief.md) | 契約、競合仮説、再現境界、決定性の設計です。 |
| [重複調査](docs/novelty-report.md) | 既存のJava記事・学習リポジトリとの四軸比較です。 |
| [デバッグ記録](docs/debugging-record.md) | 失敗テスト、ログ、デバッガー、競合仮説、原因、最小修正、回帰確認です。 |

## 制約

このラボはJDK標準の`ThreadLocal`と単一ワーカー実行器に範囲を限定します。Springのリクエストスコープ、MDC、非同期コンテキスト伝播、仮想スレッド、テナント認可そのものは扱いません。
