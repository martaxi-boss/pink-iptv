"""Read-only, credential-free output of actual known-account Xtream metadata structure."""
import os
from pathlib import Path
import subprocess

CHILD = r'''
import hashlib,json,logging,re
import httpx
from sqlalchemy import select
from app.config import get_settings
from app.clients.mega import MegaOTTClient
from app.db import build_session_factory
from app.models import SubscriptionMapping
from app.outbound_transport import PinnedHTTPTransport
from app.url_safety import resolve_dns_link_target,player_api_url
logging.disable(logging.CRITICAL)
settings=get_settings()
with MegaOTTClient(base_url=settings.mega_ott_api_base,token=settings.require_mega_token()) as mega:
    line=mega.get_subscription(9040240)
    assert hashlib.md5(line.username.encode()).hexdigest()=='325a5df019165e41992baaa59133987d'  # pragma: allowlist secret -- previously authorized test-account fingerprint
with build_session_factory(settings.database_url)() as db:
    mapping=db.scalar(select(SubscriptionMapping).where(SubscriptionMapping.mega_subscription_id==9040240))
    assert mapping is not None and mapping.username==line.username and mapping.dns_link==line.dns_link
target=resolve_dns_link_target(line.dns_link)
client=httpx.Client(transport=PinnedHTTPTransport(target),timeout=10,follow_redirects=False,trust_env=False,headers={'User-Agent':'PINK-IPTV/0.1'})
def request(action,extra=None):
    params={'username':line.username,'password':line.password.get_secret_value(),'action':action,**(extra or {})}
    with client.stream('GET',player_api_url(target.origin),params=params) as reply:
        assert reply.status_code==200
        raw=bytearray()
        for chunk in reply.iter_bytes():
            raw.extend(chunk)
            assert len(raw)<=16*1024*1024
    return json.loads(raw)
allowed={'info','movie_data','episodes','subtitles','subtitle','subtitle_tracks','subtitle_url','text_tracks','audio','streams','url','src','uri','file','language','lang','label','mime_type','mimetype','codec_type','codec_name','tags','index','title','type','format'}
def structure(value,path='',depth=0):
    found=[]
    if depth>7: return found
    if isinstance(value,dict):
        for key,item in value.items():
            key=str(key)
            if key not in allowed and not key.isdecimal() and not (re.fullmatch(r'[A-Za-z_][A-Za-z0-9_]{0,39}',key) and ('subtit' in key.lower() or 'caption' in key.lower())): continue
            next_path=(path+'.'+key).strip('.')
            if 'subtit' in key.lower() or 'caption' in key.lower() or key=='text_tracks':
                found.append({'path':next_path,'kind':type(item).__name__,
                    'fields':sorted(k for k in item if k in allowed) if isinstance(item,dict) else [],
                    'items':min(len(item),20) if isinstance(item,list) else None})
            found.extend(structure(item,next_path,depth+1))
    elif isinstance(value,list):
        for item in value[:3]: found.extend(structure(item,path+'[]',depth+1))
    return found
for catalog,info,id_key in [('get_vod_streams','get_vod_info','vod_id'),('get_series','get_series_info','series_id')]:
    rows=request(catalog)
    assert isinstance(rows,list) and rows
    value=str(rows[0].get('stream_id' if id_key=='vod_id' else 'series_id',''))
    assert re.fullmatch('[0-9]+',value)
    metadata=request(info,{id_key:value})
    assert isinstance(metadata,dict)
    print('REAL_'+id_key.upper()+'_SUBTITLE_METADATA_STRUCTURE='+json.dumps(structure(metadata),separators=(',',':')))
client.close()
'''


def main():
    assert os.geteuid() == 0
    def run(*args):
        return subprocess.check_output(args, text=True, timeout=10).strip()
    assert run('hostname') == 'vps-32bea5b6'
    assert run('systemctl', 'show', 'pink-iptv-backend', '-p', 'WorkingDirectory', '--value') == '/srv/pink-iptv/backend'
    pid = int(run('systemctl', 'show', 'pink-iptv-backend', '-p', 'MainPID', '--value'))
    assert pid > 1
    allowed = {'APP_ENV', 'DATABASE_URL', 'SESSION_SIGNING_KEY', 'SESSION_TTL_SECONDS', 'MEGA_OTT_API_BASE', 'MEGA_OTT_API_TOKEN'}
    environment = {'PATH': '/usr/bin:/bin', 'PYTHONDONTWRITEBYTECODE': '1'}
    for item in Path(f'/proc/{pid}/environ').read_bytes().split(b'\0'):
        if b'=' in item:
            key, value = item.split(b'=', 1)
            if key.decode() in allowed:
                environment[key.decode()] = value.decode()
    child = subprocess.run(['/srv/pink-iptv/backend/.venv/bin/python', '-c', CHILD],
        cwd='/srv/pink-iptv/backend', env=environment, capture_output=True, text=True, timeout=90)
    assert child.returncode == 0
    print(child.stdout, end='')


if __name__ == '__main__':
    try:
        main()
    except Exception:
        print('REAL_PROVIDER_VOD_METADATA=UNAVAILABLE')
        raise SystemExit(1)
