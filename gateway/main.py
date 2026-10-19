"""Kalaazu LAB phase 8: browser to V4 TCP bridge (lab-only, local by default).

The gateway never accepts arbitrary raw commands from a browser, and keeps
Kalaazu's session key server-side in an HttpOnly browser session.
"""
import asyncio
import os
import re
import secrets
import time
from collections import deque
from pathlib import Path

from aiohttp import ClientSession, ClientTimeout, WSMsgType, web

ROOT = Path(__file__).resolve().parents[1]
CLIENT = ROOT / "client" / "index.html"
BIND = os.getenv("BRIDGE_BIND", "127.0.0.1")
PORT = int(os.getenv("BRIDGE_PORT", "8090"))
BACKEND = os.getenv("KALAAZU_HTTP", "http://127.0.0.1:8081").rstrip("/")
GAME_HOST = os.getenv("KALAAZU_GAME_HOST", "127.0.0.1")
GAME_PORT = int(os.getenv("KALAAZU_GAME_PORT", "8080"))
PUBLIC_ORIGIN = os.getenv("BRIDGE_PUBLIC_ORIGIN", "").rstrip("/")
COOKIE_SECURE = os.getenv("BRIDGE_COOKIE_SECURE", "false").lower() == "true"
# Lab-only. Don't bind publicly unless behind TLS, firewalls, rate limits and origin config.
COOKIE_NAME = "kalaazu_lab_session"
ACCOUNTS = {}  # opaque gateway session token -> account ID, user ID and game session
MAX_SESSIONS = 20
MAX_PACKET = 65536
USERNAME = re.compile(r"^[a-zA-Z][a-zA-Z0-9_]{2,15}$")


def respond_error(message, status=400):
    return web.json_response({"status": "ERROR", "message": message}, status=status)


def valid_origin(request):
    origin = request.headers.get("Origin", "")
    expected = PUBLIC_ORIGIN or f"{request.scheme}://{request.host}"
    return origin == expected


async def home(request):
    response = web.FileResponse(CLIENT)
    response.headers["Cache-Control"] = "no-store"
    response.headers["X-Content-Type-Options"] = "nosniff"
    response.headers["Content-Security-Policy"] = (
        "default-src 'none'; script-src 'self' 'unsafe-inline'; "
        "style-src 'self' 'unsafe-inline'; connect-src 'self' ws: wss:; "
        "img-src 'self' data:; base-uri 'none'; form-action 'self'"
    )
    return response


async def health(request):
    return web.json_response({"status": "ok", "service": "kalaazu-lab-bridge", "backend": "not-checked"})


async def backend_request(request, endpoint):
    if not valid_origin(request):
        return None, respond_error("Origem não autorizada", 403)
    if request.content_length and request.content_length > 2048:
        return None, respond_error("Dados muito grandes", 413)
    try:
        payload = await request.json()
    except Exception:
        return None, respond_error("JSON inválido")
    if not isinstance(payload, dict):
        return None, respond_error("Formato inválido")
    username = str(payload.get("username", ""))
    password = str(payload.get("password", ""))
    if not USERNAME.fullmatch(username) or not (6 <= len(password) <= 128):
        return None, respond_error("Usuário ou senha fora do formato do laboratório")
    credentials = {"username": username, "password": password}
    if endpoint == "register":
        email = str(payload.get("email", ""))
        if len(email) > 80 or not re.fullmatch(r"[A-Za-z0-9_.+%-]{1,16}@[A-Za-z0-9.-]+\.[A-Za-z]{2,3}", email):
            return None, respond_error("E-mail incompatível com o cadastro do Kalaazu")
        credentials["email"] = email
    try:
        client = request.app["http_client"]
        async with client.post(f"{BACKEND}/external/{endpoint}", json=credentials) as upstream:
            if upstream.content_length and upstream.content_length > MAX_PACKET:
                return None, respond_error("Resposta da API muito grande", 502)
            data = await upstream.json(content_type=None)
            return data, None
    except Exception:
        return None, respond_error("Servidor Java indisponível", 502)


