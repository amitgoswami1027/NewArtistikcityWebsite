"""Fake Razorpay (only what the reconciler / refunds use): orders, order payments, capture, refund + /_control."""
import json, re, sys, itertools
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
ORDERS, PAYMENTS, REFUNDS = {}, {}, []
seq = itertools.count(1)
class H(BaseHTTPRequestHandler):
    def log_message(self, *a): pass
    def send(self, code, body):
        b = json.dumps(body).encode(); self.send_response(code); self.send_header('Content-Type', 'application/json'); self.send_header('Content-Length', str(len(b))); self.end_headers(); self.wfile.write(b)
    def body(self):
        n = int(self.headers.get('Content-Length') or 0); return json.loads(self.rfile.read(n) or b'{}')
    def do_GET(self):
        m = re.match(r'^/v1/orders/([^/]+)/payments$', self.path)
        if m: return self.send(200, {'items': [p for p in PAYMENTS.values() if p['order_id'] == m.group(1)]})
        if self.path == '/_control/state': return self.send(200, {'orders': ORDERS, 'payments': PAYMENTS, 'refunds': REFUNDS})
        self.send(404, {'error': {'code': 'NOT_FOUND'}})
    def do_POST(self):
        b = self.body()
        if self.path == '/v1/orders':
            oid = 'order_F%04d' % next(seq); ORDERS[oid] = {'id': oid, **b}; return self.send(200, ORDERS[oid])
        m = re.match(r'^/v1/payments/([^/]+)/capture$', self.path)
        if m:
            PAYMENTS[m.group(1)]['status'] = 'captured'; return self.send(200, PAYMENTS[m.group(1)])
        m = re.match(r'^/v1/payments/([^/]+)/refund$', self.path)
        if m:
            if m.group(1) not in PAYMENTS: return self.send(400, {'error': {'code': 'BAD_REQUEST_ERROR', 'description': 'payment not found'}})
            rid = 'rfnd_F%04d' % next(seq); REFUNDS.append({'id': rid, 'payment_id': m.group(1), **b}); return self.send(200, {'id': rid, 'status': 'processed'})
        if self.path == '/_control/pay':
            pid = 'pay_F%04d' % next(seq); PAYMENTS[pid] = {'id': pid, 'order_id': b['order_id'], 'status': b.get('status', 'captured'), 'amount': b['amount'], 'currency': 'INR'}
            return self.send(200, PAYMENTS[pid])
        self.send(404, {'error': {'code': 'NOT_FOUND'}})
ThreadingHTTPServer(('127.0.0.1', int(sys.argv[1]) if len(sys.argv) > 1 else 9998), H).serve_forever()
