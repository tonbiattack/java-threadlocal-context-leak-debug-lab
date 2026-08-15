# デバッグ記録: ThreadLocalの残留で別テナントの監査ログが混ざる

## 目的

Java 21で、タスク終了時に`ThreadLocal`を解放しないことにより、単一ワーカーで後続のテナント未指定ジョブが前テナントへ誤帰属する理由を、実行可能な最小例で確認します。

> 契約: `tenant-a`のエクスポート後に同一ワーカーでテナント未指定の照合ジョブを実行したとき、照合ジョブの戻り値と監査台帳は`SYSTEM`を返す必要があります。バグ状態ではどちらも`tenant-a`になります。

## 実行環境と再現境界

| 項目 | 内容 |
| --- | --- |
| 言語処理系 | OpenJDK 21.0.11 |
| 難易度プロファイル | 実践・上級。暗黙コンテキストとワーカースレッド再利用が組み合わさり、単独実行では不具合が見えないためです。 |
| ビルド・テスト方法 | `javac --release 21`と`./scripts/test.sh` |
| 使用する依存関係 | JDK標準ライブラリのみ |
| 使用しないもの | Spring、MDC、サーブレット、データベース、外部サービス |
| 公開境界 | `AuditJobRunner.runTenantExport`と`AuditJobRunner.runSystemReconciliation` |
| 最終観測 | `JobResult`の帰属先と`AuditLedger`の2件目の監査イベント |
| 決定性の確保 | `Executors.newSingleThreadExecutor()`によりワーカーを1本に固定し、各`Future.get()`で先行タスクの完了を待ちます。`sleep`は使用しません。 |

この境界を選んだ理由は、フレームワークのリクエストライフサイクルを介さず、`ThreadLocal`とスレッドプール再利用の相互作用を直接観測できるためです。

## 最初に観測した事実

| 観測順 | 事実 | 得られた証拠 |
| --- | --- | --- |
| 1 | `tenant-a`のエクスポート後、同じ`audit-worker-1`で未スコープ照合ジョブを実行しました。 | `docs/evidence/bug-diagnostic-output.txt` |
| 2 | 照合ジョブの直接結果は`tenant-a`であり、期待した`SYSTEM`ではありませんでした。 | `JobResult[... attributedTenant=tenant-a ...]` |
| 3 | トレースでは照合ジョブの開始時点のコンテキストが`tenant-a`でした。デバッガーも両方の`record`停止点で`TenantContext.currentTenantLabel() = "tenant-a"`と示しました。 | `docs/evidence/bug-diagnostic-output.txt`、`docs/evidence/bug-jdb-session.txt` |
| 4 | 監査台帳の2件目も`tenant-a`で保存されました。 | `docs/evidence/bug-diagnostic-output.txt`と`docs/evidence/bug-test-output.txt` |

バグ状態のコミットは`db87195`です。`./scripts/test.sh`を実行すると、`unscoped_job_is_not_attributed_to_previous_tenant`が、直接結果と永続化済み監査イベントの両方で`expected <SYSTEM> but was <tenant-a>`として失敗します。設定、依存解決、無関係なコンパイル失敗は、この観測に含めません。

## 競合仮説と検証

| 仮説 | 予測 | 検証 | 結果 |
| --- | --- | --- | --- |
| 未スコープ時の既定値実装が誤っている | 新しいワーカーでも未スコープジョブが`tenant-a`になる | `unscoped_job_uses_system_when_worker_has_no_history`を実行する | 除外しました。新しい`AuditJobRunner`では戻り値と台帳が`SYSTEM`です。 |
| 台帳への保存処理が前イベントをコピーしている | 戻り値は`SYSTEM`でも台帳だけが`tenant-a`になる | 戻り値と台帳を別々にアサートする | 除外しました。バグ状態では両方が`tenant-a`であり、修正後は両方が`SYSTEM`です。 |
| 前ジョブの`ThreadLocal`値が残っている | 2件目の`record`前に現在値が`tenant-a`になる | 診断トレースと`jdb`で`TenantContext.currentTenantLabel()`を確認する | 支持されました。同じ`audit-worker-1`の2回目の停止点でも`tenant-a`でした。 |

## 確定した原因

`runTenantExport`は同じワーカースレッドで`TenantContext.enter(tenantId)`を実行しますが、バグ状態ではタスクの終了時に`TenantContext.clear()`を呼びません。`ThreadLocal`は各スレッドが独立した値を持ち、`remove()`されるまで値が残ります。[1] `newSingleThreadExecutor()`は単一ワーカースレッドでタスクを逐次実行するため、後続の未スコープジョブも同じ値を読めます。[2]

ラボ内で直接観測した事実は、2件目のジョブ開始時・`record`停止点・最終台帳のすべてに`tenant-a`が現れたことです。`ThreadLocal`値がプール上の別タスクへ漏れ得るという一般則は、Java 21の公式ガイドで裏づけます。[1]

## 最小修正

`runTenantExport`で、`TenantContext.enter(tenantId)`の後を`try`で囲み、`finally`で`TenantContext.clear()`を必ず実行します。

```java
TenantContext.enter(tenantId);
try {
    trace("tenant-export-entry");
    return record("TENANT_EXPORT", orderId);
} finally {
    TenantContext.clear();
}
```

この修正は、前タスクのコンテキストが残る原因だけを対象にします。Executorの種類変更、コンテキストを引数へ移すAPI変更、MDC導入、依存関係追加は含めません。修正コミットは`de0c98a`です。

## 回帰保証

| 守ること | テストまたは診断 | 修正後の結果 |
| --- | --- | --- |
| テナントジョブが自分のテナントへ帰属する | `tenant_job_is_attributed_to_its_tenant` | 戻り値と台帳が`tenant-a`で成功します。 |
| 新しいワーカーの未スコープジョブが`SYSTEM`へ帰属する | `unscoped_job_uses_system_when_worker_has_no_history` | 戻り値と台帳が`SYSTEM`で成功します。 |
| 先行テナントジョブの後でも未スコープジョブが誤帰属しない | `unscoped_job_is_not_attributed_to_previous_tenant` | 戻り値と台帳が`SYSTEM`で成功します。 |
| 同じワーカーを再利用している | `unscoped_job_is_not_attributed_to_previous_tenant` | ワーカー名は`audit-worker-1`のまま成功します。 |

固定済みの状態で`./scripts/test.sh`を実行し、3件すべてのテストが成功することを確認しました。診断出力でも2件目の`JobResult`と`LEDGER`の帰属先は`SYSTEM`です。詳細は`docs/evidence/fixed-diagnostic-output.txt`と`docs/evidence/fixed-test-output.txt`に保存しています。

## 再現手順

```bash
# 修正済み状態を検証する
./scripts/test.sh

# バグ状態を確認する。作業中の変更は先に退避する
git switch --detach db87195
./scripts/test.sh
# direct job result: expected <SYSTEM> but was <tenant-a>
# persisted audit event: expected <SYSTEM> but was <tenant-a>

# 修正済み状態へ戻る
git switch main
```

## スコープと注意点

このラボは、Java 21の通常のプラットフォームスレッド、単一ワーカー実行器、テナント未指定ジョブという条件に限って再現・修正を確認しました。MDC、Springのリクエストスコープ、複数ワーカーでの伝播、仮想スレッド、認可判断、性能やメモリリーク全般へ同じ修正を自動的に拡張するものではありません。

## References

[1] [Thread-Local Variables — Java SE 21](https://docs.oracle.com/en/java/javase/21/core/thread-local-variables.html)

[2] [Executors — Java SE 21 API](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/Executors.html)
