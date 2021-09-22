import requests
import json

newzone = "http://localhost:9000/zones"


names = ["cap", "video", "aae", "papi", "dns-ops", "ios", "home", "android", "games", "viper", "headwaters", "xtv", "consec", "media", "accounts"];

records = ["10.25.3.2","155.65.10.3", "10.1.1.1", "168.82.76.5", "192.168.99.88", "FE80:0000:0000:0000:0202:B3FF:FE1E:8329", "GF77:0000:0000:0000:0411:B3DF:FE2E:4444", "CC42:0000:0000:0000:0509:B3FF:FE3E:6543", "BG50:0000:0000:0000:0203:C2EE:G3F4:9823","AA90:0000:0000:0000:0608:C2EE:FE4E:1234", "staging", "test", "admin", "assets", "admin"];

for x in range(0, 15):
    zonename = names[x]
    zoneemail = 'testuser'+ str(x) +'@example.com'
    payload = {"name": zonename, "origin": "vinyldns", "email": zoneemail}
    headers = {'Content-type': 'application/json'}
    r = requests.post(newzone, data=json.dumps(payload),headers=headers)
    print(r.text)


zones = requests.get(newzone)
zone_data = zones.json()

z=0
for i in zone_data['zones']:
    if z<5:
        z=z+1
        recurl = newzone + '/' + str(i['id']) + '/recordsets'
        print (recurl)
        payload = {"zoneId":i['id'],"name":"record."+i['name'],"type":"A","ttl":300,"records":[{"address":records[z-1]}]}
        headers = {'Content-type': 'application/json'}
        r = requests.post(recurl, data=json.dumps(payload),headers=headers)
        print(r.text)
    elif 4<z<10:
        z=z+1
        recurl = newzone + '/' + str(i['id']) + '/recordsets'
        print (recurl)
        payload = {"zoneId":i['id'],"name":"record."+i['name'],"type":"AAAA","ttl":1800,"records":[{"address":records[z-1]}]}
        headers = {'Content-type': 'application/json'}
        r = requests.post(recurl, data=json.dumps(payload),headers=headers)
        print(r.text)
    else:
        z=z+1
        recurl = newzone + '/' + str(i['id']) + '/recordsets'
        print (recurl)
        payload = {"zoneId":i['id'],"name":"record."+i['name'],"type":"CNAME","ttl":10800,"records":[{"cname":records[z-1]}]}
        headers = {'Content-type': 'application/json'}
        r = requests.post(recurl, data=json.dumps(payload),headers=headers)
        print(r.text)
