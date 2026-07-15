# Mobile Melon

**Mobile Melon** は、前払式決済サービス **melon** の利用者向け Android アプリです。自分の Melon 残高を、スマートフォンから確認できます。

残高の照会方法は 2 通りです。

1. **カード ID を入力する** — モバイル Suica / PASMO などのアプリに表示される「カード ID」を一度入力すると端末に保存され、次回以降はホーム画面に残高だけが表示されます。
2. **カードをかざす** — 実物の FeliCa カードを端末の NFC にかざして読み取り、その場で残高を照会します。

> ℹ️ 本アプリは残高の**照会（読み取り専用）**のみを行います。チャージや支払いは行いません。

## 主な機能

- カード ID（IDi の文字列形）を入力・保存し、ホームで残高を表示
- 設定画面でカード ID を編集・削除
- NFC でカードをかざし、相互認証して残高を照会
- 残高の合計と、6ヶ月失効の**有効期限別内訳**を表示

## 仕組み

melon のサーバは、口座を `(System Code, IDm, IDi)` で識別します。本アプリは 2 通りの経路でサーバのセルフ照会 API を呼び出します。

| 照会方法 | 経路 | エンドポイント |
|---|---|---|
| カード ID 入力 | IDi を**主張**（低信頼・利便） | `POST /v1/self/balance` |
| カードをかざす | **相互認証**でカード実在を証明（高信頼） | `POST /v1/self/authenticate` |

- **カード ID 入力**：モバイル Suica / PASMO 等が表示する「カード ID」を入力すると、アプリが IDi（16桁hex）へ復元して送ります（[`data/CardId.kt`](app/src/main/java/jp/unknowntech/mobilemelon/data/CardId.kt)、交通系IC＝サイバネ規格の可逆変換）。IDi を主張するだけなので、便利ですが低信頼の経路です。
- **カードをかざす**：標準の `NfcAdapter` リーダーモード（`FLAG_READER_NFC_F`）でカードにコマンドフレームを中継し（[`nfc/`](app/src/main/java/jp/unknowntech/mobilemelon/nfc/)）、**サーバ主導の相互認証**を行います。鍵はサーバが保持し、アプリはフレームを運ぶだけ。**検証済み IDi を得るのはサーバのみ**で、アプリには残高だけが返ります。カードを物理的に持っていることを証明する高信頼の経路です（複数往復するため、認証が終わるまでカードを離さない必要があります）。

いずれもサーバ応答は残高合計と失効内訳のみで、生の IDi/IDm や加盟店情報は返しません。

## 画面構成

| 画面 | 内容 |
|---|---|
| ホーム | 保存済みカードの残高を表示。「カードを読み取る」ボタン。未登録時は登録導線。 |
| 設定 | カード ID の入力・保存・削除。 |
| カード読み取り | NFC でカードをかざして残高を照会。 |

## 技術スタック

- **Kotlin** + **Jetpack Compose**（Material 3）
- 単一 Activity + Compose、状態ベースの画面遷移（`MelonViewModel` を共有）
- 通信は `HttpURLConnection` + `org.json`（追加の HTTP ライブラリなし）
- カード ID の保存は `SharedPreferences`（[`data/CardStore.kt`](app/src/main/java/jp/unknowntech/mobilemelon/data/CardStore.kt)）
- `minSdk 24` / `targetSdk 36` / `compileSdk 37`、AGP 9.x / Kotlin 2.2

## プロジェクト構成

```
app/src/main/java/jp/unknowntech/mobilemelon/
├── MainActivity.kt              エントリポイント（Compose をセット）
├── data/
│   ├── CardId.kt                カード ID ⇄ IDi の変換
│   ├── CardStore.kt             IDi の永続化（SharedPreferences）
│   └── MelonApi.kt              self/balance・self/authenticate の呼び出し
├── nfc/
│   ├── CardReader.kt            リーダーモード補助・タグ接続
│   ├── Felica.kt                FeliCa フレーム（Polling / 中継 transceive）
│   └── CardFlow.kt              相互認証の中継オーケストレーション
└── ui/
    ├── MobileMelonApp.kt        ヘッダと3画面のナビ
    ├── MelonViewModel.kt        保存カードの残高・カード読み取りの状態
    ├── HomeScreen.kt            ホーム（残高表示）
    ├── SettingsScreen.kt        設定（カード ID 編集）
    ├── ReadCardScreen.kt        カード読み取り（リーダーモード）
    ├── BalanceComponents.kt     残高カード等の共通 UI
    └── theme/                   Compose テーマ
```

## ビルドと実行

Android Studio で開くか、コマンドラインから:

```bash
# デバッグ APK をビルド
./gradlew assembleDebug

# 実機にインストール（USB デバッグ有効な端末を接続）
./gradlew installDebug

# 単体テスト
./gradlew testDebugUnitTest
```

FeliCa の読み取りには **NFC 対応の実機**が必要です（エミュレータでは NFC 読み取りは動作しません）。カード ID 入力による照会はエミュレータでも利用できます。

## 接続先サーバの設定

照会先の melon サーバは `BuildConfig.MELON_API_BASE_URL` で指定します。既定は本番（`https://melon.unknowntech.jp`、`/v1` は Web 側でプロキシ）です。ローカルサーバなどに向ける場合は [`app/build.gradle.kts`](app/build.gradle.kts) の該当行を変更してください。

```kotlin
buildConfigField("String", "MELON_API_BASE_URL", "\"https://melon.unknowntech.jp\"")
```

> ⚠️ クリアテキスト通信は既定で無効です。開発用サーバは `https://` を使うか、別途 network-security-config の設定が必要です。

## サーバ API

いずれも無認証。詳細は melon リポジトリの [`docs/api.md`](https://github.com/soltia48/melon) 参照。

**`POST /v1/self/balance`** — カード ID（IDi）で照会。

```json
{ "system_code": 3, "idi": "0102030405060708" }
```

**`POST /v1/self/authenticate`** — かざして相互認証（3ステップ中継）。サーバがフレームを駆動し、完了時に検証済みカードの残高を返す（IDi は返さない）。

```jsonc
// 開始
{ "system_code": 3, "idm": "…", "pmm": "…", "areas": [0], "services": [0] }
//   → { "step": "auth1", "session_id": "…", "command": { "frame": "…" } }
// 継続（完了まで）
{ "session_id": "…", "card_response": "…" }
//   → { "step": "complete", "result": { "system_code": 3, "total": 700, "buckets": […] } }
```

いずれも成功応答（`self/balance` は 200、`self/authenticate` は `result`）は残高合計と失効内訳のみ。404＝未登録、422＝乱数化 IDm、400＝形式不正。

```json
{ "system_code": 3, "total": 700,
  "buckets": [ { "bucket_id": "…", "remaining": 700, "expires_at": "…Z" } ] }
```

## 権限

| 権限 | 用途 |
|---|---|
| `INTERNET` | 残高照会の通信 |
| `NFC` | カードの読み取り（`uses-feature` は任意指定で、NFC 非搭載端末でもインストール可） |

---

melon 本体（サーバ・Web・端末）については、melon リポジトリの `docs/` を参照してください。
