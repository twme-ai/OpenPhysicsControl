# OpenPhysicsControl

[![Build](https://github.com/twme-ai/OpenPhysicsControl/actions/workflows/build.yml/badge.svg)](https://github.com/twme-ai/OpenPhysicsControl/actions/workflows/build.yml)

OpenPhysicsControl 是從零撰寫、採 MIT 授權的 Bukkit 世界物理控制插件。它不編譯、打包或引用 Dymeth/PhysicsControl 的原始碼；該專案僅作為資料遷移相容目標與選單資訊架構參考。

## 平台

- Paper 26.2 / Java 25（以 `26.2.build.65-beta` 編譯及實機驗證）
- Spigot 26.2 / Java 25（以 `26.2-R0.1-SNAPSHOT` 編譯驗證）
- Folia：所有物理處理只存取事件所在 region，不建立全域排程工作，也不跨 region 操作玩家或實體；官方目前尚無 26.2，已在最新穩定版 26.1.2 build 8 完成載入、指令及乾淨停用驗證
- `plugin.yml` 維持 `api-version: '1.13'`

## 功能

85 項規則可按世界獨立控制，涵蓋方塊與流體、火焰與氣候、植物生長、實體物理、玩家互動、紅石以及自動化方塊。實驗性情境控制可停止玩家右鍵方塊、玩家操作或乘坐實體、展示框與畫脫落、床與重生錨的方塊來源爆炸，並以單一規則控制嗅探獸蛋完整孵化。預設值位於 `plugins/OpenPhysicsControl/default-rules.yml`，各世界狀態儲存在 `plugins/OpenPhysicsControl/worlds/<世界名稱>.yml`。設計與設定範例見 [`docs/scenario-controls.md`](docs/scenario-controls.md)，完整事件來源及測試狀態見 [`docs/physics-matrix.md`](docs/physics-matrix.md)。

`/opc` 開啟三列固定位置的物理分類選單，選擇分類後才會顯示該組規則；分類布局延續舊版熟悉的互動、建造、重力與流體、世界、生長區域，並加入機械分類。亦可使用 `/openphysics`、`/ophysics` 或 `/pc`。其他指令：

```text
/opc set <rule> <on|off|toggle> [world]
/opc material <rule> <material> <on|off|toggle|clear> [world]
/opc language [auto|en|zh_tw]
/opc reload
```

指令狀態以規則行為為準：大多數規則的 `on` 代表物理正常運作、`off` 代表停止；`block-hit-projectile-removal` 則是相容舊版的可選清理，`on` 時會移除命中方塊的箭矢與三叉戟，預設 `off` 以保留原版箭矢留存。

`oxygen-depletion` 的 `off` 只會阻止水中氧氣減少，離水後仍會恢復氧氣；`drowning-damage` 是獨立規則。`fire-damage` 不會攔截仙人掌、甜莓叢或尖滴水石等一般接觸傷害，這些不屬於火焰與高溫控制範圍。

`default-rules.yml` 會在首次啟動時產生，並列出全部規則；除上述清理選項外，`true` 代表物理運作，`false` 代表停止。世界檔已有的值優先，只有新世界或世界檔缺少的規則才採用預設值。從舊版升級時，插件會在同名世界檔尚不存在的前提下，自動將 `<world-uuid>.yml` 搬移為 `<世界名稱>.yml`，不會覆寫既有名稱檔。世界名稱中的路徑或系統保留字元會以百分比編碼。

### 依方塊材質細化

每個規則仍保有原本的一鍵 `true`/`false` 基準值，也可用精確 Bukkit `Material` 名稱覆寫特定方塊。世界檔的 `material-overrides` 優先於 `default-rules.yml`；`on` 代表該材質的物理運作，`off` 代表停止，`clear` 則移除世界覆寫並回到預設或基準值。

```yaml
# worlds/survival.yml
gravity: false
crop-growth: true
explosion-block-damage: false

material-overrides:
  gravity:
    SAND: true
    RED_SAND: true
  crop-growth:
    WHEAT: false
  explosion-block-damage:
    WHITE_WOOL: true
```

上述範例會停止所有掉落方塊，僅保留沙與紅沙；停止小麥生長但保留其他作物；停止爆炸破壞方塊但仍允許破壞白色羊毛。一般方塊事件會以變化前的方塊為準。活塞則檢查所有被移動的方塊，任何被停止的材質都會取消整次推動；爆炸則逐一從受影響清單保護被停止的材質。天氣、生怪、氧氣與純實體事件沒有方塊主體，仍只採用規則的一鍵基準值。

`player-block-interactions` 也支援方塊材質覆寫，因此可以維持整體玩家互動開啟，只針對 `RESPAWN_ANCHOR`、床、鐵砧、雕紋書櫃等指定方塊停止右鍵操作。`player-entity-interactions` 與 `hanging-entity-detachment` 目前採世界層級開關，不套用方塊材質覆寫。

## GitHub Actions

`.github/workflows/build.yml` 會在 push、pull request 與手動觸發時使用 Java 25，先驗證 Spigot 26.2 profile，再建立 Paper 26.2 shaded JAR，並將 `OpenPhysicsControl.jar` 上傳為 workflow artifact。

## 從 Dymeth PhysicsControl 遷移

保留 `plugins/PhysicsControl` 資料目錄、移除舊 JAR 並安裝 OpenPhysicsControl 後，插件會在每個世界首次載入時自動匯入舊版設定。來源檔會保留，既有 OpenPhysicsControl 世界檔不會被覆寫。完整的資料位置、規則對照、聚合規則的保守 `false` 優先策略與無對應選項見 [`docs/migration-from-dymeth-physicscontrol.md`](docs/migration-from-dymeth-physicscontrol.md)。

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
