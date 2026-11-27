const http = require('http');
const fs = require('fs');
const path = require('path');
const crypto = require('crypto');

const PORT = process.env.PORT || 3456;
const METRICS_FILE = path.join(__dirname, '.metrics.json');
const SALT = process.env.METRICS_SALT || 'cryptonote-metrics-salt-v1';

const MIME_TYPES = {
  '.html': 'text/html; charset=UTF-8',
  '.css': 'text/css; charset=UTF-8',
  '.js': 'application/javascript; charset=UTF-8',
  '.json': 'application/json; charset=UTF-8',
  '.jpg': 'image/jpeg',
  '.jpeg': 'image/jpeg',
  '.png': 'image/png',
  '.webp': 'image/webp',
  '.svg': 'image/svg+xml',
  '.mp4': 'video/mp4',
  '.apk': 'application/vnd.android.package-archive',
  '.ico': 'image/x-icon'
};

function loadMetrics() {
  try {
    if (fs.existsSync(METRICS_FILE)) {
      const raw = fs.readFileSync(METRICS_FILE, 'utf8');
      const parsed = JSON.parse(raw);
      return {
        views: Array.isArray(parsed.views) ? parsed.views : [],
        downloads: Array.isArray(parsed.downloads) ? parsed.downloads : []
      };
    }
  } catch (err) {
    console.error('Error reading metrics:', err.message);
  }
  return { views: [], downloads: [] };
}

function saveMetrics(data) {
  try {
    fs.writeFileSync(METRICS_FILE, JSON.stringify(data, null, 2), 'utf8');
  } catch (err) {
    console.error('Error writing metrics:', err.message);
  }
}

function getClientIp(req) {
  const forwarded = req.headers['x-forwarded-for'];
  if (forwarded) {
    return forwarded.split(',')[0].trim();
  }
  let ip = req.socket.remoteAddress || '127.0.0.1';
  if (ip === '::1' || ip === '::ffff:127.0.0.1') {
    ip = '127.0.0.1';
  }
  return ip;
}

function hashIp(ip) {
  return crypto.createHash('sha256').update(`${ip}:${SALT}`).digest('hex');
}

const server = http.createServer((req, res) => {
  // CORS Preflight
  if (req.method === 'OPTIONS') {
    res.writeHead(204, {
      'Access-Control-Allow-Origin': '*',
      'Access-Control-Allow-Methods': 'GET, POST, OPTIONS',
      'Access-Control-Allow-Headers': 'Content-Type'
    });
    res.end();
    return;
  }

  const cleanUrl = req.url.split('?')[0];

  // Metrics API Endpoints (with SHA-256 IP Deduplication)
  if (cleanUrl.startsWith('/api/metrics') || cleanUrl.startsWith('/metrics')) {
    const metricsData = loadMetrics();

    if (req.method === 'GET') {
      res.writeHead(200, {
        'Content-Type': 'application/json; charset=UTF-8',
        'Access-Control-Allow-Origin': '*',
        'Cache-Control': 'no-store, no-cache, must-revalidate'
      });
      res.end(JSON.stringify({
        views: metricsData.views.length,
        downloads: metricsData.downloads.length
      }));
      return;
    }

    if (req.method === 'POST') {
      const ip = getClientIp(req);
      const ipHash = hashIp(ip);
      let incremented = false;

      if (cleanUrl.endsWith('/view')) {
        if (!metricsData.views.includes(ipHash)) {
          metricsData.views.push(ipHash);
          saveMetrics(metricsData);
          incremented = true;
        }
      } else if (cleanUrl.endsWith('/download')) {
        if (!metricsData.downloads.includes(ipHash)) {
          metricsData.downloads.push(ipHash);
          saveMetrics(metricsData);
          incremented = true;
        }
      }

      res.writeHead(200, {
        'Content-Type': 'application/json; charset=UTF-8',
        'Access-Control-Allow-Origin': '*',
        'Cache-Control': 'no-store, no-cache, must-revalidate'
      });
      res.end(JSON.stringify({
        views: metricsData.views.length,
        downloads: metricsData.downloads.length,
        incremented
      }));
      return;
    }
  }

  // Static File Serving with Directory Traversal Protection
  let reqPath = decodeURIComponent(cleanUrl);
  if (reqPath === '/' || reqPath === '') reqPath = '/index.html';
  const filePath = path.resolve(__dirname, '.' + (reqPath.startsWith('/') ? reqPath : '/' + reqPath));

  // Security: Prevent Directory Traversal outside website folder
  if (!filePath.startsWith(__dirname)) {
    res.writeHead(403, { 'Content-Type': 'text/plain; charset=UTF-8' });
    res.end('403 Forbidden');
    return;
  }

  fs.stat(filePath, (err, stats) => {
    if (err || !stats.isFile()) {
      res.writeHead(404, { 'Content-Type': 'text/plain; charset=UTF-8' });
      res.end('404 Not Found');
      return;
    }

    const ext = path.extname(filePath).toLowerCase();
    const contentType = MIME_TYPES[ext] || 'application/octet-stream';

    // Support HTTP Range requests for fast MP4 seeking
    const range = req.headers.range;
    if (range && ext === '.mp4') {
      const parts = range.replace(/bytes=/, '').split('-');
      const start = parseInt(parts[0], 10);
      const end = parts[1] ? parseInt(parts[1], 10) : stats.size - 1;
      const chunkSize = (end - start) + 1;
      const fileStream = fs.createReadStream(filePath, { start, end });

      res.writeHead(206, {
        'Content-Range': `bytes ${start}-${end}/${stats.size}`,
        'Accept-Ranges': 'bytes',
        'Content-Length': chunkSize,
        'Content-Type': contentType
      });
      fileStream.pipe(res);
    } else {
      res.writeHead(200, {
        'Content-Length': stats.size,
        'Content-Type': contentType,
        'Accept-Ranges': 'bytes'
      });
      fs.createReadStream(filePath).pipe(res);
    }
  });
});

server.listen(PORT, () => {
  console.log(`CryptoNote Web Showcase running at http://localhost:${PORT}/`);
});
