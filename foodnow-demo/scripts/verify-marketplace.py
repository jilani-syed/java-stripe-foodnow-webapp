#!/usr/bin/env python3
"""Isolated HTTP integration checks. No Stripe calls or changes to your demo data."""
import json, os, pathlib, subprocess, tempfile, time, urllib.request, urllib.error, http.cookiejar, uuid
ROOT=pathlib.Path(__file__).resolve().parents[1]
BASE='http://localhost:4253'
checks=0

def verify(condition, message):
    global checks
    assert condition, message
    checks+=1

class Client:
    def __init__(self, role='diner'):
        self.role=role
        self.locale="en-US"
        self.opener=urllib.request.build_opener(urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()))
        self.csrf=''
        self.csrf=self.get('/api/session')['csrf']
    def call(self, method, path, data=None, expected=200, csrf=True):
        headers={'X-FoodNow-Portal':self.role,'X-FoodNow-Locale':self.locale}
        if csrf: headers['X-CSRF-Token']=self.csrf
        if data is not None: headers['Content-Type']='application/json'
        req=urllib.request.Request(BASE+path, None if data is None else json.dumps(data).encode(),headers,method=method)
        try:
            response=self.opener.open(req,timeout=15); code=response.status; raw=response.read()
        except urllib.error.HTTPError as e: code=e.code; raw=e.read()
        verify(code==expected, f'{method} {path}: expected {expected}, got {code}: {raw[:300]!r}')
        return json.loads(raw) if raw else None
    def get(self,path,expected=200): return self.call('GET',path,expected=expected)
    def post(self,path,data=None,expected=200,csrf=True): return self.call('POST',path,data or {},expected,csrf)
    def login(self,email):
        result=self.post('/api/auth/login',{'role':self.role,'email':email,'password':'FoodNowDemo!2026'})
        self.csrf=result['csrf']; return self

