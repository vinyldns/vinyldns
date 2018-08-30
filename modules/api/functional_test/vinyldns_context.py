class VinylDNSTestContext:
    dns_ip = 'localhost'
    dns_zone_name = 'vinyldns.'
    dns_rev_v4_zone_name = '30.172.in-addr.arpa.'
    dns_rev_v6_zone_name = '1.9.e.f.c.c.7.2.9.6.d.f.ip6.arpa.'
    dns_key_name = 'vinyldns.'
    dns_key = 'wCZZS9lyRr77+jqfnkZ/92L9fD5ilmfrG0sslc3mgmTFsF1fRgmtJ0rj RkFITt8VHQ37wvM/nI9MAIWXYTvMqg=='
    dns_no_updates_key_name = 'vinyldns-no-updates'
    dns_no_updates_key = '1GOhWm/nwqlQop1YQ6sl96eVTjULth0E7LonKB6X4uycygaCUQRG2JPQ kHVFgp768cyUuCv4j/tvL8C+cUCkcA=='
    vinyldns_url = 'http://localhost:9000'

    @staticmethod
    def configure(ip, zone, key_name, key, url):
        VinylDNSTestContext.dns_ip = ip
        VinylDNSTestContext.dns_zone_name = zone
        VinylDNSTestContext.dns_key_name = key_name
        VinylDNSTestContext.dns_key = key
        VinylDNSTestContext.vinyldns_url = url
