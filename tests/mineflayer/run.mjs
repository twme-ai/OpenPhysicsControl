import assert from 'node:assert/strict'
import { createHash } from 'node:crypto'
import { createWriteStream } from 'node:fs'
import { copyFile, mkdir, mkdtemp, readFile, readdir, stat, writeFile } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import { dirname, join, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { spawn } from 'node:child_process'
import { pipeline } from 'node:stream/promises'
import mineflayer from 'mineflayer'
import { Vec3 } from 'vec3'

// Mineflayer 4.39 speaks at most 26.1, so the 26.3 target bridges that client with ViaVersion/ViaBackwards.
const TARGETS = {
  '26.3': {
    server: '26.3',
    build: 235,
    client: '26.1',
    // Cushions and Paper's EntityCollideWithEntityEvent first appear in 26.3.
    features: ['cushions', 'entity-collision-event'],
    sha256: '4ebaf5c9da98c654bb8f17245799943dfb05a285830be4307bfdf7bc9ca024bf',
    url: 'https://fill-data.papermc.io/v1/objects/4ebaf5c9da98c654bb8f17245799943dfb05a285830be4307bfdf7bc9ca024bf/paper-26.3-235.jar',
    plugins: [
      {
        name: 'ViaVersion-5.12.2.jar',
        url: 'https://cdn.modrinth.com/data/P1OZGk5p/versions/Fkfcr89K/ViaVersion-5.12.2.jar',
        sha512: 'c6af11f50e14bc1ba239f5a90be653cce85feee30c473a4b53336b4d5527d790369cf43843f7cb06d3ac3a34fc207e7203436737ca719070644101472453401b'
      },
      {
        name: 'ViaBackwards-5.12.2.jar',
        url: 'https://cdn.modrinth.com/data/NpvuJQoq/versions/WFO37e8T/ViaBackwards-5.12.2.jar',
        sha512: '78bc7e5fd33ecf13aee31c62e1de46a854e066a1936f294347c6798371357497fe2cc6f23f1d42003ad3050f94ac5382db95e60ffa90d765fada0322b59b0ef9'
      }
    ]
  },
  '26.1.2': {
    server: '26.1.2',
    build: 74,
    client: '26.1',
    sha256: '1d70b1dab9cf4a6de615209a536f3a45a2186240253c428213ce2188ab95e5f7',
    url: 'https://fill-data.papermc.io/v1/objects/1d70b1dab9cf4a6de615209a536f3a45a2186240253c428213ce2188ab95e5f7/paper-26.1.2-74.jar',
    plugins: []
  },
  '1.21.11': {
    server: '1.21.11',
    build: 132,
    client: '1.21.11',
    sha256: '5ffef465eeeb5f2a3c23a24419d97c51afd7dbb4923ff42df9a3f58bba1ccfba',
    url: 'https://fill-data.papermc.io/v1/objects/5ffef465eeeb5f2a3c23a24419d97c51afd7dbb4923ff42df9a3f58bba1ccfba/paper-1.21.11-132.jar',
    plugins: []
  }
}
const target = TARGETS[process.env.OPC_TEST_TARGET ?? '26.3']
assert.ok(target, `Unknown OPC_TEST_TARGET ${process.env.OPC_TEST_TARGET}; expected ${Object.keys(TARGETS).join(' or ')}`)
const bridged = target.plugins.length > 0
const supports = feature => target.features?.includes(feature) ?? false
const PORT = Number(process.env.OPC_TEST_PORT ?? 25578)
const configuredServerDirectory = process.env.OPC_TEST_SERVER_DIR
const here = dirname(fileURLToPath(import.meta.url))
const project = resolve(here, '../..')
const pluginJar = join(project, 'build/libs/OpenPhysicsControl.jar')
const driverJar = join(project, 'build/test-driver/OpenPhysicsControlTestDriver.jar')
const testServers = join(project, 'build/test-servers')
const paperCache = join(testServers, `paper-${target.server}-${target.build}.jar`)
const java = process.env.JAVA_HOME ? join(process.env.JAVA_HOME, 'bin/java') : 'java'

let server
let bot
let serverOutput = ''

function delay (milliseconds) {
  return new Promise(resolve => setTimeout(resolve, milliseconds))
}

async function digest (algorithm, path) {
  const hash = createHash(algorithm)
  const stream = (await import('node:fs')).createReadStream(path)
  for await (const chunk of stream) hash.update(chunk)
  return hash.digest('hex')
}

async function download (url, path, algorithm, expected) {
  await mkdir(dirname(path), { recursive: true })
  try {
    if (await digest(algorithm, path) === expected) return
  } catch {
    // Cache miss.
  }
  const response = await fetch(url)
  assert.equal(response.ok, true, `Download of ${url} failed: ${response.status}`)
  await pipeline(response.body, createWriteStream(path))
  assert.equal(await digest(algorithm, path), expected, `Checksum mismatch for ${url}`)
}

async function ensurePaper () {
  await download(target.url, paperCache, 'sha256', target.sha256)
  for (const plugin of target.plugins) {
    await download(plugin.url, join(testServers, plugin.name), 'sha512', plugin.sha512)
  }
}

async function waitForServer (pattern, timeout = 120000) {
  if (pattern.test(serverOutput)) return
  await new Promise((resolve, reject) => {
    const deadline = setTimeout(() => reject(new Error(`Server timeout waiting for ${pattern}`)), timeout)
    const inspect = chunk => {
      serverOutput += chunk.toString()
      if (!pattern.test(serverOutput)) return
      clearTimeout(deadline)
      server.stdout.off('data', inspect)
      resolve()
    }
    server.stdout.on('data', inspect)
    server.once('exit', code => reject(new Error(`Paper exited early with code ${code}\n${serverOutput}`)))
  })
}

function command (value) {
  server.stdin.write(`${value}\n`)
}

async function commands (...values) {
  for (const value of values) {
    command(value)
    await delay(150)
  }
}

async function block (x, y, z) {
  for (let attempt = 0; attempt < 20; attempt++) {
    const value = bot.blockAt(new Vec3(x, y, z), false)
    if (value) return value
    await delay(100)
  }
  throw new Error(`Block ${x},${y},${z} was not loaded by Mineflayer`)
}

async function expectBlock (position, expected, message) {
  const actual = await block(...position)
  assert.equal(actual.name, expected, `${message}: expected ${expected}, got ${actual.name}`)
}

async function expectNotBlock (position, unexpected, message) {
  const actual = await block(...position)
  assert.notEqual(actual.name, unexpected, `${message}: still ${unexpected}`)
}

async function expectServerBlockState (position, expected, message) {
  const marker = `opc-block-state-${Date.now()}`
  const response = nextChatMessage(new RegExp(marker), 2000)
  command(`execute if block ${position.join(' ')} minecraft:${expected} run tellraw PhysicsBot {"text":"${marker}"}`)
  try {
    await response
  } catch {
    throw new Error(`${message}: expected minecraft:${expected} at ${position.join(',')}`)
  }
}

const BLOCK_FACES = new Map([
  ['0,1,0', 'up'], ['0,-1,0', 'down'], ['0,0,-1', 'north'],
  ['0,0,1', 'south'], ['-1,0,0', 'west'], ['1,0,0', 'east']
])

// Right-clicks a block face with the bot's main hand. ViaBackwards drops the bridged client's use-item-on
// packet, so bridged targets run the identical server-side interaction through the test driver plugin.
async function useBlock (position, face = new Vec3(0, 1, 0)) {
  if (!bridged) {
    await bot.activateBlock(await block(...position), face)
    return
  }
  const side = BLOCK_FACES.get(`${face.x},${face.y},${face.z}`)
  assert.ok(side, `unsupported block face ${face}`)
  await commands(`opctest use PhysicsBot ${position.join(' ')} ${side}`)
}

async function serverMatches (condition) {
  const marker = `opc-server-check-${Date.now()}`
  const response = nextChatMessage(new RegExp(marker), 2000)
  command(`execute ${condition} run tellraw PhysicsBot {"text":"${marker}"}`)
  try {
    await response
    return true
  } catch {
    return false
  }
}

async function resetArea () {
  await commands(
    'execute in minecraft:overworld run fill -8 100 -8 30 116 8 air',
    'execute in minecraft:overworld run fill -8 99 -8 30 99 8 stone',
    'execute in minecraft:overworld run kill @e[type=minecraft:tnt]',
    'execute in minecraft:overworld run kill @e[type=minecraft:arrow]',
    'execute in minecraft:overworld run kill @e[type=minecraft:armor_stand]',
    'execute in minecraft:overworld run kill @e[type=minecraft:item_frame]',
    'execute in minecraft:overworld run kill @e[type=minecraft:minecart]',
    'execute in minecraft:overworld run kill @e[type=minecraft:sniffer]'
  )
  await delay(500)
}

function nextEvent (emitter, event, timeout = 5000) {
  return new Promise((resolve, reject) => {
    const deadline = setTimeout(() => reject(new Error(`Timeout waiting for ${event}`)), timeout)
    emitter.once(event, value => {
      clearTimeout(deadline)
      resolve(value)
    })
  })
}

function nextChatMessage (pattern, timeout = 3000) {
  return new Promise((resolve, reject) => {
    const deadline = setTimeout(() => {
      bot.off('messagestr', inspect)
      reject(new Error(`Timeout waiting for chat message ${pattern}`))
    }, timeout)
    const inspect = message => {
      const text = String(message)
      if (!pattern.test(text)) return
      clearTimeout(deadline)
      bot.off('messagestr', inspect)
      resolve(text)
    }
    bot.on('messagestr', inspect)
  })
}

function windowTitle (window) {
  if (typeof window.title === 'string') return window.title
  if (window.title && typeof window.title.value === 'string') return window.title.value
  return String(window.title)
}

function occupiedSlots (window, end = window.inventoryStart) {
  return window.slots.slice(0, end).flatMap((item, slot) => item ? [slot] : [])
}

function itemText (item) {
  const values = [item?.displayName, item?.customName]
  for (const property of ['components', 'nbt']) {
    try {
      values.push(JSON.stringify(item?.[property]))
    } catch {
      // Ignore protocol-specific non-serializable fields.
    }
  }
  try {
    if (typeof item?.toJSON === 'function') values.push(JSON.stringify(item.toJSON()))
  } catch {
    // The direct fields above are sufficient on older Mineflayer versions.
  }
  return values.filter(Boolean).join(' ')
}

function nearbyEntities (name) {
  return Object.values(bot.entities).filter(entity => entity.name === name
    && entity.position.distanceTo(bot.entity.position) < 16)
}

async function waitForNearbyEntity (name, timeout = 6000) {
  const deadline = Date.now() + timeout
  while (Date.now() < deadline) {
    if (nearbyEntities(name).length > 0) return
    await delay(100)
  }
  throw new Error(`No nearby ${name} appeared within ${timeout}ms`)
}

async function resetBotHealth () {
  await commands(
    'effect clear PhysicsBot',
    'effect give PhysicsBot minecraft:instant_health 1 10 true'
  )
  await delay(300)
  assert.equal(bot.health, 20, 'could not reset the test bot health')
}

async function testRuleStorage (serverDir) {
  const dataDirectory = join(serverDir, 'plugins/OpenPhysicsControl')
  const defaults = await readFile(join(dataDirectory, 'default-rules.yml'), 'utf8')
  assert.match(defaults, /^gravity: true$/m, 'missing default rule was not added')
  assert.match(defaults, /^note-blocks: false$/m, 'custom default rule was overwritten')
  assert.match(defaults, /^    GRAVEL: false$/m, 'custom default material override was overwritten')

  const worldFiles = (await readdir(join(dataDirectory, 'worlds'))).sort()
  assert.deepEqual(worldFiles, ['world.yml', 'world_nether.yml', 'world_the_end.yml'])
  for (const file of worldFiles) {
    const rules = await readFile(join(dataDirectory, 'worlds', file), 'utf8')
    assert.match(rules, /^note-blocks: false$/m, `${file} did not inherit the configured false default`)
    if (file === 'world.yml') {
      assert.match(rules, /^gravity: true$/m, 'missing gravity default was not added')
      assert.match(rules, /^    GRAVEL: false$/m, 'legacy gravel override was not imported')
      assert.match(rules, /^bone-meal: false$/m, 'legacy bone meal alias was not imported')
      assert.match(rules, /^end-portal-frame-filling: false$/m, 'legacy End portal frame setting was not imported')
      assert.match(rules, /^glow-berry-picking: false$/m, 'legacy glow berry setting was not imported')
      assert.match(rules, /^block-hit-projectile-removal: true$/m,
        'legacy block-hit projectile cleanup setting was not imported')
    } else if (file === 'world_nether.yml') {
      assert.match(rules, /^crop-growth: true$/m, 'missing crop growth default was not added')
      assert.match(rules, /^    WHEAT: false$/m, 'pre-1.1 legacy wheat override was not imported')
      assert.match(rules, /^gravity: true$/m, `${file} did not inherit a missing true default`)
    } else {
      assert.match(rules, /^gravity: true$/m, `${file} did not inherit a missing true default`)
    }
  }
  const legacySource = await readFile(join(serverDir, 'plugins/PhysicsControl/triggers/world.yml'), 'utf8')
  assert.equal(legacySource,
    'GRAVEL_FALLING: false\nBONE_MEAL_USAGE: false\nEND_PORTAL_FRAMES_FILLING: false\nGLOW_BERRIES_PICKING: false\nBLOCK_HIT_PROJECTILES_REMOVING: true\n')
  const legacyConfig = await readFile(join(serverDir, 'plugins/PhysicsControl/config.yml'), 'utf8')
  assert.equal(legacyConfig, 'world_nether:\n  WHEAT_GROWING: false\n')
  console.log('PASS default rules, legacy migration, and world-name storage')
}

async function testLocalizedMenu () {
  await commands('op PhysicsBot')
  bot.chat('/pc language zh_tw')
  await delay(500)

  const categoryMenuPromise = nextEvent(bot, 'windowOpen')
  bot.chat('/pc')
  let categoryMenu = await categoryMenuPromise
  await delay(300)
  assert.match(windowTitle(categoryMenu), /物理分類/)
  assert.equal(categoryMenu.inventoryStart, 27, 'category menu is not three rows')
  assert.deepEqual(occupiedSlots(categoryMenu), [3, 4, 5, 11, 12, 13, 14, 15, 22],
    'categories do not use the expected three-row layout')
  assert.match(itemText(categoryMenu.slots[3]), /運作中/)
  assert.match(itemText(categoryMenu.slots[3]), /已停止/)

  const categories = [
    {
      slot: 3,
      title: /生物互動/,
      size: 18,
      rules: [0, 1, 2, 3, 5, 6, 7, 8]
    },
    {
      slot: 4,
      title: /玩家互動/,
      size: 18,
      rules: Array.from({ length: 9 }, (_, slot) => slot)
    },
    {
      slot: 5,
      title: /實體效果/,
      size: 27,
      rules: [0, 1, 2, 3, 5, 6, 7, 8, 10, 11, 12, 13, 14, 15, 16]
    },
    {
      slot: 11,
      title: /建造與訊號/,
      size: 27,
      rules: [1, 2, 3, 5, 6, 7, 10, 11, 12, 14, 15, 16]
    },
    {
      slot: 12,
      title: /重力與流體/,
      size: 18,
      rules: [2, 3, 5, 6]
    },
    {
      slot: 13,
      title: /世界與氣候/,
      size: 27,
      rules: Array.from({ length: 18 }, (_, slot) => slot)
    },
    {
      slot: 14,
      title: /小型植物與生長/,
      size: 27,
      rules: [2, 3, 4, 5, 6, 11, 12, 13, 14, 15]
    },
    {
      slot: 15,
      title: /藤蔓與高大生長/,
      size: 18,
      rules: [2, 3, 5, 6]
    },
    {
      slot: 22,
      title: /機械與處理/,
      size: 18,
      rules: Array.from({ length: 9 }, (_, slot) => slot)
    }
  ]

  for (const [index, category] of categories.entries()) {
    const submenuPromise = nextEvent(bot, 'windowOpen')
    await bot.clickWindow(category.slot, 0, 0)
    const submenu = await submenuPromise
    await delay(300)
    assert.match(windowTitle(submenu), category.title)
    assert.equal(submenu.inventoryStart, category.size, 'category submenu has the wrong size')
    assert.deepEqual(occupiedSlots(submenu, category.size - 9), category.rules, 'rules are not centered')
    assert.deepEqual(occupiedSlots(submenu), [...category.rules, category.size - 5].sort((a, b) => a - b),
      'category submenu contains unexpected items')

    if (index === 0) {
      const ruleSlot = category.rules[0]
      assert.match(itemText(submenu.slots[ruleSlot]), /物理狀態.*運作中/)
      await bot.clickWindow(ruleSlot, 0, 0)
      await delay(400)
      assert.match(itemText(submenu.slots[ruleSlot]), /物理狀態.*已停止/)
      await bot.clickWindow(ruleSlot, 0, 0)
      await delay(400)
      assert.match(itemText(submenu.slots[ruleSlot]), /物理狀態.*運作中/)
    }

    if (index === categories.length - 1) {
      bot.closeWindow(submenu)
      break
    }
    const returnPromise = nextEvent(bot, 'windowOpen')
    await bot.clickWindow(category.size - 5, 0, 0)
    categoryMenu = await returnPromise
    await delay(250)
    assert.match(windowTitle(categoryMenu), /物理分類/)
    assert.deepEqual(occupiedSlots(categoryMenu), [3, 4, 5, 11, 12, 13, 14, 15, 22])
  }

  bot.chat('/pc language auto')
  await delay(300)
  console.log('PASS legacy-inspired categorized menu and explicit states')
}

async function testGravity () {
  await commands('opc set gravity off world', 'setblock 2 105 0 minecraft:sand')
  await delay(1200)
  await expectBlock([2, 105, 0], 'sand', 'gravity off')

  await commands('opc set gravity on world', 'setblock 2 105 0 air', 'setblock 2 105 0 minecraft:sand')
  await delay(1200)
  await expectNotBlock([2, 105, 0], 'sand', 'gravity on')
  console.log('PASS gravity')
}

async function testMaterialOverrides () {
  await commands(
    'opc set gravity off world',
    'opc material gravity sand on world',
    'setblock 3 105 0 minecraft:sand'
  )
  await delay(1200)
  await expectNotBlock([3, 105, 0], 'sand', 'gravity sand override on')

  await commands('opc set gravity on world', 'setblock 4 105 0 minecraft:gravel')
  await delay(1200)
  await expectBlock([4, 105, 0], 'gravel', 'default gravity gravel override off')

  await commands('opc material gravity gravel on world', 'setblock 4 105 0 minecraft:gravel')
  await delay(1200)
  await expectNotBlock([4, 105, 0], 'gravel', 'world gravity gravel override on')

  await commands(
    'opc material gravity gravel clear world',
    'setblock 4 105 0 minecraft:gravel'
  )
  await delay(1200)
  await expectBlock([4, 105, 0], 'gravel', 'cleared gravity gravel override')
  await commands('opc material gravity sand clear world', 'setblock 3 105 0 air', 'setblock 4 105 0 air')
  console.log('PASS per-material overrides')
}

async function testPlacedBlockConnections () {
  const existing = [7, 100, 0]
  const placed = [8, 100, 0]
  await commands(
    'opc set block-updates off world',
    'opc set placed-block-connections on world',
    'opc material placed-block-connections oak_fence off world',
    `setblock ${existing.join(' ')} minecraft:oak_fence[east=false]`,
    `setblock ${placed.join(' ')} air`,
    'tp PhysicsBot 8 100 2',
    'clear PhysicsBot',
    'give PhysicsBot minecraft:oak_fence 2'
  )
  let fence = bot.inventory.items().find(item => item.name === 'oak_fence')
  assert.ok(fence, 'oak fence was not given to the bot')
  await bot.equip(fence, 'hand')
  await useBlock([placed[0], placed[1] - 1, placed[2]])
  await delay(500)
  await expectServerBlockState(placed, 'oak_fence[west=false]',
    'placed connection off: new fence connected to its neighbor')
  await expectServerBlockState(existing, 'oak_fence[east=false]',
    'block updates off: existing fence updated toward the new fence')

  await commands(
    `setblock ${placed.join(' ')} air`,
    'opc material placed-block-connections oak_fence on world',
    'give PhysicsBot minecraft:oak_fence 1'
  )
  fence = bot.inventory.items().find(item => item.name === 'oak_fence')
  assert.ok(fence, 'second oak fence was not given to the bot')
  await bot.equip(fence, 'hand')
  await useBlock([placed[0], placed[1] - 1, placed[2]])
  await delay(500)
  await expectServerBlockState(placed, 'oak_fence[west=true]',
    'placed connection on: new fence did not connect to its neighbor')
  await expectServerBlockState(existing, 'oak_fence[east=false]',
    'block updates off: existing fence unexpectedly connected')

  await commands(
    'opc material placed-block-connections oak_fence clear world',
    'opc set block-updates on world',
    `setblock ${existing.join(' ')} air`,
    `setblock ${placed.join(' ')} air`,
    'tp PhysicsBot 0 100 0',
    'clear PhysicsBot'
  )
  console.log('PASS newly placed block connections and material override')
}

async function testPlayerBlockInteractions () {
  const position = [3, 101, 3]
  await commands(
    'opc set redstone on world',
    'opc set player-block-interactions on world',
    'opc material player-block-interactions lever off world',
    'setblock 3 100 3 minecraft:stone',
    'setblock 3 101 3 minecraft:lever[face=floor,facing=north,powered=false]'
  )
  await useBlock(position)
  await delay(400)
  assert.equal(String((await block(...position)).getProperties().powered), 'false',
    'lever material interaction off: player toggled the lever')

  await commands('opc material player-block-interactions lever on world')
  await useBlock(position)
  await delay(400)
  assert.equal(String((await block(...position)).getProperties().powered), 'true',
    'lever material interaction on: player could not toggle the lever')
  await commands('opc material player-block-interactions lever clear world', 'setblock 3 101 3 air')
  console.log('PASS player-block-interactions with material override')
}

async function testPlayerEntityInteractions () {
  await commands(
    'tp PhysicsBot 0 100 0',
    'kill @e[type=minecraft:minecart]',
    'summon minecraft:minecart 1 100 0',
    'opc set player-entity-interactions off world'
  )
  await delay(500)
  let minecart = nearbyEntities('minecart')[0]
  assert.ok(minecart, 'test minecart was not visible to Mineflayer')
  await bot.activateEntity(minecart)
  await delay(500)
  assert.equal(bot.vehicle ?? null, null, 'player entity interactions off: player mounted a minecart')

  await commands('opc set player-entity-interactions on world')
  minecart = nearbyEntities('minecart')[0]
  await bot.activateEntity(minecart)
  await delay(500)
  assert.ok(bot.vehicle, 'player entity interactions on: player did not mount the minecart')
  bot.dismount()
  await delay(250)
  await commands('kill @e[type=minecraft:minecart]')
  console.log('PASS player-entity-interactions and vehicle entry')
}

async function testHangingEntityDetachment () {
  async function unsupportedFrame () {
    await commands(
      'tp PhysicsBot 6 100 3',
      'clear PhysicsBot minecraft:item_frame',
      'give PhysicsBot minecraft:item_frame 1',
      'setblock 6 102 0 minecraft:stone',
    )
    const frameItem = bot.inventory.items().find(item => item.name === 'item_frame')
    assert.ok(frameItem, 'Mineflayer did not receive an item frame')
    await bot.equip(frameItem, 'hand')
    await useBlock([6, 102, 0], new Vec3(0, 0, 1))
    await delay(500)
    assert.ok(nearbyEntities('item_frame').length > 0, 'test item frame was not created')
    await commands('setblock 6 102 0 air')
    // Hanging entities validate support every 100 server ticks.
    await delay(6200)
  }

  await commands('kill @e[type=minecraft:item_frame]', 'opc set hanging-entity-detachment off world')
  await unsupportedFrame()
  assert.ok(nearbyEntities('item_frame').length > 0,
    'hanging entity detachment off: unsupported item frame was removed')

  await commands('opc set hanging-entity-detachment on world', 'kill @e[type=minecraft:item_frame]')
  await unsupportedFrame()
  assert.equal(nearbyEntities('item_frame').length, 0,
    'hanging entity detachment on: unsupported item frame remained')
  await commands('clear PhysicsBot minecraft:item_frame', 'tp PhysicsBot 0 100 0')
  console.log('PASS hanging-entity-detachment')
}

async function testCushionDetachment () {
  if (!supports('cushions')) return console.log(`SKIP cushions do not exist on ${target.server}`)
  async function unsupportedCushion () {
    await commands(
      'kill @e[type=minecraft:cushion]',
      'setblock 10 100 -4 minecraft:stone',
      'summon minecraft:cushion 10.5 101 -3.5'
    )
    await delay(300)
    assert.ok(await serverMatches('if entity @e[type=minecraft:cushion]'), 'test cushion was not created')
    await commands('setblock 10 100 -4 air')
    // Block-attached entities validate support every 100 server ticks.
    await delay(6200)
    return serverMatches('if entity @e[type=minecraft:cushion]')
  }

  await commands('opc set hanging-entity-detachment off world')
  assert.equal(await unsupportedCushion(), true, 'hanging entity detachment off: unsupported cushion was removed')
  await commands('opc set hanging-entity-detachment on world')
  assert.equal(await unsupportedCushion(), false, 'hanging entity detachment on: unsupported cushion remained')
  await commands('kill @e[type=minecraft:cushion]', 'kill @e[type=minecraft:item]')
  console.log('PASS hanging-entity-detachment for 26.3 cushions')
}

async function testEntityCollisions () {
  if (!supports('entity-collision-event')) {
    return console.log(`SKIP entity-collisions has no event on Paper ${target.server}`)
  }
  // Zero movement speed keeps the AI from walking while collisions may still push the pigs apart.
  const pig = tag => `{Tags:["${tag}"],Invulnerable:1b,attributes:[{id:"minecraft:movement_speed",base:0.0d}]}`
  async function pigsStayOverlapped () {
    await commands(
      'kill @e[type=minecraft:pig]',
      `summon minecraft:pig 22.5 100 -5.5 ${pig('opc-push-a')}`,
      `summon minecraft:pig 22.55 100 -5.5 ${pig('opc-push-b')}`
    )
    await delay(1500)
    return serverMatches('as @e[tag=opc-push-a] at @s if entity @e[tag=opc-push-b,distance=..0.3]')
  }

  await commands('opc set entity-collisions off world')
  assert.equal(await pigsStayOverlapped(), true, 'entity collisions off: overlapping pigs were pushed apart')
  await commands('opc set entity-collisions on world')
  assert.equal(await pigsStayOverlapped(), false, 'entity collisions on: overlapping pigs were not pushed apart')
  await commands('kill @e[type=minecraft:pig]')
  console.log('PASS entity-collisions')
}

async function testMobConstruction () {
  async function buildSnowGolem () {
    await commands(
      'kill @e[type=minecraft:snow_golem]',
      'setblock 12 100 -5 minecraft:snow_block',
      'setblock 12 101 -5 minecraft:snow_block',
      'setblock 12 102 -5 air',
      'setblock 12 102 -5 minecraft:carved_pumpkin'
    )
    await delay(500)
  }

  await commands('opc set mob-construction off world')
  await buildSnowGolem()
  assert.equal(await serverMatches('if entity @e[type=minecraft:snow_golem]'), false,
    'mob construction off: a snow golem was built')
  await expectServerBlockState([12, 102, -5], 'carved_pumpkin', 'mob construction off: the pattern was consumed')

  await commands('opc set mob-construction on world')
  await buildSnowGolem()
  assert.equal(await serverMatches('if entity @e[type=minecraft:snow_golem]'), true,
    'mob construction on: no snow golem was built')
  await expectServerBlockState([12, 102, -5], 'air', 'mob construction on: the pattern was not consumed')
  await commands('kill @e[type=minecraft:snow_golem]', 'fill 12 100 -5 12 102 -5 air')
  console.log('PASS mob-construction')
}

async function testBlockOriginExplosions () {
  const position = [3, 100, 3]
  await commands(
    'tp PhysicsBot 0 100 0',
    'clear PhysicsBot',
    'opc set player-block-interactions on world',
    'opc set block-origin-explosions off world',
    'setblock 3 100 3 minecraft:respawn_anchor[charges=1]'
  )
  await bot.unequip('hand')
  await useBlock(position)
  await delay(700)
  await expectBlock(position, 'respawn_anchor', 'block-origin explosions off')

  await commands(
    'opc set block-origin-explosions on world',
    'setblock 3 100 3 minecraft:respawn_anchor[charges=1]'
  )
  await useBlock(position)
  await delay(700)
  await expectBlock(position, 'air', 'block-origin explosions on')
  await resetArea()
  console.log('PASS block-origin-explosions')
}

async function testWaterFlow () {
  await commands(
    'opc set water-flow off world',
    'fill 4 100 -1 7 100 1 air',
    'fill 4 99 -1 7 99 1 stone',
    'setblock 5 100 0 minecraft:water'
  )
  await delay(1400)
  await expectBlock([6, 100, 0], 'air', 'water flow off')

  await commands(
    'opc set water-flow on world',
    'fill 4 100 -1 7 100 1 air',
    'setblock 5 100 0 minecraft:water'
  )
  await delay(1400)
  await expectBlock([6, 100, 0], 'water', 'water flow on')
  await commands('fill 3 100 -2 8 102 2 air')
  console.log('PASS water-flow')
}

async function testLavaFlow () {
  await commands(
    'opc set lava-flow off world',
    'fill 4 100 2 7 100 4 air',
    'fill 4 99 2 7 99 4 stone',
    'setblock 5 100 3 minecraft:lava'
  )
  await delay(3200)
  await expectBlock([6, 100, 3], 'air', 'lava flow off')

  await commands(
    'opc set lava-flow on world',
    'fill 4 100 2 7 100 4 air',
    'setblock 5 100 3 minecraft:lava'
  )
  await delay(3200)
  await expectBlock([6, 100, 3], 'lava', 'lava flow on')
  await commands('fill 3 100 2 8 102 5 air')
  console.log('PASS lava-flow')
}

async function testFluidReactions () {
  await commands(
    'opc set water-flow off world',
    'opc set lava-flow off world',
    'opc set fluid-reactions off world',
    'fill 24 100 2 27 101 4 air',
    'setblock 25 100 3 minecraft:water',
    'setblock 26 100 3 minecraft:lava'
  )
  await delay(800)
  await expectBlock([26, 100, 3], 'lava', 'fluid reactions off')

  await commands(
    'opc set fluid-reactions on world',
    'fill 24 100 2 27 101 4 air',
    'setblock 25 100 3 minecraft:water',
    'setblock 26 100 3 minecraft:lava'
  )
  await delay(800)
  await expectBlock([26, 100, 3], 'obsidian', 'fluid reactions on')
  await commands('opc set water-flow on world', 'opc set lava-flow on world')
  console.log('PASS fluid-reactions')
}

async function testBlockUpdates () {
  await commands(
    'opc set block-updates off world',
    'setblock 12 100 3 minecraft:stone',
    'setblock 12 101 3 minecraft:torch',
    'setblock 12 100 3 air'
  )
  await delay(600)
  await expectBlock([12, 101, 3], 'torch', 'block updates off')

  await commands(
    'opc set block-updates on world',
    'setblock 12 100 3 minecraft:stone',
    'setblock 12 101 3 minecraft:torch',
    'setblock 12 100 3 air'
  )
  await delay(600)
  await expectBlock([12, 101, 3], 'air', 'block updates on')
  console.log('PASS block-updates')
}

async function testSpongeAbsorption () {
  await commands(
    'opc set water-flow off world',
    'opc set sponge-absorb off world',
    'fill 17 100 2 20 102 4 minecraft:water',
    'setblock 18 101 3 minecraft:sponge'
  )
  await delay(600)
  await expectBlock([19, 101, 3], 'water', 'sponge absorption off')

  await commands(
    'opc set sponge-absorb on world',
    'setblock 18 101 3 air',
    'fill 17 100 2 20 102 4 minecraft:water',
    'setblock 18 101 3 minecraft:sponge'
  )
  await delay(600)
  await expectBlock([19, 101, 3], 'air', 'sponge absorption on')
  await commands('opc set water-flow on world')
  console.log('PASS sponge-absorb')
}

async function testTntPriming () {
  await commands(
    'opc set explosion-block-damage off world',
    'opc set tnt-prime off world',
    'setblock 23 100 3 air',
    'setblock 24 100 3 minecraft:tnt',
    'setblock 23 100 3 minecraft:redstone_block'
  )
  await delay(600)
  await expectBlock([24, 100, 3], 'tnt', 'TNT priming off')

  await commands(
    'opc set tnt-prime on world',
    'setblock 23 100 3 air',
    'setblock 24 100 3 minecraft:tnt',
    'setblock 23 100 3 minecraft:redstone_block'
  )
  await delay(600)
  await expectNotBlock([24, 100, 3], 'tnt', 'TNT priming on')
  await commands('execute in minecraft:overworld run kill @e[type=minecraft:tnt]')
  console.log('PASS tnt-prime')
}

async function testRedstone () {
  await commands(
    'opc set redstone off world',
    'setblock 9 100 0 air',
    'setblock 10 100 0 minecraft:redstone_lamp',
    'setblock 9 100 0 minecraft:redstone_block'
  )
  await delay(700)
  assert.equal((await block(10, 100, 0)).getProperties().lit, false, 'redstone off: lamp lit')

  await commands(
    'opc set redstone on world',
    'setblock 9 100 0 air',
    'setblock 10 100 0 minecraft:redstone_lamp',
    'setblock 9 100 0 minecraft:redstone_block'
  )
  await delay(700)
  assert.equal((await block(10, 100, 0)).getProperties().lit, true, 'redstone on: lamp stayed dark')
  console.log('PASS redstone')
}

async function testPistons () {
  await commands(
    'opc set pistons off world',
    'fill 13 100 -1 17 102 1 air',
    'setblock 15 100 0 minecraft:piston[facing=east]',
    'setblock 16 100 0 minecraft:stone',
    'setblock 14 100 0 minecraft:redstone_block'
  )
  await delay(700)
  await expectBlock([16, 100, 0], 'stone', 'pistons off')

  await commands(
    'opc set pistons on world',
    'fill 13 100 -1 17 102 1 air',
    'setblock 15 100 0 minecraft:piston[facing=east]',
    'setblock 16 100 0 minecraft:stone',
    'setblock 14 100 0 minecraft:redstone_block'
  )
  await delay(700)
  await expectBlock([17, 100, 0], 'stone', 'pistons on')
  console.log('PASS pistons')
}

async function testHangingMangroveMaturation () {
  const position = [27, 105, 0]
  await commands(
    'gamerule minecraft:random_tick_speed 0',
    'opc set tree-growth off world',
    'setblock 27 106 0 minecraft:mangrove_leaves[persistent=true]',
    'setblock 27 105 0 minecraft:mangrove_propagule[age=0,hanging=true,stage=0,waterlogged=false]',
    'gamerule minecraft:random_tick_speed 3000'
  )
  await delay(1400)
  assert.equal(Number((await block(...position)).getProperties().age), 0,
    'tree growth off: hanging mangrove propagule matured')

  await commands(
    'gamerule minecraft:random_tick_speed 0',
    'setblock 27 105 0 minecraft:mangrove_propagule[age=2,hanging=true,stage=0,waterlogged=false]'
  )
  assert.equal(Number((await block(...position)).getProperties().age), 2,
    'tree growth off: explicit block-state command was reverted')

  await commands(
    'opc set tree-growth on world',
    'gamerule minecraft:random_tick_speed 3000'
  )
  await delay(1400)
  assert.equal(Number((await block(...position)).getProperties().age), 4,
    'tree growth on: hanging mangrove propagule did not mature')
  await commands('gamerule minecraft:random_tick_speed 3')
  console.log('PASS hanging mangrove propagule maturation')
}

async function equipEnderEye () {
  await commands('clear PhysicsBot minecraft:ender_eye', 'give PhysicsBot minecraft:ender_eye 1')
  await delay(350)
  const eye = bot.inventory.items().find(item => item.name === 'ender_eye')
  assert.ok(eye, 'Mineflayer did not receive an Eye of Ender')
  await bot.equip(eye, 'hand')
  await delay(100)
}

async function testEndPortalFrameFilling () {
  const position = [2, 101, 0]
  await commands(
    'tp PhysicsBot 2 100 2',
    'opc set end-portal-frame-filling off world',
    'setblock 2 101 0 minecraft:end_portal_frame[facing=north,eye=false]'
  )
  await equipEnderEye()
  await useBlock(position)
  await delay(500)
  assert.equal(String((await block(...position)).getProperties().eye), 'false',
    'end portal frame filling off: Eye of Ender was inserted')

  await commands(
    'opc set end-portal-frame-filling on world',
    'setblock 2 101 0 minecraft:end_portal_frame[facing=north,eye=false]'
  )
  await equipEnderEye()
  await useBlock(position)
  await delay(500)
  assert.equal(String((await block(...position)).getProperties().eye), 'true',
    'end portal frame filling on: Eye of Ender was not inserted')
  await commands('clear PhysicsBot minecraft:ender_eye', 'setblock 2 101 0 air', 'tp PhysicsBot 0 100 0')
  console.log('PASS end-portal-frame-filling')
}

async function testGlowBerryPicking () {
  const position = [2, 102, 0]
  async function ripeVine () {
    await commands(
      'setblock 2 103 0 minecraft:stone',
      'setblock 2 102 0 minecraft:cave_vines[age=25,berries=true]'
    )
    await delay(250)
  }

  await commands('clear PhysicsBot', 'opc set glow-berry-picking off world')
  await bot.unequip('hand')
  await ripeVine()
  await useBlock(position)
  await delay(500)
  assert.equal(String((await block(...position)).getProperties().berries), 'true',
    'glow berry picking off: ripe cave vines were harvested')

  await commands('opc set glow-berry-picking on world')
  await ripeVine()
  await useBlock(position)
  await delay(500)
  assert.equal(String((await block(...position)).getProperties().berries), 'false',
    'glow berry picking on: ripe cave vines were not harvested')
  await commands('clear PhysicsBot', 'setblock 2 102 0 air', 'setblock 2 103 0 air')
  console.log('PASS glow-berry-picking')
}

function nearbyArrows () {
  return nearbyEntities('arrow')
}

async function launchArrowAtBlock () {
  await commands(
    'kill @e[type=minecraft:arrow]',
    'fill 7 101 -1 7 103 1 minecraft:stone',
    'summon minecraft:arrow 3 102 0 {Motion:[2.0d,0.0d,0.0d],NoGravity:1b,Tags:["opc-projectile-test"]}'
  )
  await delay(800)
}

async function testBlockHitProjectileRemoval () {
  await commands('opc set block-hit-projectile-removal off world')
  await launchArrowAtBlock()
  assert.ok(nearbyArrows().length > 0,
    'block-hit projectile removal off: arrow did not remain after hitting the block')

  await commands('opc set block-hit-projectile-removal on world')
  await launchArrowAtBlock()
  assert.equal(nearbyArrows().length, 0,
    'block-hit projectile removal on: arrow remained after hitting the block')
  await commands('kill @e[type=minecraft:arrow]', 'fill 7 101 -1 7 103 1 air')
  console.log('PASS block-hit-projectile-removal')
}

async function testExplosionDamage () {
  await commands(
    'opc set tnt-prime on world',
    'opc set entity-explosion-prime on world',
    'opc set explosion-block-damage off world',
    'fill 20 99 -1 22 101 1 minecraft:white_wool',
    'summon minecraft:tnt 21 102 0 {fuse:0}'
  )
  await delay(5200)
  await expectBlock([21, 101, 0], 'white_wool', 'explosion block damage off')

  await commands(
    'opc material explosion-block-damage white_wool on world',
    'fill 20 99 -1 22 101 1 minecraft:white_wool',
    'summon minecraft:tnt 21 102 0 {fuse:0}'
  )
  await delay(5200)
  await expectNotBlock([21, 101, 0], 'white_wool', 'explosion white wool override on')

  await commands(
    'opc material explosion-block-damage white_wool clear world',
    'opc set explosion-block-damage on world',
    'fill 20 99 -1 22 101 1 minecraft:white_wool',
    'summon minecraft:tnt 21 102 0 {fuse:0}'
  )
  await delay(5200)
  await expectNotBlock([21, 101, 0], 'white_wool', 'explosion block damage on')
  console.log('PASS explosion-block-damage')
}

async function testEntityExplosionPrime () {
  await commands(
    'opc set entity-explosion-prime off world',
    'opc set explosion-block-damage on world',
    'fill 20 99 -1 22 101 1 minecraft:white_wool',
    'summon minecraft:tnt 21 102 0 {fuse:0}'
  )
  await delay(5200)
  await expectBlock([21, 101, 0], 'white_wool', 'entity explosion priming off')

  await commands(
    'opc set entity-explosion-prime on world',
    'fill 20 99 -1 22 101 1 minecraft:white_wool',
    'summon minecraft:tnt 21 102 0 {fuse:0}'
  )
  await delay(5200)
  await expectNotBlock([21, 101, 0], 'white_wool', 'entity explosion priming on')
  await commands('kill @e[type=minecraft:tnt]')
  console.log('PASS entity-explosion-prime')
}

async function testSpawnerMobSpawning () {
  const spawner = 'minecraft:spawner{Delay:0s,MinSpawnDelay:0s,MaxSpawnDelay:0s,SpawnCount:1s,MaxNearbyEntities:99s,RequiredPlayerRange:16s,SpawnRange:1s,SpawnData:{entity:{id:"minecraft:armor_stand",Pos:[25.5d,100.0d,1.5d]}},SpawnPotentials:[{data:{entity:{id:"minecraft:armor_stand",Pos:[25.5d,100.0d,1.5d]}},weight:1}]}'
  await commands(
    'difficulty normal',
    'gamerule spawner_blocks_work true',
    'gamemode survival PhysicsBot',
    'tp PhysicsBot 25 100 0',
    'kill @e[type=minecraft:armor_stand]',
    'opc set spawner-mob-spawning off world',
    `setblock 25 100 3 ${spawner}`
  )
  await delay(2600)
  assert.equal(nearbyEntities('armor_stand').length, 0,
    'spawner mob spawning off: an armor stand was spawned')

  await commands(
    'setblock 25 100 3 air',
    'kill @e[type=minecraft:armor_stand]',
    'opc set spawner-mob-spawning on world',
    `setblock 25 100 3 ${spawner}`
  )
  await waitForNearbyEntity('armor_stand')
  await commands(
    'setblock 25 100 3 air',
    'kill @e[type=minecraft:armor_stand]',
    'gamemode creative PhysicsBot',
    'tp PhysicsBot 0 100 0',
    'difficulty peaceful'
  )
  console.log('PASS spawner-mob-spawning')
}

async function testTrialSpawnerMobSpawning () {
  const spawner = 'minecraft:trial_spawner{required_player_range:16,target_cooldown_length:1,normal_config:{spawn_range:1,total_mobs:1.0f,simultaneous_mobs:1.0f,ticks_between_spawn:1,spawn_potentials:[{data:{entity:{id:"minecraft:armor_stand"}},weight:1}]}}'
  await commands(
    'difficulty normal',
    'gamerule spawner_blocks_work true',
    'gamemode survival PhysicsBot',
    'tp PhysicsBot 25 100 -3',
    'kill @e[type=minecraft:armor_stand]',
    'opc set spawner-mob-spawning off world',
    `setblock 25 100 -6 ${spawner}`
  )
  await delay(3000)
  assert.equal(nearbyEntities('armor_stand').length, 0,
    'spawner mob spawning off: a trial spawner spawned an armor stand')

  await commands('opc set spawner-mob-spawning on world')
  await waitForNearbyEntity('armor_stand', 8000)
  await commands(
    'setblock 25 100 -6 air',
    'kill @e[type=minecraft:armor_stand]',
    'gamemode creative PhysicsBot',
    'tp PhysicsBot 0 100 0',
    'difficulty peaceful'
  )
  console.log('PASS trial spawner mob spawning')
}

async function testVerticalPlantAge () {
  const cane = [19, 105, -7]
  const cactus = [22, 105, -7]
  async function plant (age) {
    await commands(
      'gamerule minecraft:random_tick_speed 0',
      'fill 18 104 -8 23 107 -6 air',
      'fill 18 103 -8 23 103 -6 minecraft:stone',
      'fill 18 104 -8 20 104 -6 minecraft:stone',
      'setblock 19 104 -7 minecraft:sand',
      'setblock 20 104 -7 minecraft:water',
      'setblock 22 104 -7 minecraft:sand',
      `setblock ${cane.join(' ')} minecraft:sugar_cane[age=${age}]`,
      `setblock ${cactus.join(' ')} minecraft:cactus[age=${age}]`,
      'gamerule minecraft:random_tick_speed 3000'
    )
    await delay(1600)
    await commands('gamerule minecraft:random_tick_speed 0')
  }

  await commands('opc set vertical-plant-growth off world')
  await plant(0)
  await expectServerBlockState(cane, 'sugar_cane[age=0]', 'vertical plant growth off: sugar cane age advanced')
  await expectServerBlockState(cactus, 'cactus[age=0]', 'vertical plant growth off: cactus age advanced')
  await expectServerBlockState([19, 106, -7], 'air', 'vertical plant growth off: sugar cane grew')
  await expectServerBlockState([22, 106, -7], 'air', 'vertical plant growth off: cactus grew')

  await commands(`setblock ${cane.join(' ')} minecraft:sugar_cane[age=7]`)
  await expectServerBlockState(cane, 'sugar_cane[age=7]',
    'vertical plant growth off: explicit block-state command was reverted')

  // Start at age 15 so a single random tick grows a section instead of waiting for 16.
  await commands('opc set vertical-plant-growth on world')
  await plant(15)
  await expectServerBlockState([19, 106, -7], 'sugar_cane', 'vertical plant growth on: sugar cane did not grow')
  assert.equal(await serverMatches('unless block 22 106 -7 minecraft:air'), true,
    'vertical plant growth on: cactus did not grow')
  await commands('fill 18 103 -8 23 108 -6 air', 'gamerule minecraft:random_tick_speed 3')
  console.log('PASS vertical plant age')
}

async function testTrialVaults () {
  const vault = [0, 100, -6]
  const vaultState = state => serverMatches(`if block ${vault.join(' ')} minecraft:vault[vault_state=${state}]`)
  const holdsKey = () => serverMatches('if items entity PhysicsBot weapon.mainhand minecraft:trial_key')

  await commands(
    'gamemode survival PhysicsBot',
    'tp PhysicsBot 0 100 -4 180 30',
    'clear PhysicsBot minecraft:trial_key',
    'give PhysicsBot minecraft:trial_key 1',
    'opc set trial-vaults off world',
    `setblock ${vault.join(' ')} minecraft:vault`
  )
  await delay(350)
  const key = bot.inventory.items().find(item => item.name === 'trial_key')
  assert.ok(key, 'Mineflayer did not receive a trial key')
  await bot.equip(key, 'hand')
  await delay(1500)
  assert.equal(await vaultState('inactive'), true, 'trial vaults off: the vault activated for a nearby player')

  await commands('opc set trial-vaults on world')
  await delay(1500)
  assert.equal(await vaultState('active'), true, 'trial vaults on: the vault did not activate')

  await commands('opc set trial-vaults off world')
  await useBlock(vault, new Vec3(0, 1, 0))
  await delay(500)
  assert.equal(await holdsKey(), true, 'trial vaults off: the trial key was consumed')
  assert.equal(await vaultState('active'), true, 'trial vaults off: the vault started unlocking')

  await commands('opc set trial-vaults on world')
  await useBlock(vault, new Vec3(0, 1, 0))
  await delay(300)
  assert.equal(await holdsKey(), false, 'trial vaults on: the trial key was not consumed')
  assert.equal(await vaultState('active'), false, 'trial vaults on: the vault did not unlock')

  await commands(
    `setblock ${vault.join(' ')} air`,
    'kill @e[type=minecraft:item]',
    'clear PhysicsBot minecraft:trial_key',
    'gamemode creative PhysicsBot',
    'tp PhysicsBot 0 100 0'
  )
  console.log('PASS trial-vaults')
}

async function testOxygenDepletion () {
  async function airChangesWithOxygenRule (enabled) {
    await commands(
      `opc set oxygen-depletion ${enabled ? 'on' : 'off'} world`,
      'fill 14 100 -1 16 103 1 minecraft:water',
      'kill @e[type=minecraft:pig]',
      'summon minecraft:pig 15 101 0 {NoAI:1b,Invulnerable:1b,Air:1s}'
    )
    await delay(800)
    const expected = nextChatMessage(enabled ? /opc-oxygen-depleted/ : /opc-oxygen-preserved/)
    command(enabled
      ? 'execute as @e[type=minecraft:pig,limit=1] unless data entity @s {Air:1s} run tellraw PhysicsBot {"text":"opc-oxygen-depleted"}'
      : 'execute as @e[type=minecraft:pig,limit=1] if data entity @s {Air:1s} run tellraw PhysicsBot {"text":"opc-oxygen-preserved"}')
    await expected
  }

  await airChangesWithOxygenRule(false)
  await airChangesWithOxygenRule(true)
  await commands(
    'kill @e[type=minecraft:pig]',
    'fill 14 100 -1 16 103 1 air'
  )
  console.log('PASS oxygen-depletion')
}

async function testFireAndFreezeDamage () {
  async function damageWithRule (rule, damageType, message) {
    await commands('gamemode survival PhysicsBot', `opc set ${rule} off world`)
    await resetBotHealth()
    await commands(`damage PhysicsBot 4 minecraft:${damageType}`)
    await delay(350)
    assert.equal(bot.health, 20, `${message} off: the bot took damage`)

    await commands(`opc set ${rule} on world`)
    await resetBotHealth()
    await commands(`damage PhysicsBot 4 minecraft:${damageType}`)
    await delay(350)
    assert.ok(bot.health < 20, `${message} on: the bot did not take damage`)
  }

  await damageWithRule('fire-damage', 'in_fire', 'fire damage')
  await damageWithRule('freeze-damage', 'freeze', 'freezing damage')
  await commands('gamemode creative PhysicsBot')
  await resetBotHealth()
  console.log('PASS fire-damage and freeze-damage')
}

async function start () {
  await stat(pluginJar)
  if (bridged) await stat(driverJar)
  await ensurePaper()
  const serverDir = configuredServerDirectory
    ? resolve(configuredServerDirectory)
    : await mkdtemp(join(tmpdir(), 'openphysicscontrol-mineflayer-'))
  await mkdir(join(serverDir, 'plugins'), { recursive: true })
  await mkdir(join(serverDir, 'plugins/OpenPhysicsControl'), { recursive: true })
  await mkdir(join(serverDir, 'plugins/PhysicsControl/triggers'), { recursive: true })
  await writeFile(join(serverDir, 'plugins/OpenPhysicsControl/default-rules.yml'),
    'note-blocks: false\nmaterial-overrides:\n  gravity:\n    GRAVEL: false\n')
  await writeFile(join(serverDir, 'plugins/PhysicsControl/triggers/world.yml'),
    'GRAVEL_FALLING: false\nBONE_MEAL_USAGE: false\nEND_PORTAL_FRAMES_FILLING: false\nGLOW_BERRIES_PICKING: false\nBLOCK_HIT_PROJECTILES_REMOVING: true\n')
  await writeFile(join(serverDir, 'plugins/PhysicsControl/config.yml'), 'world_nether:\n  WHEAT_GROWING: false\n')
  await copyFile(pluginJar, join(serverDir, 'plugins/OpenPhysicsControl.jar'))
  await copyFile(paperCache, join(serverDir, 'paper.jar'))
  for (const plugin of target.plugins) {
    await copyFile(join(testServers, plugin.name), join(serverDir, 'plugins', plugin.name))
  }
  if (bridged) await copyFile(driverJar, join(serverDir, 'plugins/OpenPhysicsControlTestDriver.jar'))
  await writeFile(join(serverDir, 'eula.txt'), 'eula=true\n')
  await writeFile(join(serverDir, 'server.properties'), [
    'online-mode=false',
    // Vanilla 26.3 enables the allowlist by default.
    'white-list=false',
    `server-port=${PORT}`,
    'gamemode=creative',
    'difficulty=peaceful',
    'spawn-protection=0',
    'view-distance=4',
    'simulation-distance=4',
    'generate-structures=false',
    'level-seed=OpenPhysicsControl',
    'motd=OpenPhysicsControl Mineflayer Test'
  ].join('\n') + '\n')

  server = spawn(java, ['-Xms512M', '-Xmx1G', '-jar', 'paper.jar', '--nogui'], {
    cwd: serverDir,
    stdio: ['pipe', 'pipe', 'pipe']
  })
  server.stdout.on('data', chunk => process.stdout.write(`[paper] ${chunk}`))
  server.stderr.on('data', chunk => process.stderr.write(`[paper] ${chunk}`))
  await waitForServer(/Enabling OpenPhysicsControl v[^\r\n]+/)
  await waitForServer(/Done \([^)]*\)!/)
  await testRuleStorage(serverDir)
  await commands('opc set gravity on world', 'opc set bone-meal on world')

  await commands(
    'execute in minecraft:overworld run forceload add -16 -16 47 31'
  )
  await delay(1000)
  await commands(
    'execute in minecraft:overworld run fill -8 100 -8 30 116 8 air',
    'execute in minecraft:overworld run fill -8 99 -8 30 99 8 stone',
    'setworldspawn 0 100 0'
  )

  bot = mineflayer.createBot({
    host: '127.0.0.1',
    port: PORT,
    username: 'PhysicsBot',
    auth: 'offline',
    version: target.client
  })
  if (bridged) {
    // The bridged client's movement packets fail server validation; the suite moves the bot only by command.
    const write = bot._client.write.bind(bot._client)
    const movement = new Set(['position', 'look', 'position_look', 'flying'])
    bot._client.write = (name, params) => {
      if (!movement.has(name)) write(name, params)
    }
  }
  bot.on('kicked', reason => console.error('Mineflayer kicked:', reason))
  bot.on('error', error => console.error('Mineflayer error:', error))
  await new Promise((resolve, reject) => {
    const timeout = setTimeout(() => reject(new Error('Mineflayer spawn timeout')), 30000)
    bot.once('spawn', () => {
      clearTimeout(timeout)
      resolve()
    })
  })
  await commands('tp PhysicsBot 0 100 0')
  await bot.waitForChunksToLoad()
  await resetArea()

  const suite = [
    testLocalizedMenu,
    testGravity,
    testMaterialOverrides,
    testPlacedBlockConnections,
    testPlayerBlockInteractions,
    testPlayerEntityInteractions,
    testHangingEntityDetachment,
    testCushionDetachment,
    testEntityCollisions,
    testMobConstruction,
    testBlockOriginExplosions,
    testWaterFlow,
    testLavaFlow,
    testFluidReactions,
    testBlockUpdates,
    testSpongeAbsorption,
    testTntPriming,
    testRedstone,
    testPistons,
    testHangingMangroveMaturation,
    testVerticalPlantAge,
    testEndPortalFrameFilling,
    testGlowBerryPicking,
    testBlockHitProjectileRemoval,
    testSpawnerMobSpawning,
    testTrialSpawnerMobSpawning,
    testTrialVaults,
    testOxygenDepletion,
    testFireAndFreezeDamage,
    testEntityExplosionPrime,
    testExplosionDamage
  ]
  // OPC_TEST_ONLY=testGravity,testPistons runs a subset while debugging.
  const only = process.env.OPC_TEST_ONLY?.split(',').map(name => name.trim()).filter(Boolean)
  for (const test of suite) {
    if (!only || only.includes(test.name)) await test()
  }
  if (only) return
  console.log(`PASS Mineflayer suite on Paper ${target.server} build ${target.build}`
    + (bridged ? ` via a ${target.client} client bridged by ViaVersion/ViaBackwards` : ''))
}

async function stop () {
  if (bot) bot.quit('tests complete')
  if (!server || server.exitCode !== null) return
  command('stop')
  await Promise.race([
    new Promise(resolve => server.once('exit', resolve)),
    delay(20000).then(() => server.kill('SIGTERM'))
  ])
}

try {
  await start()
} catch (error) {
  console.error(error.stack ?? error)
  process.exitCode = 1
} finally {
  await stop()
}
