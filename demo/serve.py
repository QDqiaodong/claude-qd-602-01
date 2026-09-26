#!/usr/bin/env python3
"""最小静态服务器：dist 目录 + /api 反代到后端，供本地直接演示前端。"""
import http.server
import os
import sys
import urllib.request
from http.server import ThreadingHTTPServer

ROOT = os.path.join(os.path.dirname(__file__), "..", "frontend", "dist")
ROOT = os.path.abspath(ROOT)
PORT = int(os.environ.get("DEMO_PORT", "8262"))
BACKEND = os.environ.get("BACKEND", "http://127.0.0.1:8362")


class Handler(http.server.SimpleHTTPRequestHandler):
    def __init__(self, *args, **kwargs):
        super().__init__(*args, directory=ROOT, **kwargs)

    def do_GET(self):
        if self.path.startswith("/api/"):
            return self.proxy()
        return super().do_GET()

    def do_POST(self):
        if self.path.startswith("/api/"):
            return self.proxy()
        self.send_error(405)

    def proxy(self):
        length = int(self.headers.get("Content-Length") or 0)
        body = self.rfile.read(length) if length else None
        req = urllib.request.Request(BACKEND + self.path, data=body, method=self.command)
        ct = self.headers.get("Content-Type")
        if ct:
            req.add_header("Content-Type", ct)
        try:
            with urllib.request.urlopen(req, timeout=15) as resp:
                data = resp.read()
                self.send_response(resp.status)
                self.send_header("Content-Type", resp.headers.get("Content-Type", "application/json"))
                self.send_header("Content-Length", str(len(data)))
                self.end_headers()
                self.wfile.write(data)
        except urllib.error.HTTPError as e:
            data = e.read()
            self.send_response(e.code)
            self.send_header("Content-Type", "application/json")
            self.send_header("Content-Length", str(len(data)))
            self.end_headers()
            self.wfile.write(data)
        except Exception as e:
            payload = ('{"ok":false,"message":"后端不可达: %s"}' % str(e)).encode()
            self.send_response(502)
            self.send_header("Content-Type", "application/json")
            self.send_header("Content-Length", str(len(payload)))
            self.end_headers()
            self.wfile.write(payload)


if __name__ == "__main__":
    print(f"serving {ROOT} on :{PORT}, proxy /api -> {BACKEND}")
    ThreadingHTTPServer(("0.0.0.0", PORT), Handler).serve_forever()
