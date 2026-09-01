# EXレーダー

**人生の答えではなく、判断材料を。**

EXレーダーは、経験者の知恵・教訓・失敗・気づきを借りて、利用者本人が自分の意思で人生を選ぶための情報サービスです。「レーダー」は目的地を決めるものではなく、今まで見えていなかったものを見えるようにするもの。サービスが進路を推薦したり正解を決めたりせず、考えるための観点を増やします。

## 使用技術

- Java 21
- Spring Boot 3.5
- Spring MVC / Thymeleaf
- Spring Data JPA / Spring Security
- Flyway
- PostgreSQL（本番: Supabase）
- H2（開発・テスト）
- Maven Wrapper

## 必要な環境

- JDK 21
- WindowsではPowerShellまたはコマンドプロンプト

確認コマンド：

```powershell
java -version
```

`21`と表示されることを確認してください。

## 開発環境で起動する

Windows：

```powershell
cd ex-radar
.\mvnw.cmd spring-boot:run
```

macOS / Linux：

```bash
cd ex-radar
./mvnw spring-boot:run
```

起動後、ブラウザで <http://localhost:8080> を開きます。開発環境ではH2のファイルDBが使われます。

既存アカウントを開発環境の管理者にする場合は、起動前にメールアドレスを指定します。指定したユーザーでログイン後、<http://localhost:8080/admin> を開いてください。

```powershell
$env:EXRADAR_ADMIN_EMAIL="登録済みのメールアドレス"
.\mvnw.cmd spring-boot:run
```

管理者画面では総登録者数、公開投稿数、管理者数、停止中ユーザー数の確認、ユーザーの管理者権限変更、利用停止・復帰ができます。自分自身の降格・停止と最後の管理者の削除相当操作は防止されます。

### 開発用アカウント

| 権限 | メールアドレス | パスワード |
| --- | --- | --- |
| 一般ユーザー | `user@example.com` | `password` |
| 管理者 | `admin@example.com` | `adminpass` |

管理者画面はまだ未実装です。

## テスト

Windows：

```powershell
.\mvnw.cmd test
```

## GA4アクセス解析

本番環境でのみ、環境変数 `GA4_MEASUREMENT_ID` にGA4の測定ID（例：`G-XXXXXXXXXX`）を設定してください。開発プロファイルでは測定IDを空にしているため、GA4タグは出力されません。IPアドレスや個人情報をEXレーダーのDBへ保存することはありません。

```powershell
$env:GA4_MEASUREMENT_ID="G-XXXXXXXXXX"
.\mvnw.cmd spring-boot:run
```

GA4の「レポート > レポートのスナップショット」または「レポート > ユーザー属性 > ユーザー属性の詳細」から、アクティブユーザー数、セッション数、表示回数、参照元を確認できます。「レポート > エンゲージメント > イベント」では、次のイベントを確認できます。

- `experience_detail_view`: 投稿詳細の閲覧
- `experience_form_view`: 投稿画面への到達
- `experience_post_submit`: 投稿完了

「探索 > ファネルデータ探索」で、上記イベントを順に設定すると「訪問 > 投稿閲覧 > 投稿画面 > 投稿完了」の転換率を確認できます。ページごとの閲覧数は「レポート > エンゲージメント > ページとスクリーン」、流入元は「レポート > 集客 > トラフィック獲得」で確認できます。GA4のデータ反映には時間差があるため、設定直後は「管理 > DebugView」で動作確認してください。

macOS / Linux：

```bash
./mvnw test
```

## 主な機能

- ユーザー登録・ログイン
- 教訓、知っておきたかったこと、失敗、気づきを中心とした経験投稿・検索
- 選択肢ごとに経験者の知恵をまとめる「選択肢ガイド」
- 自分が大切にしたい価値観の登録と、その観点を優先した表示
- 本人だけが読める「意思決定メモ」と考えの変化の記録
- 公開済みの経験を1件投稿すると体験談本文・教訓・選択肢ガイドが開くGive to Get
- 「参考になった」「知らなかった」「考えるきっかけになった」等のリアクション
- 公開プロフィール
- 投稿者の傾向として扱う統計、似た状況の人の教訓、人生経験レポート
- マイページ、プロフィール・パスワード・通知管理

## 現在の未完了項目

- 管理者画面
- 全画面の共通レイアウトとスマートフォン用メニュー
- 十分なサンプル体験談
- 意思決定メモと経験投稿の相互参照
- 価値観との関連度を、単純な語句一致からタグ・文脈を含む検索へ改善

詳細は [`docs/IMPLEMENTATION_PLAN.md`](docs/IMPLEMENTATION_PLAN.md) を参照してください。

## Railway + Supabaseで本番公開する方法

本番はRailway上でSpring Bootアプリのみを動かし、データベースはSupabaseのPostgreSQLを使用します。Supabase Authへの移行は行わず、認証は引き続きSpring Securityが担当します。

### 1. Supabase側の準備

1. [Supabase](https://supabase.com/)でプロジェクトを作成します。
2. 「Project Settings > Database」から接続情報を確認します。RailwayのようなIPv4のみの環境からはDirect Connectionが利用しづらい場合があるため、その場合は「Connection Pooling」欄の**Session Pooler**の接続文字列を使用してください。
3. 取得したホスト名・ポート・データベース名・ユーザー名・パスワードから、後述の`DB_URL`（JDBC形式）・`DB_USERNAME`・`DB_PASSWORD`を組み立てます。パスワードやURLはSupabaseの管理画面以外（リポジトリやREADME等）へ書かないでください。

### 2. Railway側の設定

1. [Railway](https://railway.app/)でこのリポジトリからサービスを作成します。
2. 「Settings > Root Directory」を次の値に設定します。

   ```text
   /ex-radar
   ```

3. 「Variables」に以下の環境変数を設定します（値はSupabaseの管理画面等から取得し、Railwayの環境変数機能でのみ設定してください）。

   | 変数名 | 内容 |
   | --- | --- |
   | `SPRING_PROFILES_ACTIVE` | `prod` |
   | `DB_URL` | SupabaseのJDBC接続文字列（例: `jdbc:postgresql://<host>:<port>/postgres?sslmode=require`。Session Poolerを使う場合はそのホスト・ポートを指定） |
   | `DB_USERNAME` | Supabaseのデータベースユーザー名 |
   | `DB_PASSWORD` | Supabaseのデータベースパスワード |
   | `GA4_MEASUREMENT_ID` | GA4の測定ID（例: `G-XXXXXXXXXX`） |
   | `EXRADAR_ADMIN_EMAIL` | 本番で管理者として扱うユーザーのメールアドレス |

   `PORT`はRailwayが自動的に設定するため、追加設定は不要です（アプリ側は`${PORT:8080}`で待ち受けます）。

4. Railwayが自動的にMaven Wrapperでビルド・起動します（追加のDockerfileは不要です）。起動時にFlywayが本番DBへマイグレーションを適用します。
5. デプロイ完了後、「Settings > Networking > Generate Domain」からRailwayのドメインを発行すると、そのURLで本番サイトへアクセスできます。

### 3. 動作確認

デプロイ後、発行されたドメインにアクセスし、ユーザー登録・ログイン・投稿・検索などの主要機能が動作することを確認してください。管理者機能を確認する場合は、`EXRADAR_ADMIN_EMAIL`に指定したメールアドレスで登録・ログインしてから`/admin`を開きます。
