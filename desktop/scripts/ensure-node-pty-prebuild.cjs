/**
 * node-pty loads build/Release before prebuilds/. A failed electron-rebuild leaves
 * an empty build/ dir and breaks PTY. Remove it when official prebuilds exist.
 */
const fs = require('node:fs')
const path = require('node:path')

try {
  const pkgJson = require.resolve('node-pty/package.json')
  const root = path.dirname(pkgJson)
  const prebuildDir = path.join(root, 'prebuilds', `${process.platform}-${process.arch}`)
  const buildDir = path.join(root, 'build')

  if (fs.existsSync(prebuildDir) && fs.existsSync(buildDir)) {
    fs.rmSync(buildDir, { recursive: true, force: true })
    console.log('[postinstall] node-pty: removed stale build/ — using prebuilds/')
  }
} catch (err) {
  console.warn('[postinstall] node-pty prebuild check skipped:', err.message)
}
