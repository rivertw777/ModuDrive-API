# S3 fault proxy: storage-service and the presigned PUTs -> :8080 -> localstack:4566.
# Failure mode switched on :8081 — /?m=<mode>&s=<sec>&ops=<op,...|all>&after=<n>:
#   ops    which S3 calls fail: get | head | list | put | delete | bucket (HeadBucket etc.) — others pass
#   after  the first n matching calls pass, the rest fail (e.g. after=1: the 2nd block of a download)
import http.client, http.server, select, socket, socketserver, sys, threading, time
from urllib.parse import urlsplit, parse_qs

UPSTREAM = ("localstack", 4566)
state = {"mode": "pass", "sec": 0, "ops": "all", "after": 0, "seen": 0}
inflight = 0  # S3 calls open at once — each holds one of the caller's connections
lock = threading.Lock()
# Host is passed on as sent: it's in the SigV4 signature, which LocalStack checks on presigned URLs.
HOP = {"connection", "keep-alive", "transfer-encoding", "content-length"}

def err(code, msg):
    return f'<?xml version="1.0" encoding="UTF-8"?><Error><Code>{code}</Code><Message>{msg}</Message><RequestId>fault</RequestId></Error>'.encode()

FAULTS = {
    "unavailable": (503, err("SlowDown", "Please reduce your request rate.")),
    "denied": (403, err("AccessDenied", "Access Denied")),
}

def op(method, path, query):
    key = path.lstrip("/").partition("/")[2]
    if method == "GET" and not key:
        return "list" if "list-type" in query else "bucket"
    if not key:
        return "bucket"
    return {"GET": "get", "HEAD": "head", "PUT": "put", "DELETE": "delete"}.get(method, "other")

class Proxy(http.server.BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"
    def log_message(self, *a): pass

    def handle_any(self):
        global inflight
        with lock:
            inflight += 1
        try:
            self.handle_call()
        finally:
            with lock:
                inflight -= 1

    def handle_call(self):
        url = urlsplit(self.path)
        name = op(self.command, url.path, url.query)
        body = self.rfile.read(int(self.headers.get("Content-Length", 0)))
        with lock:
            mode = state["mode"]
            if mode != "pass" and (state["ops"] == "all" or name in state["ops"].split(",")):
                state["seen"] += 1
                if state["seen"] <= state["after"]:
                    mode = "pass"
            else:
                mode = "pass"
            sec = state["sec"]
            open_now = inflight
        # One write per line: print() from several threads interleaves its text and newline.
        sys.stdout.write(f"{time.strftime('%H:%M:%S')} S3 {name} {url.path[:80]} mode={mode} inflight={open_now}\n"); sys.stdout.flush()

        if mode == "hang":
            return self.hold()
        if mode in FAULTS:
            status, payload = FAULTS[mode]
            return self.reply(status, [("Content-Type", "application/xml")], payload)
        if mode == "slow":
            time.sleep(sec)
        status, headers, payload = self.forward(body)
        if mode == "lost":  # S3 did the work; the caller never hears back
            return self.hold()
        if mode == "corrupt" and payload:
            payload = payload[:len(payload) // 2] + bytes([payload[len(payload) // 2] ^ 0xFF]) + payload[len(payload) // 2 + 1:]
        if mode == "cut" and payload:
            # Full Content-Length promised, half the bytes sent, then the connection drops.
            self.send_response(status)
            for k, v in headers: self.send_header(k, v)
            self.send_header("Content-Length", str(len(payload)))
            self.end_headers()
            self.wfile.write(payload[:len(payload) // 2]); self.wfile.flush()
            self.close_connection = True
            return
        self.reply(status, headers, payload)

    def hold(self):
        """Answer nothing until the caller gives up and closes the connection, so inflight counts only
        calls someone still waits on — and a hung call doesn't outlive its scenario."""
        self.close_connection = True
        for _ in range(3600):
            if select.select([self.connection], [], [], 1)[0]:
                try:
                    if not self.connection.recv(1, socket.MSG_PEEK):
                        return
                except OSError:
                    return

    def forward(self, body):
        conn = http.client.HTTPConnection(*UPSTREAM, timeout=60)
        try:
            conn.request(self.command, self.path, body=body or None,
                         headers={k: v for k, v in self.headers.items() if k.lower() not in HOP})
            r = conn.getresponse()
            payload = r.read()
            headers = [(k, v) for k, v in r.getheaders() if k.lower() not in HOP]
            if self.command == "HEAD":  # no body, but the object's size is the answer
                headers.append(("Content-Length", r.getheader("Content-Length", "0")))
            return r.status, headers, payload
        finally:
            conn.close()

    def reply(self, status, headers, payload):
        self.send_response(status)
        for k, v in headers: self.send_header(k, v)
        if self.command != "HEAD":
            self.send_header("Content-Length", str(len(payload)))
        elif not any(k.lower() == "content-length" for k, _ in headers):
            self.send_header("Content-Length", "0")
        self.end_headers()
        if self.command != "HEAD":
            self.wfile.write(payload)

    do_GET = do_HEAD = do_PUT = do_DELETE = do_POST = do_OPTIONS = handle_any

class Control(http.server.BaseHTTPRequestHandler):
    def log_message(self, *a): pass
    def do_GET(self):
        q = parse_qs(urlsplit(self.path).query)
        with lock:
            state["mode"] = q.get("m", [state["mode"]])[0]
            state["sec"] = int(q.get("s", [state["sec"]])[0])
            state["ops"] = q.get("ops", [state["ops"]])[0]
            state["after"] = int(q.get("after", [state["after"]])[0])
            state["seen"] = 0
        print(f"{time.strftime('%H:%M:%S')} === mode -> {state}", flush=True)
        self.send_response(200); self.end_headers(); self.wfile.write(str(state).encode())

class Server(socketserver.ThreadingMixIn, http.server.HTTPServer):
    daemon_threads = True

threading.Thread(target=Server(("", 8081), Control).serve_forever, daemon=True).start()
Server(("", 8080), Proxy).serve_forever()
