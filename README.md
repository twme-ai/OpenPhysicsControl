# OpenPhysicsControl

OpenPhysicsControl 是從零撰寫、採 MIT 授權的 Bukkit 世界物理控制插件。它不編譯、打包或引用 Dymeth/PhysicsControl 的原始碼；原專案僅作為產品需求背景。

## 平台

- Paper 26.2 / Java 25（以 `26.2.build.65-beta` 編譯及實機驗證）
- Spigot 26.2 / Java 25（以 `26.2-R0.1-SNAPSHOT` 編譯驗證）
- Folia：所有物理處理只存取事件所在 region，不建立全域排程工作，也不跨 region 操作玩家或實體；官方目前尚無 26.2，已在最新穩定版 26.1.2 build 8 完成載入、指令及乾淨停用驗證
- `plugin.yml` 維持 `api-version: '1.13'`

## 功能

71 項規則可按世界獨立控制，涵蓋方塊與流體、火焰與氣候、植物生長、實體物理、紅石以及自動化方塊。預設值位於 `plugins/OpenPhysicsControl/default-rules.yml`，各世界狀態儲存在 `plugins/OpenPhysicsControl/worlds/<世界名稱>.yml`。完整事件來源及測試狀態見 [`docs/physics-matrix.md`](docs/physics-matrix.md)。

`/opc` 開啟置中的物理分類選單，選擇分類後才會顯示該組規則；亦可使用 `/openphysics`、`/ophysics` 或 `/pc`。其他指令：

```text
/opc set <rule> <on|off|toggle> [world]
/opc language [auto|en|zh_tw]
/opc reload
```

指令狀態以物理本身為準：`on` 代表物理正常運作，`off` 代表停止該項物理，`toggle` 則在兩者間切換。

`default-rules.yml` 會在首次啟動時產生，並列出全部規則；`true` 代表物理運作，`false` 代表停止。世界檔已有的值優先，只有新世界或世界檔缺少的規則才採用預設值。從舊版升級時，插件會在同名世界檔尚不存在的前提下，自動將 `<world-uuid>.yml` 搬移為 `<世界名稱>.yml`，不會覆寫既有名稱檔。世界名稱中的路徑或系統保留字元會以百分比編碼。

紅樹林胎生苗分成三種行為：

- 種在地面後自然長成紅樹，由 `tree-growth`（GUI「樹木生長」）控制。
- 使用骨粉催生時，`bone-meal` 可以直接停止；若骨粉已允許，長成樹仍同時受 `tree-growth` 控制。
- 懸掛在紅樹林樹葉下方的胎生苗由 age 0 自然成熟至 age 4，同樣由 `tree-growth` 控制；`off` 會將每次自然成熟安全回復至原 age。指令直接設定 age 不受影響，骨粉催熟仍由 `bone-meal` 控制。

玩家未指定語言時會依 Minecraft client locale 選擇英文或繁體中文，偏好持久化在 `player-languages.yml`。訊息檔位於 `lang/*.yml` 並使用 MiniMessage 格式。

## 依賴與建置

唯一打包的 runtime 是 MiniMessage 及其必要 Adventure 元件；所有類別 relocation 到插件內部 namespace。Bukkit/Paper API 為 `compileOnly`。

```bash
# 在本目錄執行，預設 Paper 26.2 API
JAVA_HOME=/path/to/jdk-25 ./gradlew clean build

# Spigot 26.2 API 相容性建置
JAVA_HOME=/path/to/jdk-25 ./gradlew clean build -PserverPlatform=spigot
```

產物：`build/libs/OpenPhysicsControl.jar`

## 測試

```bash
# 分類、語系完整性與 descriptor 測試
JAVA_HOME=/path/to/jdk-25 ./gradlew clean test

# Mineflayer 黑箱測試；會下載已鎖定 SHA-256 的 Paper 1.21.11 測試服
cd tests/mineflayer
npm ci
JAVA_HOME=/path/to/jdk-25 npm test
```

Mineflayer 4.37.1 目前最高支援 Minecraft 1.21.11，尚不能直接登入 26.2。黑箱測試使用同一個插件 JAR 驗證可觀測行為；26.2 仍由雙 API 編譯與 Paper/Folia 實機測試驗證。

## 授權

本目錄的程式碼採 [MIT License](LICENSE)。它不包含根目錄相容性分支或原始 Dymeth/PhysicsControl 專案的程式碼。
