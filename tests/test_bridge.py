"""Protocol and loopback integration test; no Java server needed."""
import asyncio
import os
import sys
import tempfile
from pathlib import Path

from aiohttp import ClientSession, CookieJar, web

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'gateway'))
import main as gateway

SHIP = ('0|I|1|LabPilot|1|1000|0|0|4000|4000|0|100|1000|1000|1|1|0|10000|11000|1|0|0|0|1|150000|10000|0|1||0|0|0\n\0').encode()
NPC = ('0|C|504|84|1||-=[Streuner]=-|1500|1800|1|0|1|0|0|0|1|0\n\0').encode()
BOX = ('0|c|1007|2|1400|1900\n\0').encode()


async def run():
    assert gateway.decode(SHIP[:-2].decode())['x'] == 1000
    assert gateway.decode(NPC[:-2].decode())['npc']
    assert gateway.decode(BOX[:-2].decode())['x'] == 1400
    assert gateway.decode('0|c|bad|2|20|20') is None

    async def user_api(req):
        if req.path.endswith('register'):
            return web.json_response({'status':'OK','message':'Created'})
        return web.json_response({'status':'OK','data':{'account':{'id':1,'usersId':1,'sessionId':'a'*32}}})

    api = web.Application()
    api.router.add_post('/external/{action}', user_api)
    api_runner = web.AppRunner(api)
    await api_runner.setup()
    await web.TCPSite(api_runner, '127.0.0.1', 18081).start()

    seen = []
    async def raw_backend(reader, writer):
        buf=b''
        try:
            while True:
                chunk=await asyncio.wait_for(reader.read(8192),timeout=15)
                if not chunk: break
                buf+=chunk
                while b'\n\0' in buf:
                    msg,buf=buf.split(b'\n\0',1)
                    command=msg.decode()
                    seen.append(command.split('|')[0])
                    if command.startswith('LOGIN|'):
                        writer.write(SHIP);await writer.drain()
                    elif command=='RDY':
                        writer.write(NPC+BOX);await writer.drain()
        except (asyncio.TimeoutError, ConnectionError):
            pass
        finally:
            writer.close()
            await writer.wait_closed()

    tcp=await asyncio.start_server(raw_backend,'127.0.0.1',18080)
    gateway.BACKEND='http://127.0.0.1:18081'
    gateway.GAME_PORT=18080
    app=gateway.make_app()
    runner=web.AppRunner(app)
    await runner.setup()
    await web.TCPSite(runner,'127.0.0.1',18090).start()
    origin='http://127.0.0.1:18090'
    try:
        async with ClientSession(cookie_jar=CookieJar(unsafe=True)) as client:
            async with client.get(origin+'/') as r:
                assert r.status==200 and 'HTML5' in await r.text()
            async with client.post(origin+'/api/register',json={'username':'p123456789','password':'LabOnly12345!','email':'labpilot@example.com'},headers={'Origin':origin}) as r:
                assert (await r.json())['status']=='OK'
            async with client.post(origin+'/api/login',json={'username':'p123456789','password':'LabOnly12345!'},headers={'Origin':origin}) as r:
                assert r.status==200, await r.text()
            async with client.ws_connect(origin+'/ws',headers={'Origin':origin},timeout=10) as ws:
                received=set()
                for i in range(12):
                    event=await asyncio.wait_for(ws.receive_json(),timeout=7)
                    received.add(event['type'])
                    if event['type']=='ship_init': assert event['credits']==150000
                    if {'ship_init','box','entity','status'}.issubset(received): break
                assert {'ship_init','box','entity','status'}.issubset(received),received
                await ws.send_json({'action':'move','x':1600,'y':1100,'fromX':1000,'fromY':1000})
                await ws.send_json({'action':'select','id':504})
                await asyncio.sleep(.15)
            assert 'LOGIN' in seen and 'RDY' in seen and '1' in seen and 'SEL' in seen,seen
        print('SUCCESS: WebSocket bridge, session cookie, V4 ship, NPC, box, move and select')
    finally:
        await runner.cleanup()
        tcp.close();await tcp.wait_closed()
        await api_runner.cleanup()


if __name__=='__main__':
    asyncio.run(run())
