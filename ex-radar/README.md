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

## 管理者ダッシュボードのGA4連携(任意)

管理者ダッシュボード(`/admin`)には、GA4のアクセス状況とEXレーダー内部データ(登録者数・投稿数など)をまとめた「アクセス解析ダッシュボード」があります。内部データ部分は常に表示されますが、GA4部分は下記のサーバー間連携(Google Analytics Data API)を設定しないと「Google Analyticsのデータを取得できませんでした」と表示されます(推測値は出しません)。ブラウザから直接GA4 APIを呼ぶことはなく、Spring Bootサーバーが `GA4_PROPERTY_ID` / `GA4_SERVICE_ACCOUNT_KEY` を使ってGoogle Analytics Data APIを呼び出し、管理者用API(`GET /api/admin/analytics`、ADMIN権限のみ)経由で画面に表示します。取得結果は5分間サーバー側でキャッシュします。

体験談詳細ページの閲覧数はGA4の`experience_detail_view`イベント(設定済み)から取得しています。新規登録者数・投稿者数・投稿数はGA4側に対応するイベント(`sign_up`等)が無いため、正確なEXレーダー内部DBの値を使用しています。

### Google Cloud側で行う作業

1. [Google Cloud Console](https://console.cloud.google.com/)でプロジェクトを作成(または既存のものを使用)します。
2. 「APIとサービス > ライブラリ」で **Google Analytics Data API** を検索し、有効にします。
3. 「APIとサービス > 認証情報 > 認証情報を作成 > サービスアカウント」でサービスアカウントを作成します(役割の付与は不要です。権限はGA4側で個別に付与します)。
4. 作成したサービスアカウントの「キー > 鍵を追加 > 新しい鍵を作成 > JSON」からJSON形式の秘密鍵をダウンロードします。このファイルは**絶対にGitHubへコミットしないでください**。

### Google Analytics側で行う作業

1. GA4の管理画面(<https://analytics.google.com/>)で対象プロパティを開きます。
2. 「管理 > プロパティ設定」で表示される**プロパティID**(数字のみ、例: `123456789`)を控えます。これが`GA4_PROPERTY_ID`です。
3. 「管理 > プロパティのアクセス管理」を開き、右上の「+」からユーザーを追加します。メールアドレス欄には手順4-4でダウンロードしたJSONキー内の`client_email`の値(`xxxx@xxxx.iam.gserviceaccount.com`という形式)を入力し、役割は**閲覧者**を付与します。

### Railwayで行う作業

1. Railwayの「Variables」に以下を追加します。

   | 変数名 | 値 |
   | --- | --- |
   | `GA4_PROPERTY_ID` | 上記で控えたプロパティID |
   | `GA4_SERVICE_ACCOUNT_KEY` | ダウンロードしたJSONキーファイルの中身をそのまま(1行の文字列として)貼り付け |

2. 再デプロイ後、管理者アカウントで `/admin` を開き、「アクセス解析ダッシュボード」にGA4の数値が表示されることを確認してください。GA4は反映まで数分〜数時間かかることがあるため、直後は0や空欄が続く場合があります。

これらの環境変数を設定しない場合でも、管理者ダッシュボードのEXレーダー内部データ(ユーザー状況・コンテンツ状況・ファネルの一部)は通常どおり表示され、アプリ全体が停止することはありません。

## Googleログイン(任意)

ログイン・新規登録画面に「Googleで続ける」ボタンを表示し、Google OAuth 2.0 / OpenID Connectでのログイン・新規登録に対応しています。既存のメールアドレス+パスワードでの登録・ログインはそのまま利用できます。`GOOGLE_CLIENT_ID` / `GOOGLE_CLIENT_SECRET` を設定しない環境(現在の開発・テスト環境など)では、Googleログイン関連の機能は自動的に無効化され、Googleボタンも表示されません(既存の認証には一切影響しません)。

Googleでの初回ログイン時は、Googleの氏名をそのまま使わず「表示名を設定してください」という専用画面で本人にEXレーダー上の表示名を決めてもらいます。Googleの本名・メールアドレス・ユーザーIDは公開プロフィールや体験談画面には一切表示されません。また、ログインしようとしたGoogleアカウントのメールアドレスと同じメールアドレスのアカウントが既に存在する場合は、自動で統合せずログインを拒否します(乗っ取り防止のため)。

### Google Cloud側で行う作業

1. [Google Cloud Console](https://console.cloud.google.com/)でプロジェクトを作成(または既存のものを使用、GA4連携と共用可)します。
2. 「APIとサービス > OAuth同意画面」で外部/内部を選択し、アプリ名・サポートメール等を設定します(スコープは既定の`email`・`profile`・`openid`で十分です)。
3. 「APIとサービス > 認証情報 > 認証情報を作成 > OAuthクライアントID」を選択し、アプリケーションの種類は**ウェブアプリケーション**を選びます。
4. 「承認済みのJavaScript生成元」「承認済みのリダイレクトURI」に、後述の値を設定します。
5. 作成後に表示される**クライアントID**と**クライアントシークレット**を控えます。クライアントシークレットは`GOOGLE_CLIENT_SECRET`としてRailwayの環境変数にのみ設定し、コードやREADME等には書かないでください。

### Authorized JavaScript origins(承認済みのJavaScript生成元)

```text
https://<本番のRailwayドメイン>
```

### Authorized redirect URIs(承認済みのリダイレクトURI)

```text
https://<本番のRailwayドメイン>/login/oauth2/code/google
```

`/login/oauth2/code/google` はSpring Securityが標準で提供するコールバックパスで、独自に実装したものではありません。ローカルでも動作確認する場合は、上記に加えて `http://localhost:8080` / `http://localhost:8080/login/oauth2/code/google` も同じOAuthクライアントに追加登録してください(1つのクライアントIDに複数のオリジン・リダイレクトURIを登録できます)。

### Railwayで行う作業

Railwayの「Variables」に`GOOGLE_CLIENT_ID`と`GOOGLE_CLIENT_SECRET`を追加し、再デプロイしてください。設定後、ログイン画面に「Googleで続ける」ボタンが表示されます。

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
- 管理者ダッシュボード(GA4アクセス状況 + EXレーダー内部データ)

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
   | `GA4_PROPERTY_ID`（任意） | 管理者ダッシュボードのGA4連携用。プロパティID（数字のみ） |
   | `GA4_SERVICE_ACCOUNT_KEY`（任意） | 管理者ダッシュボードのGA4連携用。サービスアカウントのJSONキーの中身をそのまま設定 |
   | `GOOGLE_CLIENT_ID`（任意） | Googleログイン用のクライアントID。未設定の場合はGoogleログイン機能自体が無効化され、従来のメール+パスワード認証のみになる |
   | `GOOGLE_CLIENT_SECRET`（任意） | Googleログイン用のクライアントシークレット |

   `PORT`はRailwayが自動的に設定するため、追加設定は不要です（アプリ側は`${PORT:8080}`で待ち受けます）。

4. Railwayが自動的にMaven Wrapperでビルド・起動します（追加のDockerfileは不要です）。起動時にFlywayが本番DBへマイグレーションを適用します。
5. デプロイ完了後、「Settings > Networking > Generate Domain」からRailwayのドメインを発行すると、そのURLで本番サイトへアクセスできます。

### 3. 動作確認

デプロイ後、発行されたドメインにアクセスし、ユーザー登録・ログイン・投稿・検索などの主要機能が動作することを確認してください。管理者機能を確認する場合は、`EXRADAR_ADMIN_EMAIL`に指定したメールアドレスで登録・ログインしてから`/admin`を開きます。