with tempfile.TemporaryDirectory(prefix='foodnow-verify-') as tmp:
    env=dict(os.environ,FOODNOW_MODE='rehearsal',PORT='4253',MARKETPLACE_STORE_PATH=tmp+'/marketplace.json',ORDER_STORE_PATH=tmp+'/legacy.json',STRIPE_PUBLISHABLE_KEY='',STRIPE_SECRET_KEY='',STRIPE_WEBHOOK_SECRET='')
    log=open(tmp+'/server.log','w+')
    process=None
    def start():
        global process
        process=subprocess.Popen(['java','-jar',str(ROOT/'target/foodnow-demo-1.0.0.jar')],cwd=ROOT,env=env,stdout=log,stderr=subprocess.STDOUT)
        for _ in range(120):
            try:
                with urllib.request.urlopen(BASE+'/health',timeout=1) as r:
                    if json.load(r).get('version')==2:return
            except (OSError,ValueError):pass
            if process.poll() is not None:break
            time.sleep(.25)
        log.seek(0);raise RuntimeError(log.read()[-5000:])
    def stop():
        if process and process.poll() is None:
            process.terminate();process.wait(timeout=15)
    try:
        start()
        guest=Client(); stranger=Client(); ops=Client('operations').login('ops@foodnow.demo')
        owner=Client('restaurant').login('basil@foodnow.demo'); other=Client('restaurant').login('bun@foodnow.demo')
        courier=Client('courier').login('courier@foodnow.demo'); courier2=Client('courier').login('jamie@foodnow.demo')
        guest.get('/api/workspace',401)
        guest.get('/api/stripe/account',401)
        owner.get('/api/stripe/account',403)
        verify(ops.get('/api/stripe/account')['connected'] is False,'Rehearsal cannot claim a Stripe connection')
        guest.post('/api/auth/logout',csrf=False,expected=403)
        guest.post('/api/auth/login',{'role':'operations','email':'diner@foodnow.demo','password':'FoodNowDemo!2026'},401)
        guest.post('/api/auth/register',{'role':'operations'},400)
        catalog=guest.get('/api/config'); verify(len(catalog['restaurants'])==5,'Five cuisines seeded')
        body={'checkoutToken':str(uuid.uuid4()),'items':[{'dishId':'1','quantity':1}],'email':'guest@example.com','name':'Guest Alex','address':'123 Demo Lane, Chicago'}
        order=guest.post('/api/checkout',body); oid=order['orderId']; path='/api/orders/'+oid
        verify(order['amount']==2198,'Server price includes transparent fees')
        verify(guest.post('/api/checkout',body)['orderId']==oid,'Checkout retry is idempotent')
        guest.post('/api/checkout',dict(body,items=[{'dishId':'1','quantity':2}]),400)
        stranger.get(path,404);other.get(path,404)
        owner.post(path+'/status',{'next':'accepted'},400)
        owner.post('/api/partners/restaurant/basil/review',{'decision':'approved'},403)
        owner.post('/api/menu',{'id':'2','title':'Wrong owner','description':'No','amount':1500,'available':True},400)
        guest.post(path+'/simulate',{'outcome':'requires_payment_method','method':'Card'})
        verify(owner.get('/api/workspace')['ledger']==[],'Decline creates no payable')
        guest.post(path+'/simulate',{'outcome':'succeeded','method':'Link'})
        before=len(ops.get('/api/workspace')['ledger'])
        guest.post(path+'/simulate',{'outcome':'succeeded','method':'Card'})
        verify(len(ops.get('/api/workspace')['ledger'])==before,'Duplicate success produces one sale journal')
        owner.post(path+'/status',{'next':'ready'},400)
        for state in ['accepted','preparing','ready']:owner.post(path+'/status',{'next':state,'confirmed':False})
        verify(len(courier.get('/api/workspace')['availableOrders'])==1,'Ready deliveries appear in courier queue')
        courier.post(path+'/assign')
        verify(courier.get(path)['pickupAddress']=='River North, Chicago','Assigned courier sees restaurant pickup address')
        owner.post(path+'/stripe-evidence',expected=403)
        ops.post(path+'/stripe-evidence',expected=400)
        courier2.post(path+'/assign',expected=400)
        courier2.get(path,404)
        ops.post(path+'/allocate',expected=400)
        courier.post(path+'/status',{'next':'picked_up','confirmed':False})
        courier.post(path+'/status',{'next':'delivered','confirmed':False},400)
        courier.post(path+'/status',{'next':'delivered','confirmed':True})
        verify(guest.get(path)['stage']=='delivered','Diner sees courier delivery confirmation')
        settled=ops.post(path+'/allocate'); ledger=ops.get('/api/workspace')['ledger']
        verify(len(settled['transfers'])==2,'Both partners receive simulated transfers')
        verify(ops.post(path+'/allocate')['transfers']==settled['transfers'],'Settlement retry retains transfer IDs')
        verify(len(ops.get('/api/workspace')['ledger'])==len(ledger),'Settlement retry does not double debit')
        journals={}
        for entry in ledger:journals[entry['journal']]=journals.get(entry['journal'],0)+entry['debit']-entry['credit']
        verify(all(v==0 for v in journals.values()),'Every journal balances')
        verify(all(e['partnerId']=='basil' for e in owner.get('/api/workspace')['ledger']),'Owner ledger is scoped')
        verify(all(e['partnerId']=='courier_1' for e in courier.get('/api/workspace')['ledger']),'Courier ledger is scoped')
        verify(other.get('/api/workspace')['orders']==[],'Other restaurant never sees the order')
        owner.post('/api/menu',{'id':'1','title':'Margherita','description':'New price','amount':1800,'available':True})
        verify(guest.post('/api/checkout',body)['amount']==2198,'Existing checkout retains old menu price')
        new=guest.post('/api/checkout',dict(body,checkoutToken=str(uuid.uuid4())))
        verify(new['amount']==2398,'New checkout uses updated menu price')
        owner.post('/api/menu',{'id':'1','title':'Margherita','description':'Sold out','amount':1800,'available':False})
        guest.post('/api/checkout',dict(body,checkoutToken=str(uuid.uuid4())),400)
        verify(not any(i['id']=='1' for i in guest.get('/api/config')['catalog']),'Sold out items removed from storefront')
        applicant=Client('restaurant')
        registration=applicant.post('/api/auth/register',{'role':'restaurant','name':'Ravi','email':'ravi@example.com','password':'ExamplePassword2026','businessName':'Ravi Kitchen','cuisine':'Indian','address':'456 Demo Lane'})
        rid=registration['user']['entityId']
        applicant.post('/api/menu',{'title':'Dal','description':'Lentils','amount':1200,'available':True})
        verify(not any(r['id']==rid for r in guest.get('/api/config')['restaurants']),'Pending restaurant hidden')
        applicant.post('/api/connect/start')
        verify(applicant.get('/api/workspace')['restaurants'][0]['approval']=='pending','Connect onboarding does not grant marketplace approval')
        ops.post('/api/partners/restaurant/'+rid+'/review',{'decision':'approved','note':'Demo review complete'})
        verify(any(r['id']==rid for r in guest.get('/api/config')['restaurants']),'Approved restaurant becomes discoverable')
        newcourier=Client('courier')
        cr=newcourier.post('/api/auth/register',{'role':'courier','name':'Maya','email':'maya@example.com','password':'ExamplePassword2026','vehicle':'Bicycle'})
        newcourier.post('/api/connect/start')
        ops.post('/api/partners/courier/'+cr['user']['entityId']+'/review',{'decision':'approved'})
        verify(newcourier.get('/api/workspace')['couriers'][0]['approval']=='approved','Courier registration and approval persist')
        diner=Client().login('diner@foodnow.demo')
        verify(diner.get('/api/orders')==[],'Unrelated account cannot see guest orders')
        guest.login('diner@foodnow.demo')
        verify(len(diner.get('/api/orders'))==2,'Guest orders claimed only by login in owning session')
        guest.role='restaurant';guest.login('basil@foodnow.demo');guest.role='diner'
        verify(guest.get('/api/session')['user']['role']=='diner','Parallel persona login preserves diner slot')
        verify(guest.get(path)['id']==oid,'Diner history survives partner login')
        stop();start()
        restored=Client('operations').login('ops@foodnow.demo').get('/api/workspace')
        verify(len(restored['orders'])==2 and len(restored['ledger'])==len(ledger),'Orders and journal survive restart')
        verify(any(r['id']==rid and r['approval']=='approved' for r in restored['restaurants']),'Registration survives restart')
        localized=Client()
        ops=Client('operations').login('ops@foodnow.demo')
        for locale,currency in [('en-GB','gbp'),('fr-FR','eur')]:
            localized.locale=locale
            request=dict(body,checkoutToken=str(uuid.uuid4()),items=[{'dishId':'4','quantity':1}])
            result=localized.post('/api/checkout',request)
            verify(result['currency']==currency and result['amount']==2398,'Locale sets real checkout currency and server price')
            verify(localized.post('/api/checkout',request)['orderId']==result['orderId'],'Localized checkout retries remain idempotent')
            localized.post('/api/orders/'+result['orderId']+'/simulate',{'outcome':'succeeded','method':'Card'})
            localized.locale='en-US'
            localized.post('/api/checkout',request,400)
            verify(localized.get('/api/orders/'+result['orderId'])['currency']==currency,'Order retains original currency across locale changes')
            ops.locale=locale
            scoped=ops.get('/api/workspace')
            verify(len(scoped['orders'])==1 and all(o['currency']==currency for o in scoped['orders']),'Dashboard orders do not mix currencies')
            verify(all(e['currency']==currency for e in scoped['ledger']),'Payable journal is currency scoped')
            verify(sum(e['debit']-e['credit'] for e in scoped['ledger'])==0,'Localized journal balances')
        summary=ops.get('/api/insights')
        verify({x['currency'] for x in summary}=={'usd','gbp','eur'},'Insights explicitly group currencies')
        verify(all(x['paidVolume']==2398 and x['averageOrderValue']==2398 for x in summary if x['currency']!='usd'),'GBP and EUR insights use independent totals')
        localized.get('/api/insights',401)
        localized.locale='de-DE'
        localized.post('/api/checkout',dict(body,checkoutToken=str(uuid.uuid4())),400)
        stop();start()
        summary=Client('operations').login('ops@foodnow.demo').get('/api/insights')
        verify(all(x['paidVolume']==2398 for x in summary if x['currency']!='usd'),'Localized order currencies and insights survive restart')
        print(f'PASS: {checks} HTTP and business assertions, including restart persistence. Isolated temporary data removed.')
    finally:stop();log.close()
