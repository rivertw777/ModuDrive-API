// .docs/perf-test/001-upload.md — uploads through the WEB in Chromium the way a person does: a member signed up and
// logged in through the API (codes read from Mailpit), the 업로드 button, the file picker, and the status panel's
// "완료" as the end. Every request goes through the gateway, as the browser sends it.
// Usage (repo root, infra + services + the WEB build served on CLIENT_URL):
//   node .scripts/perf-test/001-upload/web/upload-web.mjs files             — make the scenario files under FILES
//   node .scripts/perf-test/001-upload/web/upload-web.mjs rm-files          — delete them
//   node .scripts/perf-test/001-upload/web/upload-web.mjs <label> <rounds> <big|small|mixed> [users]
//   node .scripts/perf-test/001-upload/web/upload-web.mjs cleanup           — remove every bench member and its data
// Each round signs up new members (a fresh drive each), uploads at once, then removes them with their blocks.
import { chromium } from 'playwright'
import { execFileSync } from 'node:child_process'
import crypto from 'node:crypto'
import fs from 'node:fs'
import os from 'node:os'
import path from 'node:path'

const WEB = process.env.WEB ?? 'http://localhost:3000'
const API = process.env.API ?? 'http://localhost:10001'
const MAILPIT = process.env.MAILPIT ?? 'http://localhost:8025'
const FILES = process.env.FILES ?? path.join(os.homedir(), 'upload-bench-files')
const RESULTS = path.join(path.dirname(new URL(import.meta.url).pathname), '..', 'results')
const PASSWORD = 'Bench-password-1!'
const EMAIL_DOMAIN = 'bench.local'
const KiB = 1024, MiB = 1024 * KiB, BLOCK = 4 * MiB
const DONE = /항목 업로드 완료$|개 실패$/
const UPLOAD_TIMEOUT_MS = 60 * 60_000
// TIMELINE=1: also keep every request's start and end, CORS preflights included (results/<label>.timeline.jsonl).
const TIMELINE = !!process.env.TIMELINE
// The same work folder as upload.py's MIXED: (subfolder, extension, files, smallest, largest bytes).
const MIXED = [['문서', 'pdf', 60, 100 * KiB, 5 * MiB], ['문서', 'docx', 50, 20 * KiB, 2 * MiB],
  ['문서/보고서', 'xlsx', 40, 10 * KiB, 1 * MiB], ['문서/보고서', 'hwp', 20, 30 * KiB, 3 * MiB],
  ['코드/src/main', 'java', 250, 1 * KiB, 20 * KiB], ['코드', 'json', 30, 1 * KiB, 50 * KiB],
  ['사진', 'jpg', 60, 2 * MiB, 6 * MiB], ['사진/스크린샷', 'png', 20, 100 * KiB, 3 * MiB],
  ['음악', 'mp3', 15, 3 * MiB, 10 * MiB], ['압축', 'zip', 5, 10 * MiB, 60 * MiB],
  ['영상', 'mp4', 1, 120 * MiB, 120 * MiB], ['영상', 'mp4', 1, 400 * MiB, 400 * MiB]]
const SCENARIOS = {
  big: { menu: '파일 업로드', pick: () => [path.join(FILES, 'big', 'big.bin')] },
  small: { menu: '파일 업로드', pick: () => fs.readdirSync(path.join(FILES, 'small')).sort().map((f) => path.join(FILES, 'small', f)) },
  mixed: { menu: '폴더 업로드', pick: () => path.join(FILES, '혼합 폴더') },
}

const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms))
const run = (cmd, args, input) => execFileSync(cmd, args, { input, encoding: 'utf8', maxBuffer: 64 * MiB })
const psql = (db, sql) => run('docker', ['exec', '-i', 'modudrive-infra-postgres-1', 'psql', '-U', 'modudrive', '-d', db, '-tA', '-v', 'ON_ERROR_STOP=1'], sql).trim()
const median = (xs) => { const s = [...xs].sort((a, b) => a - b); return s.length ? (s.length % 2 ? s[(s.length - 1) / 2] : (s[s.length / 2 - 1] + s[s.length / 2]) / 2) : null }
const round1 = (x) => (x == null ? null : Math.round(x * 10) / 10)

// ---- files ----

function writeRandom(file, size) {
  fs.mkdirSync(path.dirname(file), { recursive: true })
  const fd = fs.openSync(file, 'w')
  const chunk = Buffer.alloc(Math.min(size, BLOCK) || 1)
  for (let left = size; left > 0; left -= chunk.length) {
    crypto.randomFillSync(chunk)
    fs.writeSync(fd, chunk, 0, Math.min(chunk.length, left))
  }
  fs.closeSync(fd)
}

