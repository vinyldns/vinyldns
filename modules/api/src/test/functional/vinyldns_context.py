class VinylDNSTestContext:
    name_server_ip: str = None
    resolver_ip: str = None
    dns_zone_name: str = None
    dns_key_name: str = None
    dns_key: str = None
    dns_key_algo: str = None
    vinyldns_url: str = None
    teardown: bool = False
    enable_safety_check: bool = False

    @staticmethod
    def configure(name_server_ip: str, resolver_ip: str, zone: str, key_name: str, key: str, key_algo: str, url: str, teardown: bool, enable_safety_check: bool = False) -> None:
        VinylDNSTestContext.name_server_ip = name_server_ip
        VinylDNSTestContext.resolver_ip = resolver_ip
        VinylDNSTestContext.dns_zone_name = zone
        VinylDNSTestContext.dns_key_name = key_name
        VinylDNSTestContext.dns_key = key
        VinylDNSTestContext.dns_key_algo = key_algo
        VinylDNSTestContext.vinyldns_url = url
        VinylDNSTestContext.teardown = teardown
        VinylDNSTestContext.enable_safety_check = enable_safety_check