async def register(request):
    data, error = await backend_request(request, "register")
    if error:
        return error
    return web.json_response({"status": data.get("status", "ERROR"), "message": data.get("message", "")})


async def login(request):
    data, error = await backend_request(request, "login")
    if error:
        return error
    if data.get("status") != "OK":
        return respond_error(str(data.get("message", "Falha no login")), 401)
    account = (data.get("data") or {}).get("account") or {}
    try:
        account_id = int(account["id"])
        sid = str(account["sessionId"])
        if not (account_id > 0 and re.fullmatch(r"[0-9a-fA-F]{32}", sid)):
            raise ValueError("invalid account")
    except (ValueError, TypeError, KeyError):
        return respond_error("Sessão inválida retornada pelo backend", 502)
    # the account may have a different users_id; read from accounts lookup if necessary
    # The existing Kalaazu lab's user ID starts at 1 and matches account ID in a new DB;
    # for lasting deployments, use the authoritative accounts.users_id mapping.
    try:
        user_id = int(account.get("usersId") or account.get("users_id") or account_id)
    except (TypeError, ValueError):
        return respond_error("ID inválido", 502)
    if len(ACCOUNTS) >= MAX_SESSIONS:
        return respond_error("Limite de sessões LAB atingido", 429)
    token = secrets.token_urlsafe(32)
    ACCOUNTS[token] = {"account_id": account_id, "user_id": user_id, "session": sid, "time": time.monotonic()}
    response = web.json_response({"status": "OK", "pilot": {"id": account_id, "name": request.get("pilot_name", "Piloto")}})
    response.set_cookie(COOKIE_NAME, token, httponly=True, secure=COOKIE_SECURE, samesite="Strict", path="/", max_age=3600)
    return response


async def logout(request):
    if not valid_origin(request):
        return respond_error("Origem não autorizada", 403)
    ACCOUNTS.pop(request.cookies.get(COOKIE_NAME), None)
    response = web.json_response({"status": "OK"})
    response.del_cookie(COOKIE_NAME, path="/")
    return response


def as_int(s):
    return int(s)


def decode(packet):
    """Convert a safe subset of original Kalaazu V4 packets to browser events."""
    p = packet.split("|")
    if len(p) < 2 or p[0] != "0":
        return None
    tag = p[1]
    try:
        if tag == "I" and len(p) >= 26:
            return {"type": "ship_init", "id": as_int(p[2]), "name": p[3][:32],
                    "shipId": as_int(p[4]), "speed": as_int(p[5]),
                    "shield": as_int(p[6]), "maxShield": as_int(p[7]),
                    "hp": as_int(p[8]), "maxHp": as_int(p[9]),
                    "x": as_int(p[12]), "y": as_int(p[13]), "map": as_int(p[14]),
                    "credits": as_int(p[24]), "uridium": as_int(p[25])}
        if tag == "C" and len(p) >= 17:
            return {"type": "entity", "id": as_int(p[2]), "shipId": as_int(p[3]),
                    "name": p[6][:45], "x": as_int(p[7]), "y": as_int(p[8]),
                    "npc": p[15] in ("1", "true")}
        if tag == "c" and len(p) >= 6:
            return {"type": "box", "id": as_int(p[2]), "gfx": as_int(p[3]),
                    "x": as_int(p[4]), "y": as_int(p[5])}
        if tag == "p" and len(p) >= 8:
            return {"type": "portal", "id": as_int(p[2]),
                    "x": as_int(p[5]), "y": as_int(p[6])}
        if tag == "s" and len(p) >= 9:
            return {"type": "station", "id": as_int(p[2]),
                    "x": as_int(p[7]), "y": as_int(p[8])}
        if tag == "1" and len(p) >= 6:
            return {"type": "movement", "id": as_int(p[2]),
                    "x": as_int(p[3]), "y": as_int(p[4]), "duration": as_int(p[5])}
        if tag == "2" and len(p) >= 3:
            return {"type": "remove_box", "id": as_int(p[2])}
    except (ValueError, IndexError):
        return None
    return None


