// Read-only, focused pre-publication audit. Never prints matching secret values.
const fs = require('fs');
const cp = require('child_process');
const allowed = /^(sky-take-out\/|project-rjwm-admin-vue-ts\/|mp-weixin\/|nginx-1\.20\.2\/conf\/|scripts\/|IMPROVEMENTS\.md$|PROJECT\.md$|README\.md$|\.gitignore$)/;
const git = (...args) => cp.execFileSync('git', args, { encoding: 'utf8', maxBuffer: 32 * 1024 * 1024, stdio: ['ignore', 'pipe', 'pipe'] });
const files = [...new Set(git('ls-files', '--cached', '--others', '--exclude-standard', '-z').split('\0').filter(f => f && allowed.test(f)))];
const known = new Set();
// Learn old deployment secrets without storing them in the script/report.
for (const path of ['sky-take-out/sky-server/src/main/resources/application-dev.yml', 'sky-take-out/sky-server/src/main/resources/application.yml']) {
  let original;
  try { original = git('show', '267313f948bba2001fbc497995a39ef61f2c621f:' + path); } catch (_) { continue; }
  for (const line of original.split(/\r?\n/)) {
    const match = line.match(/^\s*(?:password|secret|access-key-id|access-key-secret|apiV3Key|admin-secret-key|user-secret-key|ak):\s*["']?([^"'#\s]+)["']?/i);
    if (match && match[1].length >= 8 && !match[1].includes('${')) known.add(match[1]);
  }
}
let findings = 0;
let inspected = 0;
for (const path of files) {
  if (!fs.existsSync(path) || !fs.statSync(path).isFile()) continue;
  const buffer = fs.readFileSync(path);
  if (buffer.includes(0)) continue;
  const contents = buffer.toString('utf8');
  inspected++;
  const reasons = [];
  if ([...known].some(secret => contents.includes(secret))) reasons.push('known legacy credential');
  if (/LTAI[A-Za-z0-9]{16,}|AKIA[0-9A-Z]{16}|gh[pousr]_[A-Za-z0-9]{30,}|github_pat_[A-Za-z0-9_]{40,}|-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----/.test(contents)) reasons.push('credential/private-key pattern');
  if (reasons.length) { findings++; console.error(path + ': ' + reasons.join(', ') + ' [value redacted]'); }
}
console.log(`Publication audit: ${inspected} text files, ${known.size} legacy values checked, ${findings} findings.`);
console.log('This focused scan is not a guarantee that every possible secret has been detected.');
process.exitCode = findings ? 1 : 0;
