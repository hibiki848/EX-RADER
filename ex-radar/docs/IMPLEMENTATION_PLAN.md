# EXレーダー 精密実装計画書

## 1. 目標と完了定義

EXレーダーを、人生の選択と「その後」を投稿・検索・比較できる、実際に操作可能なSpring Boot Webサービスとして完成させる。ユーザー登録から通知までの主要導線をDB永続化込みで動作させ、管理、統計、Give to Get、人生レポート、レスポンシブUI、アニメーション、サンプルデータ、テスト、学習用READMEまで含める。

完了とは次をすべて満たす状態とする。

- 全TODOの受け入れ基準を満たす。
- 自動テストが成功する。
- 開発用プロファイルで起動できる。
- Browser UseでPC幅・スマートフォン幅の主要導線を確認する。
- 主要画面にリンク切れ、500エラー、重大な表示崩れがない。
- 未実装を示すTODOコメントやダミー操作を残さない。

## 2. 技術判断

- Java 21 LTS
- Spring Boot 3.5.16（Java 21と組み合わせやすく、Boot 4移行直後より教材として既存知見を利用しやすい安定系列）
- Spring MVC / Thymeleaf / Spring Data JPA / Spring Security 6
- PostgreSQL（本番: Supabase）、H2（自動テストと手軽な開発確認）
- Maven Wrapper
- Flywayでスキーマ変更を明示
- Bootstrap等のUIフレームワークには依存せず、HTML/CSS/小さなJavaScriptで構成
- Chart.js等の外部ランタイム依存を避け、統計はCSSによるバー・カード表示

## 3. アーキテクチャ

処理経路は `Controller → Service → Repository → Entity → DB` とし、画面入力はForm、画面出力や集計結果は必要な箇所だけDTOを使う。権限・所有者確認はServiceで必ず実施し、Controllerの画面制御だけに依存しない。

主要パッケージ:

- `config`: Security、初期データ、共通MVC設定
- `controller`: public/auth/user/experience/admin
- `service`: ユースケースとトランザクション境界
- `repository`: JPA Repositoryと検索Specification
- `entity`: DBモデル、enum
- `form`: Bean Validation付き入力モデル
- `dto`: 検索、統計、類似度、レポートの表示モデル
- `security`: UserDetailsService、認証ユーザー取得
- `exception`: 404、権限違反、業務エラー

## 4. データ設計

主要テーブル:

- users: 認証、プロフィール、role、利用停止
- categories: 管理可能なカテゴリ
- tags / experience_post_tags: 正規化したタグと中間表
- experience_posts: 選択前・選択・その後・評価を含む中心データ
- life_events: 投稿に紐づく並び替え可能な人生イベント
- reactions: user/post/typeの複合一意制約
- comments: 投稿コメント
- notifications: 宛先、種別、参照ID、既読
- reports: 対象種別・対象ID・理由・状態

重要制約:

- email、category slug、tag nameは一意。
- satisfaction/regretは1〜10。
- reactionは同一user/post/typeを一意にする。
- 投稿・コメントの更新削除は所有者または管理者のみ。
- 外部キーと削除方針を明示し、一覧のN+1は必要箇所でfetch/entity graphにより抑制する。

## 5. 画面と導線

- 公開: トップ、投稿一覧、投稿詳細、検索、カテゴリ統計、ログイン、登録
- ログイン後: 投稿CRUD、人生ルート編集、リアクション、コメント、通知、マイページ、プロフィール、パスワード、人生レポート
- 投稿済み限定: 類似ユーザー、類似ユーザー傾向、詳細統計、次の進路
- 管理者: ダッシュボード、ユーザー、投稿、コメント、通報、カテゴリ

## 6. UI・アニメーション方針

- 深いネイビー、青緑、暖色アクセントで、落ち着いた探索・レーダー表現を作る。
- トップのレーダーは装飾的なCSSアニメーション、カードはviewport進入時の短いfade/slide、通知やフォームは控えめな状態遷移を使う。
- `prefers-reduced-motion: reduce` では装飾アニメーションを停止する。
- 操作結果を待たせるアニメーション、過度なパララックス、常時大きく動く背景は使わない。
- キーボードフォーカス、コントラスト、ラベル、エラー要約を担保する。

## 7. テスト戦略

- Service単体/スライス: 登録、投稿作成、所有者権限、reaction重複防止、類似度、統計、通知。
- Controller/MockMvc: 公開範囲、認証、主要POST、validation、管理者制御、CSRF。
- Repository: 複合検索と集計。
- Browser Use: 登録→ログイン→投稿→一覧→詳細→検索→reaction→comment→通知、および管理画面。

## 8. TODOと受け入れ基準

- [x] T01 基盤・ドメイン・認証: Maven、設定、共通例外、全Entity/Repository、Security、登録/ログイン、サンプル基盤。`contextLoads`、登録、認証、DB制約テストを実装済み。Java 21不在のため実行検証はT10で必須。
- [x] T02 投稿・人生ルートCRUD: Form/Controller/Service/画面、所有者/ADMIN認可、複数life event保存、作成・validation・権限テストを実装済み。実行検証はT10で必須。
- [x] T03 公開探索: トップ、カード一覧、詳細、複合検索、人気/最新/参考投稿、プロフィール、類似投稿、タグを実装し、検索Repository/Service/Controllerテストを追加済み。実行検証はT10で必須。
- [x] T04 交流・通知・通報: 4種reaction、comment、通知、各対象のreportと詳細UIを実装し、一意制約・所有者削除・通知・CSRFテストを追加済み。実行検証はT10で必須。
- [x] T05 相談・経験者回答: サービス方針の変更により機能一式を削除済み。
- [x] T06 統計・類似・Give to Get・人生レポート: 説明可能なスコア、カテゴリ集計、1件以上投稿者の限定詳細、人生レポートを実装し、境界・集計・認可テストを追加済み。実行検証はT10で必須。
- [ ] T07 マイページとアカウント管理: profile、password、自分の投稿/reaction履歴、通知既読と専用テストを実装済み。Java 21環境で認証・validationテストを実行し、成功を確認した時点で完了とする。
- [ ] T08 管理機能: user停止、投稿/comment削除、report確認、report状態、category CRUDをADMIN限定で実装する。権限と主要操作テストが成功する。
- [ ] T09 デザイン・レスポンシブ・アニメーション: 全画面の共通レイアウト、現代的なレーダー表現、mobile navigation、空状態、エラー画面、reduced-motionを実装する。静的確認とBrowser UseでPC/mobile表示を検収する。
- [ ] T10 初期データ・README・総合品質: 複数パターン、開発用user/admin、セットアップと学習ガイド、DB図、処理経路、改善案を整備する。全テスト、起動、Browser Useの主要E2Eを通し、残存TODO/リンク切れ/500を解消する。

## 9. PDCA運用

各TODOごとにworkerへ実装と局所テストを依頼する。オーケストレーターは差分、設計整合、セキュリティ、テスト結果を確認し、基準未達なら同じworkerへ具体的な修正を再依頼する。合格後にチェックボックスと必要なら本計画を更新して次へ進む。T09/T10ではBrowser Useによる実画面確認を必須とし、発見した不具合を該当workerへ戻す。