function makeFiles() {
  fs.rmSync(FILES, { recursive: true, force: true })
  writeRandom(path.join(FILES, 'big', 'big.bin'), 1280 * BLOCK)
  for (let k = 0; k < 1000; k++) writeRandom(path.join(FILES, 'small', `f${String(k).padStart(5, '0')}.bin`), 10 * KiB)
  // Seeded sizes: the same folder every time (mulberry32).
  let seed = 0
  const next = () => { seed = (seed + 0x6d2b79f5) | 0; let t = Math.imul(seed ^ (seed >>> 15), 1 | seed); t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t; return ((t ^ (t >>> 14)) >>> 0) / 4294967296 }
  let k = 0
  for (const [sub, ext, n, lo, hi] of MIXED) {
    for (let i = 0; i < n; i++, k++) writeRandom(path.join(FILES, '혼합 폴더', sub, `${ext}-${String(k).padStart(4, '0')}.${ext}`), lo + Math.floor(next() * (hi - lo + 1)))
  }
}

// ---- members ----

const seenMail = new Set()
async function mailCode(email) {
  for (let i = 0; i < 240; i++) {
    const found = await (await fetch(`${MAILPIT}/api/v1/search?query=${encodeURIComponent(`to:"${email}"`)}`)).json()
    const message = (found.messages ?? []).find((m) => !seenMail.has(m.ID))
    if (message) {
      seenMail.add(message.ID)
      const { Text } = await (await fetch(`${MAILPIT}/api/v1/message/${message.ID}`)).json()
      const code = Text.match(/\b\d{6}\b/)?.[0]
      if (code) return code
    }
    await sleep(500)
  }
  throw new Error(`no code mailed to ${email}`)
}

/** A request from the WEB page itself, so the browser sends the Origin and the cookies as the WEB would. */
const call = (page, method, url, body) => page.evaluate(async ([method, url, body]) => {
  const response = await fetch(url, {
    method, credentials: 'include',
    headers: body === undefined ? {} : { 'Content-Type': 'application/json' },
    body: body === undefined ? undefined : JSON.stringify(body),
  })
  const json = await response.json().catch(() => null)
  if (!response.ok) throw new Error(`${method} ${url} ${response.status} ${JSON.stringify(json)}`)
  return json?.data
}, [method, API + url, body])

async function newMember(browser, k) {
  const email = `bench-${crypto.randomUUID()}@${EMAIL_DOMAIN}`
  const context = await browser.newContext()
  const page = await context.newPage()
  await page.goto(`${WEB}/login`)
  await call(page, 'POST', '/api/v1/member/verify-email/request', { email })
  await call(page, 'POST', '/api/v1/member/verify-email/confirm', { email, code: await mailCode(email) })
  await call(page, 'POST', '/api/v1/member/sign-up', { name: `bench${k}`, email, password: PASSWORD })
  const login = await call(page, 'POST', '/api/v1/auth/login', { email, password: PASSWORD })
  if (login?.verificationRequired) {
    await call(page, 'POST', '/api/v1/auth/verify-email/request')
    await call(page, 'POST', '/api/v1/auth/verify-email/confirm', { code: await mailCode(email) })
  }
  const { memberId } = await call(page, 'GET', '/api/v1/auth/session')
  // The drive comes from the sign-up event (member → file-service).
  for (let i = 0; psql('file_db', `select count(*) from namespace where user_id = '${memberId}'`) !== '1'; i++) {
    if (i > 120) throw new Error(`no drive for ${memberId}`)
    await sleep(500)
  }
  await page.goto(`${WEB}/drive`)
  await page.getByRole('button', { name: '업로드', exact: true }).waitFor()
  return { email, memberId, context, page }
}

function removeMembers(memberIds) {
  if (memberIds.length === 0) return
  const ids = memberIds.map((id) => `'${id}'`).join(',')
  const bucket = run('docker', ['exec', 'modudrive-service-storage-service-1', 'printenv', 'STORAGE_S3_BUCKET']).trim()
  for (const id of memberIds) {
    run('docker', ['exec', 'modudrive-infra-localstack-1', 'awslocal', 's3', 'rm', `s3://${bucket}/blocks/${id}/`, '--recursive', '--quiet'])
  }
  psql('file_db', `begin;
    create temp table bn as select id, user_id from namespace where user_id in (${ids});
    create temp table bf as select f.id from file f join bn on bn.id = f.namespace_id;
    delete from file_version_block where version_id in (select v.id from file_version v join bf on bf.id = v.file_id);
    delete from file_version where file_id in (select id from bf);
    delete from file_share where file_id in (select id from bf);
    delete from file_favorite where file_id in (select id from bf);
    delete from file_access where file_id in (select id from bf);
    delete from block where owner_id in (select user_id from bn);
    delete from file where id in (select id from bf);
    delete from namespace where id in (select id from bn);
    commit;`)
  psql('auth_db', `delete from known_device where member_id in (${ids});`)
  psql('member_db', `delete from member_role where member_id in (${ids}); delete from member where id in (${ids});`)
}

