// Node.js WebSocket terminal test
// Usage: node test-ws-node.js <token> <assignmentId>
// Quick run via npm script below

const WebSocket = require('ws');
const https = require('https');

const BASE = 'https://apis.cyberlearnix.com';
const EMAIL = process.env.LAB_EMAIL || 'shivakumar@cyberlearnix.com';

async function login(password) {
  return new Promise((resolve, reject) => {
    const body = JSON.stringify({ email: EMAIL, password });
    const req = https.request(`${BASE}/api/auth/login`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'Content-Length': Buffer.byteLength(body) }
    }, res => {
      let data = '';
      res.on('data', d => data += d);
      res.on('end', () => {
        if (res.statusCode !== 200) return reject(new Error(`Login ${res.statusCode}: ${data}`));
        resolve(JSON.parse(data));
      });
    });
    req.on('error', reject);
    req.write(body);
    req.end();
  });
}

async function apiGet(path, token) {
  return new Promise((resolve, reject) => {
    const req = https.request(`${BASE}${path}`, {
      headers: { Authorization: `Bearer ${token}` }
    }, res => {
      let data = '';
      res.on('data', d => data += d);
      res.on('end', () => {
        if (res.statusCode === 404) return resolve(null);
        if (res.statusCode >= 400) return reject(new Error(`GET ${path} → ${res.statusCode}: ${data}`));
        resolve(JSON.parse(data));
      });
    });
    req.on('error', reject);
    req.end();
  });
}

async function apiPost(path, token, body) {
  return new Promise((resolve, reject) => {
    const bodyStr = JSON.stringify(body);
    const req = https.request(`${BASE}${path}`, {
      method: 'POST',
      headers: {
        Authorization: `Bearer ${token}`,
        'Content-Type': 'application/json',
        'Content-Length': Buffer.byteLength(bodyStr)
      }
    }, res => {
      let data = '';
      res.on('data', d => data += d);
      res.on('end', () => {
        if (res.statusCode >= 400) return reject(new Error(`POST ${path} → ${res.statusCode}: ${data}`));
        resolve(JSON.parse(data));
      });
    });
    req.on('error', reject);
    req.write(bodyStr);
    req.end();
  });
}

function decodeJwtSub(token) {
  const b64 = token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/');
  return JSON.parse(Buffer.from(b64, 'base64').toString('utf8')).sub;
}

async function testTerminal(token, assignmentId) {
  const wsUrl = `wss://apis.cyberlearnix.com/labs/terminal/${assignmentId}?token=${token}`;
  console.log(`\n=== WebSocket Terminal Test (assignment #${assignmentId}) ===`);
  console.log(`  Connecting...`);

  return new Promise((resolve) => {
    const ws = new WebSocket(wsUrl, {
      perMessageDeflate: false,          // disable compression to avoid RSV1 mismatch through gateway proxy
      handshakeTimeout: 10000
    });
    let received = '';
    let timer;

    ws.on('unexpected-response', (req, res) => {
      console.log(`  FAIL Upgrade rejected: HTTP ${res.statusCode}`);
      let d = ''; res.on('data', c => d += c); res.on('end', () => console.log('  Body:', d.substring(0, 200)));
      clearTimeout(timer); resolve(false);
    });

    ws.on('open', () => {
      console.log('  PASS WebSocket connected');
      // send echo right away
      ws.send('echo TERMINAL_WORKS\n');
      console.log('  >> Sent: echo TERMINAL_WORKS');

      // overall timeout
      timer = setTimeout(() => {
        console.log('\n  WARN No TERMINAL_WORKS in output after 15s');
        if (received.length > 0) {
          console.log('  PASS Terminal IS streaming output (shell alive)');
          console.log('  Preview:', received.replace(/[\x00-\x08\x0B\x0C\x0E-\x1F\x7F]/g, '.').substring(0, 300));
        }
        ws.close();
        resolve(received.length > 0);
      }, 15000);
    });

    ws.on('message', (data) => {
      const text = data.toString('utf8');
      received += text;
      const safe = text.replace(/[\x00-\x08\x0B\x0C\x0E-\x1F\x7F]/g, '.').substring(0, 120);
      console.log(`  << ${data.length} bytes: ${safe}`);
      if (received.includes('TERMINAL_WORKS')) {
        clearTimeout(timer);
        console.log('\n  PASS Terminal echo confirmed - lab terminal is WORKING');
        ws.close();
        resolve(true);
      }
    });

    ws.on('error', (err) => {
      console.log('  FAIL WebSocket error:', err.message);
      clearTimeout(timer);
      resolve(false);
    });

    ws.on('close', (code, reason) => {
      clearTimeout(timer);
      console.log(`  WebSocket closed (code=${code} reason=${reason})`);
      if (!received.includes('TERMINAL_WORKS') && received.length === 0) {
        console.log('  WARN No output received from terminal');
      }
      resolve(received.length > 0);
    });
  });
}

async function main() {
  // Prompt for password
  const rl = require('readline').createInterface({ input: process.stdin, output: process.stdout });
  const password = await new Promise(res => rl.question(`Password for ${EMAIL}: `, ans => { rl.close(); res(ans); }));
  if (!password) { console.error('No password entered'); process.exit(1); }

  // 1. Login
  console.log(`\n=== Login as ${EMAIL} ===`);
  const loginResp = await login(password);
  const token = loginResp.accessToken || loginResp.token;
  if (!token) { console.error('FAIL No token in login response'); process.exit(1); }
  const userId = decodeJwtSub(token);
  console.log(`  PASS token obtained  userId=${userId}`);

  // 2. Find or create active assignment
  let assignmentId = parseInt(process.argv[2] || '0', 10);
  if (!assignmentId) {
    console.log('\n=== Find/Create Lab Assignment ===');
    const myLab = await apiGet('/api/labs/my-lab', token);
    if (myLab) {
      assignmentId = myLab.assignment.id;
      console.log(`  Found assignment #${assignmentId}  status=${myLab.assignment.status}`);
    } else {
      console.log('  No active lab - assigning from first template...');
      const templates = await apiGet('/api/labs/templates', token);
      if (!templates || templates.length === 0) { console.error('FAIL No templates'); process.exit(1); }
      const tpl = templates[0];
      console.log(`  Using template #${tpl.id} (${tpl.name}  image: ${tpl.dockerImage})`);
      const assigned = await apiPost('/api/labs/assign', token, { studentId: userId, templateId: tpl.id });
      assignmentId = assigned.id;
      console.log(`  PASS Assigned lab #${assignmentId}  status=${assigned.status}`);
      console.log('  Waiting 5s for container to start...');
      await new Promise(r => setTimeout(r, 5000));
    }
  }

  // 3. Test WebSocket terminal
  const ok = await testTerminal(token, assignmentId);
  process.exit(ok ? 0 : 1);
}

main().catch(err => { console.error('FAIL', err.message); process.exit(1); });
