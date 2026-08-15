# ThreadLocal Context Leak Debug Lab

単一ワーカーのスレッドプールでテナント処理を行った後、テナント未指定のシステムジョブの監査ログが前テナントへ誤帰属する不具合を再現するJava 21のデバッグラボです。

既定ブランチは修正済みの状態です。`./scripts/test.sh`で3件の回帰テストが成功します。失敗を確認する場合は、バグ再現コミット`db87195`をチェックアウトしてください。

## この題材で守る契約

| 操作順 | 期待する監査帰属 | バグ状態の監査帰属 |
| --- | --- | --- |
| `tenant-a`のエクスポート | `tenant-a` | `tenant-a` |
| 続くテナント未指定の照合ジョブ | `SYSTEM` | `tenant-a` |

`./scripts/reproduce.sh`は、同じワーカーで2件のタスクが実行され、バグ状態では2件目の開始時点に`tenant-a`が残っていることを出力します。`./scripts/test.sh`は、戻り値と監査台帳の最終状態を別々に検証します。

## 最短の開始手順

Java 21以上が必要です。外部ライブラリやビルドツールは使用しません。修正済みの既定ブランチで、診断と全回帰テストを実行します。

```bash
java -version
./scripts/reproduce.sh
./scripts/test.sh
```

期待値は、`SYSTEM_RECONCILIATION`の`JobResult`と`LEDGER`の帰属先がともに`SYSTEM`であり、3件のテストがすべて`PASS`となることです。

## バグを再現する

バグコミットへ移動して同じテストを実行します。作業中の変更がある場合は、先に退避してください。

```bash
git switch --detach db87195
./scripts/reproduce.sh
./scripts/test.sh
# FAIL unscoped_job_is_not_attributed_to_previous_tenant
# direct job result: expected <SYSTEM> but was <tenant-a>
# persisted audit event: expected <SYSTEM> but was <tenant-a>

git switch main
./scripts/test.sh
# 3 tests PASS
```

## 文書

| 文書 | 内容 |
| --- | --- |
| [題材企画](docs/topic-brief.md) | 契約、競合仮説、再現境界、決定性の設計です。 |
| [重複調査](docs/novelty-report.md) | 既存のJava記事・学習リポジトリとの四軸比較です。 |
| [デバッグ記録](docs/debugging-record.md) | 失敗テスト、ログ、デバッガー、競合仮説、原因、最小修正、回帰確認です。 |

## 制約

このラボはJDK標準の`ThreadLocal`と単一ワーカー実行器に範囲を限定します。Springのリクエストスコープ、MDC、非同期コンテキスト伝播、仮想スレッド、テナント認可そのものは扱いません。

## References

[1] [Thread-Local Variables — Java SE 21](https://docs.oracle.com/en/java/javase/21/core/thread-local-variables.html)

[2] [ThreadLocal — Java SE 21 API](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/ThreadLocal.html)

[3] [Executors — Java SE 21 API](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/Executors.html)
