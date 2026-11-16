// Minimal static server for the e2e test page: e2e/index.html and the
// shadow-cljs :e2e build output in target/e2e/js.
import http from 'node:http';
import fs from 'node:fs';
import path from 'node:path';

const root = path.resolve(import.meta.dirname, '..');
const port = Number(process.env.PORT || 8091);
const types = { '.html': 'text/html', '.js': 'text/javascript', '.map': 'application/json' };

http.createServer((req, res) => {
  const url = new URL(req.url, 'http://localhost').pathname;
  const file = url === '/'
    ? path.join(root, 'e2e/index.html')
    : url.startsWith('/js/')
      ? path.join(root, 'target/e2e', path.normalize(url))
      : null;
  if (!file || !file.startsWith(root)) {
    res.writeHead(404).end();
    return;
  }
  fs.readFile(file, (err, data) => {
    if (err) {
      res.writeHead(404).end();
    } else {
      res.writeHead(200, { 'Content-Type': types[path.extname(file)] || 'application/octet-stream' });
      res.end(data);
    }
  });
}).listen(port, '127.0.0.1', () => console.log(`e2e server on http://127.0.0.1:${port}`));
