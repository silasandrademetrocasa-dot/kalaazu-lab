"""Render staging access gate, wrapping the original Kalaazu bridge.

Not a complete production auth system. Keep staging private to testers.
Requires KALAAZU_PREVIEW_KEY and TLS on the upstream Render proxy.
"""
import hashlib
import hmac
import html
import os
import sys
import time
from collections import deque
from pathlib import Path
from aiohttp import web

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'gateway'))
from main import make_app, BIND, PORT  # noqa: E402

KEY = os.environ['KALAAZU_PREVIEW_KEY'].encode()
COOKIE = 'kalaazu_preview_gate'
COOKIE_VALUE = hmac.new(KEY, b'kalaazu-lab-phase9-staging', hashlib.sha256).hexdigest()
FAILURES = {}


def gate_html(msg=''):
    return web.Response(text='''<!doctype html><html lang="pt-BR"><meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1"><title>Kalaazu LAB — Acesso de testes</title>
<style>body{font:16px system-ui;color:#def;background:#07101d;display:grid;place-items:center;min-height:95vh}
main{background:#10213a;border:1px solid #2b5674;padding:30px;border-radius:16px;width:min(400px,90vw)}
input,button{width:100%;padding:12px;margin-top:12px;background:#06101a;border:1px solid #488da9;color:white;border-radius:7px}
button{background:#11647e;cursor:pointer}small{color:#acc9dd}</style><main><h2>✦ Kalaazu LAB</h2>
<p>Acesso restrito ao ambiente de testes.</p><form action="/lab-access" method="post">
<input type="password" name="key" placeholder="Chave de acesso" required autofocus>
<button>Entrar</button></form><p>''' + html.escape(msg) + '''</p><small>Esta é uma versão experimental, não a versão pública do jogo.</small></main></html>''', content_type='text/html')


@web.middleware
async def staging_gate(request, handler):
    if request.path == '/healthz':
        return await handler(request)
    if request.path == '/lab-access':
        if request.method == 'GET':
            return gate_html()
        if request.method != 'POST':
            raise web.HTTPMethodNotAllowed(request.method, ['GET', 'POST'])
        ip = request.remote or 'unknown'
        now = time.monotonic()
        attempts = FAILURES.setdefault(ip, deque())
        while attempts and attempts[0] < now - 600:
            attempts.popleft()
        if len(attempts) >= 5:
            return web.Response(status=429, text='Tente novamente mais tarde.')
        form = await request.post()
        supplied = str(form.get('key', '')).encode()
        if len(supplied) > 256 or not hmac.compare_digest(supplied, KEY):
            attempts.append(now)
            return gate_html('Chave incorreta.')
        FAILURES.pop(ip, None)
        raise_cookie = web.HTTPFound('/')
        raise_cookie.set_cookie(COOKIE, COOKIE_VALUE, max_age=43200,
                                httponly=True, secure=True, samesite='Strict', path='/')
        raise raise_cookie
    provided = request.cookies.get(COOKIE, '')
    if not hmac.compare_digest(provided, COOKIE_VALUE):
        raise web.HTTPFound('/lab-access')
    return await handler(request)


app = make_app()
app.middlewares.insert(0, staging_gate)
if __name__ == '__main__':
    web.run_app(app, host=BIND, port=PORT, access_log=None)