async def bridge(request):
    if not valid_origin(request):
        return respond_error("WebSocket: origem não permitida", 403)
    token = request.cookies.get(COOKIE_NAME)
    user = ACCOUNTS.get(token)
    if not user or (time.monotonic() - user["time"] > 3600):
        return respond_error("Faça login antes de conectar", 401)
    try:
        reader, writer = await asyncio.wait_for(asyncio.open_connection(GAME_HOST, GAME_PORT), timeout=5)
    except (OSError, asyncio.TimeoutError):
        return respond_error("Servidor TCP offline", 502)
    socket = web.WebSocketResponse(heartbeat=30, max_msg_size=2048, autoping=True)
    await socket.prepare(request)
    await socket.send_json({"type": "status", "state": "connecting"})
    login_packet = f'LOGIN|{user["user_id"]}|{user["session"]}|V4\n\0'
    writer.write(login_packet.encode("utf-8"))
    await writer.drain()
    timestamps = deque(maxlen=20)
    initialized = asyncio.Event()
    async def tcp_to_ws():
        buf = bytearray()
        try:
            while not socket.closed:
                part = await reader.read(16384)
                if not part:
                    break
                buf.extend(part)
                if len(buf) > MAX_PACKET * 4:
                    raise ValueError("TCP stream buffer exceeded")
                while True:
                    end = buf.find(b"\n\x00")
                    if end == -1:
                        break
                    raw = bytes(buf[:end]); del buf[:end+2]
                    if len(raw) > MAX_PACKET:
                        continue
                    packet = raw.decode("utf-8", errors="replace")
                    event = decode(packet)
                    if event:
                        await socket.send_json(event)
                        if event["type"] == "ship_init" and not initialized.is_set():
                            # Same ordering as the successful GitHub Actions TCP test.
                            writer.write(b"RDY\n\0")
                            await writer.drain()
                            initialized.set()
                            await socket.send_json({"type": "status", "state": "ready"})
        except (OSError, ConnectionError, ValueError, asyncio.CancelledError):
            pass
        finally:
            if not socket.closed:
                await socket.close()
    forward = asyncio.create_task(tcp_to_ws())
    try:
        async for msg in socket:
            if msg.type != WSMsgType.TEXT or not initialized.is_set():
                continue
            try:
                data = msg.json()
            except ValueError:
                continue
            if not isinstance(data, dict):
                continue
            now = time.monotonic()
            while timestamps and now - timestamps[0] > 1:
                timestamps.popleft()
            if len(timestamps) >= 12:
                continue
            action = data.get("action")
            command = None
            try:
                if action == "move":
                    x = as_int(data["x"]); y = as_int(data["y"])
                    fx = as_int(data["fromX"]); fy = as_int(data["fromY"])
                    if 0 <= x <= 20800 and 0 <= y <= 12800 and 0 <= fx <= 20800 and 0 <= fy <= 12800:
                        command = f"1|{x}|{y}|{fx}|{fy}"
                elif action == "select":
                    entity_id = as_int(data["id"])
                    if 0 < entity_id < 10000000:
                        command = f"SEL|{entity_id}"
            except (TypeError, ValueError, KeyError):
                continue
            if command:
                timestamps.append(now)
                writer.write(command.encode("utf-8") + b"\n\0")
                await writer.drain()
    finally:
        forward.cancel()
        writer.close()
        try:
            await writer.wait_closed()
        except OSError:
            pass
        await asyncio.gather(forward, return_exceptions=True)
    return socket


async def lifecycle(app):
    timeout = ClientTimeout(total=15)
    app["http_client"] = ClientSession(timeout=timeout)
    yield
    await app["http_client"].close()


def make_app():
    app = web.Application(client_max_size=2048)
    app.cleanup_ctx.append(lifecycle)
    app.router.add_get("/", home)
    app.router.add_get("/healthz", health)
    app.router.add_post("/api/register", register)
    app.router.add_post("/api/login", login)
    app.router.add_post("/api/logout", logout)
    app.router.add_get("/ws", bridge)
    return app


if __name__ == "__main__":
    if BIND not in ("127.0.0.1", "::1", "localhost"):
        print("WARNING: public binding requires TLS, origin allowlisting and production hardening", flush=True)
    web.run_app(make_app(), host=BIND, port=PORT, access_log=None)
