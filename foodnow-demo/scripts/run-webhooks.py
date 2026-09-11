#!/usr/bin/env python3
"""Run Stripe test webhooks and privately update the local signing secret."""
import os,re,json,subprocess,urllib.request,urllib.error, pathlib,signal,sys
path=pathlib.Path(__file__).resolve().parents[1]/'.env'
def read():
 return {k.strip():v.strip().strip('\"\'') for line in path.read_text().splitlines() if line.strip() and not line.lstrip().startswith('#') and '=' in line for k,v in [line.split('=',1)]}
v=read()
if not v.get('STRIPE_SECRET_KEY','').startswith('sk_test_'):sys.exit('Valid test secret key required')
req=urllib.request.Request('https://api.stripe.com/v1/account',headers={'Authorization':'Bearer '+v['STRIPE_SECRET_KEY']})
try:
 with urllib.request.urlopen(req,timeout=30) as r:a=json.load(r)
except urllib.error.HTTPError as e:sys.exit('Stripe account validation failed: HTTP '+str(e.code)+'. No credentials displayed.')
except Exception:sys.exit('Could not reach Stripe account API. No credentials displayed.')
print('Verified Stripe test credentials for account '+a['id']+' ('+str(a.get('country'))+').',flush=True)
env=dict(os.environ,STRIPE_API_KEY=v['STRIPE_SECRET_KEY'])
p=subprocess.Popen(['stripe','listen','--skip-update','--events','payment_intent.succeeded,payment_intent.processing,payment_intent.payment_failed,payment_intent.canceled','--forward-to','localhost:4242/webhook'],env=env,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,text=True,bufsize=1)
def stop(*args):
 p.terminate();sys.exit(0)
signal.signal(signal.SIGTERM,stop);signal.signal(signal.SIGINT,stop)
for line in p.stdout:
 m=re.search(r'whsec_[A-Za-z0-9]+',line)
 if m:
  content=path.read_text()
  for key,value in [('STRIPE_WEBHOOK_SECRET',m.group()),('FOODNOW_MODE','stripe')]:
   content=re.sub(r'^'+key+r'=.*$',lambda _:key+'='+value,content,flags=re.M) if re.search(r'^'+key+r'=',content,re.M) else content+'\n'+key+'='+value+'\n'
  path.write_text(content);path.chmod(0o600)
  print('Listener ready. Its signing secret was saved privately to .env; Stripe mode selected.',flush=True)
 else:
  line=re.sub(r'(?:sk|pk|rk)_(?:test|live)_[A-Za-z0-9]+','[redacted]',line)
  print(line.rstrip(),flush=True)
sys.exit(p.wait())