function cleanupAll() {
  const ids = psql('member_db', `select id from member where email like 'bench-%@${EMAIL_DOMAIN}'`).split('\n').filter(Boolean)
  removeMembers(ids)
  return ids.length
}

// ---- counters ----

const resetDb = () => psql('file_db', 'select pg_stat_statements_reset()')
const NOISE = ['outbox', 'shedlock', 'processed_event', 'pg_stat_statements']
function dbCalls() {
  const rows = psql('file_db', `select s.calls, s.query from pg_stat_statements s join pg_roles r on r.oid = s.userid
    join pg_database d on d.oid = s.dbid where r.rolname = 'file_service' and d.datname = 'file_db'`)
  return rows.split('\n').filter(Boolean).map((line) => line.split('|'))
    .filter(([, q]) => !NOISE.some((x) => (q ?? '').toLowerCase().includes(x))).reduce((sum, [calls]) => sum + Number(calls), 0)
}
function redisCalls() {
  const info = run('docker', ['exec', 'modudrive-infra-redis-1', 'sh', '-c', 'redis-cli -a "$REDIS_PASSWORD" --no-auth-warning info commandstats'])
  return Object.fromEntries([...info.matchAll(/cmdstat_(\w+):calls=(\d+)/g)].map(([, c, n]) => [c, Number(n)]))
}
function redisDelta(before) {
  const after = redisCalls()
  after.auth -= 1; after.info -= 1 // this read's own
  const byCommand = Object.fromEntries(Object.entries(after).map(([c, n]) => [c, n - (before[c] ?? 0)]).filter(([, n]) => n > 0))
  const inScript = new Set(['incr', 'incrby', 'decr', 'decrby', 'pexpire'])
  return { redis: Object.entries(byCommand).filter(([c]) => !inScript.has(c)).reduce((s, [, n]) => s + n, 0), redis_by_command: byCommand }
}

// ---- one upload ----

function kindOf(request) {
  const url = new URL(request.url())
  if (url.port === '4566' && request.method() === 'PUT') return 'put'
  if (url.origin !== API) return null
  if (url.pathname === '/api/v1/files/batch') return 'batch'
  if (url.pathname === '/api/v1/files/commit') return 'commit'
  if (url.pathname === '/api/v1/storage/blocks/upload-urls') return 'urls'
  return 'other'
}

async function upload(member, scenario) {
  const { page } = member
  const requests = []
  const errors = [] // the first few failed requests, with what came back
  const onFinished = async (request, failed) => {
    const kind = kindOf(request)
    if (!kind) return
    const timing = request.timing()
    const response = failed ? null : await request.response().catch(() => null)
    const entry = { kind, start: timing.startTime, end: timing.startTime + timing.responseEnd, status: response?.status() ?? 0 }
    // A commit that still asked for blocks is a first commit; one that made the versions, a second.
    if (kind === 'commit' && response) {
      const results = (await response.json().catch(() => null))?.data?.results ?? []
      entry.kind = results.some((r) => r.needBlocks?.length) ? 'commit1' : 'commit2'
    }
    if (entry.status === 0 || entry.status >= 400) {
      const detail = failed ? request.failure()?.errorText : (await response?.text().catch(() => '') ?? '').slice(0, 300)
      if (errors.length < 5) errors.push({ kind, method: request.method(), path: new URL(request.url()).pathname, status: entry.status, detail })
    }
    requests.push(entry)
  }
  page.on('requestfinished', (r) => onFinished(r, false))
  page.on('requestfailed', (r) => onFinished(r, true))
  const timeline = TIMELINE ? await recordTimeline(page) : null

  await page.getByRole('button', { name: '업로드', exact: true }).click()
  const chooser = page.waitForEvent('filechooser')
  await page.getByText(SCENARIOS[scenario].menu, { exact: true }).click()
  const picker = await chooser
  const t0 = Date.now()
  await picker.setFiles(SCENARIOS[scenario].pick())
  const header = page.getByText(DONE)
  await header.waitFor({ timeout: UPLOAD_TIMEOUT_MS })
  const t1 = Date.now()
  const headerText = await header.textContent()
  await sleep(500) // the last requestfinished handlers
  page.removeAllListeners('requestfinished')
  page.removeAllListeners('requestfailed')

  const kinds = {}
  for (const kind of ['batch', 'commit1', 'urls', 'put', 'commit2', 'other']) {
    const of = requests.filter((r) => r.kind === kind)
    if (of.length === 0) continue
    kinds[kind] = {
      n: of.length,
      failed: of.filter((r) => r.status === 0 || r.status >= 400).length,
      p50_ms: round1(median(of.map((r) => r.end - r.start))),
      first_ms: round1(Math.min(...of.map((r) => r.start)) - t0),
      last_ms: round1(Math.max(...of.map((r) => r.end)) - t0),
    }
  }
  return { t0, t1, total_ms: t1 - t0, header: headerText, ok: /항목 업로드 완료$/.test(headerText), kinds, errors,
    timeline: timeline?.stop(t0) }
}

