const fs = require('fs');
const html = fs.readFileSync('docs/interactive-architecture.html', 'utf8');

const expectedNodes = [
  'flutter', 'staff', 'otherbank', 'gateway',
  'auth', 'customer', 'transaction', 'orchestration', 'risk', 'notification',
  'redis', 'kafka', 'dlt', 'identity_db', 'customer_db', 'transaction_db', 'notification_db',
  'account', 'ledger', 'audit', 'batch', 'oracle', 'audit_store'
];

let missing = [];
expectedNodes.forEach(id => {
  if (!html.includes('id="node-' + id + '"')) {
    missing.push(id);
  }
});

console.log('Nodes check:', missing.length === 0 ? 'ALL 23 NODES PRESENT' : 'MISSING: ' + missing.join(', '));

const match = html.match(/const FLOW_CONNECTIONS = (\[[\s\S]*?\]);/);
if (match) {
  const connections = eval(match[1]);
  console.log('Connections count:', connections.length);
  let badConn = [];
  connections.forEach(c => {
    if (!expectedNodes.includes(c.from)) badConn.push('Invalid from: ' + c.from);
    if (!expectedNodes.includes(c.to)) badConn.push('Invalid to: ' + c.to);
  });
  console.log('Bad connections:', badConn.length === 0 ? 'NONE (ALL 43 VALID)' : badConn.join(', '));
} else {
  console.log('FLOW_CONNECTIONS not matched!');
}
