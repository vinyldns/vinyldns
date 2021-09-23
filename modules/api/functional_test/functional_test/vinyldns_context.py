class VinylDNSTestContext:
    dns_ip = 'localhost'
    dns_zone_name = 'vinyldns.'
    dns_rev_v4_zone_name = '10.10.in-addr.arpa.'
    dns_rev_v6_zone_name = '1.9.e.f.c.c.7.2.9.6.d.f.ip6.arpa.'
    dns_key_name = 'vinyldns.'
    dns_key = 'nzisn+4G2ldMn0q1CV3vsg=='
    vinyldns_url = 'http://localhost:9000'
    teardown = True

    @staticmethod
    def configure(ip, zone, key_name, key, url, teardown):
        VinylDNSTestContext.dns_ip = ip
        VinylDNSTestContext.dns_zone_name = zone
        VinylDNSTestContext.dns_key_name = key_name
        VinylDNSTestContext.dns_key = key
        VinylDNSTestContext.vinyldns_url = url
        VinylDNSTestContext.teardown = teardown.lower() == 'true'