/** Every request the page sends, through the DevTools protocol: Playwright's own events leave CORS preflights
 * out. stop(t0) gives [kind, startMs, endMs, status] relative to t0, kind as kindOf() or 'preflight'. */
async function recordTimeline(page) {
  const cdp = await page.context().newCDPSession(page)
  await cdp.send('Network.enable')
  const seen = new Map()
  let offset = null // wall clock ms minus the protocol's monotonic ms
  cdp.on('Network.requestWillBeSent', (e) => {
    offset ??= e.wallTime * 1000 - e.timestamp * 1000
    const url = new URL(e.request.url)
    const preflight = e.type === 'Preflight' || e.initiator?.type === 'preflight' || e.request.method === 'OPTIONS'
    const kind = preflight ? 'preflight'
      : url.port === '4566' && e.request.method === 'PUT' ? 'put'
        : url.origin !== API ? null
          : { '/api/v1/files/batch': 'batch', '/api/v1/files/commit': 'commit', '/api/v1/storage/blocks/upload-urls': 'urls' }[url.pathname] ?? 'other'
    if (kind) seen.set(e.requestId, { kind, start: e.timestamp * 1000, end: null, status: 0 })
  })
  cdp.on('Network.responseReceived', (e) => { const r = seen.get(e.requestId); if (r) r.status = e.response.status })
  const end = (e) => { const r = seen.get(e.requestId); if (r) r.end = e.timestamp * 1000 }
  cdp.on('Network.loadingFinished', end)
  cdp.on('Network.loadingFailed', end)
  return {
    stop(t0) {
      cdp.detach().catch(() => {})
      return [...seen.values()].filter((r) => r.end != null)
        .map((r) => [r.kind, round1(r.start + offset - t0), round1(r.end + offset - t0), r.status])
    },
  }
}

// ---- a measurement ----

async function measure(label, rounds, scenario, users) {
  if (!SCENARIOS[scenario]) throw new Error(`unknown scenario ${scenario}`)
  // The full Chromium (new headless), not the stripped headless shell Playwright launches by default.
  const browser = await chromium.launch({ channel: 'chromium' })
  fs.mkdirSync(RESULTS, { recursive: true })
  try {
    for (let r = 0; r < rounds; r++) {
      const members = await Promise.all(Array.from({ length: users }, (_, k) => newMember(browser, k)))
      try {
        resetDb()
        const redis0 = redisCalls()
        const each = await Promise.all(members.map((m) => upload(m, scenario)))
        const out = {
          label, scenario, users, web: process.env.WEB_BUILD ?? null, round: r + 1,
          wall_ms: Math.max(...each.map((e) => e.t1)) - Math.min(...each.map((e) => e.t0)),
          total_ms: { median: median(each.map((e) => e.total_ms)), max: Math.max(...each.map((e) => e.total_ms)) },
          ok: each.every((e) => e.ok),
          db: dbCalls(), ...redisDelta(redis0),
          per_user: each.map(({ t0, t1, timeline, ...rest }) => rest),
        }
        const line = JSON.stringify(out)
        console.log(line)
        fs.appendFileSync(path.join(RESULTS, `${label}.jsonl`), line + '\n')
        if (TIMELINE) {
          for (const [k, e] of each.entries()) {
            fs.appendFileSync(path.join(RESULTS, `${label}.timeline.jsonl`), JSON.stringify({ round: r + 1, user: k, total_ms: e.total_ms, requests: e.timeline }) + '\n')
          }
        }
        if (!out.ok) throw new Error(`round ${r + 1}: ${each.map((e) => e.header).join(' / ')}`)
      } finally {
        for (const m of members) await call(m.page, 'POST', '/api/v1/auth/logout').catch(() => {})
        await Promise.all(members.map((m) => m.context.close()))
        removeMembers(members.map((m) => m.memberId))
      }
    }
  } finally {
    await browser.close()
  }
}

const [command, ...args] = process.argv.slice(2)
if (command === 'files') makeFiles()
else if (command === 'rm-files') fs.rmSync(FILES, { recursive: true, force: true })
else if (command === 'cleanup') console.log(`removed ${cleanupAll()} bench members`)
else if (command && args.length >= 2) await measure(command, Number(args[0]), args[1], Number(args[2] ?? 1))
else { console.error(fs.readFileSync(new URL(import.meta.url), 'utf8').split('\n').slice(0, 9).join('\n')); process.exit(1) }
