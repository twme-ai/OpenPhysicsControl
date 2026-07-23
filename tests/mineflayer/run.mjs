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

const TEST_VERSION = '1.21.11'
const PAPER_BUILD = 132
const PAPER_SHA256 = '5ffef465eeeb5f2a3c23a24419d97c51afd7dbb4923ff42df9a3f58bba1ccfba'
const PAPER_URL = 'https://fill-data.papermc.io/v1/objects/5ffef465eeeb5f2a3c23a24419d97c51afd7dbb4923ff42df9a3f58bba1ccfba/paper-1.21.11-132.jar'
const PORT = Number(process.env.OPC_TEST_PORT ?? 25578)
const here = dirname(fileURLToPath(import.meta.url))
const project = resolve(here, '../..')
const pluginJar = join(project, 'build/libs/OpenPhysicsControl.jar')
const paperCache = join(project, 'build/test-servers', `paper-${TEST_VERSION}-${PAPER_BUILD}.jar`)
const java = process.env.JAVA_HOME ? join(process.env.JAVA_HOME, 'bin/java') : 'java'

let server
let bot
let serverOutput = ''

function delay (milliseconds) {
  return new Promise(resolve => setTimeout(resolve, milliseconds))
}

async function sha256 (path) {
  const hash = createHash('sha256')
  const stream = (await import('node:fs')).createReadStream(path)
  for await (const chunk of stream) hash.update(chunk)
  return hash.digest('hex')
}

async function ensurePaper () {
  await mkdir(dirname(paperCache), { recursive: true })
  try {
    if (await sha256(paperCache) === PAPER_SHA256) return
  } catch {
    // Cache miss.
  }
  const response = await fetch(PAPER_URL)
  assert.equal(response.ok, true, `Paper download failed: ${response.status}`)
  await pipeline(response.body, createWriteStream(paperCache))
  assert.equal(await sha256(paperCache), PAPER_SHA256, 'Paper checksum mismatch')
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

async function resetArea () {
  await commands(
    'execute in minecraft:overworld run fill -8 100 -8 30 116 8 air',
    'execute in minecraft:overworld run fill -8 99 -8 30 99 8 stone',
    'execute in minecraft:overworld run kill @e[type=minecraft:tnt]'
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

async function testRuleStorage (serverDir) {
  const dataDirectory = join(serverDir, 'plugins/OpenPhysicsControl')
  const defaults = await readFile(join(dataDirectory, 'default-rules.yml'), 'utf8')
  assert.match(defaults, /^gravity: true$/m, 'missing default rule was not added')
  assert.match(defaults, /^note-blocks: false$/m, 'custom default rule was overwritten')

  const worldFiles = (await readdir(join(dataDirectory, 'worlds'))).sort()
  assert.deepEqual(worldFiles, ['world.yml', 'world_nether.yml', 'world_the_end.yml'])
  for (const file of worldFiles) {
    const rules = await readFile(join(dataDirectory, 'worlds', file), 'utf8')
    assert.match(rules, /^gravity: true$/m, `${file} did not inherit a missing true default`)
    assert.match(rules, /^note-blocks: false$/m, `${file} did not inherit the configured false default`)
  }
  console.log('PASS default rules and world-name storage')
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
  assert.deepEqual(occupiedSlots(categoryMenu), [11, 12, 13, 14, 15], 'categories are not centered')
  assert.match(itemText(categoryMenu.slots[11]), /運作中/)
  assert.match(itemText(categoryMenu.slots[11]), /已停止/)

  const categories = [
    {
      slot: 11,
      title: /方塊與訊號/,
      size: 27,
      rules: [1, 2, 3, 4, 5, 6, 7, 10, 11, 12, 13, 14, 15, 16]
    },
    {
      slot: 12,
      title: /火焰、氣候與時間/,
      size: 27,
      rules: [0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 14, 15, 16, 17]
    },
    {
      slot: 13,
      title: /植物與生長/,
      size: 27,
      rules: [1, 2, 3, 4, 5, 6, 7, 10, 11, 12, 13, 14, 15, 16]
    },
    {
      slot: 14,
      title: /實體與玩家/,
      size: 27,
      rules: Array.from({ length: 18 }, (_, slot) => slot)
    },
    {
      slot: 15,
      title: /機械與處理/,
      size: 18,
      rules: [0, 1, 2, 3, 5, 6, 7, 8]
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
    assert.deepEqual(occupiedSlots(categoryMenu), [11, 12, 13, 14, 15])
  }

  bot.chat('/pc language auto')
  await delay(300)
  console.log('PASS centered categorized menu and explicit states')
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

async function testExplosionDamage () {
  await commands(
    'opc set tnt-prime on world',
    'opc set explosion-block-damage off world',
    'fill 20 99 -1 22 101 1 minecraft:white_wool',
    'summon minecraft:tnt 21 102 0 {fuse:0}'
  )
  await delay(5200)
  await expectBlock([21, 101, 0], 'white_wool', 'explosion block damage off')

  await commands(
    'opc set explosion-block-damage on world',
    'fill 20 99 -1 22 101 1 minecraft:white_wool',
    'summon minecraft:tnt 21 102 0 {fuse:0}'
  )
  await delay(5200)
  await expectNotBlock([21, 101, 0], 'white_wool', 'explosion block damage on')
  console.log('PASS explosion-block-damage')
}

async function start () {
  await stat(pluginJar)
  await ensurePaper()
  const serverDir = await mkdtemp(join(tmpdir(), 'openphysicscontrol-mineflayer-'))
  await mkdir(join(serverDir, 'plugins'), { recursive: true })
  await mkdir(join(serverDir, 'plugins/OpenPhysicsControl'), { recursive: true })
  await writeFile(join(serverDir, 'plugins/OpenPhysicsControl/default-rules.yml'), 'note-blocks: false\n')
  await copyFile(pluginJar, join(serverDir, 'plugins/OpenPhysicsControl.jar'))
  await copyFile(paperCache, join(serverDir, 'paper.jar'))
  await writeFile(join(serverDir, 'eula.txt'), 'eula=true\n')
  await writeFile(join(serverDir, 'server.properties'), [
    'online-mode=false',
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
    version: TEST_VERSION
  })
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

  await testLocalizedMenu()
  await testGravity()
  await testWaterFlow()
  await testLavaFlow()
  await testFluidReactions()
  await testBlockUpdates()
  await testSpongeAbsorption()
  await testTntPriming()
  await testRedstone()
  await testPistons()
  await testHangingMangroveMaturation()
  await testExplosionDamage()
  console.log(`PASS Mineflayer suite on Paper ${TEST_VERSION} build ${PAPER_BUILD}`)
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
