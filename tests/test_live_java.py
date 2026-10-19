"""Run this *inside GitHub Actions* while Java and the bridge are listening."""
import asyncio
import os
from aiohttp import ClientSession, CookieJar

URL = 'http://127.0.0.1:8090'


async def run():
    username = 'p' + os.environ['GITHUB_RUN_ID']
    password = 'LabOnly12345!'
    async with ClientSession(cookie_jar=CookieJar(unsafe=True)) as client:
        async with client.get(URL + '/healthz') as reply:
            assert reply.status == 200
        async with client.post(URL + '/api/register', headers={'Origin': URL}, json={
            'username': username, 'password': password, 'email': username + '@example.com'
        }) as reply:
            data = await reply.json()
            assert data.get('status') == 'OK', data.get('message', 'registration failed')
            print('SUCCESS: register through gateway', flush=True)
        async with client.post(URL + '/api/login', headers={'Origin': URL}, json={
            'username': username, 'password': password
        }) as reply:
            data = await reply.json()
            assert reply.status == 200 and data.get('status') == 'OK', data.get('message', 'login failed')
            print('SUCCESS: HTTP login with gateway HttpOnly cookie', flush=True)
        async with client.ws_connect(URL + '/ws', headers={'Origin': URL}, timeout=30) as ws:
            events=set()
            npc_id=None
            for _ in range(180):
                try:
                    msg=await asyncio.wait_for(ws.receive_json(),timeout=3)
                except asyncio.TimeoutError:
                    break
                events.add(msg['type'])
                if msg['type']=='entity' and msg.get('npc'):
                    npc_id=msg['id']
                if msg['type']=='ship_init':
                    assert msg['map']==1, 'Player did not spawn in 1-1'
                    print('SUCCESS: real Java Phoenix initialization received over WebSocket', flush=True)
                if msg['type']=='status' and msg.get('state')=='ready':
                    print('SUCCESS: real LOGIN + RDY handshake bridged', flush=True)
                if {'ship_init','status','entity','box'}.issubset(events):
                    break
            assert 'ship_init' in events and 'status' in events,events
            # Only valid, allowlisted gameplay commands; no password/session in browser.
            await ws.send_json({'action':'move','x':1600,'y':1100,'fromX':1000,'fromY':1000})
            if npc_id:
                await ws.send_json({'action':'select','id':npc_id})
            await asyncio.sleep(3)
            print('SUCCESS: Browser-to-Java game commands submitted safely', flush=True)
    print('SUCCESS: KALAAZU LAB PHASE 8 BRIDGE VERIFIED', flush=True)

if __name__=='__main__':
    asyncio.run(run())
