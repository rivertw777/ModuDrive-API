# SES fault proxy: mail-service -> :8080 -> localstack:4566, failure mode switched on :8081 (/?m=<mode>&s=<sec>)
import http.server, socketserver, threading, time, urllib.request, urllib.error, sys

UPSTREAM = "http://localstack:4566"
state = {"mode": "pass", "sec": 0}

def err(code, typ, msg):
    return (f'<ErrorResponse xmlns="http://ses.amazonaws.com/doc/2010-12-01/"><Error><Type>{typ}</Type>'
            f'<Code>{code}</Code><Message>{msg}</Message></Error><RequestId>fault</RequestId></ErrorResponse>').encode()

FAULTS = {
    "unavailable": (503, err("ServiceUnavailable", "Receiver", "Service is unavailable")),
    "throttle": (400, err("Throttling", "Sender", "Maximum sending rate exceeded.")),
    "rejected": (400, err("MessageRejected", "Sender", "Email address is not verified.")),
    "paused": (400, err("AccountSendingPausedException", "Sender", "Account sending paused.")),
    "noconfigset": (400, err("ConfigurationSetDoesNotExist", "Sender", "Configuration set mail-events does not exist.")),
}

class Proxy(http.server.BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"
    def log_message(self, *a): pass
    def do_POST(self):
        body = self.rfile.read(int(self.headers.get("Content-Length", 0)))
        mode, sec = state["mode"], state["sec"]
        print(f"{time.strftime('%H:%M:%S')} SES call mode={mode}", flush=True)
        if mode == "hang":
            time.sleep(3600); return
        if mode == "slow":
            time.sleep(sec)
        if mode in FAULTS:
            status, payload = FAULTS[mode]
        else:
            req = urllib.request.Request(UPSTREAM + self.path, data=body, method="POST",
                                         headers={k: v for k, v in self.headers.items() if k.lower() != "host"})
            try:
                with urllib.request.urlopen(req) as r: status, payload = r.status, r.read()
            except urllib.error.HTTPError as e: status, payload = e.code, e.read()
        self.send_response(status)
        self.send_header("Content-Type", "text/xml")
        self.send_header("Content-Length", str(len(payload)))
        self.end_headers()
        self.wfile.write(payload)

class Control(http.server.BaseHTTPRequestHandler):
    def log_message(self, *a): pass
    def do_GET(self):
        from urllib.parse import urlparse, parse_qs
        q = parse_qs(urlparse(self.path).query)
        state["mode"] = q.get("m", [state["mode"]])[0]
        state["sec"] = int(q.get("s", [state["sec"]])[0])
        print(f"{time.strftime('%H:%M:%S')} === mode -> {state}", flush=True)
        self.send_response(200); self.end_headers(); self.wfile.write(str(state).encode())

class Server(socketserver.ThreadingMixIn, http.server.HTTPServer):
    daemon_threads = True

threading.Thread(target=Server(("", 8081), Control).serve_forever, daemon=True).start()
Server(("", 8080), Proxy).serve_forever()
