// Upload under an S3 fault, as the WEB does it: real Chromium runs the WEB's own uploadGroup (upload-file.ts,
// bundled as is) — first commit, upload URLs, block PUTs straight to S3 through the proxy, second commit.
// Run by scenario.sh put. node put.mjs <user> <files> [capSeconds] — <files> files of one 4MB block, one group.
// Prints one JSON line per PUT attempt, then a summary. Tools (Playwright, rolldown) come from the WEB checkout
// next to this repo (env WEB_DIR, default ../ModuDrive-WEB; its Chromium once: npx playwright install chromium);
// env UPLOAD_FILE: another upload-file.ts to run, e.g. an older version's from a worktree.
import { createRequire } from 'node:module'
import { createHash, randomBytes } from 'node:crypto'
import { execFileSync } from 'node:child_process'
const WEB_DIR = process.env.WEB_DIR ?? `${process.cwd()}/../ModuDrive-WEB`
const require = createRequire(`${WEB_DIR}/package.json`)
const { chromium } = require('playwright')
const { build } = await import(require.resolve('rolldown'))

const [user, files, cap = '900'] = process.argv.slice(2)
const BLOCK = 4 * 1024 * 1024
const data = Array.from({ length: +files }, () => randomBytes(BLOCK))
const hashes = data.map((b) => createHash('sha256').update(b).digest('hex'))

// apiClient as the WEB's interceptor leaves it: the payload on success, else an Error with status and Retry-After.
const API = `export const apiClient = { post: async (url, body) => {
  const r = await window.api(url, body)
  if (r.status >= 200 && r.status < 300) return r.body.data
  throw Object.assign(new Error(r.body?.message ?? 'request failed'), { status: r.status ?? undefined, retryAfter: r.retryAfter })
} }`
const { output } = await build({
  input: process.env.UPLOAD_FILE ?? `${WEB_DIR}/src/features/upload/api/upload-file.ts`,
  write: false,
  output: { format: 'iife', name: 'Upload' },
  plugins: [{ name: 'api', resolveId: (id) => (id === '@/lib/api-client' ? '\0api' : null), load: (id) => (id === '\0api' ? API : null) }],
})

const browser = await chromium.launch({ args: ['--disable-features=LocalNetworkAccessChecks'] }) // the route-served page isn't a loopback origin to Chrome; the real WEB on localhost:3000 is
const page = await browser.newPage()
let t0 // the first API call: the page's uploadGroup starts on the same instant
const calls = []
await page.exposeFunction('api', (url, body) => {
  t0 ??= Date.now()
  const start = Date.now() - t0
  const r = JSON.parse(execFileSync('docker', ['exec', 's3-fault', 'python', '/app/client.py', 'api', user, url, JSON.stringify(body)]).toString())
  const results = r.body?.data?.results
  calls.push({ api: url.split('/').pop(), start, end: Date.now() - t0, status: r.status,
    ...(results && { files: results.map((f) => f.error?.status ?? (f.needBlocks?.length ? 'need' : 'ok')).join(',') }) })
  return r
})
// Logs each PUT the upload code sends (XHR now, fetch before) without touching that code.
await page.addInitScript(() => {
  const at = () => Math.round(performance.now() - window.t0)
  window.puts = []
  const open = XMLHttpRequest.prototype.open
  XMLHttpRequest.prototype.open = function (method, url, ...rest) {
    const entry = { url: String(url).split('?')[0].slice(-12) }
    this.addEventListener('loadstart', () => (entry.start = at()))
    this.addEventListener('loadend', () => window.puts.push({ ...entry, end: at(), status: this.status }))
    return open.call(this, method, url, ...rest)
  }
  const fetch = window.fetch
  window.fetch = (url, init) => {
    const entry = { url: String(url).split('?')[0].slice(-12), start: at() }
    return fetch(url, init).then(
      (r) => (window.puts.push({ ...entry, end: at(), status: r.status }), r),
      (e) => (window.puts.push({ ...entry, end: at(), error: String(e) }), Promise.reject(e)),
    )
  }
})
await page.route('http://localhost:3000/', (r) => r.fulfill({ contentType: 'text/html', body: '<html></html>' }))
await page.goto('http://localhost:3000/')
await page.addScriptTag({ content: output[0].code })
const result = await page.evaluate(
  async ({ blocks, hashes, cap }) => {
    const at = () => Math.round(performance.now() - window.t0)
    const targets = blocks.map((b, i) => ({
      path: '/',
      name: `s3-fault-${hashes[i].slice(0, 8)}.bin`,
      file: new File([Uint8Array.from(atob(b), (c) => c.charCodeAt(0))], 'block'),
    }))
    const pauses = []
    window.t0 = performance.now() // the PUT log's clock too
    const run = window.Upload.uploadGroup(
      targets,
      hashes.map((h) => [h]),
      () => {},
      (paused) => pauses.push({ paused, at: at() }),
    ).then(
      (outcomes) => outcomes.map((o) => (o === null ? 'ok' : `failed: ${o.status ?? ''} ${o.message}`)),
      (e) => [`group failed: ${e.status ?? ''} ${e.message}`],
    )
    const outcomes = await Promise.race([run, new Promise((r) => setTimeout(() => r(['cap reached']), cap * 1000))])
    return { outcomes, pauses, ms: at() }
  },
  { blocks: data.map((b) => b.toString('base64')), hashes, cap: +cap },
)
for (const p of await page.evaluate(() => window.puts)) console.log(JSON.stringify(p))
for (const c of calls) console.log(JSON.stringify(c))
const tally = {}
for (const o of result.outcomes) tally[o] = (tally[o] ?? 0) + 1
console.log(JSON.stringify({ outcomes: tally, pauses: result.pauses, ms: result.ms }))
await browser.close()
