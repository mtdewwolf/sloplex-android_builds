#!/usr/bin/env python3
"""Build the audited Go source, create an isolated test account/library, run the actual Retrofit client.
Usage: python3 scripts/run-server-contracts.py --server-source /path/to/sloplex
Requires JDK 17, Go 1.24+, ffmpeg/ffprobe and the Android SDK. Never targets an existing user server.
"""
from pathlib import Path
import argparse,json,os,secrets,shutil,socket,subprocess,tempfile,time,urllib.request,urllib.error
parser=argparse.ArgumentParser()
parser.add_argument('--server-source',type=Path,required=True)
args=parser.parse_args()
android=Path(__file__).resolve().parents[1]
with tempfile.TemporaryDirectory(prefix='sloplex-contract-') as tmp:
    root=Path(tmp); media=root/'movies';media.mkdir()
    shutil.copyfile(android/'app-mobile/src/androidTest/assets/test.mp4',media/'Sloplex Fixture (2026).mp4')
    binary=root/'sloplex'
    subprocess.run(['go','build','-o',str(binary),'./cmd/sloplex'],cwd=args.server_source,check=True)
    with socket.socket() as s: s.bind(('127.0.0.1',0));port=s.getsockname()[1]
    recovery=secrets.token_hex(32)
    config={'friendlyName':'Android contract fixture','listen':f'127.0.0.1:{port}','dataDir':str(root/'data'),'discovery':False,'allowedNetworks':[],'tokens':[recovery],'watch':False,'scanInterval':'0','updateCheck':False,'hwaccel':'none','gpuToneMapping':'off','x264Preset':'ultrafast','libraries':[{'title':'Fixtures','type':'movie','paths':[str(media)]}]}
    (root/'config.json').write_text(json.dumps(config))
    base=f'http://127.0.0.1:{port}/'
    def request(path,body=None):
        req=urllib.request.Request(base+path,data=None if body is None else json.dumps(body).encode(),headers={'Accept':'application/json','X-Plex-Token':recovery,'Content-Type':'application/json'})
        with urllib.request.urlopen(req,timeout=5) as response: return json.load(response)
    with open(root/'server.log','w') as log:
        server=subprocess.Popen([str(binary),'-config',str(root/'config.json')],stdout=log,stderr=log)
        try:
            for _ in range(60):
                if server.poll() is not None: raise RuntimeError('Fixture server exited; inspect it locally')
                try:
                    sections=request('library/sections')['MediaContainer'].get('Directory',[])
                    if sections and request('library/sections/'+sections[0]['key']+'/all')['MediaContainer'].get('Metadata'): break
                except (OSError,urllib.error.URLError): pass
                time.sleep(0.5)
            else: raise RuntimeError('Fixture library did not finish scanning')
            request('sloplex/accounts',{'name':'android-fixture','password':'sloplex-local-contract-test-password','admin':False,'libraries':None})
            env=os.environ.copy();env['SLOPLEX_TEST_SERVER']=base
            subprocess.run([str(android/'gradlew'),':core:model:test',':core:network:test','--rerun-tasks','--no-daemon','--max-workers=2','-Dorg.gradle.jvmargs=-Xmx1536m','--console=plain'],cwd=android,env=env,check=True)
        finally: server.terminate();server.wait(timeout=10)
