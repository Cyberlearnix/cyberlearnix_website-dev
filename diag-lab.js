const https = require('https');

// Read password from stdin first line
const rl = require('readline').createInterface({ input: process.stdin, output: process.stdout });
rl.question('Password: ', async (pwd) => {
  rl.close();

  function post(url, body) {
    return new Promise((res, rej) => {
      const b = JSON.stringify(body);
      const req = https.request(url, { method: 'POST', headers: { 'Content-Type': 'application/json', 'Content-Length': Buffer.byteLength(b) } }, (r) => {
        let d = ''; r.on('data', c => d += c); r.on('end', () => r.statusCode < 400 ? res(JSON.parse(d)) : rej(new Error(r.statusCode + ': ' + d)));
      });
      req.on('error', rej); req.write(b); req.end();
    });
  }
  function get(url, token) {
    return new Promise((res, rej) => {
      const req = https.request(url, { headers: { Authorization: 'Bearer ' + token } }, (r) => {
        let d = ''; r.on('data', c => d += c); r.on('end', () => r.statusCode < 400 ? res(JSON.parse(d)) : rej(new Error(r.statusCode + ': ' + d)));
      });
      req.on('error', rej); req.end();
    });
  }

  try {
    const login = await post('https://apis.cyberlearnix.com/api/auth/login', { email: 'shivakumar@cyberlearnix.com', password: pwd });
    const token = login.accessToken || login.token;
    console.log('Token OK');

    const active = await get('https://apis.cyberlearnix.com/api/labs/admin/active', token);
    if (!active.length) { console.log('No active assignments'); return; }
    active.forEach(x => {
      console.log(`\nAssignment #${x.assignment.id}  containerId=${x.assignment.containerId}  status=${x.assignment.status}`);
      console.log(`  containerStats: ${JSON.stringify(x.containerStats)}`);
    });
  } catch (e) { console.error('FAIL', e.message); }
});
